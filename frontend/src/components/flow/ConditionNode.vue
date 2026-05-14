<script setup>
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true }
})

const label = computed(() => props.data?.label || '条件')
const condition = computed(() => props.data?.config?.condition || 'output.contains(\'问题\')')
const stepIndex = computed(() => props.data?.stepIndex || '')
const status = computed(() => props.data?.status || 'pending')
</script>

<template>
  <div class="condition-node" :class="'status-' + status">
    <Handle type="target" :position="Position.Top" class="cond-handle" id="top" />
    <Handle type="target" :position="Position.Left" class="cond-handle" id="left" />
    <div class="cond-index">#{{ stepIndex }}</div>
    <div class="node-status-dot" />
    <div class="cond-icon">⇢</div>
    <div class="cond-label">{{ label }}</div>
    <div class="cond-expr">{{ condition }}</div>
    <div class="cond-outputs">
      <span class="output-tag pass">✓ 通过 → 右侧拖出</span>
      <span class="output-tag fail">✗ 不通过 → 下方拖出</span>
    </div>
    <Handle type="source" :position="Position.Right" class="cond-handle cond-out-pass" id="pass" />
    <span class="handle-tag pass-tag">✓ 通过</span>
    <Handle type="source" :position="Position.Bottom" class="cond-handle cond-out-fail" id="fail" />
    <span class="handle-tag fail-tag">✗ 不通过</span>
  </div>
</template>

<style scoped>
.condition-node {
  padding: 10px 14px 8px;
  border-radius: 14px;
  border: 2px solid rgba(250,173,20,0.4);
  background: linear-gradient(145deg, rgba(250,173,20,0.08) 0%, rgba(10,14,23,0.85) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  min-width: 120px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  box-shadow: 0 0 12px rgba(250,173,20,0.15), inset 0 1px 0 rgba(255,255,255,0.04);
  transform: rotate(0deg);
}
.condition-node:hover {
  box-shadow: 0 0 24px rgba(250,173,20,0.3), 0 4px 20px rgba(0,0,0,0.4);
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

.cond-index {
  position: absolute; top: 6px; left: 8px;
  font-size: 9px; color: rgba(255,255,255,0.3);
  font-family: 'SF Mono','Consolas',monospace;
}
.cond-icon { font-size: 22px; margin-bottom: 2px; }
.cond-label { font-size: 11px; color: rgba(255,255,255,0.85); font-weight: 600; line-height: 1.3; }
.cond-expr {
  font-size: 9px; color: rgba(250,173,20,0.6);
  font-family: 'SF Mono','Consolas',monospace;
  margin-top: 2px;
  max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.cond-out-pass {
  border-color: #52c41a !important;
  background: rgba(82,196,26,0.2) !important;
  width: 10px !important; height: 10px !important;
}
.cond-out-fail {
  border-color: #ff4d4f !important;
  background: rgba(255,77,79,0.2) !important;
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
.pass-tag {
  right: -4px; top: calc(50% + 10px);
  background: rgba(82,196,26,0.15); color: #73d13d;
  border: 1px solid rgba(82,196,26,0.3);
}
.fail-tag {
  bottom: -4px; left: calc(50% + 10px);
  background: rgba(255,77,79,0.15); color: #ff7875;
  border: 1px solid rgba(255,77,79,0.3);
}

.cond-outputs {
  display: flex; gap: 6px; justify-content: center; margin-top: 6px;
}
.output-tag {
  font-size: 9px; padding: 1px 8px; border-radius: 10px;
  font-weight: 600;
}
.output-tag.pass { background: rgba(82,196,26,0.15); color: #73d13d; border: 1px solid rgba(82,196,26,0.3); }
.output-tag.fail { background: rgba(255,77,79,0.15); color: #ff7875; border: 1px solid rgba(255,77,79,0.3); }

/* Handles */
.cond-handle {
  width: 6px !important; height: 6px !important;
  border: 1px solid rgba(255,255,255,0.3) !important;
  background: rgba(255,255,255,0.08) !important;
}
</style>
