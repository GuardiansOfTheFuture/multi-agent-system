package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String filename;
    private String fileType;
    private String title;
    private String authors;
    private Integer year;
    private String scope;
    private String status;
    private Integer totalChunks;
    private Integer totalChars;
    private String storePath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
