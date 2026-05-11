package com.paperai.model.vo;

import com.paperai.model.enums.AgentMessageType;
import com.paperai.model.enums.AgentRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 之间的消息 VO
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageVO {

    /** 消息ID */
    private String messageId;
    /** 发送者角色 */
    private AgentRole sender;
    /** 目标接收者角色（null 表示广播） */
    private AgentRole receiver;
    /** 消息类型 */
    private AgentMessageType type;
    /** 消息内容 */
    private String content;
    /** 关联的任务ID */
    private String taskId;
    /** 时间戳 */
    private LocalDateTime timestamp;

    public AgentMessageVO(AgentRole sender, AgentRole receiver, AgentMessageType type, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
}
