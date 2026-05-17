<template>
  <div class="write-paper">
    <a-row :gutter="16">
      <!-- 左侧：表单 -->
      <a-col :span="9">
        <a-card :bordered="false">
          <a-form :model="form" layout="vertical">
            <!-- 论文任务选择 -->
            <a-form-item label="论文任务" v-if="!isCoreLocked">
              <a-select
                v-model:value="selectedPaperId"
                placeholder="选择已有任务或手动填写"
                allow-clear
                :disabled="isCoreLocked"
                @change="onPaperSelect"
              >
                <a-select-option v-for="p in pendingPapers" :key="p.id" :value="p.id">
                  {{ p.title }}
                </a-select-option>
              </a-select>
              <div style="font-size:10px;color:rgba(255,255,255,0.25);margin-top:2px">
                选择一个待执行的论文任务，或留空手动填写
              </div>
            </a-form-item>

            <!-- 流程选择 -->
            <a-form-item label="写作流程">
              <a-select
                v-model:value="selectedFlowId"
                :disabled="isCoreLocked"
                @change="onFlowChange"
              >
                <a-select-option
                  v-for="flow in availableFlows"
                  :key="flow.id"
                  :value="flow.id"
                >
                  <div style="display:flex;align-items:center;gap:8px">
                    <span>{{ flow.name }}</span>
                    <span style="color:#999;font-size:11px">{{ flow.description }}</span>
                  </div>
                </a-select-option>
              </a-select>
            </a-form-item>

            <a-form-item label="论文主题" required>
              <a-input
                v-model:value="form.topic"
                placeholder="如：深度学习在医疗影像分割中的应用"
                :disabled="isCoreLocked"
              />
              <div v-if="isCoreLocked" style="color: #faad14; font-size: 11px; margin-top: 2px">
                ⚠ 修改核心参数将重新开始写作，已生成内容将丢失
              </div>
            </a-form-item>

            <a-form-item label="详细描述">
              <a-textarea
                v-model:value="form.description"
                :rows="5"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                placeholder="建议填写：研究背景与意义、核心研究问题、采用的研究方法、预期的创新点和贡献"
                :disabled="phase !== 'idle' && phase !== 'writing'"
              />
            </a-form-item>

            <a-form-item label="关键词">
              <a-input
                v-model:value="form.keywords"
                placeholder="用逗号分隔，如：深度学习,医疗影像,图像分割"
                :disabled="phase !== 'idle' && phase !== 'writing'"
              />
            </a-form-item>

            <!-- 章节设置 — 可编辑列表 -->
            <a-form-item label="章节设置">
              <a-select
                v-model:value="selectedTemplate"
                @change="applyTemplate"
                placeholder="选择论文模板..."
                size="small"
                style="margin-bottom: 8px"
                :disabled="isCoreLocked"
              >
                <a-select-option value="">自定义章节</a-select-option>
                <a-select-option value="bachelor">本科毕业论文</a-select-option>
                <a-select-option value="master">硕士论文</a-select-option>
                <a-select-option value="journal">期刊论文</a-select-option>
                <a-select-option value="conference">会议论文</a-select-option>
              </a-select>

              <div class="section-list">
                <div
                  v-for="(section, index) in form.sections"
                  :key="index"
                  class="section-item"
                >
                  <span class="section-drag-handle">☰</span>
                  <a-input
                    v-model:value="form.sections[index]"
                    size="small"
                    :disabled="isCoreLocked"
                    style="flex: 1"
                  />
                  <a-button
                    v-if="!isCoreLocked"
                    type="text"
                    danger
                    size="small"
                    @click="removeSection(index)"
                    :disabled="form.sections.length <= 1"
                  >×</a-button>
                </div>
                <a-button
                  v-if="!isCoreLocked"
                  type="dashed"
                  size="small"
                  block
                  @click="addSection"
                  style="margin-top: 4px"
                >+ 添加章节</a-button>
              </div>
            </a-form-item>

            <!-- 附加要求 -->
            <a-form-item label="附加要求">
              <a-textarea
                v-model:value="form.requirements"
                :rows="3"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                placeholder="如：需要引用近 3 年的 SCI 论文；字数控制在 8000 字左右；重点分析 XX 技术的优缺点"
                :disabled="phase !== 'idle' && phase !== 'writing'"
              />
            </a-form-item>

            <!-- 审稿迭代轮次 -->
            <a-form-item label="审稿迭代轮次">
              <a-row :gutter="8" align="middle">
                <a-col :span="2">
                  <span class="slider-label">1 轮</span>
                </a-col>
                <a-col :span="16">
                  <a-slider
                    v-model:value="form.maxReviewRounds"
                    :min="1"
                    :max="5"
                    :disabled="phase !== 'idle' && phase !== 'writing'"
                  />
                </a-col>
                <a-col :span="2">
                  <span class="slider-label">5 轮</span>
                </a-col>
                <a-col :span="4" style="text-align: center">
                  <a-tag color="blue" style="font-size: 13px; font-weight: 600">
                    {{ form.maxReviewRounds }} 轮
                  </a-tag>
                </a-col>
              </a-row>
              <div style="color: #999; font-size: 11px; margin-top: 2px">
                AI 将自动对生成的论文进行多轮审稿和修改，轮次越多质量越高但耗时越长
              </div>
            </a-form-item>

            <a-form-item label="关联知识图谱">
              <a-select v-model:value="form.kgId" placeholder="（可选）选择知识图谱辅助写作" allow-clear :disabled="phase !== 'idle'">
                <a-select-option v-for="kg in kgList" :key="kg.id" :value="kg.id">{{ kg.name }}</a-select-option>
              </a-select>
              <div style="font-size:10px;color:rgba(255,255,255,0.2);margin-top:2px">绑定后 AI 写作时将自动引用图谱中的概念和关系</div>
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
                开始写作
              </a-button>
            </a-form-item>
            <a-form-item v-if="phase === 'writing'">
              <a-button danger size="large" block @click="showStopConfirm = true">
                <template #icon><stop-outlined /></template>
                停止写作
              </a-button>
            </a-form-item>
            <a-form-item v-if="phase === 'writing' || phase === 'done' || phase === 'error'">
              <a-button size="large" block @click="resetToIdle">
                <template #icon><sync-outlined /></template>
                重置页面
              </a-button>
            </a-form-item>
            <!-- 停止写作确认弹窗 -->
            <a-modal
              v-model:open="showStopConfirm"
              title="确认停止写作"
              ok-text="确认停止"
              cancel-text="取消"
              ok-type="danger"
              @ok="handleStop"
            >
              <p>停止后已生成的内容将保存为当前版本，您可以稍后继续编辑。</p>
            </a-modal>

            <!-- 写作中参数修改提示 -->
            <div v-if="phase === 'writing' && paramModified" class="param-hint">
              💡 参数已修改，将在当前步骤完成后生效
            </div>
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
                <span>{{ phase === 'writing' ? '论文写作中' : '写作完成' }}</span>
                <a-tag v-if="!sseConnected && phase === 'writing'" color="orange" size="small">
                  正在连接服务器
                </a-tag>
              </span>
            </template>

            <!-- ====== 分步进度指示器 ====== -->
            <div class="progress-section">
              <div class="progress-status-text" v-if="phase === 'writing'">
                {{ progressStatusText }}
              </div>
              <div class="step-timeline">
                <div
                  v-for="(step, index) in progressSteps"
                  :key="step.key"
                  class="step-node"
                  :class="{
                    'step-active': step.status === 'active',
                    'step-done': step.status === 'done',
                    'step-pending': step.status === 'pending',
                    'step-error': step.status === 'error'
                  }"
                >
                  <div class="step-indicator">
                    <loading-outlined v-if="step.status === 'active'" spin class="step-spin" />
                    <check-circle-filled v-else-if="step.status === 'done'" style="color: #52c41a" />
                    <close-circle-filled v-else-if="step.status === 'error'" style="color: #ff4d4f" />
                    <span v-else class="step-dot" />
                  </div>
                  <div class="step-info">
                    <div class="step-label">{{ step.label }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 流式文本实时展示 -->
            <div v-if="streamingText" style="padding: 0 0 16px 0">
              <a-card size="small" :title="`🤖 ${streamingStepName} - 实时生成中...`" :bordered="true">
                <div class="ai-output-body" style="max-height: 45vh; overflow-y: auto">
                  <MarkdownRender :content="streamingText" />
                </div>
              </a-card>
            </div>

            <!-- 等待第一个步骤或处于连接中 -->
            <div v-if="!(currentResult?.steps?.length) && !streamingText && sseConnected" class="waiting-hint">
              <loading-outlined spin style="font-size: 20px; margin-bottom: 8px; display: block" />
              <span>正在等待写作引擎返回第一步...</span>
            </div>
            <div v-if="!(currentResult?.steps?.length) && !streamingText && !sseConnected" class="waiting-hint">
              <loading-outlined spin style="font-size: 20px; margin-bottom: 8px; display: block" />
              <span>正在连接服务器...</span>
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
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createPaper, startWriting, stopWriting, getFlowList, getPaperList, getPaperDetail, listKg } from '@/api'
import { usePaperStepSSE } from '@/composables/usePaperStepSSE'
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

// ===== 论文模板 =====
const SECTION_TEMPLATES = {
  bachelor: ['摘要', '引言', '文献综述', '研究方法', '实验与分析', '结论与展望', '参考文献', '致谢'],
  master: ['摘要', '引言', '国内外研究现状', '理论基础', '方法设计', '实验验证', '结果讨论', '结论', '参考文献', '致谢'],
  journal: ['摘要', '引言', '相关工作', '方法', '实验', '结论', '参考文献'],
  conference: ['摘要', '引言', '方法', '实验', '结论', '参考文献']
}

const router = useRouter()
const route = useRoute()

// ===== 论文任务选择 =====
const pendingPapers = ref([])
const selectedPaperId = ref(null)

async function loadPendingPapers() {
  try {
    const res = await getPaperList()
    pendingPapers.value = (res.data || []).filter(p => p.status === 'DRAFT' || p.status === 'FAILED')
  } catch (_) {}
}

async function onPaperSelect(paperId) {
  if (!paperId) { resetTaskForm(); return }
  try {
    const res = await getPaperDetail(paperId)
    const paper = res?.data
    if (paper) {
      form.topic = paper.title || ''
      form.description = paper.description || ''
      form.keywords = paper.keywords || ''
      if (paper.flowId) selectedFlowId.value = paper.flowId
    }
  } catch (_) {}
}

function resetTaskForm() {
  form.topic = ''; form.description = ''; form.keywords = ''; selectedFlowId.value = 'standard'
}

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

// SSE 连接状态（由 hook 暴露）
const { connected: sseConnected } = usePaperStepSSE(currentPaperId, {
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
    if (phase.value !== 'writing') return
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
    currentPaperId.value = null
  }
})

// ===== 表单 =====
const form = reactive({
  topic: '',
  description: '',
  keywords: '',
  sections: [...SECTION_TEMPLATES.journal],
  requirements: '',
  maxReviewRounds: 3,
  kgId: null
})

const kgList = ref([])
async function loadKgList(){ try{ const r = await listKg(); kgList.value = (r.data||[]).map(k=>({id:k.id,name:k.name})) }catch(_){} }

const selectedTemplate = ref('')
const showStopConfirm = ref(false)
const paramModified = ref(false)

// ===== 流程选择 =====
const availableFlows = ref([])
const selectedFlowId = ref('standard')

function onFlowChange(flowId) {
  if (flowId === 'deep_research') form.maxReviewRounds = 5
}

const isCoreLocked = computed(() => phase.value === 'writing' || phase.value === 'creating')

// ===== 模板 =====
function applyTemplate(key) {
  if (!key || !SECTION_TEMPLATES[key]) return
  form.sections = [...SECTION_TEMPLATES[key]]
}
function addSection() { form.sections.push('新章节'); paramModified.value = true }
function removeSection(index) {
  if (form.sections.length <= 1) return
  form.sections.splice(index, 1)
  paramModified.value = true
}

// ===== 监听非核心参数修改 =====
watch(
  () => [form.keywords, form.requirements, form.maxReviewRounds],
  () => { if (phase.value === 'writing') paramModified.value = true },
  { deep: true }
)

// 缓存最近一次请求参数，用于重试
const lastRequest = ref(null)

function getSections() {
  return form.sections.filter(s => s.trim())
}

// ===== 动态进度：直接显示后端返回的实际步骤 =====
const progressSteps = computed(() => {
  const backendSteps = currentResult.value?.steps || []
  // 连接中
  if (backendSteps.length === 0 && phase.value === 'writing') {
    return [{ key: 'connect', label: '正在连接写作引擎...', status: 'active', agentRole: null }]
  }
  // 已完成
  if (phase.value === 'done') {
    return backendSteps.map(s => ({
      key: s.agentName + s.agentRole,
      label: roleLabel(s.agentRole) + ' · ' + s.agentName,
      status: 'done',
      agentRole: s.agentRole
    }))
  }
  // 进行中
  return backendSteps.map(s => ({
    key: s.agentName + s.agentRole,
    label: roleLabel(s.agentRole) + ' · ' + s.agentName,
    status: s.status === 'COMPLETED' ? 'done' : s.status === 'FAILED' ? 'error' : 'active',
    agentRole: s.agentRole
  }))
})

const progressStatusText = computed(() => {
  const backendSteps = currentResult.value?.steps || []
  const done = backendSteps.filter(s => s.status === 'COMPLETED').length
  const total = backendSteps.length
  if (total === 0) return '正在连接写作引擎，请稍候...'
  if (phase.value === 'done') return `全部完成 · ${total} 步`
  return `已完成 ${done} / ${total} 步`
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
  try {
    const res = await getFlowList()
    availableFlows.value = res.data || []
  } catch (_) {}

  // 加载待执行论文列表
  await loadPendingPapers()
  await loadKgList()

  // 支持从论文列表跳转过来（?paperId=xxx）
  const queryPaperId = route.query.paperId
  if (queryPaperId) {
    selectedPaperId.value = Number(queryPaperId)
    await onPaperSelect(selectedPaperId.value)
  }

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
    maxReviewRounds: form.maxReviewRounds,
    flowId: selectedFlowId.value,
    kgId: form.kgId
  }
  lastRequest.value = reqData

  phase.value = 'creating'
  currentResult.value = { steps: [] }

  try {
    let paperId
    if (selectedPaperId.value) {
      paperId = selectedPaperId.value
    } else {
      const createRes = await createPaper(reqData)
      paperId = createRes?.data?.paperId
      if (!paperId) throw new Error('未获取到论文ID')
    }

    // 2. 保存 paperId，触发 SSE 连接
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
  showStopConfirm.value = false
  if (!currentPaperId.value) return
  try {
    await stopWriting(currentPaperId.value)
    message.info('写作已停止，内容已保存')
  } catch (e) {
    message.error('停止请求失败: ' + e.message)
  }
  // 清理 SSE 连接和本地状态
  currentPaperId.value = null
  phase.value = 'idle'
  localStorage.removeItem('paperai_paperId')
  localStorage.removeItem('paperai_steps')
  localStorage.removeItem('paperai_done')
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

/* ===== 分步进度指示器 ===== */
.progress-section {
  padding: 8px 0 4px 0;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  margin-bottom: 12px;
}
.progress-status-text {
  font-size: 13px;
  color: #1890ff;
  margin-bottom: 8px;
  font-weight: 500;
}
.step-timeline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.step-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 12px;
  font-size: 11px;
  background: rgba(255,255,255,0.04);
  transition: all 0.3s;
}
.step-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d9d9d9;
}
.step-indicator {
  font-size: 12px;
}
.step-spin { font-size: 12px; color: #1890ff; }
.step-active { background: #e6f7ff; border-color: #91d5ff; }
.step-active .step-label { color: #1890ff; font-weight: 600; }
.step-done { background: #f6ffed; }
.step-done .step-label { color: #52c41a; }
.step-error { background: #fff2f0; }
.step-error .step-label { color: #ff4d4f; }
.step-sub { font-size: 10px; color: #999; margin-top: 1px; }

/* ===== 等待提示 ===== */
.waiting-hint {
  padding: 24px 0;
  text-align: center;
  color: rgba(255,255,255,0.4);
}

/* ===== 参数修改提示 ===== */
.param-hint {
  padding: 8px 12px;
  margin-top: 8px;
  background: rgba(255,197,61,0.08);
  border: 1px solid rgba(255,197,61,0.2);
  border-radius: 4px;
  font-size: 12px;
  color: #ffc53d;
}

/* ===== 章节列表 ===== */
.section-list {
  margin-top: 4px;
}
.section-item {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
.section-drag-handle {
  cursor: grab;
  color: rgba(255,255,255,0.4);
  font-size: 14px;
  user-select: none;
}

/* ===== 滑块标签 ===== */
.slider-label {
  font-size: 10px;
  color: rgba(255,255,255,0.4);
  user-select: none;
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
  background: rgba(255,255,255,0.1);
  font-size: 12px;
  font-weight: bold;
  color: rgba(255,255,255,0.5);
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
  color: rgba(255,255,255,0.4);
  font-size: 12px;
  margin-left: auto;
  white-space: nowrap;
}

/* ===== AI 输出区域 ===== */
.ai-output {
  padding: 6px 0;
}
.ai-output-header {
  margin-bottom: 10px;
  font-size: 12px;
  color: rgba(255,255,255,0.35);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.ai-output-body {
  color: #e0e0e8;
}
.ai-output-body :deep(h1) { font-size: 22px; color: #fff; margin: 20px 0 10px; border-bottom: 1px solid rgba(255,255,255,0.06); padding-bottom: 6px; }
.ai-output-body :deep(h2) { font-size: 18px; color: #f0f0f5; margin: 16px 0 8px; }
.ai-output-body :deep(h3) { font-size: 15px; color: #e0e0e8; margin: 14px 0 6px; }
.ai-output-body :deep(p) { margin: 8px 0; line-height: 1.75; color: #d8d8e0; }
.ai-output-body :deep(code) { background: rgba(146,84,222,0.1); color: #d4b8ff; padding: 2px 6px; border-radius: 3px; font-size: 0.88em; }
.ai-output-body :deep(pre) { background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.05); border-radius: 8px; padding: 14px; margin: 12px 0; }
.ai-output-body :deep(pre code) { background: none; padding: 0; color: #c8d0e0; font-size: 12px; line-height: 1.6; }
.ai-output-body :deep(blockquote) { border-left: 3px solid #9254de; padding: 8px 14px; margin: 12px 0; background: rgba(146,84,222,0.04); color: #b8b8c8; border-radius: 0 4px 4px 0; }
.ai-output-body :deep(table) { border-collapse: collapse; margin: 10px 0; font-size: 12px; }
.ai-output-body :deep(th), .ai-output-body :deep(td) { border: 1px solid rgba(255,255,255,0.06); padding: 6px 12px; }
.ai-output-body :deep(th) { background: rgba(255,255,255,0.03); color: #c0c0d0; }
.ai-output-text {
  font-family: 'Consolas','Monaco','Courier New',monospace;
  font-size: 13px; line-height: 1.6; color: #d8d8e0;
  word-break: break-word !important; white-space: pre-wrap !important; overflow-wrap: break-word !important;
}
.ai-output-scroll {
  max-height: 35vh; overflow-y: auto; padding-right: 6px;
}
.ai-output-scroll::-webkit-scrollbar { width: 4px; }
.ai-output-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 2px; }

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
