package com.paperai.service;

import com.paperai.model.vo.PaperWritingVO.StepRecordVO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 通过 WebSocket STOMP 推送 Agent 步骤事件到前端
 */
@Service
public class StepEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public StepEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 推送单个步骤事件
     */
    public void publishStep(Long paperId, StepRecordVO step) {
        messagingTemplate.convertAndSend(
                "/topic/paper/" + paperId + "/step",
                step
        );
    }

    /**
     * 推送流式 token（逐字推送当前步骤的实时文本）
     * @param paperId 论文ID
     * @param stepSeq 当前步骤序号
     * @param agentName 步骤名称
     * @param fullText 当前累计完整文本
     */
    public void publishStreamToken(Long paperId, int stepSeq, String agentName, String fullText) {
        messagingTemplate.convertAndSend(
                "/topic/paper/" + paperId + "/stream",
                java.util.Map.of(
                        "stepSeq", stepSeq,
                        "agentName", agentName,
                        "fullText", fullText,
                        "ts", System.currentTimeMillis()
                )
        );
    }

    /**
     * 推送完成事件
     */
    public void publishComplete(Long paperId) {
        messagingTemplate.convertAndSend(
                "/topic/paper/" + paperId + "/complete",
                java.util.Map.of("status", "COMPLETED", "paperId", paperId)
        );
    }

    /**
     * 推送错误事件
     */
    public void publishError(Long paperId, String error) {
        messagingTemplate.convertAndSend(
                "/topic/paper/" + paperId + "/error",
                java.util.Map.of("status", "FAILED", "error", error)
        );
    }
}
