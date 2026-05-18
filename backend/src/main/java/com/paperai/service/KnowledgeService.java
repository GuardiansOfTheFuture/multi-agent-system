package com.paperai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.mapper.KnowledgeChunkMapper;
import com.paperai.mapper.KnowledgeDocumentMapper;
import com.paperai.config.RagConfig;
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
public class KnowledgeService implements VectorStore {

    /** 当前请求的用户 ID，由 FlowEngine 在执行前设置 */
    public static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    @Resource private KnowledgeDocumentMapper docMapper;
    @Resource private KnowledgeChunkMapper chunkMapper;
    @Resource private EmbeddingModel embeddingModel;
    @Resource private RagConfig ragConfig;

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
            List<String> parts = text.length() <= 2000 ? List.of(text) : llmSplitChunk(text);
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

    // ===== 混合检索（向量 + 关键词 + 重排序） =====

    public List<Document> search(String query, int finalK, Long userId) {
        RagConfig cfg = ragConfig;
        Set<String> seen = new LinkedHashSet<>(); // 去重
        List<Document> all = new ArrayList<>();

        // 1. 向量检索
        if (cfg.getVectorTopK() > 0) {
            File userDir = new File("data/" + userId);
            File[] files = userDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    try {
                        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
                        store.load(f);
                        List<Document> vec = store.similaritySearch(
                                SearchRequest.builder().query(query).topK(cfg.getVectorTopK())
                                        .similarityThreshold(cfg.getSimilarityThreshold()).build());
                        for (Document d : vec) {
                            if (seen.add(fingerprint(d.getText()))) all.add(d);
                        }
                    } catch (Exception e) { log.warn("向量检索 {} 失败: {}", f.getName(), e.getMessage()); }
                }
            }
        }

        // 2. 关键词检索（MySQL LIKE）
        if (cfg.isHybridEnabled() && cfg.getKeywordTopK() > 0) {
            List<KnowledgeChunk> kwChunks = keywordSearch(query, cfg.getKeywordTopK(), userId);
            for (KnowledgeChunk kc : kwChunks) {
                if (seen.add(fingerprint(kc.getContent()))) {
                    all.add(new Document(kc.getContent(), Map.of("docTitle", "", "chunkIndex", kc.getChunkIndex())));
                }
            }
        }

        // 3. 按相似度排序
        all.sort((a, b) -> Double.compare(
                ((Number) b.getMetadata().getOrDefault("similarity", 0.0)).doubleValue(),
                ((Number) a.getMetadata().getOrDefault("similarity", 0.0)).doubleValue()));

        // 4. 重排序（DashScope gte-rerank）
        if (cfg.isRerankEnabled() && all.size() > finalK) {
            all = rerank(query, all, cfg.getFinalTopK());
        }

        // 5. 截断到最终数量
        return all.stream().limit(finalK).toList();
    }

    /** 关键词检索：MySQL 全文搜索分块文本 */
    private List<KnowledgeChunk> keywordSearch(String query, int topK, Long userId) {
        // 按空格拆词，每个词至少出现一次
        String[] words = query.split("[\\s,，。]+");
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        for (String w : words) {
            if (w.length() >= 2) wrapper.like(KnowledgeChunk::getContent, w);
        }
        wrapper.last("LIMIT " + topK);
        return chunkMapper.selectList(wrapper);
    }

    /** 调用 DashScope gte-rerank 重排序 */
    private List<Document> rerank(String query, List<Document> candidates, int topK) {
        try {
            // DashScope ReRank API
            String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", ragConfig.getRerankModel());
            body.put("input", Map.of("query", query, "documents",
                    candidates.stream().map(Document::getText).toList()));
            body.put("parameters", Map.of("top_n", topK, "return_documents", false));

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + resolveApiKey());
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> req =
                    new org.springframework.http.HttpEntity<>(body, headers);
            var resp = new org.springframework.web.client.RestTemplate()
                    .postForEntity(url, req, Map.class);
            var respBody = resp.getBody();
            if (respBody != null && respBody.get("output") != null) {
                @SuppressWarnings("unchecked")
                var output = (Map<String, Object>) respBody.get("output");
                @SuppressWarnings("unchecked")
                var results = (List<Map<String, Object>>) output.get("results");
                if (results != null) {
                    List<Document> reranked = new ArrayList<>();
                    for (var r : results) {
                        int idx = ((Number) r.get("index")).intValue();
                        if (idx < candidates.size()) {
                            Document d = candidates.get(idx);
                            d.getMetadata().put("rerank_score", r.get("relevance_score"));
                            reranked.add(d);
                        }
                    }
                    log.info("[ReRank] {} 条 → {} 条", candidates.size(), reranked.size());
                    return reranked;
                }
            }
        } catch (Exception e) { log.warn("ReRank 失败: {}", e.getMessage()); }
        return candidates.stream().limit(topK).toList();
    }

    private String fingerprint(String text) {
        return text.length() < 60 ? text : text.substring(0, 30) + text.substring(text.length() - 30);
    }

    private String resolveApiKey() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        return key != null ? key : "sk-f4ab3e4883774139a7ab2e8f54c4f115";
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

    // ===== LLM 语义分块 =====

    @Resource private com.paperai.config.AiConfig aiConfig;

    /** LLM 语义分块：调用一次轻量模型，按主题边界切分并提取标题。失败回退简单切分。 */
    List<String> llmSplitChunk(String text) {
        try {
            String sample = text.length() > 20000 ? text.substring(0, 20000) : text;
            String prompt = """
                你是文档工程师。把以下文档按语义边界切成块，每块 500~2000 字。
                输出严格 JSON：[{"title":"章节标题","content":"块文本"}, ...]
                要求：标题概括内容；不在句子中间切；保留表格代码块完整；只输出 JSON 数组，无其他文字。

                文档：
                %s
                """.formatted(sample);
            String resp = aiConfig.callLightLlm("你是文档工程师，擅长分析文档结构。", prompt);
            // 解析 JSON
            int start = resp.indexOf('['), end = resp.lastIndexOf(']');
            if (start >= 0 && end > start) {
                List<Map<String, Object>> items = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(resp.substring(start, end + 1), List.class);
                List<String> result = new ArrayList<>();
                for (var item : items) {
                    String title = (String) item.get("title");
                    String content = (String) item.get("content");
                    if (title != null && content != null) {
                        result.add("## " + title + "\n" + content);
                    } else if (content != null) {
                        result.add(content);
                    }
                }
                if (!result.isEmpty()) return result;
            }
        } catch (Exception e) { log.warn("LLM分块失败，回退简单切分: {}", e.getMessage()); }
        return simpleSplit(text, 2000);
    }

    static List<String> simpleSplit(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        String[] paras = text.split("\n\n");
        StringBuilder buf = new StringBuilder();
        for (String p : paras) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (buf.length() + t.length() > maxLen && buf.length() > 100) {
                result.add(buf.toString().trim());
                buf = new StringBuilder(t);
            } else {
                if (buf.length() > 0) buf.append("\n\n");
                buf.append(t);
            }
        }
        if (buf.length() > 0) result.add(buf.toString().trim());
        return result;
    }

    static String sanitize(String name) {
        return name == null ? "unnamed" : name.replaceAll("[\\\\/:*?\"<>|]", "_").replace(".json", "");
    }

    // ===== VectorStore 接口（供 QuestionAnswerAdvisor 使用） =====

    @Override
    public void add(List<Document> documents) {
        // 上传时已有专门逻辑，此处预留
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        Long uid = currentUserId.get();
        if (uid == null) return Collections.emptyList();
        return search(request.getQuery(), request.getTopK(), uid);
    }

    @Override public void delete(String id) { }
    @Override public void delete(java.util.List<String> idList) { }
    @Override public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) { }
}
