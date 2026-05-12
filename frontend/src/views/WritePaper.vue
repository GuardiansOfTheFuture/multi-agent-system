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
                :disabled="phase !== 'idle'"
              />
            </a-form-item>

            <a-form-item label="详细描述">
              <a-textarea
                v-model:value="form.description"
                :rows="3"
                placeholder="研究方向、核心问题、预期贡献..."
                :disabled="phase !== 'idle'"
              />
            </a-form-item>

            <a-form-item label="关键词">
              <a-input
                v-model:value="form.keywords"
                placeholder="用逗号分隔，如：深度学习,医疗影像,图像分割"
                :disabled="phase !== 'idle'"
              />
            </a-form-item>

            <a-form-item label="章节设置">
              <a-select
                v-model:value="form.sectionPreset"
                @change="handleSectionChange"
                :disabled="phase !== 'idle'"
              >
                <a-select-option value="default">默认章节（摘要/引言/相关工作/方法/实验/结论）</a-select-option>
                <a-select-option value="custom">自定义章节</a-select-option>
              </a-select>
              <a-input
                v-if="form.sectionPreset === 'custom'"
                v-model:value="form.customSections"
                placeholder="用逗号分隔章节名"
                style="margin-top: 8px"
                :disabled="phase !== 'idle'"
              />
            </a-form-item>

            <a-form-item label="附加要求">
              <a-textarea
                v-model:value="form.requirements"
                :rows="2"
                placeholder="如：需要引用近三年顶会论文，重点分析...的技术"
                :disabled="phase !== 'idle'"
              />
            </a-form-item>

            <a-form-item label="审稿迭代轮次">
              <a-slider v-model:value="form.maxReviewRounds" :min="1" :max="5" :disabled="phase !== 'idle'" />
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                size="large"
                block
                :loading="phase === 'creating' || phase === 'writing'"
                :disabled="!form.topic || phase !== 'idle'"
                @click="handleWrite"
              >
                <template #icon><send-outlined /></template>
                {{ phase === 'creating' ? '创建论文中...' : phase === 'writing' ? '论文写作中...' : '开始写作' }}
              </a-button>
            </a-form-item>
            <a-form-item v-if="phase === 'writing'">
              <a-button danger size="large" block @click="handleStop">
                <template #icon><stop-outlined /></template>
                停止写作
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>

      <!-- 右侧：实时进度 + AI 回复 -->
      <a-col :span="15" style="min-width: 0">
        <!-- 空闲：未开始 -->
        <a-card v-if="phase === 'idle'" :bordered="false" class="result-card">
          <a-empty description="输入主题后点击「开始写作」，查看实时写作进度" />
        </a-card>

        <!-- 创建阶段：请求后端创建论文 -->
        <a-card v-if="phase === 'creating'" :bordered="false" class="result-card">
          <div style="padding: 48px 0; text-align: center; color: #999">
            <loading-outlined spin style="font-size: 28px; margin-bottom: 16px; display: block" />
            <div style="font-size: 15px; font-weight: 500; color: #555">正在创建论文记录…</div>
            <div style="margin-top: 4px; font-size: 13px">连接后端服务，准备启动写作引擎</div>
          </div>
        </a-card>

        <!-- 写作阶段：WebSocket 连接中 或 步骤进行中 -->
        <div v-if="phase === 'writing' || phase === 'done'">
          <a-card :bordered="false" class="result-card">
            <template #title>
              <span style="display: flex; align-items: center; gap: 8px">
                <sync-outlined v-if="phase === 'writing'" spin style="color: #1890ff" />
                <check-circle-outlined v-else style="color: #52c41a" />
                <span>
                  {{ phase === 'writing'
                    ? `论文写作中 (已完成 ${(currentResult?.steps?.length) || 0} 步)`
                    : `写作完成 (共 ${(currentResult?.steps?.length) || 0} 步)` }}
                </span>
              </span>
            </template>
            <template #extra>
              <a-tag v-if="!wsConnected && phase === 'writing'" color="orange">
                <loading-outlined spin style="margin-right: 4px" />WS 连接中
              </a-tag>
              <a-space v-if="phase === 'done'">
                <a-button type="link" size="small" @click="viewDetail">查看最终结果 →</a-button>
                <a-button size="small" @click="resetToIdle">开始新的写作</a-button>
              </a-space>
            </template>

            <!-- 流式文本实时展示（当前步骤正在生成时） -->
            <div v-if="streamingText" style="padding: 0 0 16px 0">
              <a-card size="small" :title="`🤖 ${streamingStepName} - 实时生成中...`" :bordered="true">
                <div class="ai-output-body" style="max-height: 45vh; overflow-y: auto">
                  <MarkdownRender :content="streamingText" />
                </div>
              </a-card>
            </div>

            <!-- 等待第一个步骤或处于连接中 — 有流式文本就不显示了 -->
            <div v-if="!(currentResult?.steps?.length) && !streamingText && wsConnected" style="padding: 32px 0; text-align: center; color: #999">
              <loading-outlined spin style="font-size: 24px; margin-bottom: 12px; display: block" />
              <span>正在等待写作引擎返回第一步...</span>
              <div style="margin-top: 16px">
                <a-button size="small" @click="resetToIdle">取消等待</a-button>
              </div>
            </div>
            <div v-if="!(currentResult?.steps?.length) && !streamingText && !wsConnected" style="padding: 32px 0; text-align: center; color: #999">
              <loading-outlined spin style="font-size: 24px; margin-bottom: 12px; display: block" />
              <span>正在连接写作引擎...</span>
              <div style="margin-top: 16px">
                <a-button size="small" @click="resetToIdle">取消等待</a-button>
              </div>
            </div>

            <!-- 步骤折叠面板 -->
            <a-collapse accordion v-if="currentResult?.steps?.length">
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
                    <span class="panel-label">{{ step.summary || '等待中...' }}</span>
                    <span class="panel-time">{{ step.durationMs ? formatMs(step.durationMs) : '' }}</span>
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
                <a-empty v-else-if="step.status === 'COMPLETED'" description="暂无输出" style="padding: 16px 0" />
                <a-empty v-else description="步骤进行中..." style="padding: 16px 0">
                  <template #description>
                    <span><loading-outlined spin /> 步骤进行中...</span>
                  </template>
                </a-empty>
              </a-collapse-panel>
            </a-collapse>
          </a-card>
        </div>

        <!-- 错误 -->
        <a-card v-if="phase === 'error'" :bordered="false" class="result-card">
          <a-result status="error" title="写作服务异常" :sub-title="errorMsg">
            <template #extra>
              <a-button type="primary" @click="handleRetry">重试</a-button>
              <a-button @click="resetToIdle">返回</a-button>
            </template>
          </a-result>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { createPaper, startWriting, stopWriting } from '@/api'
import { usePaperStepWebSocket } from '@/composables/usePaperStepWebSocket'
import { message } from 'ant-design-vue'
import MarkdownRender from '@/components/MarkdownRender.vue'
import {
  SendOutlined,
  CheckCircleOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  SyncOutlined,
  ClockCircleFilled,
  LoadingOutlined,
  StopOutlined
} from '@ant-design/icons-vue'

const router = useRouter()

// =============================================
// 单一状态机
// idle → creating → writing → done
//                        ↓
//                      error (可重试)
// =============================================
const phase = ref('idle')           // idle | creating | writing | done | error
const errorMsg = ref('')
const currentResult = ref(null)     // { steps: [...] }
const currentPaperId = ref(null)
// 当前步骤的流式文本（步骤完成后清空）
const streamingText = ref('')
const streamingStepName = ref('')

// WebSocket 连接状态（由 hook 暴露）
const { connected: wsConnected } = usePaperStepWebSocket(currentPaperId, {
  onStep: (step) => {
    if (!currentResult.value) {
      currentResult.value = { steps: [] }
    }
    // 查找是否已有该名称的步骤（流式推送可能在步骤完成前就开始了）
    const existingIdx = currentResult.value.steps.findIndex(
      s => s.agentName === step.agentName && s.status !== 'COMPLETED' && s.status !== 'FAILED'
    )
    if (existingIdx >= 0) {
      // 替换已有的占位步骤
      currentResult.value.steps[existingIdx] = step
    } else {
      currentResult.value.steps.push(step)
    }
    // 该步骤完成，清空流式文本
    if (step.status === 'COMPLETED' || step.status === 'FAILED') {
      streamingText.value = ''
      streamingStepName.value = ''
    }
    currentResult.value = { ...currentResult.value }
    localStorage.setItem('paperai_steps', JSON.stringify(currentResult.value.steps))
  },
  // 新增：流式 token 回调
  onStream: (data) => {
    streamingStepName.value = data.agentName
    streamingText.value = data.fullText
  },
  onComplete: () => {
    phase.value = 'done'
    message.success('论文写作完成！')
    // 3 秒后自动重置页面
    setTimeout(() => {
      if (phase.value === 'done') resetToIdle()
    }, 3000)
  },
  onError: (err) => {
    if (phase.value !== 'writing') return
    phase.value = 'error'
    errorMsg.value = err
    message.error('写作出错: ' + err)
    // 断开 WebSocket，清理状态
    currentPaperId.value = null
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

// 缓存最近一次请求参数，用于重试
const lastRequest = ref(null)

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

// ===== 挂载时恢复状态 =====
onMounted(async () => {
  const savedId = localStorage.getItem('paperai_paperId')
  const savedSteps = localStorage.getItem('paperai_steps')
  const savedDone = localStorage.getItem('paperai_done')

  if (!savedId) return

  const pid = Number(savedId)
  if (!pid) return

  currentPaperId.value = pid
  currentResult.value = { steps: savedSteps ? JSON.parse(savedSteps) : [] }

  if (savedDone === 'true') {
    // 之前已完成
    phase.value = 'done'
    return
  }

  // savedDone !== 'true' — 可能还在进行中，也可能已经完成但没写标记
  // 去后端查一下这个 paper 的实际状态
  try {
    const { default: axios } = await import('axios')
    const res = await axios.get(`/api/paper/${pid}`)
    const paper = res?.data?.data
    if (paper) {
      if (paper.status === 'COMPLETED') {
        // 后端显示已完成，但前端没标记 — 更新为 done
        localStorage.setItem('paperai_done', 'true')
        phase.value = 'done'
        return
      }
      if (paper.status === 'FAILED') {
        // 后端显示失败
        phase.value = 'error'
        errorMsg.value = '论文写作失败'
        return
      }
    }
  } catch (_) {
    // 后端不可达，忽略
  }

  // 后端确认还在进行中，恢复 writing 状态
  // 设置超时：如果 30 秒内没收到任何步骤，自动变为 idle
  phase.value = 'writing'

  if (!currentResult.value?.steps?.length) {
    // 没有任何步骤记录 — 可能是个无效会话，10 秒后自动重置
    setTimeout(() => {
      if (phase.value === 'writing' && !(currentResult.value?.steps?.length)) {
        message.warning('未检测到写作活动，已自动重置')
        resetToIdle()
      }
    }, 10000)
  }
})

async function handleWrite() {
  if (!form.topic) {
    message.warning('请输入论文主题')
    return
  }

  // 保存请求参数
  const reqData = {
    topic: form.topic,
    description: form.description,
    keywords: form.keywords,
    sections: getSections(),
    requirements: form.requirements,
    maxReviewRounds: form.maxReviewRounds
  }
  lastRequest.value = reqData

  // 1. 创建阶段
  phase.value = 'creating'
  currentResult.value = { steps: [] }

  try {
    const createRes = await createPaper(reqData)

    if (!createRes?.data?.paperId) {
      throw new Error('未获取到论文ID')
    }

    const paperId = createRes.data.paperId

    // 2. 保存 paperId，触发 WebSocket 连接
    currentPaperId.value = paperId
    localStorage.setItem('paperai_paperId', paperId)
    localStorage.setItem('paperai_done', 'false')
    localStorage.removeItem('paperai_steps')

    // 3. 进入写作阶段
    phase.value = 'writing'

    // 4. 异步启动写作
    await startWriting(paperId, reqData)
  } catch (e) {
    if (phase.value === 'writing') return // WebSocket 错误已处理
    phase.value = 'error'
    errorMsg.value = '写作请求失败: ' + (e.message || '未知错误')
    message.error(errorMsg.value)
  }
}

async function handleStop() {
  if (!currentPaperId.value) return
  try {
    await stopWriting(currentPaperId.value)
    phase.value = 'done'
    message.info('写作已停止')
  } catch (e) {
    message.error('停止失败: ' + e.message)
  }
}

function handleRetry() {
  // 清除错误状态，重新进入 idle
  phase.value = 'idle'
  errorMsg.value = ''
  // 重新点击
  if (form.topic) handleWrite()
}

function resetToIdle() {
  phase.value = 'idle'
  errorMsg.value = ''
  currentPaperId.value = null
  currentResult.value = null
  localStorage.removeItem('paperai_paperId')
  localStorage.removeItem('paperai_steps')
  localStorage.removeItem('paperai_done')
}

function viewDetail() {
  if (currentPaperId.value) {
    router.push(`/paper/${currentPaperId.value}`)
  }
}
</script>

<style scoped>
.write-paper {
  max-width: 100%;
  margin: 0 auto;
  height: calc(100vh - 140px);
  overflow: hidden;
}
.write-paper :deep(.ant-row) {
  height: 100%;
}
.write-paper :deep(.ant-col) {
  height: 100%;
  overflow-y: auto;
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
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
  line-height: 1.4;
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
