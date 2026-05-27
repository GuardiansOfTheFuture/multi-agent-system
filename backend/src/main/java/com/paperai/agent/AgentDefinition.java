package com.paperai.agent;

import com.paperai.model.enums.AgentRole;

/**
 * Agent 定义 — 纯数据，替代原来 5 个 Agent 子类的继承体系。
 * 每个 Agent 只是一个角色 + System Prompt。
 */
public record AgentDefinition(AgentRole role, String systemPrompt) {}
