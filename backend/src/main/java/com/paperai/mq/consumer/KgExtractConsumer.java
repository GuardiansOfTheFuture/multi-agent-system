package com.paperai.mq.consumer;

import com.paperai.agent.AgentExecutor;
import com.paperai.mq.TaskMessage;
import com.paperai.service.KnowledgeGraphService;
import com.paperai.model.entity.KnowledgeGraph;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class KgExtractConsumer {

    @Resource private AgentExecutor agentExecutor;
    @Resource private KnowledgeGraphService knowledgeGraphService;

    @RabbitListener(queues = "paperai.q.kg.extract", concurrency = "1-2")
    public void handleKgExtract(TaskMessage message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Map<String, Object> payload = message.getPayload();
        Long kgId = ((Number) payload.get("kgId")).longValue();
        String text = (String) payload.get("text");
        String topic = (String) payload.get("topic");

        log.info("[MQ] 开始知识图谱抽取: kgId={}, taskId={}", kgId, message.getTaskId());

        try {
            String result = agentExecutor.extractKnowledgeGraph(text, topic, null, null, 0.7);

            KnowledgeGraph kg = knowledgeGraphService.getById(kgId);
            if (kg != null) {
                kg.setGraphData(result);
                knowledgeGraphService.update(kgId, kg, kg.getUserId());
            }

            channel.basicAck(deliveryTag, false);
            log.info("[MQ] 知识图谱抽取完成: kgId={}", kgId);
        } catch (Exception e) {
            log.error("[MQ] 知识图谱抽取失败: kgId={}, error={}", kgId, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
