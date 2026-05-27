package com.paperai.service;

import com.paperai.model.entity.KnowledgeChunk;
import com.paperai.model.entity.KnowledgeDocument;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {

    KnowledgeDocument upload(MultipartFile file, Long userId, String scope) throws Exception;

    List<Document> search(String query, int finalK, Long userId);

    List<KnowledgeChunk> getChunks(Long docId);

    List<KnowledgeDocument> listByUser(Long userId);

    List<KnowledgeDocument> listShared();

    void delete(Long docId, Long userId);

    /**
     * 用当前任务文本去知识库检索，把命中的文献块拼到任务前面返回。
     * 供 OrchestratorService / FlowEngine 在每个 Agent 步骤执行前调用。
     */
    String augment(String task, Long userId);
}
