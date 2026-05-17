package com.paperai.service;

import com.paperai.model.entity.Reference;

import java.util.List;

public interface ReferenceService {

    List<Reference> listByPaperId(Long paperId);

    Reference getById(Long id);

    Reference add(Long paperId, Reference ref);

    Reference update(Long id, Reference ref, Long userId);

    void delete(Long id, Long userId);

    int importBibtex(Long paperId, String bibtexText);

    List<Reference> extractFromResearchOutput(Long paperId, String researchOutput);
}
