package com.paperai.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskMessage implements Serializable {

    private String taskId;
    private Long paperId;
    private Long userId;
    private String taskType;
    private Map<String, Object> payload;
    private int retryCount;
    private LocalDateTime createdAt;
}
