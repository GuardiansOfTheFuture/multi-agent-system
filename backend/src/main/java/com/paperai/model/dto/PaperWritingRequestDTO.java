package com.paperai.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 论文写作请求 DTO
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
public class PaperWritingRequestDTO {

    /** 论文标题 */
    private String topic;

    /** 研究方向描述 */
    private String description;

    /** 关键词 */
    private String keywords;

    /** 章节列表（省略则自动生成大纲） */
    private List<String> sections;

    /** 附加要求 */
    private String requirements;

    /** 最大审稿迭代轮次 */
    private Integer maxReviewRounds = 3;

    /** 写作流程ID（为空时使用标准流程） */
    private String flowId;

    /** 关联的知识图谱ID */
    private Long kgId;
}
