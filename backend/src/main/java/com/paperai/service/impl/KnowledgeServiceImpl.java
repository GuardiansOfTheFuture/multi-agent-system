package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.config.RagConfig;
import com.paperai.mapper.KnowledgeChunkMapper;
import com.paperai.mapper.KnowledgeDocumentMapper;
import com.paperai.model.entity.KnowledgeChunk;
import com.paperai.model.entity.KnowledgeDocument;
import com.paperai.service.KnowledgeService;
import com.paperai.service.impl.chunk.ChunkStrategy;
import com.paperai.service.impl.chunk.SemanticChunkStrategy;
import com.paperai.service.impl.rerank.RerankStrategy;
import com.paperai.utils.TextUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Resource private KnowledgeDocumentMapper docMapper;
    @Resource private KnowledgeChunkMapper chunkMapper;
    @Resource private EmbeddingModel embeddingModel;
    @Resource private RagConfig ragConfig;
    @Resource private SemanticChunkStrategy chunkStrategy;
    @Resource private RerankStrategy rerankStrategy;

    /** 当前 embedding 模型的向量维度，延迟计算 */
    private volatile int currentEmbedDim;

    @Override
    @CacheEvict(value = "knowledgeDocs", key = "'user:' + #userId")
    public KnowledgeDocument upload(MultipartFile file, Long userId, String scope) throws Exception {
        String filename = file.getOriginalFilename();
        String ext = filename != null ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "txt";
        String title = filename != null ? filename.replaceAll("\\.[^.]+$", "") : null;

        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        List<Document> docs = reader.read();
        if (docs.isEmpty()) throw new RuntimeException("文档未提取到文本内容");

        int totalChars = docs.stream().mapToInt(d -> d.getText().length()).sum();
        KnowledgeDocument kd = new KnowledgeDocument();
        kd.setUserId(userId); kd.setFilename(filename); kd.setFileType(ext);
        kd.setTitle(title); kd.setScope(scope != null ? scope : "PRIVATE");
        kd.setTotalChars(totalChars); kd.setTotalChunks(0);
        docMapper.insert(kd);

        String storeDir = "data/" + userId;
        String storePath = storeDir + "/" + kd.getId() + "_" + TextUtils.sanitize(filename) + ".json";
        new File(storeDir).mkdirs();
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        Map<String, Object> baseMeta = new HashMap<>();
        baseMeta.put("docId", kd.getId()); baseMeta.put("userId", userId);
        baseMeta.put("scope", scope); baseMeta.put("docTitle", title);

        List<Document> toEmbed = new ArrayList<>();
        int chunkIdx = 0;
        for (Document d : docs) {
            String text = d.getText();
            List<String> parts = chunkStrategy.split(text, 2000);
            for (String part : parts) {
                KnowledgeChunk kc = new KnowledgeChunk();
                kc.setDocumentId(kd.getId()); kc.setChunkIndex(chunkIdx);
                kc.setContent(part); kc.setCharCount(part.length());
                chunkMapper.insert(kc);

                Map<String, Object> m = new HashMap<>(baseMeta);
                m.put("chunkIndex", chunkIdx);
                toEmbed.add(new Document(part, m));
                chunkIdx++;
            }
        }
        store.add(toEmbed);
        store.save(new File(storePath));

        kd.setTotalChunks(chunkIdx); kd.setStorePath(storePath);
        kd.setEmbedDim(getEmbedDim());
        docMapper.updateById(kd);

        log.info("知识库入库: {} → {} 块, {} 字, dim={}, {}", filename, chunkIdx, totalChars, kd.getEmbedDim(), storePath);
        return kd;
    }

    @Override
    public List<Document> search(String query, int finalK, Long userId) {
        long start = System.currentTimeMillis();
        RagConfig cfg = ragConfig;
        Set<String> seen = new LinkedHashSet<>();
        List<Document> all = new ArrayList<>();

        log.info("[RAG检索] query={}, userId={}, finalK={}", truncateForLog(query), userId, finalK);

        int currentDim = getEmbedDim();
        int vecCount = 0;
        if (cfg.getVectorTopK() > 0) {
            File userDir = new File("data/" + userId);
            File[] files = userDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    // 提取文件名中的 docId，查 DB 校验 embedding 维度是否兼容
                    Integer storedDim = getStoredDim(f);
                    if (storedDim != null && storedDim != currentDim) {
                        log.warn("[RAG] 跳过 {}: embedding 维度不兼容 (存储={}D, 当前={}D)，请重新上传此文档",
                                f.getName(), storedDim, currentDim);
                        continue;
                    }
                    try {
                        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
                        store.load(f);
                        List<Document> vec = store.similaritySearch(
                                SearchRequest.builder().query(query).topK(cfg.getVectorTopK())
                                        .similarityThreshold(cfg.getSimilarityThreshold()).build());
                        for (Document d : vec)
                            if (seen.add(TextUtils.fingerprint(d.getText()))) all.add(d);
                        vecCount += vec.size();
                    } catch (Exception e) {
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("lengths must be equal")) {
                            log.warn("[RAG] 跳过 {}: 向量维度不匹配 (当前={}D)，请重新上传此文档。原因: {}",
                                    f.getName(), currentDim, msg);
                        } else {
                            log.warn("[RAG] 向量检索 {} 失败: {}", f.getName(), msg);
                        }
                    }
                }
            }
        }

        int kwCount = 0;
        if (cfg.isHybridEnabled() && cfg.getKeywordTopK() > 0) {
            List<KnowledgeChunk> kwChunks = keywordSearch(query, cfg.getKeywordTopK(), userId);
            for (KnowledgeChunk kc : kwChunks) {
                if (seen.add(TextUtils.fingerprint(kc.getContent())))
                    all.add(new Document(kc.getContent(), Map.of("docTitle", "", "chunkIndex", kc.getChunkIndex())));
            }
            kwCount = kwChunks.size();
        }

        all.sort((a, b) -> Double.compare(
                ((Number) b.getMetadata().getOrDefault("similarity", 0.0)).doubleValue(),
                ((Number) a.getMetadata().getOrDefault("similarity", 0.0)).doubleValue()));

        int beforeRerank = all.size();
        if (cfg.isRerankEnabled() && all.size() > finalK)
            all = rerankStrategy.rerank(query, all, cfg.getFinalTopK());

        long elapsed = System.currentTimeMillis() - start;
        log.info("[RAG检索] 完成: 向量{} + 关键词{} = {} → rerank → {} 条, {}ms",
                vecCount, kwCount, beforeRerank, all.size(), elapsed);
        return all.stream().limit(finalK).toList();
    }

    private List<KnowledgeChunk> keywordSearch(String query, int topK, Long userId) {
        String[] words = query.split("[\\s,，。]+");
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        for (String w : words) if (w.length() >= 2) wrapper.like(KnowledgeChunk::getContent, w);
        wrapper.last("LIMIT " + topK);
        return chunkMapper.selectList(wrapper);
    }

    @Override
    @Cacheable(value = "knowledgeDocs", key = "'chunks:' + #docId")
    public List<KnowledgeChunk> getChunks(Long docId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, docId).orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    @Override
    @Cacheable(value = "knowledgeDocs", key = "'user:' + #userId")
    public List<KnowledgeDocument> listByUser(Long userId) {
        return docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getUserId, userId).orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    @Override
    @Cacheable(value = "knowledgeDocs", key = "'shared'")
    public List<KnowledgeDocument> listShared() {
        return docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getScope, "SHARED").orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    @Override
    @CacheEvict(value = "knowledgeDocs", key = "'user:' + #userId")
    public void delete(Long docId, Long userId) {
        KnowledgeDocument kd = docMapper.selectById(docId);
        if (kd == null || !kd.getUserId().equals(userId))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除");
        if (kd.getStorePath() != null) new File(kd.getStorePath()).delete();
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId, docId));
        docMapper.deleteById(docId);
    }

    // ===== 显式 RAG：Agent 执行前调用，检索知识并拼入任务 =====

    @Override
    public String augment(String task, Long userId) {
        if (task == null || task.isBlank()) return task;
        if (userId == null) {
            log.debug("[RAG] augment 跳过：userId 为空");
            return task;
        }
        List<Document> docs = search(task, 5, userId);
        if (docs.isEmpty()) {
            log.debug("[RAG] augment 跳过：未检索到相关知识");
            return task;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料（来自你的知识库）】\n");
        int i = 1;
        for (var d : docs) {
            String title = (String) d.getMetadata().getOrDefault("docTitle", "文献");
            String text = d.getText();
            if (text.length() > 500) text = text.substring(0, 500) + "...";
            sb.append("[参考").append(i++).append("] ").append(title).append(":\n").append(text).append("\n\n");
        }
        sb.append("━━━━━━━━━━\n").append(task);
        String result = sb.toString();
        log.info("[RAG] 注入上下文: {} 条文献, userId={}", docs.size(), userId);
        return result;
    }

    private static String truncateForLog(String s) {
        return s == null ? "" : s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }

    /** 获取当前 embedding 模型的向量维度（缓存，避免重复 embed("test")） */
    private int getEmbedDim() {
        if (currentEmbedDim > 0) return currentEmbedDim;
        synchronized (this) {
            if (currentEmbedDim > 0) return currentEmbedDim;
            List<float[]> vec = embeddingModel.embed(java.util.List.of("test"));
            currentEmbedDim = vec.get(0).length;
            log.info("[RAG] 当前 embedding 维度: {}D", currentEmbedDim);
            return currentEmbedDim;
        }
    }

    /** 从文件名提取 docId，查 DB 获取存储时的 embedding 维度 */
    private Integer getStoredDim(File f) {
        try {
            String name = f.getName();  // e.g. "1_deepLearning.pdf.json"
            int underscore = name.indexOf('_');
            if (underscore > 0) {
                long docId = Long.parseLong(name.substring(0, underscore));
                KnowledgeDocument kd = docMapper.selectById(docId);
                return kd != null ? kd.getEmbedDim() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
