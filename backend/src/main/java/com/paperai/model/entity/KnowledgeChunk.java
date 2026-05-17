package com.paperai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer charCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
