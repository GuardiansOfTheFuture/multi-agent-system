package com.paperai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperai.model.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 消息 Mapper 接口
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}
