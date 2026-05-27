package com.paperai.service;

import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.entity.FlowDefinition;

public interface FlowEngineService {

    void execute(Long paperId, FlowDefinition def, PaperWritingRequestDTO req);

    void stop(Long paperId);
}
