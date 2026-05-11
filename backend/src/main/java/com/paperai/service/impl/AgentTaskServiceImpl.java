package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.mapper.TaskMapper;
import com.paperai.model.entity.Task;
import com.paperai.model.enums.TaskStatus;
import com.paperai.service.AgentTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 任务服务实现
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Slf4j
@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    @Resource
    private TaskMapper taskMapper;

    @Override
    public Task createTask(Long paperId, String agentRole, Integer sortOrder, String description) {
        Task task = new Task();
        task.setPaperId(paperId);
        task.setAgentRole(agentRole);
        task.setSortOrder(sortOrder);
        task.setDescription(description);
        task.setStatus(TaskStatus.PENDING.getCode());
        taskMapper.insert(task);
        log.info("创建任务: id={}, role={}, paperId={}", task.getId(), agentRole, paperId);
        return task;
    }

    @Override
    public void updateStatus(Long taskId, TaskStatus status) {
        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status.getCode());
            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                task.setCompletedAt(LocalDateTime.now());
            }
            taskMapper.updateById(task);
        }
    }

    @Override
    public void updateOutput(Long taskId, String output, long durationMs) {
        Task task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setOutputData(output);
            task.setDurationMs(durationMs);
            task.setStatus(TaskStatus.COMPLETED.getCode());
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    @Override
    public List<Task> getTasksByPaperId(Long paperId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getPaperId, paperId)
                        .orderByAsc(Task::getSortOrder)
        );
    }
}
