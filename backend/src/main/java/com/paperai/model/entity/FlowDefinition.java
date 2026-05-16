package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程定义实体
 *
 * @author ch
 * @date 2026年05月14日
 */
@Data
@TableName("flow_definition")
public class FlowDefinition implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 流程名称 */
    private String name;

    /** 流程描述 */
    private String description;

    /** 分类：preset/custom/template */
    private String category;

    /** 完整节点 + 边 JSON */
    private String graphData;

    /** 是否为模板 */
    private Integer isTemplate;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
