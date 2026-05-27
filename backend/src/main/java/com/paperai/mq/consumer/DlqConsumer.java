package com.paperai.mq.consumer;

import com.paperai.common.Constants;
import com.paperai.event.StepEventPublisher;
import com.paperai.mq.TaskMessage;
import com.paperai.service.PaperService;
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
public class DlqConsumer {

    @Resource private PaperService paperService;
    @Resource private StepEventPublisher stepEventPublisher;

    @RabbitListener(queues = "paperai.q.dlq")
    public void handleDlq(TaskMessage message, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("[DLQ] 死信消息: taskId={}, type={}, paperId={}, retryCount={}",
                message.getTaskId(), message.getTaskType(), message.getPaperId(), message.getRetryCount());

        if ("WRITE".equals(message.getTaskType()) && message.getPaperId() != null) {
            try {
                paperService.updateStatus(message.getPaperId(), Constants.PAPER_STATUS_FAILED);
                stepEventPublisher.publishError(message.getPaperId(), "写作任务处理失败，已重试多次");
            } catch (Exception e) {
                log.error("[DLQ] 更新论文状态失败: paperId={}", message.getPaperId(), e);
            }
        }

        channel.basicAck(deliveryTag, false);
    }
}
