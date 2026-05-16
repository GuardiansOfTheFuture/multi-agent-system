package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.mapper.FlowDefinitionMapper;
import com.paperai.model.entity.FlowDefinition;
import com.paperai.service.FlowDefinitionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

    @Resource
    private FlowDefinitionMapper flowDefinitionMapper;

    @jakarta.annotation.Resource
    @org.springframework.context.annotation.Lazy
    private FlowDefinitionServiceImpl self;

    @Override
    @Transactional
    @CacheEvict(value = "flowDefinitions", key = "'user:' + #def.userId")
    public FlowDefinition create(FlowDefinition def) {
        flowDefinitionMapper.insert(def);
        log.info("创建流程定义: id={}, name={}, userId={}", def.getId(), def.getName(), def.getUserId());
        return def;
    }

    @Override
    @Cacheable(value = "flowDefinitions", key = "#id")
    public FlowDefinition getById(Long id) {
        FlowDefinition def = flowDefinitionMapper.selectById(id);
        if (def == null) throw new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在");
        return def;
    }

    @Override
    public FlowDefinition getByIdAndUser(Long id, Long userId) {
        FlowDefinition def = self.getById(id);
        if (!def.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该流程");
        }
        return def;
    }

    @Override
    @Cacheable(value = "flowDefinitions", key = "'user:' + #userId")
    public List<FlowDefinition> listByUser(Long userId) {
        return flowDefinitionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getUserId, userId)
                .orderByDesc(FlowDefinition::getUpdatedAt)
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "flowDefinitions", key = "#id"),
        @CacheEvict(value = "flowDefinitions", key = "'user:' + #userId")
    })
    public FlowDefinition update(Long id, FlowDefinition def, Long userId) {
        FlowDefinition existing = getByIdAndUser(id, userId);
        existing.setName(def.getName() != null ? def.getName() : existing.getName());
        existing.setDescription(def.getDescription());
        existing.setGraphData(def.getGraphData() != null ? def.getGraphData() : existing.getGraphData());
        existing.setCategory(def.getCategory() != null ? def.getCategory() : existing.getCategory());
        existing.setIsTemplate(def.getIsTemplate() != null ? def.getIsTemplate() : existing.getIsTemplate());
        flowDefinitionMapper.updateById(existing);
        log.info("更新流程定义: id={}, name={}", id, existing.getName());
        return existing;
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "flowDefinitions", key = "#id"),
        @CacheEvict(value = "flowDefinitions", key = "'user:' + #userId")
    })
    public void delete(Long id, Long userId) {
        getByIdAndUser(id, userId);
        flowDefinitionMapper.deleteById(id);
        log.info("删除流程定义: id={}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "flowDefinitions", key = "'user:' + #userId")
    public FlowDefinition duplicate(Long id, Long userId) {
        FlowDefinition src = self.getById(id);
        FlowDefinition copy = new FlowDefinition();
        copy.setUserId(userId);
        copy.setName((src.getName() != null ? src.getName() : "") + " (副本)");
        copy.setDescription(src.getDescription());
        copy.setGraphData(src.getGraphData());
        copy.setCategory(src.getCategory());
        copy.setIsTemplate(0);
        flowDefinitionMapper.insert(copy);
        log.info("复制流程定义: {} -> {}", id, copy.getId());
        return copy;
    }
}
