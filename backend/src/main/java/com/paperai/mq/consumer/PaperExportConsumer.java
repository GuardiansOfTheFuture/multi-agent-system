package com.paperai.mq.consumer;

import com.paperai.converter.ExportConverter;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.PaperVersion;
import com.paperai.mq.TaskMessage;
import com.paperai.service.PaperService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PaperExportConsumer {

    @Resource private PaperService paperService;
    @Resource private ExportConverter exportConverter;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private StringRedisTemplate redisTemplate;

    @RabbitListener(queues = "paperai.q.paper.export", concurrency = "1-2")
    public void handleExport(TaskMessage message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Map<String, Object> payload = message.getPayload();
        Long paperId = message.getPaperId();
        String format = (String) payload.get("format");

        log.info("[MQ] 开始导出: paperId={}, format={}, taskId={}", paperId, format, message.getTaskId());

        try {
            Paper paper = paperService.getPaperById(paperId);
            String title = paper.getTitle() != null ? paper.getTitle() : "paper";
            PaperVersion latest = paperService.getLatestVersion(paperId);
            String content = latest != null ? latest.getContent() : "";

            byte[] data;
            String contentType;
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeTitle.length() > 50) safeTitle = safeTitle.substring(0, 50);

            switch (format.toLowerCase()) {
                case "html" -> {
                    data = exportConverter.toHtml(content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    contentType = "text/html; charset=UTF-8";
                }
                case "pdf" -> {
                    data = exportConverter.export("pdf", content, title);
                    contentType = "application/pdf";
                }
                case "latex" -> {
                    data = exportConverter.export("latex", content, title);
                    contentType = "application/x-latex";
                }
                default -> {
                    data = exportConverter.export("docx", content, title);
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
            }

            // 将导出结果存入 Redis，前端通过 taskId 轮询下载
            if (redisTemplate != null) {
                String key = "paperai:export:" + message.getTaskId();
                redisTemplate.opsForValue().set(key, java.util.Base64.getEncoder().encodeToString(data),
                        10, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set(key + ":meta",
                        safeTitle + "|" + contentType, 10, TimeUnit.MINUTES);
            }

            channel.basicAck(deliveryTag, false);
            log.info("[MQ] 导出完成: paperId={}, format={}, size={}", paperId, format, data.length);
        } catch (Exception e) {
            log.error("[MQ] 导出失败: paperId={}, error={}", paperId, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
