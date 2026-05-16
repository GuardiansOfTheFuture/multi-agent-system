package com.paperai.service;

import com.paperai.model.entity.KnowledgeGraph;

import java.util.List;

public interface KnowledgeGraphService {

    KnowledgeGraph create(KnowledgeGraph kg);

    KnowledgeGraph getById(Long id);

    KnowledgeGraph getByIdAndUser(Long id, Long userId);

    List<KnowledgeGraph> listByUser(Long userId);

    List<KnowledgeGraph> listByPaper(Long paperId);

    KnowledgeGraph update(Long id, KnowledgeGraph kg, Long userId);

    void delete(Long id, Long userId);

    KnowledgeGraph duplicate(Long id, Long userId);
}
