package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_reference")
public class Reference implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private String title;

    private String authors;

    private Integer year;

    private String journal;

    private String volume;

    private String issue;

    private String pages;

    private String doi;

    private String url;

    private String type;

    private String rawText;

    private Integer cited;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
