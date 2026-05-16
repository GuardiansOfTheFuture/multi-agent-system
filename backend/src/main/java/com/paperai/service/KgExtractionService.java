package com.paperai.service;

import com.paperai.agent.ResearcherAgent;
import com.paperai.model.entity.PaperVersion;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KgExtractionService {

    @Resource
    private ResearcherAgent researcherAgent;

    @Resource
    private PaperService paperService;

    public String extractFromPaper(Long paperId, List<String> entityTypes, List<String> relationTypes, double confidence) {
        PaperVersion latest = paperService.getLatestVersion(paperId);
        if (latest == null || latest.getContent() == null || latest.getContent().isBlank()) {
            throw new com.paperai.common.BusinessException(com.paperai.common.ResultCode.NOT_FOUND, "论文没有可抽取的内容");
        }
        String topic = paperService.getPaperById(paperId).getTitle();
        return extract(latest.getContent(), topic, entityTypes, relationTypes, confidence);
    }

    public String extractFromText(String text, String topic, List<String> entityTypes, List<String> relationTypes, double confidence) {
        if (text == null || text.isBlank()) {
            throw new com.paperai.common.BusinessException(com.paperai.common.ResultCode.BAD_REQUEST, "文本内容不能为空");
        }
        return extract(text, topic, entityTypes, relationTypes, confidence);
    }

    private String extract(String text, String topic, List<String> entityTypes, List<String> relationTypes, double confidence) {
        String kgJson = researcherAgent.extractKnowledgeGraph(text, topic, entityTypes, relationTypes, confidence);
        log.info("KG抽取完成: types={}, rels={}, conf={}, resultLen={}",
                entityTypes != null ? entityTypes.size() : 0,
                relationTypes != null ? relationTypes.size() : 0,
                confidence,
                kgJson != null ? kgJson.length() : 0);
        return kgJson;
    }
}
