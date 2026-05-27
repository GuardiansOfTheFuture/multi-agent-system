package com.paperai.mq.consumer;

import com.paperai.agent.AgentExecutor;
import com.paperai.model.ResearchResult;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.mq.TaskMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ResearchConsumer {

    @Resource private AgentExecutor agentExecutor;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = "paperai.q.paper.research", concurrency = "1-2")
    public void handleResearch(TaskMessage message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Map<String, Object> payload = message.getPayload();
        String topic = (String) payload.get("topic");
        String requirements = (String) payload.get("requirements");

        log.info("[MQ] 开始调研: topic={}, taskId={}", topic, message.getTaskId());

        try {
            ResearchRequestDTO req = new ResearchRequestDTO();
            req.setTopic(topic);
            req.setRequirements(requirements);
            ResearchResult result = agentExecutor.executeStructuredResearch(req);

            // 存入 Redis
            if (redisTemplate != null) {
                String key = "paperai:research:" + message.getTaskId();
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result),
                        10, TimeUnit.MINUTES);
            }

            channel.basicAck(deliveryTag, false);
            log.info("[MQ] 调研完成: topic={}", topic);
        } catch (Exception e) {
            log.error("[MQ] 调研失败: topic={}, error={}", topic, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
