package com.paperai.service.impl.rerank;

import com.paperai.config.RagConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** DashScope gte-rerank 重排序 */
@Slf4j
@Component
public class DashScopeRerankStrategy implements RerankStrategy {

    @Resource private RagConfig ragConfig;

    @Override
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        try {
            String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", ragConfig.getRerankModel());
            body.put("input", Map.of("query", query, "documents",
                    candidates.stream().map(Document::getText).toList()));
            body.put("parameters", Map.of("top_n", topK, "return_documents", false));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + resolveApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            var resp = new RestTemplate().postForEntity(url, req, Map.class);
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

    private String resolveApiKey() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        return key != null ? key : "sk-f4ab3e4883774139a7ab2e8f54c4f115";
    }
}
