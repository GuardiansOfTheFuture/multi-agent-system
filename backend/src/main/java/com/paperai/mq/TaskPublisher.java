package com.paperai.mq;

import com.paperai.config.MqConfig;
import com.paperai.model.dto.PaperWritingRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class TaskPublisher {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public String publishPaperWrite(Long paperId, Long userId, PaperWritingRequestDTO request) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(UUID.randomUUID().toString());
        msg.setPaperId(paperId);
        msg.setUserId(userId);
        msg.setTaskType("WRITE");
        msg.setPayload(Map.of(
                "topic", request.getTopic(),
                "description", request.getDescription() != null ? request.getDescription() : "",
                "keywords", request.getKeywords() != null ? request.getKeywords() : "",
                "flowId", request.getFlowId() != null ? request.getFlowId() : "standard",
                "kgId", request.getKgId() != null ? request.getKgId() : 0,
                "maxReviewRounds", request.getMaxReviewRounds() != null ? request.getMaxReviewRounds() : 3,
                "sections", request.getSections() != null ? request.getSections() : List.of(),
                "requirements", request.getRequirements() != null ? request.getRequirements() : ""
        ));
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());

        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "paperai.paper.write", msg, m -> {
            m.getMessageProperties().setPriority(10);
            m.getMessageProperties().setMessageId(msg.getTaskId());
            return m;
        });
        log.info("[MQ] 发布论文写作任务: paperId={}, taskId={}", paperId, msg.getTaskId());
        return msg.getTaskId();
    }

    public String publishKnowledgeUpload(Long docId, Long userId) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(UUID.randomUUID().toString());
        msg.setUserId(userId);
        msg.setTaskType("UPLOAD");
        msg.setPayload(Map.of("docId", docId));
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());

        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "paperai.knowledge.upload", msg, m -> {
            m.getMessageProperties().setPriority(5);
            m.getMessageProperties().setMessageId(msg.getTaskId());
            return m;
        });
        log.info("[MQ] 发布文档处理任务: docId={}, taskId={}", docId, msg.getTaskId());
        return msg.getTaskId();
    }

    public String publishKgExtract(Long kgId, Long userId, String text, String topic) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(UUID.randomUUID().toString());
        msg.setUserId(userId);
        msg.setTaskType("KG_EXTRACT");
        msg.setPayload(Map.of("kgId", kgId, "text", text, "topic", topic != null ? topic : ""));
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());

        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "paperai.kg.extract", msg, m -> {
            m.getMessageProperties().setPriority(5);
            m.getMessageProperties().setMessageId(msg.getTaskId());
            return m;
        });
        log.info("[MQ] 发布知识图谱抽取任务: kgId={}, taskId={}", kgId, msg.getTaskId());
        return msg.getTaskId();
    }

    public String publishExport(Long paperId, Long userId, String format) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(UUID.randomUUID().toString());
        msg.setPaperId(paperId);
        msg.setUserId(userId);
        msg.setTaskType("EXPORT");
        msg.setPayload(Map.of("format", format));
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());

        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "paperai.paper.export", msg);
        log.info("[MQ] 发布导出任务: paperId={}, format={}, taskId={}", paperId, format, msg.getTaskId());
        return msg.getTaskId();
    }

    public String publishAgentEdit(Long paperId, Long userId, String selectedText, String instruction) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(UUID.randomUUID().toString());
        msg.setPaperId(paperId);
        msg.setUserId(userId);
        msg.setTaskType("AGENT_EDIT");
        msg.setPayload(Map.of("selectedText", selectedText, "instruction", instruction));
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());

        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "paperai.paper.agent-edit", msg);
        log.info("[MQ] 发布AI编辑任务: paperId={}, taskId={}", paperId, msg.getTaskId());
        return msg.getTaskId();
    }

    public String publishResearch(Long paperId, Long userId, String topic, String requirements) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(UUID.randomUUID().toString());
        msg.setPaperId(paperId);
        msg.setUserId(userId);
        msg.setTaskType("RESEARCH");
        msg.setPayload(Map.of("topic", topic, "requirements", requirements != null ? requirements : ""));
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());

        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, "paperai.paper.research", msg);
        log.info("[MQ] 发布调研任务: paperId={}, taskId={}", paperId, msg.getTaskId());
        return msg.getTaskId();
    }
}
