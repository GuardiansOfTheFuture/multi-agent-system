<template>
  <div class="write-paper">
    <a-row :gutter="16">
      <!-- 左侧：表单 -->
      <a-col :span="9">
        <a-card title="📝 论文写作" :bordered="false">
          <a-form :model="form" layout="vertical">
            <a-form-item label="论文主题" required>
              <a-input
                v-model:value="form.topic"
                placeholder="如：深度学习在医疗影像分割中的应用"
                :disabled="isWriting"
              />
            </a-form-item>

            <a-form-item label="详细描述">
              <a-textarea
                v-model:value="form.description"
                :rows="3"
                placeholder="研究方向、核心问题、预期贡献..."
                :disabled="isWriting"
              />
            </a-form-item>

            <a-form-item label="关键词">
              <a-input
                v-model:value="form.keywords"
                placeholder="用逗号分隔，如：深度学习,医疗影像,图像分割"
                :disabled="isWriting"
              />
            </a-form-item>

            <a-form-item label="章节设置">
              <a-select
                v-model:value="form.sectionPreset"
                @change="handleSectionChange"
                :disabled="isWriting"
              >
                <a-select-option value="default">默认章节（摘要/引言/相关工作/方法/实验/结论）</a-select-option>
                <a-select-option value="custom">自定义章节</a-select-option>
              </a-select>
              <a-input
                v-if="form.sectionPreset === 'custom'"
                v-model:value="form.customSections"
                placeholder="用逗号分隔章节名"
                style="margin-top: 8px"
                :disabled="isWriting"
              />
            </a-form-item>

            <a-form-item label="附加要求">
              <a-textarea
                v-model:value="form.requirements"
                :rows="2"
                placeholder="如：需要引用近三年顶会论文，重点分析...的技术"
                :disabled="isWriting"
              />
            </a-form-item>

            <a-form-item label="审稿迭代轮次">
              <a-slider v-model:value="form.maxReviewRounds" :min="1" :max="5" :disabled="isWriting" />
            </a-form-item>

            <a-button
              type="primary"
              size="large"
              block
              :loading="isWriting"
              :disabled="!form.topic"
              @click="handleWrite"
            >
              <template #icon><send-outlined /></template>
              {{ isWriting ? '论文写作中...' : '开始写作' }}
            </a-button>
          </a-form>
        </a-card>
      </a-col>

      <!-- 右侧：实时进度 + AI 回复 -->
      <a-col :span="15" style="min-width: 0">
        <!-- 等待中 -->
        <a-card v-if="!writingStarted && !currentResult" :bordered="false" class="result-card">
          <a-empty description="输入主题后点击「开始写作」，查看实时写作进度" />
        </a-card>

        <!-- 写作进行中 / 已完成：步骤折叠面板 -->
        <div v-if="currentResult?.steps?.length">
          <a-card :bordered="false" class="result-card">
            <template #title>
              <span style="display: flex; align-items: center; gap: 8px">
                <sync-outlined v-if="isWriting" spin style="color: #1890ff" />
                <check-circle-outlined v-else style="color: #52c41a" />
                <span>{{ isWriting ? `论文写作中 (已完成 ${currentResult.steps.length} 步)` : `写作完成 (共 ${currentResult.steps.length} 步)` }}</span>
              </span>
            </template>
            <template #extra>
              <a-button v-if="!isWriting" type="link" size="small" @click="viewDetail">
                查看最终结果 →
              </a-button>
            </template>

            <!-- 步骤折叠面板 -->
            <a-collapse accordion>
              <a-collapse-panel
                v-for="(step, i) in currentResult.steps"
                :key="i"
              >
                <template #header>
                  <span class="panel-header">
                    <check-circle-filled v-if="step.status === 'COMPLETED'" style="color: #52c41a" />
                    <close-circle-filled v-else-if="step.status === 'FAILED'" style="color: #ff4d4f" />
                    <clock-circle-filled v-else style="color: #999" />
                    <span class="panel-num">{{ i + 1 }}</span>
                    <a-tag :color="getRoleColor(step.agentRole)" size="small">{{ step.agentName }}</a-tag>
                    <span class="panel-label">{{ step.summary }}</span>
                    <span class="panel-time">{{ formatMs(step.durationMs) }}</span>
                  </span>
                </template>

                <div v-if="step.fullOutput" class="ai-output">
                  <div class="ai-output-header">
                    <span>🤖 AI 原始回复（{{ step.fullOutput.length }} 字）</span>
                  </div>
                  <div class="ai-output-body ai-output-scroll">
                    <MarkdownRender :content="step.fullOutput" />
                  </div>
                </div>
                <a-empty v-else description="暂无输出" style="padding: 16px 0" />
              </a-collapse-panel>
            </a-collapse>
          </a-card>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { writePaper } from '@/api'
import { usePaperStepWebSocket } from '@/composables/usePaperStepWebSocket'
import { message } from 'ant-design-vue'
import MarkdownRender from '@/components/MarkdownRender.vue'
import {
  SendOutlined,
  CheckCircleOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  SyncOutlined,
  ClockCircleFilled
} from '@ant-design/icons-vue'

const router = useRouter()

const isWriting = ref(false)
const writingStarted = ref(false)
const currentResult = ref(null)
const currentPaperId = ref(null)

// paperId 变化时 usePaperStepWebSocket 自动连接/断开
usePaperStepWebSocket(currentPaperId, {
  onStep: (step) => {
    // 恢复状态时先把已有的 steps 填回去
    if (!currentResult.value) {
      currentResult.value = { steps: [] }
    }
    currentResult.value.steps.push(step)
    currentResult.value = { ...currentResult.value }
    localStorage.setItem('paperai_steps', JSON.stringify(currentResult.value.steps))
  },
  onComplete: () => {
    isWriting.value = false
    message.success('论文写作完成！')
  },
  onError: (err) => {
    isWriting.value = false
    message.error('写作出错: ' + err)
  }
})

const form = reactive({
  topic: '',
  description: '',
  keywords: '',
  sectionPreset: 'default',
  customSections: '',
  requirements: '',
  maxReviewRounds: 3
})

function handleSectionChange(value) {
  if (value === 'default') {
    form.customSections = ''
  }
}

function getSections() {
  if (form.sectionPreset === 'custom' && form.customSections) {
    return form.customSections.split(/[,，]/).map(s => s.trim()).filter(Boolean)
  }
  return ['摘要', '引言', '相关工作', '方法', '实验', '结论']
}

function formatMs(ms) {
  if (!ms) return ''
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return (ms / 60000).toFixed(1) + 'min'
}

function getStepColor(status) {
  switch (status) {
    case 'COMPLETED': return 'green'
    case 'FAILED': return 'red'
    case 'IN_PROGRESS': return 'blue'
    default: return 'gray'
  }
}

function getRoleColor(role) {
  const colors = {
    SUPERVISOR: 'purple',
    RESEARCHER: 'blue',
    WRITER: 'green',
    REVIEWER: 'orange',
    POLISHER: 'cyan'
  }
  return colors[role] || 'default'
}

// ===== 挂载时恢复 localStorage 中的写作状态 =====
onMounted(() => {
  const savedId = localStorage.getItem('paperai_paperId')
  const savedSteps = localStorage.getItem('paperai_steps')
  if (savedId) {
    const pid = Number(savedId)
    if (pid) {
      currentPaperId.value = pid
      writingStarted.value = true
      currentResult.value = { steps: savedSteps ? JSON.parse(savedSteps) : [] }
      isWriting.value = currentResult.value.steps.length === 0
    }
  }
})

async function handleWrite() {
  if (!form.topic) {
    message.warning('请输入论文主题')
    return
  }

  isWriting.value = true
  writingStarted.value = true
  currentResult.value = { steps: [] }

  try {
    // 1. 发起写作请求，获取 paperId
    const res = await writePaper({
      topic: form.topic,
      description: form.description,
      keywords: form.keywords,
      sections: getSections(),
      requirements: form.requirements,
      maxReviewRounds: form.maxReviewRounds
    })

    if (!res?.data?.paperId) {
      throw new Error('未获取到论文ID')
    }

    // 2. 保存 paperId 到 localStorage + 响应式变量
    currentPaperId.value = res.data.paperId
    localStorage.setItem('paperai_paperId', res.data.paperId)
    localStorage.removeItem('paperai_steps')
    currentResult.value = res.data

    if (res.data.status === 'COMPLETED') {
      isWriting.value = false
      message.success('论文写作完成！')
    }
  } catch (e) {
    isWriting.value = false
    message.error('写作请求失败: ' + e.message)
  }
}

function viewDetail() {
  if (currentResult.value?.paperId) {
    router.push(`/paper/${currentResult.value.paperId}`)
  }
}
</script>

<style scoped>
.write-paper {
  max-width: 100%;
  margin: 0 auto;
  overflow: hidden;
}
.result-card {
  margin-bottom: 16px;
}

/* ===== 折叠面板头部 ===== */
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.panel-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #e8e8e8;
  font-size: 12px;
  font-weight: bold;
  color: #666;
}
.panel-label {
  flex: 1;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.panel-time {
  color: #999;
  font-size: 12px;
  margin-left: auto;
  white-space: nowrap;
}

/* ===== AI 输出区域 ===== */
.ai-output {
  padding: 4px 0;
}
.ai-output-header {
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}
.ai-output-text {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #333;
  word-break: break-word !important;
  white-space: pre-wrap !important;
  overflow-wrap: break-word !important;
}
.ai-output-scroll {
  max-height: 35vh;
  overflow-y: auto;
  padding-right: 6px;
}

/* ===== 折叠面板全局 ===== */
:deep(.ant-collapse-header) {
  align-items: center !important;
}
:deep(.ant-collapse-content-box) {
  overflow-x: hidden;
}
:deep(.ant-card-body) {
  overflow-x: hidden;
}
:deep(ant-layout-content) {
  overflow-x: hidden;
}
</style>
