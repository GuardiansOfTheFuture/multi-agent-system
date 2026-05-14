package com.paperai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通过 SSE (Server-Sent Events) 推送 Agent 步骤事件到前端
 * 替代原来的 WebSocket STOMP 方案，更简单可靠
 */
@Service
public class StepEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StepEventPublisher.class);

    /** paperId → 该论文的所有 SSE 连接（通常 1 个） */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 为指定 paperId 创建一个 SSE 发射器，前端通过 GET /api/paper/write/{paperId}/stream 获取
     */
    public SseEmitter createEmitter(Long paperId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 无超时
        emitters.computeIfAbsent(paperId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(paperId, emitter));
        emitter.onTimeout(() -> remove(paperId, emitter));
        emitter.onError(e -> remove(paperId, emitter));

        // 立即发送连接成功事件
        sendToOne(emitter, "connected", Map.of("paperId", paperId));

        log.info("SSE emitter 创建: paperId={}", paperId);
        return emitter;
    }

    private void remove(Long paperId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(paperId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(paperId);
        }
        log.info("SSE emitter 移除: paperId={}", paperId);
    }

    // ===== 推送方法 =====

    /**
     * 推送单个步骤事件
     */
    public void publishStep(Long paperId, com.paperai.model.vo.PaperWritingVO.StepRecordVO step) {
        sendEvent(paperId, "step", step);
    }

    /**
     * 推送流式 token（逐字推送当前步骤的实时文本）
     */
    public void publishStreamToken(Long paperId, int stepSeq, String agentName, String fullText) {
        sendEvent(paperId, "stream", Map.of(
                "stepSeq", stepSeq,
                "agentName", agentName,
                "fullText", fullText,
                "ts", System.currentTimeMillis()
        ));
    }

    /**
     * 推送单个节点的执行状态（FlowEngine 使用，包含 nodeId 供画布染色）
     */
    public void publishNodeStatus(Long paperId, Map<String, Object> payload) {
        sendEvent(paperId, "node", payload);
    }

    /**
     * 推送完成事件，并关闭所有 emitter
     */
    public void publishComplete(Long paperId) {
        sendEvent(paperId, "complete", Map.of("status", "COMPLETED", "paperId", paperId));
        closeEmitters(paperId);
    }

    /**
     * 推送错误事件，并关闭所有 emitter
     */
    public void publishError(Long paperId, String error) {
        sendEvent(paperId, "error", Map.of("status", "FAILED", "error", error));
        closeEmitters(paperId);
    }

    // ===== 内部方法 =====

    private void sendEvent(Long paperId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(paperId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            sendToOne(emitter, eventName, data);
        }
    }

    private void sendToOne(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            // emitter 将通过 onError 回调自动移除
        }
    }

    private void closeEmitters(Long paperId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(paperId);
        if (list != null) {
            for (SseEmitter e : list) {
                try { e.complete(); } catch (Exception ignored) {}
            }
        }
    }
}
