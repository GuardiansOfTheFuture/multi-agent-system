package com.paperai.mq.consumer;

import com.paperai.event.StepEventPublisher;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.mq.TaskMessage;
import com.paperai.service.OrchestratorService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PaperWriteConsumer {

    @Resource private OrchestratorService orchestratorService;
    @Resource private StepEventPublisher stepEventPublisher;

    @RabbitListener(queues = "paperai.q.paper.write", concurrency = "1-3")
    public void handlePaperWrite(TaskMessage message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("[MQ] 开始处理论文写作: paperId={}, taskId={}, retry={}",
                message.getPaperId(), message.getTaskId(), message.getRetryCount());

        try {
            Map<String, Object> payload = message.getPayload();
            PaperWritingRequestDTO req = new PaperWritingRequestDTO();
            req.setTopic((String) payload.get("topic"));
            req.setDescription((String) payload.get("description"));
            req.setKeywords((String) payload.get("keywords"));
            req.setFlowId((String) payload.get("flowId"));
            req.setKgId(payload.get("kgId") != null ? ((Number) payload.get("kgId")).longValue() : null);
            req.setMaxReviewRounds(payload.get("maxReviewRounds") != null
                    ? ((Number) payload.get("maxReviewRounds")).intValue() : 3);
            Object sections = payload.get("sections");
            if (sections instanceof List<?> list && !list.isEmpty()) {
                req.setSections(list.stream().map(Object::toString).toList());
            }
            req.setRequirements((String) payload.get("requirements"));

            orchestratorService.executeAsync(message.getPaperId(), req);

            channel.basicAck(deliveryTag, false);
            log.info("[MQ] 论文写作完成: paperId={}", message.getPaperId());
        } catch (Exception e) {
            log.error("[MQ] 论文写作失败: paperId={}, error={}", message.getPaperId(), e.getMessage(), e);
            try {
                stepEventPublisher.publishError(message.getPaperId(),
                        e.getMessage() != null ? e.getMessage() : "写作任务异常");
            } catch (Exception ignored) {}
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
