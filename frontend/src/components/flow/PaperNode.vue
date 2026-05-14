<script setup>
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true }
})

const label = computed(() => props.data?.label || '论文任务')
const paperTitle = computed(() => props.data?.config?.paperTitle || '未选择')
const paperId = computed(() => props.data?.config?.paperId || null)
const status = computed(() => props.data?.status || 'pending')
</script>

<template>
  <div class="paper-node" :class="'status-' + status">
    <div class="paper-index">起始</div>
    <div class="node-status-dot" />
    <div class="paper-icon">📄</div>
    <div class="paper-label">{{ label }}</div>
    <div class="paper-title" :title="paperTitle">{{ paperTitle }}</div>
    <Handle type="source" :position="Position.Bottom" id="bottom" />
    <Handle type="source" :position="Position.Right" id="right" />
  </div>
</template>

<style scoped>
.paper-node {
  padding: 10px 14px 8px;
  border-radius: 14px;
  border: 2px solid rgba(146,84,222,0.5);
  background: linear-gradient(145deg, rgba(146,84,222,0.1) 0%, rgba(10,14,23,0.85) 100%);
  backdrop-filter: blur(12px);
  width: 150px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  box-shadow: 0 0 14px rgba(146,84,222,0.18), inset 0 1px 0 rgba(255,255,255,0.04);
}
.paper-node:hover {
  box-shadow: 0 0 24px rgba(146,84,222,0.35), 0 4px 20px rgba(0,0,0,0.4);
  transform: translateY(-3px);
}
.status-in_progress { border-color: #597ef7 !important; box-shadow: 0 0 20px rgba(89,126,247,0.5) !important; animation: pulse 1.5s ease-in-out infinite; }
.status-completed { border-color: #52c41a !important; box-shadow: 0 0 12px rgba(82,196,26,0.3) !important; }
.status-failed { border-color: #ff4d4f !important; box-shadow: 0 0 12px rgba(255,77,79,0.3) !important; }
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 20px rgba(89,126,247,0.5); }
  50% { box-shadow: 0 0 35px rgba(89,126,247,0.7); }
}
.node-status-dot {
  position: absolute; top: 8px; right: 8px;
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(255,255,255,0.2);
}
.status-in_progress .node-status-dot { background: #597ef7; animation: dotPulse 0.8s ease-in-out infinite; }
.status-completed .node-status-dot { background: #52c41a; }
@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.5); }
}
.paper-index {
  position: absolute; top: 6px; left: 8px;
  font-size: 9px; color: rgba(146,84,222,0.5);
  font-family: 'SF Mono','Consolas',monospace;
  font-weight: 600;
}
.paper-icon { font-size: 20px; margin-bottom: 2px; }
.paper-label { font-size: 11px; color: rgba(255,255,255,0.85); font-weight: 600; }
.paper-title {
  font-size: 10px; color: rgba(255,255,255,0.4);
  margin-top: 3px; width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
:deep(.vue-flow__handle) {
  width: 6px; height: 6px;
  border: 1px solid rgba(255,255,255,0.3);
  background: rgba(255,255,255,0.08);
}
</style>
