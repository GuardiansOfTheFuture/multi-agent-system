package com.paperai.service;

import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.vo.PaperWritingVO;

import java.util.function.Consumer;

public interface OrchestratorService {

    PaperWritingVO execute(PaperWritingRequestDTO req);

    PaperWritingVO execute(PaperWritingRequestDTO req, Long userId);

    void executeStream(PaperWritingRequestDTO req, Consumer<PaperWritingVO.StepRecordVO> cb);

    void executeStream(PaperWritingRequestDTO req, Consumer<PaperWritingVO.StepRecordVO> cb, Long userId);

    void executeAsync(Long paperId, PaperWritingRequestDTO req);

    void stopTask(Long paperId);
}
