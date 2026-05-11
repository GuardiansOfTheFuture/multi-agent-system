package com.paperai.model;

import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 消息模型
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@AllArgsConstructor
public class AgentMessage {

    private String messageId;
    private AgentRole sender;
    private AgentRole receiver;
    private AgentMessageType type;
    private String content;
    private String taskId;
    private LocalDateTime timestamp;

    public AgentMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public AgentMessage(AgentRole sender, AgentRole receiver, AgentMessageType type, String content) {
        this();
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.content = content;
    }

    public AgentMessage(AgentRole sender, AgentRole receiver, AgentMessageType type, String content, String taskId) {
        this(sender, receiver, type, content);
        this.taskId = taskId;
    }
}
