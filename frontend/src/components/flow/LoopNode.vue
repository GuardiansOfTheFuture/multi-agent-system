<script setup>
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true }
})

const label = computed(() => props.data?.label || '循环')
const maxIter = computed(() => props.data?.config?.maxIterations || 3)
const stepIndex = computed(() => props.data?.stepIndex || '')
const status = computed(() => props.data?.status || 'pending')
</script>

<template>
  <div class="loop-node" :class="'status-' + status">
    <Handle type="target" :position="Position.Top" class="loop-handle" id="top" />
    <Handle type="target" :position="Position.Left" class="loop-handle" id="left" />
    <div class="loop-index">#{{ stepIndex }}</div>
    <div class="node-status-dot" />
    <div class="loop-icon">↺</div>
    <div class="loop-label">{{ label }}</div>
    <div class="loop-max">最多 {{ maxIter }} 次迭代</div>
    <div class="loop-ring">
      <span class="ring-dot" />
      <span class="ring-line" />
      <span class="ring-dot" />
    </div>
    <Handle type="source" :position="Position.Right" class="loop-handle loop-out-next" id="next" />
    <span class="handle-tag next-tag">→ 下一步</span>
    <Handle type="source" :position="Position.Bottom" class="loop-handle loop-out-back" id="back" />
    <span class="handle-tag back-tag">↺ 回退</span>
  </div>
</template>

<style scoped>
.loop-node {
  padding: 10px 14px 8px;
  border-radius: 50px;
  border: 2px solid rgba(89,126,247,0.4);
  background: linear-gradient(145deg, rgba(89,126,247,0.08) 0%, rgba(10,14,23,0.85) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  min-width: 110px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  box-shadow: 0 0 12px rgba(89,126,247,0.15), inset 0 1px 0 rgba(255,255,255,0.04);
}
.loop-node:hover {
  box-shadow: 0 0 24px rgba(89,126,247,0.3), 0 4px 20px rgba(0,0,0,0.4);
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
.status-failed .node-status-dot { background: #ff4d4f; }

@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.5); }
}

.loop-index {
  position: absolute; top: 6px; left: 10px;
  font-size: 9px; color: rgba(255,255,255,0.3);
  font-family: 'SF Mono','Consolas',monospace;
}
.loop-icon { font-size: 20px; margin-bottom: 0; }
.loop-label { font-size: 11px; color: rgba(255,255,255,0.85); font-weight: 600; line-height: 1.3; }
.loop-max {
  font-size: 9px; color: rgba(89,126,247,0.5);
  font-family: 'SF Mono','Consolas',monospace;
  margin-top: 2px;
}
.loop-ring {
  display: flex; align-items: center; justify-content: center; gap: 0; margin-top: 4px;
}
.ring-dot { width: 5px; height: 5px; border-radius: 50%; background: rgba(89,126,247,0.4); }
.ring-line { width: 20px; height: 1px; background: rgba(89,126,247,0.25); }

.loop-out-next {
  border-color: #52c41a !important;
  background: rgba(82,196,26,0.2) !important;
  width: 10px !important; height: 10px !important;
}
.loop-out-back {
  border-color: #597ef7 !important;
  background: rgba(89,126,247,0.2) !important;
  width: 10px !important; height: 10px !important;
}
.handle-tag {
  position: absolute;
  font-size: 9px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 600;
  pointer-events: none;
  white-space: nowrap;
  z-index: 5;
}
.next-tag {
  right: -4px; top: calc(50% + 10px);
  background: rgba(82,196,26,0.15); color: #73d13d;
  border: 1px solid rgba(82,196,26,0.3);
}
.back-tag {
  bottom: -4px; left: calc(50% + 10px);
  background: rgba(89,126,247,0.15); color: #85a5ff;
  border: 1px solid rgba(89,126,247,0.3);
}

/* Handles */
.loop-handle {
  width: 6px !important; height: 6px !important;
  border: 1px solid rgba(255,255,255,0.3) !important;
  background: rgba(255,255,255,0.08) !important;
}
</style>
