package com.paperai.event;

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
 * SSE (Server-Sent Events) 推送 Agent 步骤事件到前端
 */
@Service
public class StepEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StepEventPublisher.class);

    /** paperId → 该论文的所有 SSE 连接 */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long paperId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.computeIfAbsent(paperId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> { remove(paperId, emitter); log.info("SSE completed: paperId={}", paperId); });
        emitter.onTimeout(() -> { remove(paperId, emitter); log.info("SSE timeout: paperId={}", paperId); });
        emitter.onError(e -> { remove(paperId, emitter); log.warn("SSE error paperId={}: {}", paperId, e.getMessage()); });

        sendToOne(emitter, "connected", Map.of("paperId", paperId));
        log.info("SSE 创建: paperId={}, 当前活跃={}", paperId, countActive());
        return emitter;
    }

    private int countActive() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    private void remove(Long paperId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(paperId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(paperId);
        }
    }

    public void publishStep(Long paperId, com.paperai.model.vo.PaperWritingVO.StepRecordVO step) {
        sendEvent(paperId, "step", step);
    }

    public void publishStreamToken(Long paperId, int stepSeq, String agentName, String fullText) {
        sendEvent(paperId, "stream", Map.of(
                "stepSeq", stepSeq,
                "agentName", agentName,
                "fullText", fullText,
                "ts", System.currentTimeMillis()
        ));
    }

    public void publishNodeStatus(Long paperId, Map<String, Object> payload) {
        sendEvent(paperId, "node", payload);
    }

    public void publishComplete(Long paperId) {
        sendEvent(paperId, "complete", Map.of("status", "COMPLETED", "paperId", paperId));
        closeEmitters(paperId);
    }

    public void publishError(Long paperId, String error) {
        sendEvent(paperId, "error", Map.of("status", "FAILED", "error", error));
        closeEmitters(paperId);
    }

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
