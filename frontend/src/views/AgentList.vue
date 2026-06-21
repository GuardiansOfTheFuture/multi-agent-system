<template>
  <div class="agent-list">
    <div class="agent-section-title">内置 Agent</div>
    <a-row :gutter="[16, 16]">
      <a-col :span="8" v-for="agent in builtinAgents" :key="agent.code">
        <a-card hoverable class="agent-card">
          <template #title>
            <span style="display:flex;align-items:center;gap:8px">
              <span style="font-size:24px">{{ agent.icon }}</span>
              <span>{{ agent.displayName }}</span>
            </span>
          </template>
          <template #extra>
            <a-tag :color="agent.tagColor" style="border-radius:6px">{{ agent.code }}</a-tag>
          </template>

          <p class="agent-desc">{{ agent.description }}</p>
          <a-divider />
          <a-form layout="vertical" size="small">
            <a-form-item label="Temperature">
              <a-slider :value="agent.temperature" :min="0" :max="1" :step="0.1" disabled />
            </a-form-item>
          </a-form>

          <template #actions>
            <a-button type="link" @click="openChat(agent)">
              <template #icon><message-outlined /></template>
              测试对话
            </a-button>
          </template>
        </a-card>
      </a-col>
    </a-row>

    <a-divider />

    <div style="display:flex;align-items:center;justify-content:space-between;margin:12px 0 8px">
      <span class="agent-section-title">自定义 Agent</span>
      <a-button size="small" type="primary" @click="openCreate"><plus-outlined /> 新建</a-button>
    </div>
    <a-row :gutter="[16, 16]">
      <a-col :span="8" v-for="agent in customAgents" :key="agent.id">
        <a-card hoverable class="agent-card custom-agent-card">
          <template #title>
            <span style="display:flex;align-items:center;gap:8px">
              <span style="font-size:24px">{{ agent.icon || '🤖' }}</span>
              <span>{{ agent.name }}</span>
            </span>
          </template>
          <template #extra>
            <a-space :size="4">
              <a-tag color="green" style="border-radius:6px">{{ agent.model }}</a-tag>
              <a-button type="text" size="small" @click="openEdit(agent)"><edit-outlined /></a-button>
              <a-popconfirm title="删除？" @confirm="deleteCustom(agent.id)">
                <a-button type="text" size="small" danger><delete-outlined /></a-button>
              </a-popconfirm>
            </a-space>
          </template>

          <p class="agent-desc">{{ agent.description || '无描述' }}</p>
          <a-divider />
          <a-form layout="vertical" size="small">
            <a-form-item label="System Prompt">
              <div class="agent-prompt-preview">{{ truncateText(agent.systemPrompt, 80) }}</div>
            </a-form-item>
            <a-form-item label="Temperature">
              <a-slider :value="agent.temperature" :min="0" :max="1" :step="0.1" disabled />
            </a-form-item>
          </a-form>

          <template #actions>
            <a-button type="link" @click="openChat({ displayName: agent.name, icon: agent.icon || '🤖', code: 'CUSTOM_' + agent.id })">
              <template #icon><message-outlined /></template>
              测试对话
            </a-button>
          </template>
        </a-card>
      </a-col>

      <a-col :span="8" v-if="!customAgents.length && !loadingCustom">
        <a-card class="agent-card empty-card" :body-style="{ display:'flex', alignItems:'center', justifyContent:'center', minHeight:'200px' }">
          <a-empty description="暂无自定义 Agent" :image-style="{ height:'40px' }">
            <a-button size="small" type="primary" @click="openCreate"><plus-outlined /> 创建我的第一个 Agent</a-button>
          </a-empty>
        </a-card>
      </a-col>
    </a-row>

    <!-- 编辑/创建弹窗 -->
    <a-modal
      v-model:open="editVisible"
      :title="editingId ? '编辑自定义 Agent' : '创建自定义 Agent'"
      :mask-closable="false"
      @ok="saveCustom"
      :confirm-loading="saving"
      width="600"
    >
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="图标 Emoji" required>
              <a-input v-model:value="editForm.icon" placeholder="🤖" maxlength="4" />
            </a-form-item>
          </a-col>
          <a-col :span="16">
            <a-form-item label="名称" required>
              <a-input v-model:value="editForm.name" placeholder="如：数据分析专家" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="描述">
          <a-input v-model:value="editForm.description" placeholder="简要描述此Agent的角色和用途" />
        </a-form-item>
        <a-form-item label="System Prompt" required>
          <a-textarea
            v-model:value="editForm.systemPrompt"
            :rows="6"
            placeholder="定义Agent的角色、能力和输出格式..."
          />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="默认模型">
              <a-select v-model:value="editForm.model" :options="modelOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="默认温度">
              <a-slider v-model:value="editForm.temperature" :min="0" :max="1" :step="0.1" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <!-- 对话弹窗 -->
    <a-modal
      v-model:open="chatVisible"
      :title="`与 ${currentAgent?.displayName} 对话`"
      @ok="sendChat"
      :confirmLoading="chatLoading"
    >
      <a-form layout="vertical" size="small">
        <a-form-item label="使用模型">
          <a-select v-model:value="chatModel" :options="modelOptions" style="width:150px" />
        </a-form-item>
        <a-form-item label="输入问题">
          <a-input v-model:value="chatInput" placeholder="输入你想问的问题..." :disabled="chatLoading" />
        </a-form-item>
      </a-form>
      <a-divider />
      <div class="chat-response" v-if="chatResponse">
        <strong>回复：</strong>
        <div class="markdown-body">{{ chatResponse }}</div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { chatWithAgent, listCustomAgents, createCustomAgent, updateCustomAgent, deleteCustomAgent } from '@/api'
import { message } from 'ant-design-vue'
import { MessageOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const modelOptions = [
  { value: 'mimo-v2.5-pro', label: 'MiMo V2.5 Pro' },
  { value: 'qwen-plus', label: 'Qwen Plus' },
  { value: 'qwen-max', label: 'Qwen Max' },
  { value: 'deepseek-v3', label: 'DeepSeek V3' }
]

const builtinAgents = [
  { code: 'SUPERVISOR', displayName: '导师 Agent', icon: '🧭', description: '把控研究方向，审阅大纲，给出修改意见', temperature: 0.5, tagColor: 'purple' },
  { code: 'RESEARCHER', displayName: '研究员 Agent', icon: '🔬', description: '文献调研，信息收集，综述撰写', temperature: 0.3, tagColor: 'blue' },
  { code: 'WRITER', displayName: '写手 Agent', icon: '✍️', description: '撰写论文各章节，组织语言', temperature: 0.7, tagColor: 'green' },
  { code: 'REVIEWER', displayName: '审稿人 Agent', icon: '📝', description: '批判性审阅，找漏洞，提改进意见', temperature: 0.6, tagColor: 'orange' },
  { code: 'POLISHER', displayName: '润色 Agent', icon: '✨', description: '语法校对，格式规范，引用检查', temperature: 0.4, tagColor: 'cyan' }
]

const customAgents = ref([])
const loadingCustom = ref(false)

// Chat
const chatVisible = ref(false)
const chatLoading = ref(false)
const currentAgent = ref(null)
const chatInput = ref('')
const chatResponse = ref('')
const chatModel = ref('mimo-v2.5-pro')

// Edit
const editVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const editForm = reactive({
  icon: '🤖', name: '', description: '', systemPrompt: '',
  model: 'mimo-v2.5-pro', temperature: 0.7
})

function truncateText(text, max) {
  if (!text) return '无'
  return text.length > max ? text.substring(0, max) + '...' : text
}

// ===== 自定义 Agent CRUD =====
async function loadCustom() {
  loadingCustom.value = true
  try {
    const res = await listCustomAgents()
    customAgents.value = res.data || []
  } catch (e) {
    console.error('加载自定义Agent失败:', e)
  } finally { loadingCustom.value = false }
}

function openCreate() {
  editingId.value = null
  Object.assign(editForm, { icon: '🤖', name: '', description: '', systemPrompt: '', model: 'mimo-v2.5-pro', temperature: 0.7 })
  editVisible.value = true
}

function openEdit(agent) {
  editingId.value = agent.id
  Object.assign(editForm, {
    icon: agent.icon || '🤖',
    name: agent.name,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt || '',
    model: agent.model || 'mimo-v2.5-pro',
    temperature: agent.temperature != null ? agent.temperature : 0.7
  })
  editVisible.value = true
}

async function saveCustom() {
  if (!editForm.name.trim() || !editForm.systemPrompt.trim()) {
    message.warning('名称和System Prompt不能为空')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateCustomAgent(editingId.value, { ...editForm })
      message.success('已更新')
    } else {
      await createCustomAgent({ ...editForm })
      message.success('已创建')
    }
    editVisible.value = false
    await loadCustom()
  } catch (e) {
    message.error('操作失败: ' + (e.message || '未知错误'))
  } finally { saving.value = false }
}

async function deleteCustom(id) {
  try {
    await deleteCustomAgent(id)
    message.success('已删除')
    await loadCustom()
  } catch (e) {
    message.error('删除失败: ' + (e.message || '未知错误'))
  }
}

// ===== 对话 =====
function openChat(agent) {
  currentAgent.value = agent
  chatInput.value = ''
  chatResponse.value = ''
  chatModel.value = 'mimo-v2.5-pro'
  chatVisible.value = true
}

async function sendChat() {
  if (!chatInput.value) return
  chatLoading.value = true
  try {
    const agentName = currentAgent.value.code === 'SUPERVISOR' ? 'supervisorAgent'
      : currentAgent.value.code === 'RESEARCHER' ? 'researcherAgent'
      : currentAgent.value.code === 'WRITER' ? 'writerAgent'
      : currentAgent.value.code === 'REVIEWER' ? 'reviewerAgent'
      : currentAgent.value.code === 'POLISHER' ? 'polisherAgent'
      : currentAgent.value.code
    const res = await chatWithAgent(agentName, '测试主题', chatInput.value, chatModel.value)
    chatResponse.value = res.data || '无响应'
  } catch (e) {
    chatResponse.value = '请求失败: ' + e.message
  } finally { chatLoading.value = false }
}

onMounted(() => { loadCustom() })
</script>
<style scoped>
.agent-section-title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
  margin-bottom: 4px;
}
.agent-card {
  background: rgba(15,20,38,0.6) !important;
  border: 1px solid rgba(255,255,255,0.06) !important;
  border-radius: 12px !important;
  transition: all 0.3s;
}
.agent-card:hover {
  border-color: rgba(255,255,255,0.15) !important;
  box-shadow: 0 4px 24px rgba(0,0,0,0.3);
}
.agent-desc {
  color: rgba(255,255,255,0.5);
  min-height: 40px;
  line-height: 1.7;
  font-size: 13px;
}
.agent-prompt-preview {
  font-size: 11px;
  color: rgba(255,255,255,0.35);
  background: rgba(0,0,0,0.2);
  padding: 6px 10px;
  border-radius: 4px;
  max-height: 32px;
  overflow: hidden;
  font-family: monospace;
}
.custom-agent-card {
  border-color: rgba(139,92,246,0.15) !important;
}
.empty-card {
  border-style: dashed !important;
  border-color: rgba(255,255,255,0.08) !important;
}
.chat-response {
  background: rgba(0,0,0,0.2);
  padding: 14px;
  border-radius: 8px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  border: 1px solid rgba(255,255,255,0.05);
}
</style>
