<script setup>
import { computed } from 'vue'

const props = defineProps({
  node: { type: Object, default: null }
})

const ROLE_INFO = {
  SUPERVISOR: {
    name: '导师 Agent', icon: '🧭', color: '#d4b8ff', accent: '#722ed1',
    desc: '负责选题评估、大纲审阅和最终审核，把控论文整体方向和质量',
    inputs: ['论文主题', '论文大纲', '完整草稿'],
    outputs: ['选题评估意见', '大纲修改建议', '终审结论']
  },
  RESEARCHER: {
    name: '研究员 Agent', icon: '🔬', color: '#b8d8ff', accent: '#1890ff',
    desc: '进行文献调研、信息收集和综述撰写，为写作提供学术素材支撑',
    inputs: ['论文主题', '关键词', '研究方向'],
    outputs: ['文献调研报告', '研究现状分析', '参考资料列表']
  },
  WRITER: {
    name: '写作者 Agent', icon: '✍️', color: '#c8ffb0', accent: '#52c41a',
    desc: '根据大纲和研究材料撰写论文章节，组织语言表达',
    inputs: ['章节大纲', '研究材料', '写作要求'],
    outputs: ['章节内容', '段落文本']
  },
  REVIEWER: {
    name: '审稿人 Agent', icon: '📝', color: '#ffe0b0', accent: '#fa8c16',
    desc: '批判性审阅论文全文，发现逻辑漏洞、数据问题和论证缺陷',
    inputs: ['论文全文', '审稿标准'],
    outputs: ['审稿意见', '问题列表', '修改建议']
  },
  POLISHER: {
    name: '润色师 Agent', icon: '✨', color: '#b0f8f0', accent: '#13c2c2',
    desc: '进行语法校对、格式规范、引用检查和语言润色',
    inputs: ['论文全文'],
    outputs: ['润色后论文', '格式检查报告']
  }
}

const role = computed(() => props.node?.data?.agentRole || '')
const info = computed(() => ROLE_INFO[role.value] || null)
const stepLabel = computed(() => (props.node?.data?.label || '').replace(/^[^\s]+\s/, ''))
</script>

<template>
  <div class="config-panel" v-if="node && info">
    <!-- 角色头部 -->
    <div class="panel-header">
      <span class="panel-icon">{{ info.icon }}</span>
      <div>
        <div class="panel-role-name">{{ info.name }}</div>
        <div class="panel-step-name">{{ stepLabel }}</div>
      </div>
    </div>

    <div class="panel-divider" />

    <!-- 描述 -->
    <div class="panel-section">
      <div class="panel-section-title">📖 角色说明</div>
      <p class="panel-desc">{{ info.desc }}</p>
    </div>

    <!-- 输入 -->
    <div class="panel-section">
      <div class="panel-section-title">📥 输入</div>
      <div class="panel-tags">
        <span v-for="(item, i) in info.inputs" :key="'in-' + i" class="panel-tag tag-in">{{ item }}</span>
      </div>
    </div>

    <!-- 输出 -->
    <div class="panel-section">
      <div class="panel-section-title">📤 输出</div>
      <div class="panel-tags">
        <span v-for="(item, i) in info.outputs" :key="'out-' + i" class="panel-tag tag-out">{{ item }}</span>
      </div>
    </div>

    <div class="panel-divider" />

    <!-- 步骤信息 -->
    <div class="panel-meta" :style="{ borderColor: info.accent }">
      <div class="panel-meta-item">
        <span class="meta-label">步骤序号</span>
        <span class="meta-value">#{{ node.data?.stepIndex || '-' }}</span>
      </div>
      <div class="panel-meta-item">
        <span class="meta-label">执行模式</span>
        <span class="meta-mode">串行执行</span>
      </div>
    </div>
  </div>

  <!-- 未选中节点 -->
  <div v-else class="panel-empty">
    <div class="empty-icon">⚡</div>
    <div class="empty-text">选择节点<br/>查看详情</div>
  </div>
</template>

<style scoped>
.config-panel {
  padding: 4px 2px;
}
.panel-empty {
  padding: 48px 0;
  text-align: center;
}
.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
  opacity: 0.4;
}
.empty-text {
  font-size: 12px;
  color: rgba(255,255,255,0.3);
  line-height: 1.6;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.panel-icon {
  font-size: 28px;
}
.panel-role-name {
  font-size: 15px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}
.panel-step-name {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  margin-top: 2px;
}
.panel-divider {
  height: 1px;
  background: linear-gradient(90deg, rgba(255,255,255,0.08), transparent);
  margin: 14px 0;
}
.panel-section {
  margin: 12px 0;
}
.panel-section-title {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255,255,255,0.5);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.panel-desc {
  font-size: 12px;
  color: rgba(255,255,255,0.6);
  line-height: 1.8;
  margin: 0;
}
.panel-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.panel-tag {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 20px;
  font-weight: 500;
}
.tag-in {
  background: rgba(24,144,255,0.12);
  color: #91caff;
  border: 1px solid rgba(24,144,255,0.2);
}
.tag-out {
  background: rgba(82,196,26,0.12);
  color: #b7eb8f;
  border: 1px solid rgba(82,196,26,0.2);
}
.panel-meta {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.panel-meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
.meta-label {
  color: rgba(255,255,255,0.35);
}
.meta-value {
  font-weight: 600;
  color: rgba(255,255,255,0.8);
  font-family: 'SF Mono', 'Consolas', monospace;
}
.meta-mode {
  font-size: 11px;
  color: rgba(255,255,255,0.45);
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255,255,255,0.05);
}
</style>
