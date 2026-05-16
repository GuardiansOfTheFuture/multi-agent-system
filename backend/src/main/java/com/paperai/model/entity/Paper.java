package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文实体
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@TableName("paper")
public class Paper implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** 论文ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 论文标题 */
    private String title;

    /** 摘要 */
    private String abstractText;

    /** 关键词 */
    private String keywords;

    /** 研究方向/描述 */
    private String description;

    /** 论文状态（DRAFT/REVIEWING/PUBLISHED） */
    private String status;

    /** 最终内容（仅运行时从版本表填充，不持久化到 paper 表） */
    @TableField(exist = false)
    private String content;

    private Long userId;

    /** 当前最新版本号 */
    private Integer currentVersion;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
