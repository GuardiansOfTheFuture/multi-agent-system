package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 消息实体
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@TableName("agent_message")
public class AgentMessage {

    /** 消息ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联论文ID */
    private Long paperId;

    /** 关联任务ID */
    private Long taskId;

    /** 发送者角色 */
    private String senderRole;

    /** 接收者角色 */
    private String receiverRole;

    /** 消息类型 */
    private String messageType;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
