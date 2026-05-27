package com.paperai.mq.consumer;

import com.paperai.mq.TaskMessage;
import com.paperai.service.KnowledgeService;
import com.paperai.mapper.KnowledgeDocumentMapper;
import com.paperai.model.entity.KnowledgeDocument;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
public class KnowledgeUploadConsumer {

    @Resource private KnowledgeDocumentMapper docMapper;

    @RabbitListener(queues = "paperai.q.knowledge.upload", concurrency = "1-2")
    public void handleKnowledgeUpload(TaskMessage message, Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long docId = ((Number) message.getPayload().get("docId")).longValue();
        log.info("[MQ] 开始处理文档: docId={}, taskId={}", docId, message.getTaskId());

        try {
            KnowledgeDocument doc = docMapper.selectById(docId);
            if (doc == null) {
                log.warn("[MQ] 文档不存在: docId={}", docId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            doc.setStatus("COMPLETED");
            docMapper.updateById(doc);

            channel.basicAck(deliveryTag, false);
            log.info("[MQ] 文档处理完成: docId={}", docId);
        } catch (Exception e) {
            log.error("[MQ] 文档处理失败: docId={}, error={}", docId, e.getMessage(), e);
            try {
                KnowledgeDocument doc = docMapper.selectById(docId);
                if (doc != null) {
                    doc.setStatus("FAILED");
                    docMapper.updateById(doc);
                }
            } catch (Exception ignored) {}
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
