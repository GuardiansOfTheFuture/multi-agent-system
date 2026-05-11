package com.paperai.model.vo;

import com.paperai.model.enums.AgentRole;
import com.paperai.model.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论文写作结果 VO
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
public class PaperWritingVO {

    /** 上下文ID */
    private String contextId;

    /** 数据库中的论文ID（持久化后会回写） */
    private Long paperId;

    /** 论文标题 */
    private String topic;

    /** 最终稿 */
    private String finalDraft;

    /** 摘要 */
    private String abstractText;

    /** 各章节内容 */
    private List<SectionVO> sections;

    /** 审稿意见 */
    private List<String> reviewComments;

    /** 执行步骤记录 */
    private List<StepRecordVO> steps;

    /** 总体状态 */
    private String status;

    /** 总耗时(ms) */
    private long totalDurationMs;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @Data
    public static class SectionVO {
        private String title;
        private int length;
    }

    @Data
    public static class StepRecordVO {
        private String agentName;
        private AgentRole agentRole;
        private TaskStatus status;
        private long durationMs;
        private String summary;
        /** AI 完整返回内容 */
        private String fullOutput;
    }
}
