<script setup>
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true }
})

const role = computed(() => props.data?.agentRole || 'SUPERVISOR')
const label = computed(() => (props.data?.label || '').replace(/^[^\s]+\s/, ''))
const icon = computed(() => (props.data?.label || '').match(/^[^\s]+/)?.[0] || '')
const stepIndex = computed(() => props.data?.stepIndex || '')
const status = computed(() => props.data?.status || 'pending')
const disabled = computed(() => props.data?.disabled || false)

const ROLE_STYLE = {
  SUPERVISOR: { label: '导师', glow: 'rgba(146,84,222,0.6)', bg: 'rgba(146,84,222,0.12)', border: 'rgba(180,130,255,0.5)', text: '#d4b8ff' },
  RESEARCHER: { label: '研究员', glow: 'rgba(24,144,255,0.6)', bg: 'rgba(24,144,255,0.10)', border: 'rgba(100,180,255,0.5)', text: '#b8d8ff' },
  WRITER:     { label: '写作者', glow: 'rgba(82,196,26,0.6)', bg: 'rgba(82,196,26,0.10)', border: 'rgba(140,240,80,0.5)', text: '#c8ffb0' },
  REVIEWER:   { label: '审稿人', glow: 'rgba(250,140,22,0.6)', bg: 'rgba(250,140,22,0.10)', border: 'rgba(255,190,100,0.5)', text: '#ffe0b0' },
  POLISHER:   { label: '润色师', glow: 'rgba(19,194,194,0.6)', bg: 'rgba(19,194,194,0.10)', border: 'rgba(80,230,230,0.5)', text: '#b0f8f0' }
}

const style = computed(() => ROLE_STYLE[role.value] || ROLE_STYLE.SUPERVISOR)

const statusDot = computed(() => {
  const m = { pending: 'rgba(255,255,255,0.2)', in_progress: '#597ef7', completed: '#52c41a', failed: '#ff4d4f' }
  return m[status.value] || m.pending
})
</script>

<template>
  <div
    class="agent-node"
    :class="['status-' + status, { 'node-disabled': disabled }]"
    :style="{
      '--glow': style.glow, '--bg': style.bg, '--border': style.border, '--text': style.text,
      '--status-color': statusDot
    }"
  >
    <Handle type="target" :position="Position.Top" class="agent-handle" id="top" />
    <Handle type="target" :position="Position.Left" class="agent-handle" id="left" />
    <div class="node-index">#{{ stepIndex }}</div>
    <div class="node-status-dot" />
    <div class="node-icon">{{ icon }}</div>
    <div class="node-label">{{ label }}</div>
    <div class="node-role" :style="{ color: style.text }">{{ disabled ? '已禁用' : style.label }}</div>
    <Handle type="source" :position="Position.Right" class="agent-handle" id="right" />
    <Handle type="source" :position="Position.Bottom" class="agent-handle" id="bottom" />
  </div>
</template>

<style scoped>
.agent-node {
  padding: 10px 16px 8px;
  border-radius: 12px;
  border: 2px solid var(--border);
  background: linear-gradient(145deg, var(--bg) 0%, rgba(10,14,23,0.85) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  min-width: 110px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  box-shadow: 0 0 12px var(--glow), inset 0 1px 0 rgba(255,255,255,0.04);
}
.agent-node:hover {
  transform: translateY(-3px);
  box-shadow: 0 0 24px var(--glow), 0 4px 20px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.06);
}

/* 执行状态 */
.status-pending { border-color: var(--border); }
.status-in_progress {
  border-color: #597ef7 !important;
  box-shadow: 0 0 20px rgba(89,126,247,0.5), 0 0 40px rgba(89,126,247,0.2), inset 0 1px 0 rgba(255,255,255,0.04) !important;
  animation: pulse 1.5s ease-in-out infinite;
}
.status-completed { border-color: #52c41a !important; box-shadow: 0 0 12px rgba(82,196,26,0.4), inset 0 1px 0 rgba(255,255,255,0.04) !important; }
.status-failed { border-color: #ff4d4f !important; box-shadow: 0 0 12px rgba(255,77,79,0.4), inset 0 1px 0 rgba(255,255,255,0.04) !important; }
.node-disabled { opacity: 0.4; pointer-events: none; }

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 20px rgba(89,126,247,0.5), 0 0 40px rgba(89,126,247,0.2), inset 0 1px 0 rgba(255,255,255,0.04); }
  50% { box-shadow: 0 0 35px rgba(89,126,247,0.7), 0 0 60px rgba(89,126,247,0.35), inset 0 1px 0 rgba(255,255,255,0.06); }
}

.node-status-dot {
  position: absolute; top: 8px; right: 8px;
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--status-color);
  transition: background 0.3s;
}
.status-in_progress .node-status-dot {
  animation: dotPulse 0.8s ease-in-out infinite;
}
@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.5); }
}

.node-index {
  position: absolute; top: 6px; left: 8px;
  font-size: 9px; color: rgba(255,255,255,0.3);
  font-family: 'SF Mono','Consolas',monospace;
}
.node-icon { font-size: 22px; margin-bottom: 2px; }
.node-label { font-size: 11px; color: rgba(255,255,255,0.85); font-weight: 500; line-height: 1.3; white-space: nowrap; }
.node-role { font-size: 10px; font-weight: 600; margin-top: 2px; letter-spacing: 0.5px; }

.agent-handle {
  width: 6px !important; height: 6px !important;
  border: 1px solid rgba(255,255,255,0.3) !important;
  background: rgba(255,255,255,0.08) !important;
  transition: all 0.2s;
}
.agent-handle:hover { border-color: rgba(255,255,255,0.6) !important; background: rgba(255,255,255,0.2) !important; }
</style>
