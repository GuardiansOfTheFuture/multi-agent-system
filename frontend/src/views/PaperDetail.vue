<template>
  <div class="paper-detail" v-if="paper">
    <!-- ===== 顶部栏：重构 ===== -->
    <header class="detail-header">
      <div class="header-left">
        <a-button type="text" class="back-btn" @click="$router.push('/papers')">
          <template #icon><arrow-left-outlined /></template>
        </a-button>
        <div class="header-info">
          <h1 class="header-title">{{ paper.title }}</h1>
          <div class="header-meta">
            <a-tag v-if="isEditing" color="red" size="small">编辑中</a-tag>
            <span class="header-version">v{{ activeVersion || paper.currentVersion || 1 }}</span>
            <span class="header-saved" v-if="lastSavedTime">
              <clock-circle-outlined style="font-size:12px" />
              {{ lastSavedTime }}
            </span>
          </div>
        </div>
      </div>

      <div class="header-actions">
        <template v-if="!isEditing">
          <a-button @click="enterEditMode">
            <template #icon><edit-outlined /></template>
            编辑论文
          </a-button>
          <a-button @click="showVersionDrawer = true" :disabled="!versions.length">
            <template #icon><clock-circle-outlined /></template>
            版本历史
          </a-button>
          <a-dropdown>
            <a-button>
              <template #icon><download-outlined /></template>
              导出
            </a-button>
            <template #overlay>
              <a-menu @click="handleExport">
                <a-menu-item key="docx">📄 Word (.docx)</a-menu-item>
                <a-menu-item key="pdf">📕 PDF (.pdf)</a-menu-item>
                <a-menu-item key="html">🌐 HTML (.html)</a-menu-item>
                <a-menu-item key="latex">📜 LaTeX (.tex)</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>

        <template v-if="isEditing">
          <a-button @click="handleManualSave">
            <template #icon><save-outlined /></template>
            保存
          </a-button>
          <a-button @mousedown.prevent="handleAgentEditClick" :disabled="!selectedText">
            <template #icon><robot-outlined /></template>
            🤖 Agent 修改
          </a-button>
          <a-button type="primary" @click="handleSave">
            <template #icon><save-outlined /></template>
            保存版本
          </a-button>
          <a-button @click="cancelEdit">取消</a-button>
        </template>
      </div>
    </header>

    <!-- ===== 主体三栏布局 ===== -->
    <div class="detail-body">
      <!-- 左侧：可折叠目录 -->
      <aside class="left-col" :class="{ 'left-col-collapsed': tocCollapsed }">
        <div class="toc-panel">
          <div class="toc-panel-header">
            <span class="toc-panel-title">📑 目录</span>
            <a-button
              type="text"
              size="small"
              @click="tocCollapsed = !tocCollapsed"
              :title="tocCollapsed ? '展开目录' : '折叠目录'"
            >
              <template #icon>
                <menu-fold-outlined v-if="!tocCollapsed" />
                <menu-unfold-outlined v-else />
              </template>
            </a-button>
          </div>
          <div class="toc-panel-body" v-show="!tocCollapsed">
            <TableOfContents
              :content="tocContent"
              :scroll-container="scrollContainer"
              @heading-click="onHeadingClick"
            />
          </div>
        </div>
      </aside>

      <!-- 中间：论文内容（统一滚动区） -->
      <main class="center-col" ref="mainScrollRef" id="paper-content-scroll">
        <!-- 查看模式 -->
        <template v-if="!isEditing">
          <div v-if="displayedContent" class="markdown-body paper-content-view">
            <div class="rendered-content">
              <MarkdownRender :content="displayedContent" />
            </div>
          </div>
          <a-empty v-else description="暂无内容" style="margin-top:60px" />
        </template>

        <!-- 编辑模式 -->
        <template v-else>
          <div class="edit-toolbar">
            <a-space>
              <span class="word-count">{{ editableContent.length }} 字</span>
              <a-tag v-if="selectedText" color="blue" class="selection-tag" @click="clearSelection">
                已选中 {{ selectedText.length }} 字 ✕
              </a-tag>
            </a-space>
          </div>
          <textarea
            ref="editorRef"
            v-model="editableContent"
            class="edit-textarea"
            @select="handleTextSelect"
            @mouseup="handleTextSelect"
            @keyup.shift="handleTextSelect"
            @keyup.ctrl="handleTextSelect"
            placeholder="在此编辑论文内容..."
          />
        </template>
      </main>

      <!-- 右侧：标签页面板 -->
      <aside class="right-col">
        <a-card :bordered="false" class="right-panel">
          <a-tabs v-model:activeKey="rightTab" size="small">
            <a-tab-pane key="tasks" tab="⚡ 执行记录">
              <div class="tab-content">
                <div class="task-version-badge" v-if="filteredTasks.length">
                  v{{ activeVersion || 0 }} · {{ filteredTasks.length }} 条记录
                </div>
                <template v-if="filteredTasks.length">
                  <div
                    v-for="task in filteredTasks"
                    :key="task.id"
                    class="task-card"
                    :class="{ 'task-card-expanded': expandedTaskId === task.id }"
                  >
                    <div class="task-card-header" @click="toggleTaskExpand(task)">
                      <span class="task-dot" :class="'dot-' + (task.status || '').toLowerCase()"></span>
                      <span class="task-role">{{ roleLabel(task.agentRole) }}</span>
                      <span class="task-desc">{{ task.description }}</span>
                      <span class="task-card-time" v-if="task.durationMs">{{ (task.durationMs / 1000).toFixed(1) }}s</span>
                      <span class="task-card-status" :class="'status-' + (task.status || '').toLowerCase()">{{ statusText(task.status) }}</span>
                    </div>
                    <div class="task-card-body" v-if="expandedTaskId === task.id && task.outputData">
                      <MarkdownRender :content="task.outputData" />
                    </div>
                  </div>
                </template>
                <a-empty v-else description="暂无执行记录" :image-style="{ height: '40px' }" />
              </div>
            </a-tab-pane>

            <a-tab-pane key="versions" tab="💾 版本管理">
              <div class="tab-content">
                <template v-if="versions.length">
                  <div
                    v-for="(ver, i) in versions"
                    :key="ver.id"
                    class="version-item"
                    :class="{ 'version-active': ver.versionNo === activeVersion }"
                    @click="switchVersion(ver)"
                  >
                    <div class="version-meta">
                      <a-tag :color="stageColor(ver.stage)" size="small">{{ ver.stage }}</a-tag>
                      <span class="version-no">v{{ ver.versionNo }}</span>
                    </div>
                    <div class="version-summary">{{ ver.summary || '无摘要' }}</div>
                    <div class="version-time">{{ formatTime(ver.createdAt) }}</div>
                  </div>
                </template>
                <a-empty v-else description="暂无版本记录" :image-style="{ height: '40px' }" />
              </div>
            </a-tab-pane>

            <a-tab-pane key="reviews" tab="🔎 审稿意见">
              <div class="tab-content">
                <template v-if="reviewComments.length">
                  <div v-for="(comment, i) in reviewComments" :key="i" class="review-item">
                    <div class="review-index">#{{ i + 1 }}</div>
                    <div class="review-text">{{ comment }}</div>
                  </div>
                </template>
                <a-empty v-else description="暂无审稿意见" :image-style="{ height: '40px' }" />
              </div>
            </a-tab-pane>

          </a-tabs>
        </a-card>
      </aside>
    </div>

    <!-- ===== 弹窗 ===== -->

    <!-- Agent 修改对话框 -->
    <a-modal
      v-model:open="showAgentEditModal"
      title="🤖 Agent 修改文本"
      :mask-closable="false"
    >
      <a-form layout="vertical">
        <a-form-item label="选中的文本">
          <a-textarea :value="selectedText" disabled :rows="3" />
        </a-form-item>
        <a-form-item label="修改指令" required>
          <a-textarea
            v-model:value="agentInstruction"
            placeholder="例如：润色这段话、用更学术的语言、补充数据支撑、精简为100字以内..."
            :rows="3"
          />
        </a-form-item>
        <a-form-item v-if="agentResult" label="修改结果">
          <div class="agent-result">
            <div class="agent-result-diff">
              <div class="diff-old">
                <div class="diff-label">修改前</div>
                <div class="diff-content">{{ selectedText }}</div>
              </div>
              <div class="diff-arrow">→</div>
              <div class="diff-new">
                <div class="diff-label">修改后</div>
                <div class="diff-content">{{ agentResult }}</div>
              </div>
            </div>
          </div>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="closeAgentEdit">取消</a-button>
        <a-button
          v-if="!agentResult"
          type="primary"
          :loading="agentEditing"
          @click="handleAgentEdit"
        >
          🤖 开始修改
        </a-button>
        <template v-if="agentResult">
          <a-button @click="agentResult = ''; agentInstruction = ''">重新修改</a-button>
          <a-button type="primary" @click="applyAgentResult">
            ✅ 应用修改
          </a-button>
        </template>
      </template>
    </a-modal>

    <!-- 保存版本对话框 -->
    <a-modal
      v-model:open="showSaveVersionModal"
      title="💾 保存新版本"
      :confirm-loading="saving"
      ok-text="保存版本"
      @ok="handleSaveVersion"
    >
      <a-form layout="vertical">
        <a-form-item label="版本日志" required>
          <a-textarea
            v-model:value="versionSummary"
            placeholder="描述本次修改内容，例如：修改了第三章的实验结果分析，补充了数据表格"
            :rows="4"
          />
        </a-form-item>
        <a-form-item label="字数变化">
          <a-statistic
            :value="editableContent?.length || 0"
            suffix="字"
            :value-style="{ fontSize: '16px' }"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 版本抽屉 -->
    <a-drawer
      v-model:open="showVersionDrawer"
      title="📜 版本历史"
      placement="right"
      width="400"
    >
      <a-timeline v-if="versions.length">
        <a-timeline-item
          v-for="(ver, i) in versions"
          :key="ver.id"
          :color="stageColor(ver.stage)"
        >
          <div class="drawer-version-item" :class="{ 'drawer-version-active': ver.versionNo === activeVersion }">
            <div class="drawer-version-header">
              <a-tag :color="stageColor(ver.stage)">{{ ver.stage }}</a-tag>
              <strong>v{{ ver.versionNo }}</strong>
              <a-button
                type="link"
                size="small"
                @click="switchVersion(ver)"
              >
                {{ ver.versionNo === activeVersion ? '当前' : '查看' }}
              </a-button>
            </div>
            <div class="drawer-version-summary">{{ ver.summary || '无摘要' }}</div>
            <div class="drawer-version-time">{{ formatTime(ver.createdAt) }}</div>
            <div class="drawer-version-words" v-if="ver.wordCount">
              字数：{{ ver.wordCount.toLocaleString() }}
            </div>
          </div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无版本记录" />
    </a-drawer>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { usePaperStore } from '@/stores/paper'
import { message } from 'ant-design-vue'
import MarkdownRender from '@/components/MarkdownRender.vue'
import TableOfContents from '@/components/TableOfContents.vue'
import {
  ClockCircleOutlined,
  EditOutlined,
  SaveOutlined,
  RobotOutlined,
  ArrowLeftOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CloseOutlined,
  DownloadOutlined
} from '@ant-design/icons-vue'
import {
  getPaperTasks,
  getPaperVersions,
  getPaperVersion,
  getLatestVersion,
  updatePaperContent,
  agentEditPaper,
  savePaperVersion,
  exportPaper
} from '@/api'

const route = useRoute()
const paperStore = usePaperStore()

// ===== 页面状态 =====
const mainScrollRef = ref(null)
const editorRef = ref(null)
const showVersionDrawer = ref(false)
const activeVersion = ref(0)
const rightTab = ref('tasks')
const tocCollapsed = ref(false)
const displayedContent = ref('')

// ===== 编辑模式 =====
const isEditing = ref(false)
const editableContent = ref('')
const selectedText = ref('')
const selectionStart = ref(0)
const selectionEnd = ref(0)

// ===== Agent 修改 =====
const showAgentEditModal = ref(false)
const agentInstruction = ref('')
const agentEditing = ref(false)
const agentResult = ref('')

// ===== 保存版本 =====
const showSaveVersionModal = ref(false)
const versionSummary = ref('')
const saving = ref(false)

// ===== 任务详情弹窗 =====
const expandedTaskId = ref(null)

const lastSavedTime = ref('')

/** 正文滚动容器选择器，传给 TOC 组件 */
const scrollContainer = computed(() => '#paper-content-scroll')

/** 目录内容：查看模式用 displayedContent，编辑模式用 editableContent */
const tocContent = computed(() => {
  return isEditing.value ? editableContent.value : displayedContent.value
})

// ===== 论文数据 =====
const paper = computed(() => paperStore.currentPaper)
const tasks = computed(() => paperStore.currentTasks || [])
const filteredTasks = computed(() => {
  return tasks.value.filter(t => (t.versionNo || 0) === activeVersion.value)
})
const versions = ref([])

// ===== 审稿意见 =====
const reviewComments = computed(() => {
  if (!tasks.value.length) return []
  const reviewTasks = tasks.value.filter(t =>
    t.agentRole === 'REVIEWER' && t.outputData && (t.versionNo || 0) === activeVersion.value
  )
  return reviewTasks.map(t => {
    const text = typeof t.outputData === 'string' ? t.outputData : JSON.stringify(t.outputData)
    return text
  })
})

function onHeadingClick(id) {
}

// ===== 编辑模式 =====
function enterEditMode() {
  editableContent.value = displayedContent.value
  isEditing.value = true
  selectedText.value = ''
  agentResult.value = ''
  nextTick(() => {
    if (editorRef.value) {
      editorRef.value.focus()
    }
  })
}

function cancelEdit() {
  isEditing.value = false
  editableContent.value = ''
  selectedText.value = ''
}

// ===== 文本选择 =====
function handleTextSelect() {
  const el = editorRef.value
  if (!el) return
  const start = el.selectionStart
  const end = el.selectionEnd
  if (start !== end) {
    selectedText.value = editableContent.value.substring(start, end)
    selectionStart.value = start
    selectionEnd.value = end
  }
}

function clearSelection() {
  selectedText.value = ''
  selectionStart.value = 0
  selectionEnd.value = 0
}

// ===== Agent 修改 =====
function closeAgentEdit() {
  showAgentEditModal.value = false
  agentInstruction.value = ''
  agentResult.value = ''
}

function handleAgentEditClick() {
  const el = editorRef.value
  if (el) {
    const start = el.selectionStart
    const end = el.selectionEnd
    if (start !== end) {
      selectedText.value = editableContent.value.substring(start, end)
      selectionStart.value = start
      selectionEnd.value = end
    }
  }
  showAgentEditModal.value = true
}

async function handleAgentEdit() {
  if (!selectedText.value || !agentInstruction.value) {
    message.warning('请选中文本并输入修改指令')
    return
  }
  agentEditing.value = true
  agentResult.value = ''
  try {
    const res = await agentEditPaper(
      Number(route.params.id),
      selectedText.value,
      agentInstruction.value
    )
    agentResult.value = res.data?.modifiedText || ''
    message.success('AI 修改完成，请预览结果')
  } catch (e) {
    message.error('Agent 修改失败: ' + (e.message || '未知错误'))
  } finally {
    agentEditing.value = false
  }
}

function applyAgentResult() {
  if (!agentResult.value) return
  const pre = editableContent.value.substring(0, selectionStart.value)
  const post = editableContent.value.substring(selectionEnd.value)
  editableContent.value = pre + agentResult.value + post
  selectionEnd.value = selectionStart.value + agentResult.value.length
  showAgentEditModal.value = false
  agentInstruction.value = ''
  message.success('已应用修改')
}

// ===== 手动保存 =====
const manualSaving = ref(false)

async function handleManualSave() {
  if (!editableContent.value) {
    message.warning('内容不能为空')
    return
  }
  const versionToSave = activeVersion.value || paper.value?.currentVersion || 0
  manualSaving.value = true
  try {
    await updatePaperContent(Number(route.params.id), versionToSave, editableContent.value)
    displayedContent.value = editableContent.value

    // 同步更新本地版本列表，避免页面刷新
    const localVer = versions.value.find(v => v.versionNo === versionToSave)
    if (localVer) {
      localVer.content = editableContent.value
      localVer.wordCount = editableContent.value.length
    }

    const now = new Date()
    lastSavedTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    message.success('已保存到 v' + versionToSave)
  } catch (e) {
    message.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    manualSaving.value = false
  }
}

// ===== 保存版本 =====
async function handleSave() {
  if (!editableContent.value) {
    message.warning('内容不能为空')
    return
  }
  showSaveVersionModal.value = true
  versionSummary.value = ''
}

async function handleSaveVersion() {
  if (!versionSummary.value.trim()) {
    message.warning('请填写版本日志')
    return
  }
  saving.value = true
  try {
    const paperId = Number(route.params.id)
    const res = await savePaperVersion(paperId, editableContent.value, versionSummary.value.trim())
    const newVersionNo = res.data?.versionNo
    message.success(`版本 v${newVersionNo} 已保存`)

    // 更新本地状态，无需手动刷新页面
    displayedContent.value = editableContent.value
    activeVersion.value = newVersionNo || (paper.value?.currentVersion || 0) + 1
    if (newVersionNo) {
      versions.value.unshift({
        id: res.data?.versionId,
        paperId,
        versionNo: newVersionNo,
        stage: 'MANUAL_EDIT',
        summary: versionSummary.value.trim(),
        content: editableContent.value,
        wordCount: editableContent.value.length,
        editType: 'MANUAL',
        createdAt: new Date().toISOString()
      })
    }

    showSaveVersionModal.value = false
    isEditing.value = false
    editableContent.value = ''
  } catch (e) {
    message.error('保存版本失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// ===== 任务操作 =====
function toggleTaskExpand(task) {
  expandedTaskId.value = expandedTaskId.value === task.id ? null : task.id
}

function roleLabel(role) {
  const labels = { SUPERVISOR: '导师', RESEARCHER: '研究员', WRITER: '写作', REVIEWER: '审稿', POLISHER: '润色' }
  return labels[role] || role
}

// ===== 版本管理 =====
function stageColor(stage) {
  const colors = {
    DRAFT: 'default',
    REVIEWED: 'orange',
    POLISHED: 'cyan',
    FINAL: 'green',
    MANUAL_EDIT: 'blue'
  }
  return colors[stage] || 'default'
}

async function switchVersion(ver) {
  activeVersion.value = ver.versionNo
  showVersionDrawer.value = false

  // 优先使用预加载的版本内容
  if (ver.content) {
    displayedContent.value = ver.content
    return
  }

  // 预加载列表不含 content 时，请求单版本详情
  try {
    const paperId = Number(route.params.id)
    const res = await getPaperVersion(paperId, ver.versionNo)
    if (res.data?.content) {
      displayedContent.value = res.data.content
    }
  } catch (e) {
    message.error('加载版本内容失败: ' + (e.message || '未知错误'))
  }
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// ===== 初始化加载 =====
onMounted(async () => {
  const id = Number(route.params.id)
  if (id) {
    await paperStore.fetchPaperDetail(id)
    displayedContent.value = paperStore.currentPaper?.content || ''
    activeVersion.value = paperStore.currentPaper?.currentVersion || 0

    if (paperStore.currentPaper?.updatedAt) {
      const d = new Date(paperStore.currentPaper.updatedAt)
      lastSavedTime.value = d.toLocaleTimeString('zh-CN', {
        month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
      })
    }

    try {
      const res = await getPaperTasks(id)
      paperStore.currentTasks = res.data?.tasks || []
    } catch (e) {
      console.error('获取任务记录失败:', e)
    }

    try {
      const verRes = await getPaperVersions(id)
      versions.value = verRes.data || []
    } catch (e) {
      console.error('获取版本列表失败:', e)
    }

  }
})

onUnmounted(() => {})

// ===== 导出 =====
async function handleExport({ key }) {
  try {
    message.loading({ content: '正在导出...', key: 'export', duration: 0 })
    await exportPaper(Number(route.params.id), key, activeVersion.value || undefined, paper.value?.title)
    message.success({ content: '导出成功', key: 'export' })
  } catch (e) {
    message.error({ content: '导出失败: ' + (e.message || '未知错误'), key: 'export' })
  }
}

// ===== 状态工具 =====
function statusColor(status) {
  const map = { DRAFT: 'default', REVIEWING: 'processing', COMPLETED: 'success', FAILED: 'error' }
  return map[status] || 'default'
}

function statusText(status) {
  const map = { DRAFT: '草稿', REVIEWING: '审阅中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}

</script>

<style scoped>
/* ===== 整体布局 ===== */
.paper-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 顶部栏 ===== */
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: rgba(15,20,38,0.7);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
  gap: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}
.back-btn {
  font-size: 18px;
  flex-shrink: 0;
}
.header-info {
  min-width: 0;
}
.header-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: rgba(255,255,255,0.9);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}
.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}
.header-version {
  font-weight: 500;
  color: #1890ff;
}
.header-saved {
  display: flex;
  align-items: center;
  gap: 4px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* ===== 主体三栏 ===== */
.detail-body {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px 16px 12px;
  overflow: hidden;
  min-height: 0;
}

/* ===== 左侧目录 ===== */
.left-col {
  width: 210px;
  flex-shrink: 0;
  transition: width 0.3s ease;
}
.left-col-collapsed {
  width: 40px;
}
.toc-panel {
  background: rgba(15,20,38,0.7);
  border-radius: 8px;
  border: 1px solid rgba(255,255,255,0.06);
  height: 100%;
  display: flex;
  flex-direction: column;
}
.toc-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  flex-shrink: 0;
}
.toc-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.85);
  white-space: nowrap;
}
.toc-panel-body {
  flex: 1;
  overflow: hidden;
}

/* ===== 中间正文 ===== */
.center-col {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: linear-gradient(180deg, rgba(18,24,42,0.85) 0%, rgba(15,20,38,0.75) 100%);
  border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.05);
  padding: 40px 44px 56px;
}
.center-col::-webkit-scrollbar { width: 5px; }
.center-col::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 3px; }
.center-col::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.14); }

.paper-content-view {
  max-width: 780px;
  margin: 0 auto;
  word-wrap: break-word;
  overflow-wrap: break-word;
}
.paper-content-view :deep(table) { width: 100%; display: block; overflow-x: auto; }
.paper-content-view :deep(img) { max-width: 100%; border-radius: 6px; }

.rendered-content {
  line-height: 1.9;
  font-size: 15px;
  color: #e8e8ec;
}
.rendered-content :deep(h1) {
  font-size: 26px; font-weight: 700; color: #fff;
  margin: 36px 0 16px; padding-bottom: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  letter-spacing: 0.5px;
}
.rendered-content :deep(h2) {
  font-size: 21px; font-weight: 650; color: #f0f0f5;
  margin: 28px 0 12px; padding-bottom: 6px;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}
.rendered-content :deep(h3) {
  font-size: 17px; font-weight: 600; color: #e0e0e8;
  margin: 22px 0 10px;
}
.rendered-content :deep(p) { margin: 12px 0; }
.rendered-content :deep(strong) { color: #fff; font-weight: 650; }
.rendered-content :deep(a) { color: #b37feb; text-decoration: underline; text-underline-offset: 3px; }
.rendered-content :deep(a:hover) { color: #d4b8ff; }

/* 代码 */
.rendered-content :deep(code) {
  background: rgba(146,84,222,0.1);
  color: #d4b8ff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.88em;
  font-family: 'SF Mono','Consolas','Monaco',monospace;
}
.rendered-content :deep(pre) {
  background: rgba(0,0,0,0.35);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  padding: 18px 20px;
  overflow-x: auto;
  margin: 16px 0;
}
.rendered-content :deep(pre code) {
  background: none; color: #c8d0e0; padding: 0; font-size: 13px; line-height: 1.7;
}

/* 表格 */
.rendered-content :deep(table) { border-collapse: collapse; margin: 16px 0; }
.rendered-content :deep(th) {
  background: rgba(146,84,222,0.08); color: #c8c8d8; font-weight: 600;
  padding: 10px 16px; border: 1px solid rgba(255,255,255,0.08);
  font-size: 13px;
}
.rendered-content :deep(td) {
  padding: 8px 16px; border: 1px solid rgba(255,255,255,0.05);
  color: #d0d0d8; font-size: 13px;
}
.rendered-content :deep(tr:nth-child(even)) { background: rgba(255,255,255,0.015); }

/* 引用 */
.rendered-content :deep(blockquote) {
  border-left: 3px solid #9254de;
  padding: 10px 18px; margin: 16px 0;
  background: rgba(146,84,222,0.04);
  color: #b8b8c8;
  border-radius: 0 6px 6px 0;
}
.rendered-content :deep(hr) { border: none; border-top: 1px solid rgba(255,255,255,0.06); margin: 28px 0; }

/* 列表 */
.rendered-content :deep(ul), .rendered-content :deep(ol) { padding-left: 24px; margin: 10px 0; }
.rendered-content :deep(li) { margin: 6px 0; color: #d8d8e0; }

/* 选中高亮 */
.rendered-content :deep(::selection) { background: rgba(146,84,222,0.3); color: #fff; }

/* ===== 编辑模式 ===== */
.edit-toolbar {
  padding: 0 0 10px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  margin-bottom: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.word-count {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}
.selection-tag {
  cursor: pointer;
}
.edit-textarea {
  width: 100%;
  min-height: calc(100vh - 280px);
  background: rgba(10,14,23,0.8);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  padding: 18px 20px;
  font-size: 14px;
  line-height: 1.8;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace;
  color: #e0e0e8;
  resize: none;
  outline: none;
  transition: border-color 0.3s, box-shadow 0.3s;
}
.edit-textarea::placeholder {
  color: rgba(255,255,255,0.2);
}
.edit-textarea:focus {
  border-color: rgba(146,84,222,0.5);
  box-shadow: 0 0 0 2px rgba(146,84,222,0.15);
}

/* ===== 右侧面板 ===== */
.right-col {
  width: 280px;
  flex-shrink: 0;
}
.right-panel {
  height: 100%;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
}
.right-panel :deep(.ant-card-body) {
  padding: 8px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.right-panel :deep(.ant-tabs) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.right-panel :deep(.ant-tabs-content-holder) {
  flex: 1;
  overflow: hidden;
}
.right-panel :deep(.ant-tabs-content) {
  height: 100%;
}
.right-panel :deep(.ant-tabs-tabpane) {
  height: 100%;
  overflow-y: auto;
}
.right-panel :deep(.ant-tabs-tabpane)::-webkit-scrollbar {
  width: 4px;
}
.right-panel :deep(.ant-tabs-tabpane)::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.15);
  border-radius: 2px;
}
.tab-content {
  padding: 4px 0;
}

/* 执行记录 */
.task-version-badge {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  margin-bottom: 8px;
  padding: 0 2px;
}
.task-card {
  background: rgba(255,255,255,0.03);
  border-radius: 6px;
  margin-bottom: 6px;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.task-card:hover {
  border-color: #d9d9d9;
  background: rgba(255,255,255,0.04);
}
.task-card-expanded {
  border-color: rgba(100,180,255,0.4);
  background: rgba(24,144,255,0.06);
}
.task-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  user-select: none;
}
.task-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-completed { background: #52c41a; }
.dot-failed { background: #ff4d4f; }
.dot-in_progress { background: #1890ff; }
.dot-pending { background: #d9d9d9; }

.task-role {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255,255,255,0.45);
  flex-shrink: 0;
  min-width: 32px;
}
.task-desc {
  font-size: 12px;
  color: rgba(255,255,255,0.85);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-card-time {
  font-size: 11px;
  color: rgba(255,255,255,0.4);
  flex-shrink: 0;
}
.task-card-status {
  font-size: 11px;
  flex-shrink: 0;
}
.status-completed { color: #52c41a; }
.status-failed { color: #ff4d4f; }
.status-in_progress { color: #1890ff; }
.status-pending { color: #999; }

.task-card-body {
  padding: 0 10px 10px;
  border-top: 1px solid #f0f0f0;
  margin-top: 4px;
  padding-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}
.task-card-body :deep(p) { margin: 4px 0; }
.task-card-body :deep(h1),
.task-card-body :deep(h2),
.task-card-body :deep(h3) { font-size: 14px; margin: 6px 0 2px; }

/* 版本列表 */
.version-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 6px;
  border: 1px solid rgba(255,255,255,0.06);
}
.version-item:hover {
  border-color: rgba(100,180,255,0.4);
  background: rgba(24,144,255,0.08);
}
.version-active {
  border-color: #1890ff;
  background: rgba(24,144,255,0.08);
}
.version-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}
.version-no {
  font-weight: 600;
  font-size: 13px;
  color: rgba(255,255,255,0.9);
}
.version-summary {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  margin: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.version-time {
  font-size: 11px;
  color: rgba(255,255,255,0.4);
}

/* 审稿意见 */
.review-item {
  padding: 6px 0;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.review-item:last-child {
  border-bottom: none;
}
.review-index {
  font-weight: bold;
  color: #1890ff;
  font-size: 12px;
  margin-bottom: 4px;
}
.review-text {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== Agent 弹窗 ===== */
.agent-result {
  margin-top: 8px;
}
.agent-result-diff {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.diff-old,
.diff-new {
  flex: 1;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}
.diff-old {
  background: rgba(255,77,79,0.1);
  border: 1px solid rgba(255,77,79,0.3);
}
.diff-new {
  background: rgba(82,196,26,0.1);
  border: 1px solid rgba(82,196,26,0.3);
}
.diff-label {
  font-weight: 600;
  margin-bottom: 4px;
  font-size: 11px;
}
.diff-content {
  white-space: pre-wrap;
  word-break: break-word;
}
.diff-arrow {
  font-size: 20px;
  padding-top: 20px;
  color: rgba(255,255,255,0.4);
  flex-shrink: 0;
}

/* ===== 版本抽屉 ===== */
.drawer-version-item {
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid rgba(255,255,255,0.06);
  margin-bottom: 8px;
  transition: all 0.2s;
}
.drawer-version-item:hover {
  border-color: rgba(100,180,255,0.4);
}
.drawer-version-active {
  border-color: #1890ff;
  background: rgba(24,144,255,0.06);
}
.drawer-version-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.drawer-version-summary {
  font-size: 13px;
  color: rgba(255,255,255,0.45);
  margin: 4px 0;
}
.drawer-version-time {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}
.drawer-version-words {
  font-size: 12px;
  color: #1890ff;
  margin-top: 2px;
}

</style>
