package com.paperai.service;

import com.paperai.model.entity.Paper;
import com.paperai.model.entity.PaperVersion;
import com.paperai.model.dto.PaperWritingRequestDTO;

import java.util.List;

public interface PaperService {

    Paper createPaper(PaperWritingRequestDTO request, Long userId);

    Paper getPaperById(Long id);

    List<Paper> listAll();

    List<Paper> listByUserId(Long userId);

    void checkOwner(Long paperId, Long userId);

    void updateContent(Long id, String content);

    void updateStatus(Long id, String status);

    void deletePaper(Long id);

    PaperVersion saveVersion(Long paperId, String stage, String summary, String content);

    List<PaperVersion> getVersions(Long paperId);

    PaperVersion getVersion(Long paperId, Integer versionNo);

    PaperVersion getLatestVersion(Long paperId);
}
