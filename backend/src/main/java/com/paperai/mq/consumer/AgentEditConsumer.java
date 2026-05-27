package com.paperai.mq.consumer;

import com.paperai.cache.LlmCacheService;
import com.paperai.mq.TaskMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AgentEditConsumer {

    @Resource private ChatClient dashScopeChatClient;
    @Resource private LlmCacheService llmCacheService;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private StringRedisTemplate redisTemplate;

    @RabbitListener(queues = "paperai.q.paper.agent-edit", concurrency = "1-2")
    public void handleAgentEdit(TaskMessage message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Map<String, Object> payload = message.getPayload();
        String selectedText = (String) payload.get("selectedText");
        String instruction = (String) payload.get("instruction");

        log.info("[MQ] 开始AI编辑: paperId={}, taskId={}", message.getPaperId(), message.getTaskId());

        try {
            StringBuilder prompt = new StringBuilder("""
                    你是一位专业的学术论文编辑助手。请根据指令修改选中的文本，只返回修改后的内容，不要解释。

                    """);
            prompt.append("【选中文本（需要修改的部分）】\n").append(selectedText).append("\n\n");
            prompt.append("【修改指令】\n").append(instruction);

            String cacheKey = llmCacheService.computeKey(null, prompt.toString());
            String cached = llmCacheService.get(cacheKey);
            String result = cached != null ? cached : dashScopeChatClient.prompt().user(prompt.toString()).call().content();
            if (cached == null && result != null) llmCacheService.put(cacheKey, result);

            // 存入 Redis，前端通过 taskId 获取结果
            if (redisTemplate != null) {
                String key = "paperai:agent-edit:" + message.getTaskId();
                redisTemplate.opsForValue().set(key, result != null ? result : "", 5, TimeUnit.MINUTES);
            }

            channel.basicAck(deliveryTag, false);
            log.info("[MQ] AI编辑完成: paperId={}", message.getPaperId());
        } catch (Exception e) {
            log.error("[MQ] AI编辑失败: paperId={}, error={}", message.getPaperId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
