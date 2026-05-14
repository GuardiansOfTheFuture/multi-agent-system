package com.paperai.service;

import com.paperai.model.entity.Task;
import com.paperai.model.enums.TaskStatus;

import java.util.List;

/**
 * Agent 任务服务接口
 *
 * @author: ch
 * @date 2026年05月11日
 */
public interface AgentTaskService {

    /**
     * 创建任务
     */
    Task createTask(Long paperId, String agentRole, Integer sortOrder, String description, Integer versionNo);

    /**
     * 更新任务状态
     */
    void updateStatus(Long taskId, TaskStatus status);

    /**
     * 更新任务输出
     */
    void updateOutput(Long taskId, String output, long durationMs);

    /**
     * 查询论文的所有任务
     */
    List<Task> getTasksByPaperId(Long paperId);
}
