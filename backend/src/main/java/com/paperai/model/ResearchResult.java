package com.paperai.model;

import com.paperai.model.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 研究结果模型（领域层，非展示层）
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
public class ResearchResult {

    private String taskId;
    private String topic;
    private TaskStatus status;
    private String literatureReview;
    private List<String> keyFindings;
    private List<String> suggestedDirections;
    private List<String> references;
    private String rawResponse;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;
}
