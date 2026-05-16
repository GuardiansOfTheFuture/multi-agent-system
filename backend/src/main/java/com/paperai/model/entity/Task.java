package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 任务实体
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@TableName("task")
public class Task implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联论文ID */
    private Long paperId;

    /** Agent 角色编码 */
    private String agentRole;

    /** 关联版本号 */
    private Integer versionNo;

    /** 执行顺序 */
    private Integer sortOrder;

    /** 任务描述 */
    private String description;

    /** 任务输入 */
    private String inputData;

    /** 任务输出 */
    private String outputData;

    /** 状态（PENDING/IN_PROGRESS/COMPLETED/FAILED） */
    private String status;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
