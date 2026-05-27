package com.paperai.service.impl.rerank;

import org.springframework.ai.document.Document;
import java.util.List;

/** 检索结果重排序策略 */
public interface RerankStrategy {
    List<Document> rerank(String query, List<Document> candidates, int topK);
}
