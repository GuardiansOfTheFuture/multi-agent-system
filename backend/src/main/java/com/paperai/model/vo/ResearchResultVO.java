package com.paperai.model.vo;

import com.paperai.model.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 研究员产出结果 VO
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
public class ResearchResultVO {

    /** 任务ID */
    private String taskId;
    /** 研究主题 */
    private String topic;
    /** 执行状态 */
    private TaskStatus status;
    /** 文献综述 */
    private String literatureReview;
    /** 关键发现列表 */
    private List<String> keyFindings;
    /** 建议的研究方向 */
    private List<String> suggestedDirections;
    /** 参考文献列表 */
    private List<String> references;
    /** 原始完整响应 */
    private String rawResponse;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 完成时间 */
    private LocalDateTime endTime;
    /** 执行耗时（毫秒） */
    private long durationMs;
}
