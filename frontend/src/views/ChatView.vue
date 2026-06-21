<template>
  <div class="chat-container">
    <!-- 左侧：对话列表 -->
    <aside class="conv-sidebar">
      <div class="conv-header">
        <span class="conv-title">对话</span>
        <button class="new-conv-btn" @click="handleNewConv" title="新对话">＋</button>
      </div>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: currentConvId === conv.id }"
          @click="switchConv(conv.id)"
        >
          <span class="conv-item-title">{{ conv.title }}</span>
          <button class="conv-del-btn" @click.stop="handleDeleteConv(conv.id)" title="删除">×</button>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">暂无对话</div>
      </div>
    </aside>

    <!-- 右侧：对话区域 -->
    <div class="chat-main">
      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesRef">
        <div class="messages-inner">
          <!-- 欢迎页 -->
          <div v-if="messages.length === 0" class="welcome-section">
            <div class="welcome-logo">
              <div class="logo-icon">⚡</div>
              <h1 class="logo-text gradient-text">PaperAI</h1>
            </div>
            <p class="welcome-desc">学术论文智能写作助手</p>
            <div class="welcome-capabilities">
              <div class="cap-card" v-for="cap in capabilities" :key="cap.title">
                <span class="cap-icon">{{ cap.icon }}</span>
                <span class="cap-title">{{ cap.title }}</span>
                <span class="cap-desc">{{ cap.desc }}</span>
              </div>
            </div>
            <div class="welcome-examples">
              <div class="example-item" v-for="ex in examples" :key="ex" @click="sendMessage(ex)">
                {{ ex }}
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="msg-row"
            :class="'msg-row-' + msg.role"
          >
            <div v-if="msg.role === 'agent'" class="msg-avatar agent-avatar">🤖</div>

            <div class="msg-bubble" :class="'bubble-' + msg.role">
              <template v-if="msg.role === 'user'">
                <div class="msg-text">{{ msg.content }}</div>
              </template>
              <template v-else-if="msg.role === 'agent'">
                <div v-if="msg.loading && !msg.content" class="typing-dots">
                  <span></span><span></span><span></span>
                </div>
                <template v-else>
                  <!-- Agent 思考轮次 -->
                  <div v-if="msg.thinkRounds && msg.thinkRounds.length" class="think-rounds">
                    <div
                      v-for="(tr, tri) in msg.thinkRounds"
                      :key="tri"
                      class="think-round"
                    >
                      <div class="think-round-header" @click="tr.expanded = !tr.expanded">
                        <span class="think-round-icon">🧠</span>
                        <span class="think-round-title">{{ typeof tr.round === 'number' ? '第 ' + tr.round + ' 轮思考' : tr.round.startsWith('task-') ? '任务 ' + tr.round.split('-')[1] + ' 思考' : tr.round === 'review' ? '回顾检查' : '思考' }}</span>
                        <span class="think-round-arrow" :class="{ expanded: tr.expanded }">›</span>
                      </div>
                      <div v-if="tr.expanded" class="think-round-content">
                        <MarkdownRender :content="tr.content" />
                        <span v-if="tr.streaming" class="streaming-cursor"></span>
                      </div>
                    </div>
                  </div>

                  <!-- 任务列表 -->
                  <div v-if="msg.tasks && msg.tasks.length" class="task-list">
                    <div
                      v-for="(task, ti) in msg.tasks"
                      :key="ti"
                      class="task-item"
                      :class="'task-' + task.status"
                    >
                      <span class="task-icon">
                        {{ task.status === 'done' ? '✅' : task.status === 'running' ? '🔄' : task.status === 'failed' ? '❌' : '⏳' }}
                      </span>
                      <span class="task-name">{{ task.name }}</span>
                      <span v-if="task.summary" class="task-summary">{{ task.summary }}</span>
                    </div>
                  </div>

                  <!-- 正式回复 -->
                  <div class="msg-markdown">
                    <MarkdownRender :content="msg.content" />
                    <span v-if="msg.streaming" class="streaming-cursor"></span>
                  </div>

                  <!-- 建议卡片（AI 动态生成） -->
                  <div v-if="msg.cards && msg.cards.length" class="suggestions-section">
                    <div class="suggestions-grid">
                      <div
                        v-for="(card, ci) in msg.cards"
                        :key="ci"
                        class="suggestion-card"
                        @click="sendMessage(card.action)"
                      >
                        <span class="sug-title">{{ card.title }}</span>
                        <span class="sug-desc">{{ card.desc }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- Agent 审阅卡片 -->
                  <div v-if="msg.cards && msg.cards.length" class="agent-review-cards">
                    <div class="agent-review-title">🤖 提交 Agent 审阅</div>
                    <div class="agent-review-grid">
                      <div
                        v-for="ac in AGENT_CARDS"
                        :key="ac.role"
                        class="agent-review-card"
                        @click="startAgentReview(ac.role)"
                      >
                        <span class="ar-icon">{{ ac.icon }}</span>
                        <span class="ar-name">{{ ac.name }}</span>
                        <span class="ar-desc">{{ ac.desc }}</span>
                      </div>
                    </div>
                  </div>
                </template>
              </template>
              <template v-else-if="msg.role === 'system'">
                <div class="system-text">{{ msg.content }}</div>
              </template>
            </div>

            <div v-if="msg.role === 'user'" class="msg-avatar user-avatar">👤</div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-wrapper">
        <div class="input-container">
          <div class="input-box" :class="{ focused: isFocused }">
            <textarea
              ref="textareaRef"
              v-model="inputText"
              placeholder="输入你的问题..."
              @focus="isFocused = true"
              @blur="isFocused = false"
              @keydown.enter.exact.prevent="handleSend"
              @input="autoResize"
              rows="1"
              :disabled="isLoading"
            />
            <button
              class="send-btn"
              :disabled="!inputText.trim() || isLoading"
              @click="handleSend"
            >
              <span v-if="isLoading" class="btn-loading"></span>
              <span v-else class="btn-send">↑</span>
            </button>
          </div>
          <div class="input-options">
            <label class="thinking-toggle" :class="{ active: enableThinking }">
              <span class="toggle-icon">🧠</span>
              <span class="toggle-label">深度思考</span>
              <span class="toggle-switch">
                <input type="checkbox" v-model="enableThinking" />
                <span class="switch-slider"></span>
              </span>
            </label>
            <span class="input-hint">Enter 发送 · Shift+Enter 换行</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：文档面板 -->
    <aside class="doc-panel" :class="{ open: docPanelOpen }">
      <div class="doc-panel-header">
        <span class="doc-panel-title">{{ docContent ? '📄 论文文档' : '📄 文档输出' }}</span>
        <div class="doc-panel-actions">
          <button v-if="docContent" class="doc-export-btn" @click="exportDoc" title="导出 Markdown">📥 导出</button>
          <button class="doc-panel-close" @click="docPanelOpen = false" title="关闭">×</button>
        </div>
      </div>
      <div class="doc-panel-body" ref="docPanelBodyRef" @mouseup="onDocSelect">
        <div v-if="!docContent" class="doc-panel-empty">
          <span>当 AI 撰写论文时，实际内容将在这里显示</span>
        </div>
        <div v-else class="doc-panel-content">
          <MarkdownRender :content="docContent" />
          <div v-if="isDocStreaming" class="doc-writing-indicator">
            <span class="doc-writing-dot"></span>
            <span>Agent 正在撰写...</span>
          </div>
        </div>

        <!-- 选中时的浮动按钮（跟随选区位置） -->
        <div
          v-if="selState.show && selState.mode === 'toolbar'"
          class="sel-float-btn"
          :style="{ top: selState.top + 'px', left: selState.left + 'px' }"
          @mousedown.stop
        >
          <button class="sel-btn" @click="startRewrite">✏️ 改写</button>
        </div>
      </div>

      <!-- 底部固定改写面板（不会消失） -->
      <div v-if="selState.show && selState.mode !== 'toolbar'" class="rewrite-panel" @mousedown.stop>
        <!-- 选中文本预览 -->
        <div class="rewrite-selected">
          <span class="rewrite-label">选中：</span>
          <span class="rewrite-preview">{{ selState.text.slice(0, 80) }}{{ selState.text.length > 80 ? '...' : '' }}</span>
        </div>

        <!-- 输入行 -->
        <div v-if="selState.mode === 'input'" class="rewrite-input-row">
          <input
            ref="selInputRef"
            v-model="selState.instruction"
            class="rewrite-input"
            placeholder="输入改写要求，如：更简洁、加数据支撑、换个说法..."
            @keydown.enter.prevent="submitRewrite"
            @keydown.escape.prevent="cancelRewrite"
          />
          <button class="rewrite-send" @click="submitRewrite" :disabled="!selState.instruction.trim()">↑</button>
          <button class="rewrite-cancel" @click="cancelRewrite">×</button>
        </div>

        <!-- 改写中/结果 -->
        <div v-if="selState.mode === 'rewriting'" class="rewrite-result-row">
          <div class="rewrite-result" ref="rewriteResultRef">{{ selState.result || '改写中...' }}</div>
          <button class="rewrite-cancel" @click="cancelRewrite">×</button>
        </div>
      </div>
    </aside>

    <!-- Agent 审阅浮动面板 -->
    <div v-if="agentReview.open" class="agent-review-panel">
      <div class="arp-header">
        <span class="arp-title">{{ agentReview.agentName }} 审阅</span>
        <button class="arp-close" @click="closeAgentReview">×</button>
      </div>
      <div class="arp-body" ref="arpBodyRef">
        <div v-if="!agentReview.content && agentReview.loading" class="arp-loading">
          <span class="arp-loading-dot"></span>
          <span>{{ agentReview.agentName }}正在审阅文档...</span>
        </div>
        <div v-else class="arp-content">
          <MarkdownRender :content="agentReview.content" />
          <span v-if="agentReview.loading" class="streaming-cursor"></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import {
  getConversationList, createConversation, getConversationMessages,
  sendChatMessage, sendChatMessageStream, sendRewriteStream,
  sendAgentReviewStream, deleteConversation
} from '@/api'
import MarkdownRender from '@/components/MarkdownRender.vue'

// ===== 状态 =====
const conversations = ref([])
const currentConvId = ref(null)
const messages = ref([])
const inputText = ref('')
const isLoading = ref(false)
const isFocused = ref(false)
const enableThinking = ref(false)
const messagesRef = ref(null)
const textareaRef = ref(null)
let abortStream = null
let userScrolledUp = false  // 用户是否手动向上滚动了

// ===== 文档面板 =====
const docPanelOpen = ref(false)
const docContent = ref('')
const isDocStreaming = ref(false)
const docPanelBodyRef = ref(null)

function scrollDocPanel() {
  nextTick(() => {
    if (docPanelBodyRef.value) docPanelBodyRef.value.scrollTop = docPanelBodyRef.value.scrollHeight
  })
}

// ===== 选中改写 =====
const selState = ref({
  show: false,        // 浮动工具栏是否显示
  mode: 'toolbar',    // 'toolbar' | 'input' | 'rewriting'
  text: '',           // 选中的文本
  instruction: '',    // 用户输入的改写指令
  result: '',         // 改写结果
  top: 0,
  left: 0,
})
let abortRewrite = null
const selInputRef = ref(null)

// ===== Agent 审阅面板 =====
const agentReview = ref({
  open: false,
  agentName: '',
  agentRole: '',
  content: '',
  loading: false,
})
let abortAgentReview = null

const AGENT_CARDS = [
  { role: 'SUPERVISOR', icon: '👨‍🏫', name: '导师审阅', desc: '学术方向、研究价值' },
  { role: 'REVIEWER', icon: '📝', name: '审稿人审阅', desc: '质量审查、逻辑性、创新性' },
  { role: 'POLISHER', icon: '✨', name: '润色师审阅', desc: '语言规范、格式、引用' },
  { role: 'RESEARCHER', icon: '🔬', name: '研究员审阅', desc: '文献调研、研究现状' },
]

function startAgentReview(agentRole) {
  if (!currentConvId.value) return
  const agent = AGENT_CARDS.find(a => a.role === agentRole)
  agentReview.value = {
    open: true,
    agentName: agent?.name || 'Agent',
    agentRole,
    content: '',
    loading: true,
  }

  abortAgentReview = sendAgentReviewStream(currentConvId.value, agentRole, {
    onStart(name) {
      agentReview.value.agentName = name
    },
    onToken(content) {
      agentReview.value.content = content
    },
    onDone(content) {
      agentReview.value.content = content
      agentReview.value.loading = false
      abortAgentReview = null
    },
    onError(err) {
      agentReview.value.content = '审阅失败：' + (err.message || '网络错误')
      agentReview.value.loading = false
      abortAgentReview = null
    },
  })
}

function closeAgentReview() {
  agentReview.value.open = false
  if (abortAgentReview) { abortAgentReview(); abortAgentReview = null }
}

function onDocSelect(e) {
  const sel = window.getSelection()
  const text = sel?.toString().trim()
  if (!text || text.length < 5) {
    // 如果正在改写中，不隐藏面板
    if (selState.value.mode !== 'input' && selState.value.mode !== 'rewriting') {
      selState.value.show = false
    }
    return
  }
  // 获取选区位置（考虑滚动偏移）
  const range = sel.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  const panel = docPanelBodyRef.value
  if (!panel) return
  const panelRect = panel.getBoundingClientRect()

  selState.value = {
    show: true,
    mode: 'toolbar',
    text,
    instruction: '',
    result: '',
    top: rect.top - panelRect.top + panel.scrollTop - 36,
    left: Math.max(0, rect.left - panelRect.left + rect.width / 2 - 60),
  }
}

// ===== 导出文档 =====
function exportDoc() {
  if (!docContent.value) return
  // 从文档内容提取标题作为文件名
  let filename = '论文文档'
  const firstLine = docContent.value.split('\n')[0]
  if (firstLine.startsWith('# ')) {
    filename = firstLine.slice(2).trim()
  }
  // 清理文件名中的非法字符
  filename = filename.replace(/[\\/:*?"<>|]/g, '_').slice(0, 50)

  const blob = new Blob([docContent.value], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filename}.md`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function startRewrite() {
  selState.value.mode = 'input'
  selState.value.instruction = ''
  nextTick(() => { selInputRef.value?.focus() })
}

function cancelRewrite() {
  selState.value.show = false
  selState.value.mode = 'toolbar'
  if (abortRewrite) { abortRewrite(); abortRewrite = null }
}

function submitRewrite() {
  if (!currentConvId.value || !selState.value.text) return
  const instruction = selState.value.instruction.trim() || '润色改写这段文字'
  selState.value.mode = 'rewriting'
  selState.value.result = ''

  abortRewrite = sendRewriteStream(currentConvId.value, selState.value.text, instruction, {
    onToken(content) {
      selState.value.result = content
    },
    onDone(content) {
      // 替换文档中的选中文本
      const original = selState.value.text
      const replaced = docContent.value.replace(original, content)
      if (replaced !== docContent.value) {
        docContent.value = replaced
      }
      selState.value.show = false
      selState.value.mode = 'toolbar'
      abortRewrite = null
    },
    onError(err) {
      selState.value.result = '改写失败：' + (err.message || '网络错误')
      selState.value.mode = 'input'
      abortRewrite = null
    },
  })
}

// ===== 示例 =====
const capabilities = [
  { icon: '📝', title: '论文写作', desc: '全流程辅助撰写' },
  { icon: '🔬', title: '文献调研', desc: '自动检索分析' },
  { icon: '📊', title: '数据可视化', desc: '图表自动生成' },
  { icon: '✏️', title: '润色修改', desc: '语言格式优化' }
]

const examples = [
  '帮我写一篇关于大语言模型的综述论文',
  '深度学习在医疗影像中有哪些应用？',
  '如何设计一个 RAG 系统的实验方案？',
  '帮我润色这段学术论文的摘要'
]

// ===== 对话管理 =====
async function loadConversations() {
  try {
    const res = await getConversationList()
    conversations.value = res?.data || []
  } catch (e) {
    console.error('加载对话列表失败:', e)
  }
}

async function handleNewConv() {
  currentConvId.value = null
  messages.value = []
  docContent.value = ''
  docPanelOpen.value = false
}

async function switchConv(convId) {
  currentConvId.value = convId
  messages.value = []
  docContent.value = ''
  try {
    const res = await getConversationMessages(convId)
    const data = res?.data || {}
    const msgs = data.messages || data || []
    messages.value = Array.isArray(msgs) ? msgs.map(m => {
      // 尝试解析 JSON 格式的消息（包含思考、任务、卡片）
      let parsed = null
      try { parsed = JSON.parse(m.content) } catch (e) { /* 不是 JSON */ }
      if (parsed && parsed.reply !== undefined) {
        return {
          role: m.role,
          content: parsed.reply,
          thinking: parsed.thinking || '',
          thinkRounds: parsed.thinkRounds || [],
          tasks: parsed.tasks || [],
          cards: parsed.cards || [],
        }
      }
      return { role: m.role, content: m.content }
    }) : []
    // 恢复 Article 文档内容
    if (data.article?.content) {
      docContent.value = data.article.content
      docPanelOpen.value = true
    }
  } catch (e) {
    console.error('加载消息失败:', e)
  }
}

async function handleDeleteConv(convId) {
  try {
    await deleteConversation(convId)
    conversations.value = conversations.value.filter(c => c.id !== convId)
    if (currentConvId.value === convId) {
      currentConvId.value = null
      messages.value = []
    }
  } catch (e) {
    console.error('删除对话失败:', e)
  }
}

// ===== 发送消息（流式） =====
async function sendMessage(text) {
  if (!text.trim() || isLoading.value) return

  const userMsg = text.trim()
  inputText.value = ''
  resizeTextarea()

  messages.value.push({ role: 'user', content: userMsg })
  messages.value.push({ role: 'agent', content: '', thinking: '', thinkRounds: [], tasks: [], loading: true, streaming: true })
  isLoading.value = true
  userScrolledUp = false  // 新消息发送时恢复自动滚动
  scrollToBottom()

  // 文档面板状态（不清空 docContent，保留已有文档）
  isDocStreaming.value = false

  // 如果没有当前对话，先创建
  if (!currentConvId.value) {
    const convRes = await createConversation(userMsg.slice(0, 30))
    currentConvId.value = convRes?.data?.id
    await loadConversations()
  }

  const lastMsg = messages.value[messages.value.length - 1]

  abortStream = sendChatMessageStream(currentConvId.value, userMsg, enableThinking.value, {
    onThink(round, content, streaming) {
      if (!lastMsg.thinkRounds) lastMsg.thinkRounds = []
      const existing = lastMsg.thinkRounds.find(t => t.round === round)
      if (existing) {
        existing.content = content
        existing.streaming = streaming
      } else {
        lastMsg.thinkRounds.push({ round, content, expanded: true, streaming })
      }
      scrollToBottom()
    },
    onTasklist(tasks) {
      lastMsg.tasks = tasks.map(t => ({ ...t }))
      scrollToBottom()
    },
    onTaskUpdate(index, status, summary) {
      if (lastMsg.tasks && lastMsg.tasks[index]) {
        lastMsg.tasks[index].status = status
        if (summary) lastMsg.tasks[index].summary = summary
      }
      scrollToBottom()
    },
    onThinking(content) {
      lastMsg.thinking = content
      scrollToBottom()
    },
    onToken(content) {
      lastMsg.content = content
      lastMsg.loading = false
      scrollToBottom()
    },
    onDoc(content) {
      // Agent 写入的论文内容 → 文档面板
      if (!docPanelOpen.value) docPanelOpen.value = true
      docContent.value = content
      isDocStreaming.value = true
      scrollDocPanel()
    },
    onDone(data) {
      lastMsg.content = data.reply || lastMsg.content
      // 保留 thinkRounds（不清空）
      if (data.thinking && (!lastMsg.thinkRounds || !lastMsg.thinkRounds.length)) {
        lastMsg.thinking = data.thinking
      }
      lastMsg.cards = data.cards || null
      lastMsg.loading = false
      lastMsg.streaming = false
      isDocStreaming.value = false
      isLoading.value = false
      abortStream = null
      if (data.doc) docContent.value = data.doc
      loadConversations()
      scrollToBottom()
    },
    onError(err) {
      lastMsg.content = lastMsg.content || '请求失败：' + (err.message || '网络错误')
      lastMsg.loading = false
      lastMsg.streaming = false
      isDocStreaming.value = false
      isLoading.value = false
      abortStream = null
      scrollToBottom()
    },
  })
}

function handleSend() {
  sendMessage(inputText.value)
}


// ===== 工具函数 =====
function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function resizeTextarea() {
  nextTick(() => {
    if (textareaRef.value) textareaRef.value.style.height = 'auto'
  })
}

function scrollToBottom() {
  if (userScrolledUp) return  // 用户手动滚上去了，不强制拉回
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

// 检测用户是否手动滚动
function onMessagesScroll() {
  const el = messagesRef.value
  if (!el) return
  // 距离底部超过 100px 认为是用户向上滚动了
  const distToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  userScrolledUp = distToBottom > 100
}

watch(messages, scrollToBottom, { deep: true })

onMounted(() => {
  loadConversations()
  // 监听滚动事件
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.addEventListener('scroll', onMessagesScroll, { passive: true })
    }
  })
})
</script>

<style scoped>
.chat-container {
  height: 100%;
  display: flex;
  background: transparent;
  overflow: hidden;
}

/* ===== 左侧对话列表 ===== */
.conv-sidebar {
  width: 220px;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(10, 14, 23, 0.6);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.conv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.conv-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.new-conv-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.6);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.new-conv-btn:hover {
  background: rgba(146, 84, 222, 0.15);
  border-color: rgba(146, 84, 222, 0.3);
  color: #fff;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 2px;
}

.conv-item:hover {
  background: rgba(255, 255, 255, 0.04);
}

.conv-item.active {
  background: rgba(146, 84, 222, 0.12);
  border: 1px solid rgba(146, 84, 222, 0.15);
}

.conv-item-title {
  flex: 1;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-item.active .conv-item-title {
  color: rgba(255, 255, 255, 0.9);
}

.conv-del-btn {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.15);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.15s;
}

.conv-item:hover .conv-del-btn {
  opacity: 1;
}

.conv-del-btn:hover {
  background: rgba(255, 77, 79, 0.15);
  color: #ff4d4f;
}

.conv-empty {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.15);
}

/* ===== 右侧对话区域 ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
}

.messages-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 40px 0;
}

/* ===== 欢迎页 ===== */
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px 20px;
  text-align: center;
}

.welcome-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.logo-icon { font-size: 36px; }
.logo-text { font-size: 32px; font-weight: 800; letter-spacing: -1px; }

.welcome-desc {
  color: rgba(255, 255, 255, 0.35);
  font-size: 14px;
  margin-bottom: 24px;
}

.welcome-capabilities {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 24px;
  width: 100%;
  max-width: 560px;
}

.cap-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  transition: all 0.2s;
}

.cap-card:hover {
  background: rgba(146, 84, 222, 0.06);
  border-color: rgba(146, 84, 222, 0.15);
}

.cap-icon { font-size: 22px; }
.cap-title { font-size: 12px; font-weight: 600; color: rgba(255, 255, 255, 0.75); }
.cap-desc { font-size: 10px; color: rgba(255, 255, 255, 0.25); }

.welcome-examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 560px;
}

.example-item {
  padding: 7px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 18px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: all 0.2s;
}

.example-item:hover {
  background: rgba(146, 84, 222, 0.1);
  border-color: rgba(146, 84, 222, 0.3);
  color: rgba(255, 255, 255, 0.8);
}

/* ===== 消息行 ===== */
.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  animation: msgIn 0.3s ease;
}

@keyframes msgIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.msg-row-user { justify-content: flex-end; }
.msg-row-agent { justify-content: flex-start; }
.msg-row-system { justify-content: center; }

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.agent-avatar { background: linear-gradient(135deg, #9254de, #597ef7); }
.user-avatar { background: linear-gradient(135deg, #36cfc9, #597ef7); }

.msg-bubble { max-width: 82%; line-height: 1.7; font-size: 14px; }

.bubble-user {
  background: linear-gradient(135deg, rgba(146, 84, 222, 0.18), rgba(89, 126, 247, 0.18));
  border: 1px solid rgba(146, 84, 222, 0.18);
  padding: 10px 14px;
  border-radius: 16px 16px 4px 16px;
  color: rgba(255, 255, 255, 0.9);
}

.bubble-agent {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  padding: 12px 16px;
  border-radius: 16px 16px 16px 4px;
  color: rgba(255, 255, 255, 0.85);
  min-width: 50px;
}

.bubble-system { background: none; padding: 4px 0; }

.system-text {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  text-align: center;
}

/* ===== 打字动画 ===== */
.typing-dots { display: flex; gap: 4px; padding: 4px 0; }

.typing-dots span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(146, 84, 222, 0.5);
  animation: dotBounce 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(1) { animation-delay: 0s; }
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}

/* ===== AI 消息内容 ===== */
.msg-markdown :deep(h1), .msg-markdown :deep(h2), .msg-markdown :deep(h3) {
  color: rgba(255, 255, 255, 0.9);
  margin: 10px 0 6px;
}
.msg-markdown :deep(h1) { font-size: 17px; }
.msg-markdown :deep(h2) { font-size: 15px; }
.msg-markdown :deep(h3) { font-size: 14px; }
.msg-markdown :deep(p) { margin: 6px 0; color: rgba(255, 255, 255, 0.8); }
.msg-markdown :deep(code) { background: rgba(146, 84, 222, 0.1); color: #d4b8ff; padding: 2px 5px; border-radius: 3px; font-size: 0.88em; }
.msg-markdown :deep(pre) { background: rgba(0, 0, 0, 0.3); border-radius: 8px; padding: 12px; margin: 8px 0; overflow-x: auto; }
.msg-markdown :deep(pre code) { background: none; padding: 0; color: #c8d0e0; font-size: 12px; }
.msg-markdown :deep(ul), .msg-markdown :deep(ol) { padding-left: 18px; margin: 6px 0; }
.msg-markdown :deep(li) { margin: 3px 0; color: rgba(255, 255, 255, 0.8); }
.msg-markdown :deep(blockquote) { border-left: 3px solid #9254de; padding: 6px 12px; margin: 8px 0; background: rgba(146, 84, 222, 0.04); border-radius: 0 4px 4px 0; color: rgba(255, 255, 255, 0.7); }
.msg-markdown :deep(table) { border-collapse: collapse; margin: 8px 0; font-size: 12px; }
.msg-markdown :deep(th), .msg-markdown :deep(td) { border: 1px solid rgba(255, 255, 255, 0.08); padding: 5px 10px; }
.msg-markdown :deep(th) { background: rgba(255, 255, 255, 0.04); }

/* ===== 流式光标 ===== */
.streaming-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: rgba(146, 84, 222, 0.8);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: cursorBlink 0.8s ease-in-out infinite;
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ===== 思考过程 ===== */
.thinking-block {
  margin-bottom: 10px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(146, 84, 222, 0.12);
  background: rgba(146, 84, 222, 0.04);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.2s;
  user-select: none;
}

.thinking-header:hover { background: rgba(146, 84, 222, 0.08); }

.thinking-icon { font-size: 16px; }

.thinking-title {
  flex: 1;
  font-size: 13px;
  color: rgba(146, 84, 222, 0.8);
  font-weight: 500;
}

.thinking-arrow {
  font-size: 14px;
  color: rgba(146, 84, 222, 0.5);
  transition: transform 0.2s;
  font-weight: bold;
}

.thinking-arrow.expanded { transform: rotate(90deg); }

.thinking-content {
  padding: 12px 14px;
  border-top: 1px solid rgba(146, 84, 222, 0.08);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.7;
  max-height: 400px;
  overflow-y: auto;
}

.thinking-content :deep(p) { margin: 4px 0; color: rgba(255, 255, 255, 0.55); }
.thinking-content :deep(code) { background: rgba(146, 84, 222, 0.1); color: #d4b8ff; padding: 2px 5px; border-radius: 3px; font-size: 0.88em; }
.thinking-content :deep(pre) { background: rgba(0, 0, 0, 0.2); border-radius: 6px; padding: 10px; margin: 6px 0; }
.thinking-content :deep(pre code) { background: none; padding: 0; font-size: 12px; }

/* ===== Agent 思考轮次 ===== */
.think-rounds {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.think-round {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(146, 84, 222, 0.1);
  background: rgba(146, 84, 222, 0.03);
}

.think-round-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.2s;
  user-select: none;
}

.think-round-header:hover { background: rgba(146, 84, 222, 0.06); }

.think-round-icon { font-size: 14px; }

.think-round-title {
  flex: 1;
  font-size: 12px;
  color: rgba(146, 84, 222, 0.7);
  font-weight: 500;
}

.think-round-arrow {
  font-size: 12px;
  color: rgba(146, 84, 222, 0.4);
  transition: transform 0.2s;
  font-weight: bold;
}

.think-round-arrow.expanded { transform: rotate(90deg); }

.think-round-content {
  padding: 10px 12px;
  border-top: 1px solid rgba(146, 84, 222, 0.06);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  line-height: 1.6;
  max-height: 300px;
  overflow-y: auto;
}

.think-round-content :deep(p) { margin: 3px 0; color: rgba(255, 255, 255, 0.5); }
.think-round-content :deep(code) { background: rgba(146, 84, 222, 0.1); color: #d4b8ff; padding: 1px 4px; border-radius: 3px; font-size: 0.85em; }

/* ===== 任务列表 ===== */
.task-list {
  margin: 10px 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  transition: all 0.3s;
}

.task-pending {
  background: rgba(255, 255, 255, 0.02);
  color: rgba(255, 255, 255, 0.4);
}

.task-running {
  background: rgba(146, 84, 222, 0.08);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(146, 84, 222, 0.15);
}

.task-done {
  background: rgba(82, 196, 26, 0.06);
  color: rgba(82, 196, 26, 0.8);
}

.task-failed {
  background: rgba(255, 77, 79, 0.06);
  color: rgba(255, 77, 79, 0.8);
}

.task-icon { font-size: 14px; flex-shrink: 0; }
.task-name { flex: 1; }
.task-summary { font-size: 11px; color: rgba(255, 255, 255, 0.3); }

/* ===== Agent 审阅卡片 ===== */
.agent-review-cards {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.agent-review-title {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 10px;
  font-weight: 500;
}

.agent-review-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.agent-review-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.agent-review-card:hover {
  background: rgba(146, 84, 222, 0.08);
  border-color: rgba(146, 84, 222, 0.2);
  transform: translateY(-1px);
}

.ar-icon { font-size: 20px; flex-shrink: 0; }
.ar-name { font-size: 13px; font-weight: 600; color: rgba(255, 255, 255, 0.8); }
.ar-desc { font-size: 11px; color: rgba(255, 255, 255, 0.35); flex: 1; }

/* ===== Agent 审阅浮动面板 ===== */
.agent-review-panel {
  position: fixed;
  top: 60px;
  right: 20px;
  width: 500px;
  max-height: calc(100vh - 100px);
  background: rgba(10, 14, 23, 0.96);
  border: 1px solid rgba(146, 84, 222, 0.2);
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  z-index: 100;
  animation: arpSlideIn 0.25s ease;
}

@keyframes arpSlideIn {
  from { opacity: 0; transform: translateX(20px); }
  to { opacity: 1; transform: translateX(0); }
}

.arp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.arp-title {
  font-size: 15px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}

.arp-close {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.4);
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.arp-close:hover {
  background: rgba(255, 77, 79, 0.15);
  color: #ff4d4f;
}

.arp-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.arp-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: rgba(146, 84, 222, 0.7);
  font-size: 14px;
  padding: 20px 0;
}

.arp-loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9254de;
  animation: arpPulse 1.2s ease-in-out infinite;
}

@keyframes arpPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

.arp-content {
  font-size: 14px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.8);
}

.arp-content :deep(h1), .arp-content :deep(h2), .arp-content :deep(h3) {
  color: rgba(255, 255, 255, 0.9);
  margin: 12px 0 6px;
}

.arp-content :deep(h2) { font-size: 16px; }
.arp-content :deep(h3) { font-size: 14px; }
.arp-content :deep(p) { margin: 6px 0; }
.arp-content :deep(code) { background: rgba(146, 84, 222, 0.1); color: #d4b8ff; padding: 2px 5px; border-radius: 3px; font-size: 0.88em; }
.arp-content :deep(ul), .arp-content :deep(ol) { padding-left: 18px; margin: 6px 0; }
.arp-content :deep(li) { margin: 3px 0; }
.arp-content :deep(strong) { color: rgba(255, 255, 255, 0.9); }

/* ===== 建议卡片 ===== */
.suggestions-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.suggestions-title {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 10px;
  font-weight: 500;
}

.suggestions-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.suggestion-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.suggestion-card:hover {
  background: rgba(146, 84, 222, 0.08);
  border-color: rgba(146, 84, 222, 0.2);
  transform: translateY(-1px);
}

.sug-icon { font-size: 18px; }
.sug-title { font-size: 13px; font-weight: 600; color: rgba(255, 255, 255, 0.8); }
.sug-desc { font-size: 11px; color: rgba(255, 255, 255, 0.35); }


/* ===== 输入区域 ===== */
.input-wrapper {
  padding: 8px 40px 12px;
  background: linear-gradient(to top, rgba(10, 14, 23, 0.95) 60%, transparent);
}

.input-container { max-width: 960px; margin: 0 auto; }

.input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 10px 10px 10px 16px;
  transition: all 0.2s;
}

.input-box.focused {
  border-color: rgba(146, 84, 222, 0.4);
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 3px rgba(146, 84, 222, 0.08);
}

.input-box textarea {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  max-height: 160px;
  font-family: inherit;
}

.input-box textarea::placeholder { color: rgba(255, 255, 255, 0.2); }
.input-box textarea:disabled { opacity: 0.4; }

.send-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #9254de, #597ef7);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
}

.send-btn:hover:not(:disabled) { transform: scale(1.05); box-shadow: 0 4px 12px rgba(146, 84, 222, 0.4); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.btn-send { font-size: 16px; font-weight: bold; line-height: 1; }

.btn-loading {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.input-hint {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.1);
}

/* ===== 输入选项栏 ===== */
.input-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 4px 0;
}

.thinking-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 10px 4px 8px;
  border-radius: 20px;
  transition: all 0.2s;
  user-select: none;
}

.thinking-toggle:hover {
  background: rgba(146, 84, 222, 0.06);
}

.thinking-toggle.active {
  background: rgba(146, 84, 222, 0.1);
}

.toggle-icon {
  font-size: 14px;
}

.toggle-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.thinking-toggle.active .toggle-label {
  color: rgba(146, 84, 222, 0.9);
}

.toggle-switch {
  position: relative;
  width: 32px;
  height: 18px;
  display: inline-block;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  transition: all 0.2s;
}

.switch-slider::before {
  content: '';
  position: absolute;
  width: 14px;
  height: 14px;
  left: 2px;
  bottom: 2px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 50%;
  transition: all 0.2s;
}

.toggle-switch input:checked + .switch-slider {
  background: linear-gradient(135deg, #9254de, #597ef7);
}

.toggle-switch input:checked + .switch-slider::before {
  transform: translateX(14px);
  background: white;
}

/* ===== 右侧文档面板 ===== */
.doc-panel {
  width: 480px;
  flex-shrink: 0;
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(10, 14, 23, 0.85);
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  max-height: 100%;
}

.doc-panel.open {
  transform: translateX(0);
}

.doc-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.doc-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.doc-panel-close {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.4);
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.doc-panel-close:hover {
  background: rgba(255, 77, 79, 0.15);
  color: #ff4d4f;
}

.doc-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  position: relative;
}

.doc-panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(255, 255, 255, 0.15);
  font-size: 13px;
  text-align: center;
  padding: 20px;
}

.doc-panel-content {
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.85);
}

/* 文档面板内 Markdown 样式 */
.doc-panel-content :deep(h1) {
  font-size: 22px;
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.95);
}

.doc-panel-content :deep(h2) {
  font-size: 18px;
  margin: 20px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.9);
}

.doc-panel-content :deep(h3) {
  font-size: 16px;
  margin: 16px 0 8px;
  color: rgba(255, 255, 255, 0.85);
}

.doc-panel-content :deep(p) {
  margin: 10px 0;
  color: rgba(255, 255, 255, 0.8);
}

.doc-panel-content :deep(code) {
  background: rgba(146, 84, 222, 0.1);
  color: #d4b8ff;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.doc-panel-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
  padding: 14px;
  margin: 10px 0;
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.doc-panel-content :deep(pre code) {
  background: none;
  padding: 0;
  color: #c8d0e0;
  font-size: 13px;
}

.doc-panel-content :deep(ul),
.doc-panel-content :deep(ol) {
  padding-left: 22px;
  margin: 8px 0;
}

.doc-panel-content :deep(li) {
  margin: 4px 0;
  color: rgba(255, 255, 255, 0.8);
}

.doc-panel-content :deep(blockquote) {
  border-left: 3px solid #9254de;
  padding: 8px 14px;
  margin: 10px 0;
  background: rgba(146, 84, 222, 0.04);
  border-radius: 0 6px 6px 0;
  color: rgba(255, 255, 255, 0.65);
}

.doc-panel-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.doc-panel-content :deep(th),
.doc-panel-content :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 8px 12px;
  text-align: left;
}

.doc-panel-content :deep(th) {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.7);
  font-weight: 600;
}

.doc-panel-content :deep(strong) {
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
}

.doc-panel-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  margin: 20px 0;
}

/* ===== 文档撰写指示器 ===== */
.doc-writing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  margin-top: 12px;
  color: rgba(146, 84, 222, 0.7);
  font-size: 13px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.doc-writing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9254de;
  animation: docPulse 1.2s ease-in-out infinite;
}

@keyframes docPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

/* ===== 文档头部按钮 ===== */
.doc-panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.doc-export-btn {
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.doc-export-btn:hover {
  background: rgba(146, 84, 222, 0.12);
  border-color: rgba(146, 84, 222, 0.3);
  color: rgba(255, 255, 255, 0.8);
}

/* ===== 选中浮动按钮 ===== */
.sel-float-btn {
  position: absolute;
  z-index: 10;
  animation: selFadeIn 0.12s ease;
}

@keyframes selFadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.sel-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  background: linear-gradient(135deg, #9254de, #597ef7);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: 0 4px 14px rgba(146, 84, 222, 0.4);
  transition: all 0.15s;
}

.sel-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(146, 84, 222, 0.5); }

/* ===== 底部固定改写面板 ===== */
.rewrite-panel {
  border-top: 1px solid rgba(146, 84, 222, 0.2);
  background: rgba(10, 14, 23, 0.95);
  padding: 10px 14px;
  flex-shrink: 0;
  animation: panelSlideUp 0.2s ease;
}

@keyframes panelSlideUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.rewrite-selected {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 11px;
  overflow: hidden;
}

.rewrite-label {
  color: rgba(146, 84, 222, 0.7);
  flex-shrink: 0;
  font-weight: 500;
}

.rewrite-preview {
  color: rgba(255, 255, 255, 0.35);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rewrite-input-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rewrite-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 7px 12px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}

.rewrite-input:focus {
  border-color: rgba(146, 84, 222, 0.4);
}

.rewrite-input::placeholder { color: rgba(255, 255, 255, 0.2); }

.rewrite-send {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #9254de, #597ef7);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
}

.rewrite-send:hover:not(:disabled) { transform: scale(1.05); }
.rewrite-send:disabled { opacity: 0.3; cursor: not-allowed; }

.rewrite-cancel {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.35);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
}

.rewrite-cancel:hover { background: rgba(255, 77, 79, 0.12); color: #ff4d4f; }

.rewrite-result-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.rewrite-result {
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.75);
  max-height: 160px;
  overflow-y: auto;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.04);
  word-break: break-word;
}
</style>
