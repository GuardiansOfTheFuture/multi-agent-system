package com.paperai.model.dto;

import lombok.Data;

/**
 * 研究请求 DTO
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Data
public class ResearchRequestDTO {

    /** 研究主题/论文标题 */
    private String topic;

    /** 研究方向描述 */
    private String description;

    /** 关键词列表（逗号分隔） */
    private String keywords;

    /** 附加要求 */
    private String requirements;
}
