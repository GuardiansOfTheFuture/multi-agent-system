package com.paperai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.mapper.KnowledgeChunkMapper;
import com.paperai.mapper.KnowledgeDocumentMapper;
import com.paperai.model.entity.KnowledgeChunk;
import com.paperai.model.entity.KnowledgeDocument;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@Slf4j
@Service
public class KnowledgeService {

    @Resource private KnowledgeDocumentMapper docMapper;
    @Resource private KnowledgeChunkMapper chunkMapper;
    @Resource private EmbeddingModel embeddingModel;

    // ===== 上传入库 =====

    public KnowledgeDocument upload(MultipartFile file, Long userId, String scope) throws Exception {
        String filename = file.getOriginalFilename();
        String ext = filename != null ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "txt";
        String title = filename != null ? filename.replaceAll("\\.[^.]+$", "") : null;

        // 1. Tika 解析
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        List<Document> docs = reader.read();
        if (docs.isEmpty()) throw new RuntimeException("文档未提取到文本内容");

        // 2. 先插 MySQL 拿 docId
        int totalChars = docs.stream().mapToInt(d -> d.getText().length()).sum();
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setUserId(userId);
        kd.setFilename(filename);
        kd.setFileType(ext);
        kd.setTitle(title);
        kd.setScope(scope != null ? scope : "PRIVATE");
        kd.setTotalChars(totalChars);
        kd.setTotalChunks(0);
        docMapper.insert(kd);

        // 3. 切分超长页 + 写 chunk 表 + 构建 VectorStore
        String storeDir = "data/" + userId;
        String storePath = storeDir + "/" + kd.getId() + "_" + sanitize(filename) + ".json";
        new File(storeDir).mkdirs();
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        Map<String, Object> baseMeta = new HashMap<>();
        baseMeta.put("docId", kd.getId());
        baseMeta.put("userId", userId);
        baseMeta.put("scope", scope);
        baseMeta.put("docTitle", title);

        List<Document> toEmbed = new ArrayList<>();
        int chunkIdx = 0;
        for (Document d : docs) {
            String text = d.getText();
            List<String> parts = text.length() <= 2000 ? List.of(text) : splitChunk(text, 2000);
            for (String part : parts) {
                // chunk 表存文本
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocumentId(kd.getId());
                kc.setChunkIndex(chunkIdx);
                kc.setContent(part);
                kc.setCharCount(part.length());
                chunkMapper.insert(kc);

                // 向量库
                Map<String, Object> m = new HashMap<>(baseMeta);
                m.put("chunkIndex", chunkIdx);
                toEmbed.add(new Document(part, m));
                chunkIdx++;
            }
        }
        store.add(toEmbed);
        store.save(new File(storePath));

        // 4. 更新 MySQL
        kd.setTotalChunks(chunkIdx);
        kd.setStorePath(storePath);
        docMapper.updateById(kd);

        log.info("知识库入库: {} → {} 块, {} 字, {}", filename, chunkIdx, totalChars, storePath);
        return kd;
    }

    // ===== 检索（加载用户所有 JSON 合并检索） =====

    public List<Document> search(String query, int k, Long userId) {
        List<Document> results = new ArrayList<>();
        File userDir = new File("data/" + userId);
        File[] files = userDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return results;

        // 分别查每个文档的 store，合并
        for (File f : files) {
            try {
                SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
                store.load(f);
                results.addAll(store.similaritySearch(
                        SearchRequest.builder().query(query).topK(3)
                                .similarityThreshold(0.5).build()));
            } catch (Exception e) { log.warn("检索 {} 失败: {}", f.getName(), e.getMessage()); }
        }
        // 按相似度排序取 Top-K
        return results.stream()
                .sorted((a, b) -> Double.compare(
                        ((Number) b.getMetadata().getOrDefault("similarity", 0.0)).doubleValue(),
                        ((Number) a.getMetadata().getOrDefault("similarity", 0.0)).doubleValue()))
                .limit(k).toList();
    }

    // ===== 分块查询（从 MySQL） =====

    public List<KnowledgeChunk> getChunks(Long docId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    // ===== 管理 =====

    public List<KnowledgeDocument> listByUser(Long userId) {
        return docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getUserId, userId).orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    public List<KnowledgeDocument> listShared() {
        return docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getScope, "SHARED")
                .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    public void delete(Long docId, Long userId) {
        KnowledgeDocument kd = docMapper.selectById(docId);
        if (kd == null || !kd.getUserId().equals(userId)) throw new RuntimeException("无权删除");
        // 删 JSON 文件
        if (kd.getStorePath() != null) new File(kd.getStorePath()).delete();
        // 删 chunk 表
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId));
        // 删 doc 表
        docMapper.deleteById(docId);
    }

    // ===== 分块 =====

    static List<String> splitChunk(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            if (end < text.length()) {
                int cut = text.lastIndexOf("。", end);
                if (cut <= start + maxLen / 2) cut = text.lastIndexOf("\n", end);
                if (cut > start + maxLen / 2) end = cut + 1;
            }
            result.add(text.substring(start, end).trim());
            start = end;
        }
        return result;
    }

    static String sanitize(String name) {
        return name == null ? "unnamed" : name.replaceAll("[\\\\/:*?\"<>|]", "_").replace(".json", "");
    }
}
