package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_version")
public class PaperVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Integer versionNo;

    private String stage;

    private String summary;

    private String content;

    private Integer wordCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
