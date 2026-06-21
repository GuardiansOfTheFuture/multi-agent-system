<template>
  <div class="agent-sidebar">
    <div class="sidebar-header">
      <span class="sidebar-title">⚡ Agent 状态</span>
      <a-tag v-if="isRunning" color="processing" size="small">执行中</a-tag>
      <a-tag v-else color="default" size="small">空闲</a-tag>
    </div>

    <div class="steps-list">
      <div
        v-for="(step, i) in steps"
        :key="i"
        class="step-item"
        :class="'step-' + (step.status || '').toLowerCase()"
      >
        <div class="step-indicator">
          <loading-outlined v-if="step.status === 'IN_PROGRESS'" spin class="step-spin" />
          <check-circle-filled v-else-if="step.status === 'COMPLETED'" class="step-done-icon" />
          <close-circle-filled v-else-if="step.status === 'FAILED'" class="step-fail-icon" />
          <span v-else class="step-dot" />
        </div>
        <div class="step-info">
          <div class="step-name">{{ step.agentName }}</div>
          <div class="step-role">{{ roleLabel(step.agentRole) }}</div>
          <div v-if="step.durationMs" class="step-time">{{ formatMs(step.durationMs) }}</div>
        </div>
      </div>

      <div v-if="steps.length === 0" class="steps-empty">
        <span style="color: rgba(255,255,255,0.2)">等待开始...</span>
      </div>
    </div>

    <!-- 当前正在执行的 Agent -->
    <div v-if="currentStep" class="current-agent">
      <div class="agent-pulse"></div>
      <span>{{ currentStep }} 执行中...</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { LoadingOutlined, CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons-vue'

const props = defineProps({
  paperId: { type: Number, default: null },
  steps: { type: Array, default: () => [] },
  currentStep: { type: String, default: '' }
})

const isRunning = computed(() => {
  return props.steps.some(s => s.status === 'IN_PROGRESS') || !!props.currentStep
})

function roleLabel(role) {
  const m = { SUPERVISOR: '导师', RESEARCHER: '研究员', WRITER: '写作者', REVIEWER: '审稿人', POLISHER: '润色师' }
  return m[role] || role
}

function formatMs(ms) {
  if (!ms) return ''
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return (ms / 60000).toFixed(1) + 'min'
}
</script>

<style scoped>
.agent-sidebar {
  width: 240px;
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(15, 20, 38, 0.5);
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

/* ===== 步骤列表 ===== */
.steps-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  transition: all 0.2s;
}

.step-active {
  background: rgba(24, 144, 255, 0.08);
}

.step-done {
  opacity: 0.7;
}

.step-fail {
  background: rgba(255, 77, 79, 0.06);
}

.step-indicator {
  margin-top: 2px;
  flex-shrink: 0;
}

.step-spin {
  font-size: 14px;
  color: #1890ff;
}

.step-done-icon {
  font-size: 14px;
  color: #52c41a;
}

.step-fail-icon {
  font-size: 14px;
  color: #ff4d4f;
}

.step-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  display: block;
  margin-top: 4px;
}

.step-info {
  flex: 1;
  min-width: 0;
}

.step-name {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step-role {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  margin-top: 2px;
}

.step-time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  margin-top: 2px;
}

.steps-empty {
  padding: 20px;
  text-align: center;
  font-size: 12px;
}

/* ===== 当前执行 Agent ===== */
.current-agent {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(146, 84, 222, 0.08);
  border: 1px solid rgba(146, 84, 222, 0.15);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(146, 84, 222, 0.8);
}

.agent-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9254de;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}
</style>
