package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("custom_agent")
public class CustomAgent implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 角色名，如"数据分析专家" */
    private String name;

    /** 图标 emoji */
    private String icon;

    /** 角色描述 */
    private String description;

    /** 自定义 System Prompt */
    private String systemPrompt;

    /** 默认模型 */
    private String model;

    /** 默认温度 */
    private Double temperature;

    /** 是否启用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
