<script setup>
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true }
})

const type = computed(() => props.data?.type || 'concept')
const label = computed(() => props.data?.label || '')
const desc = computed(() => props.data?.desc || '')

const TYPE_STYLE = {
  concept: { icon: '💡', color: '#a78bfa', border: 'rgba(167,139,250,0.5)', bg: 'rgba(167,139,250,0.10)', text: '#d4c5ff' },
  paper:   { icon: '📄', color: '#60a5fa', border: 'rgba(96,165,250,0.5)', bg: 'rgba(96,165,250,0.10)', text: '#c5ddff' },
  author:  { icon: '👤', color: '#f59e0b', border: 'rgba(245,158,11,0.5)', bg: 'rgba(245,158,11,0.10)', text: '#ffe4b0' },
  method:  { icon: '⚙️', color: '#10b981', border: 'rgba(16,185,129,0.5)', bg: 'rgba(16,185,129,0.10)', text: '#b0ffe0' },
  dataset: { icon: '📊', color: '#f472b6', border: 'rgba(244,114,182,0.5)', bg: 'rgba(244,114,182,0.10)', text: '#ffc8e8' },
  topic:   { icon: '🎯', color: '#ef4444', border: 'rgba(239,68,68,0.5)', bg: 'rgba(239,68,68,0.10)', text: '#ffc8c8' },
  problem: { icon: '❓', color: '#f97316', border: 'rgba(249,115,22,0.5)', bg: 'rgba(249,115,22,0.10)', text: '#ffd8b8' },
  finding: { icon: '✨', color: '#06b6d4', border: 'rgba(6,182,212,0.5)', bg: 'rgba(6,182,212,0.10)', text: '#b8f4ff' }
}

const style = computed(() => TYPE_STYLE[type.value] || TYPE_STYLE.concept)
</script>

<template>
  <div
    class="entity-node"
    :style="{
      '--color': style.color, '--border': style.border, '--bg': style.bg, '--text': style.text
    }"
  >
    <Handle type="target" :position="Position.Top" class="entity-handle" id="top" />
    <Handle type="target" :position="Position.Left" class="entity-handle" id="left" />
    <div class="entity-type-tag" :style="{ background: style.color + '22', color: style.color }">
      {{ type }}
    </div>
    <div class="entity-icon">{{ style.icon }}</div>
    <div class="entity-label">{{ label }}</div>
    <div v-if="desc" class="entity-desc">{{ desc.length > 60 ? desc.slice(0,60) + '...' : desc }}</div>
    <Handle type="source" :position="Position.Right" class="entity-handle" id="right" />
    <Handle type="source" :position="Position.Bottom" class="entity-handle" id="bottom" />
  </div>
</template>

<style scoped>
.entity-node {
  padding: 10px 14px 8px;
  border-radius: 12px;
  border: 2px solid var(--border);
  background: linear-gradient(145deg, var(--bg) 0%, rgba(10,14,23,0.88) 100%);
  backdrop-filter: blur(8px);
  min-width: 100px;
  max-width: 180px;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 0 8px color-mix(in srgb, var(--color) 30%, transparent);
}
.entity-node:hover {
  transform: translateY(-2px);
  box-shadow: 0 0 20px color-mix(in srgb, var(--color) 50%, transparent), 0 2px 12px rgba(0,0,0,0.4);
}
.entity-type-tag {
  position: absolute; top: 6px; right: 6px;
  font-size: 8px; padding: 1px 5px; border-radius: 6px;
  font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;
}
.entity-icon { font-size: 20px; margin-bottom: 2px; }
.entity-label { font-size: 11px; color: rgba(255,255,255,0.9); font-weight: 500; line-height: 1.3; }
.entity-desc { font-size: 9px; color: rgba(255,255,255,0.4); margin-top: 4px; line-height: 1.3; }
.entity-handle {
  width: 6px !important; height: 6px !important;
  border: 1px solid rgba(255,255,255,0.2) !important;
  background: rgba(255,255,255,0.06) !important;
  transition: all 0.2s;
}
.entity-handle:hover { border-color: rgba(255,255,255,0.5) !important; background: rgba(255,255,255,0.15) !important; }
</style>
