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
     * @param paperId 论文ID（前端订阅 /topic/paper/{paperId}/step）
     * @param step    步骤数据
     */
    public void publishStep(Long paperId, StepRecordVO step) {
        messagingTemplate.convertAndSend(
                "/topic/paper/" + paperId + "/step",
                step
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
