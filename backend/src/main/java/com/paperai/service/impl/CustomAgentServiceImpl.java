package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.mapper.CustomAgentMapper;
import com.paperai.model.entity.CustomAgent;
import com.paperai.service.CustomAgentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CustomAgentServiceImpl implements CustomAgentService {

    @Resource private CustomAgentMapper customAgentMapper;

    @Override
    public List<CustomAgent> listByUser(Long userId) {
        return customAgentMapper.selectList(
                new LambdaQueryWrapper<CustomAgent>()
                        .eq(CustomAgent::getUserId, userId)
                        .eq(CustomAgent::getEnabled, 1)
                        .orderByDesc(CustomAgent::getUpdatedAt));
    }

    @Override
    public CustomAgent getById(Long id) {
        CustomAgent ca = customAgentMapper.selectById(id);
        if (ca == null) throw new BusinessException(ResultCode.NOT_FOUND, "自定义Agent不存在");
        return ca;
    }

    @Override
    public CustomAgent create(CustomAgent agent) {
        if (agent.getEnabled() == null) agent.setEnabled(1);
        if (agent.getTemperature() == null) agent.setTemperature(0.7);
        if (agent.getModel() == null) agent.setModel("qwen-max");
        if (agent.getIcon() == null) agent.setIcon("🤖");
        customAgentMapper.insert(agent);
        return agent;
    }

    @Override
    public CustomAgent update(Long id, CustomAgent agent, Long userId) {
        CustomAgent existing = customAgentMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "自定义Agent不存在");
        if (!existing.getUserId().equals(userId))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改");
        agent.setId(id);
        customAgentMapper.updateById(agent);
        return customAgentMapper.selectById(id);
    }

    @Override
    public void delete(Long id, Long userId) {
        CustomAgent existing = customAgentMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "自定义Agent不存在");
        if (!existing.getUserId().equals(userId))
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除");
        customAgentMapper.deleteById(id);
    }
}
