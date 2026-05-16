package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.mapper.KnowledgeGraphMapper;
import com.paperai.model.entity.KnowledgeGraph;
import com.paperai.service.KnowledgeGraphService;
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
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    @Resource
    private KnowledgeGraphMapper knowledgeGraphMapper;

    @jakarta.annotation.Resource
    @org.springframework.context.annotation.Lazy
    private KnowledgeGraphServiceImpl self;

    @Override
    @Transactional
    @CacheEvict(value = "knowledgeGraphs", key = "'user:' + #kg.userId")
    public KnowledgeGraph create(KnowledgeGraph kg) {
        knowledgeGraphMapper.insert(kg);
        log.info("创建知识图谱: id={}, name={}, userId={}", kg.getId(), kg.getName(), kg.getUserId());
        return kg;
    }

    @Override
    @Cacheable(value = "knowledgeGraphs", key = "#id")
    public KnowledgeGraph getById(Long id) {
        KnowledgeGraph kg = knowledgeGraphMapper.selectById(id);
        if (kg == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识图谱不存在");
        return kg;
    }

    @Override
    public KnowledgeGraph getByIdAndUser(Long id, Long userId) {
        KnowledgeGraph kg = self.getById(id);
        if (!kg.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该知识图谱");
        }
        return kg;
    }

    @Override
    @Cacheable(value = "knowledgeGraphs", key = "'user:' + #userId")
    public List<KnowledgeGraph> listByUser(Long userId) {
        return knowledgeGraphMapper.selectList(
            new LambdaQueryWrapper<KnowledgeGraph>()
                .eq(KnowledgeGraph::getUserId, userId)
                .orderByDesc(KnowledgeGraph::getUpdatedAt)
        );
    }

    @Override
    public List<KnowledgeGraph> listByPaper(Long paperId) {
        return knowledgeGraphMapper.selectList(
            new LambdaQueryWrapper<KnowledgeGraph>()
                .eq(KnowledgeGraph::getPaperId, paperId)
                .orderByDesc(KnowledgeGraph::getUpdatedAt)
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "knowledgeGraphs", key = "#id"),
        @CacheEvict(value = "knowledgeGraphs", key = "'user:' + #userId")
    })
    public KnowledgeGraph update(Long id, KnowledgeGraph kg, Long userId) {
        KnowledgeGraph existing = getByIdAndUser(id, userId);
        existing.setName(kg.getName() != null ? kg.getName() : existing.getName());
        existing.setDescription(kg.getDescription());
        existing.setGraphData(kg.getGraphData() != null ? kg.getGraphData() : existing.getGraphData());
        existing.setPaperId(kg.getPaperId());
        knowledgeGraphMapper.updateById(existing);
        log.info("更新知识图谱: id={}, name={}", id, existing.getName());
        return existing;
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "knowledgeGraphs", key = "#id"),
        @CacheEvict(value = "knowledgeGraphs", key = "'user:' + #userId")
    })
    public void delete(Long id, Long userId) {
        getByIdAndUser(id, userId);
        knowledgeGraphMapper.deleteById(id);
        log.info("删除知识图谱: id={}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "knowledgeGraphs", key = "'user:' + #userId")
    public KnowledgeGraph duplicate(Long id, Long userId) {
        KnowledgeGraph src = self.getById(id);
        KnowledgeGraph copy = new KnowledgeGraph();
        copy.setUserId(userId);
        copy.setName((src.getName() != null ? src.getName() : "") + " (副本)");
        copy.setDescription(src.getDescription());
        copy.setGraphData(src.getGraphData());
        copy.setPaperId(src.getPaperId());
        knowledgeGraphMapper.insert(copy);
        log.info("复制知识图谱: {} -> {}", id, copy.getId());
        return copy;
    }
}
