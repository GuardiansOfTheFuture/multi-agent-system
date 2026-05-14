package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文版本实体
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
@TableName("paper_version")
public class PaperVersion {

    /** 版本ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属论文ID */
    private Long paperId;

    /** 版本号 */
    private Integer versionNo;

    /** 阶段: DRAFT/REVIEWED/POLISHED/FINAL/MANUAL_EDIT */
    private String stage;

    /** 版本摘要/日志 */
    private String summary;

    /** 论文全文(Markdown) */
    private String content;

    /** 字数统计 */
    private Integer wordCount;

    /** 编辑类型: MANUAL/AGENT/BATCH */
    private String editType;

    /** 详细修改说明 */
    private String changeSummary;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
