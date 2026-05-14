<template>
  <div class="agent-list">
    <a-row :gutter="[16, 16]">
      <a-col :span="8" v-for="agent in agents" :key="agent.code">
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

    <a-modal
      v-model:open="chatVisible"
      :title="`与 ${currentAgent?.displayName} 对话`"
      @ok="sendChat"
      :confirmLoading="chatLoading"
    >
      <a-input v-model:value="chatInput" placeholder="输入你想问的问题..." :disabled="chatLoading" />
      <a-divider />
      <div class="chat-response" v-if="chatResponse">
        <strong>回复：</strong>
        <div class="markdown-body">{{ chatResponse }}</div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { chatWithAgent } from '@/api'
import { message } from 'ant-design-vue'
import { MessageOutlined } from '@ant-design/icons-vue'

const agents = [
  { code: 'SUPERVISOR', displayName: '导师 Agent', icon: '🧭', description: '把控研究方向，审阅大纲，给出修改意见', temperature: 0.5, tagColor: 'purple' },
  { code: 'RESEARCHER', displayName: '研究员 Agent', icon: '🔬', description: '文献调研，信息收集，综述撰写', temperature: 0.3, tagColor: 'blue' },
  { code: 'WRITER', displayName: '写手 Agent', icon: '✍️', description: '撰写论文各章节，组织语言', temperature: 0.7, tagColor: 'green' },
  { code: 'REVIEWER', displayName: '审稿人 Agent', icon: '📝', description: '批判性审阅，找漏洞，提改进意见', temperature: 0.6, tagColor: 'orange' },
  { code: 'POLISHER', displayName: '润色 Agent', icon: '✨', description: '语法校对，格式规范，引用检查', temperature: 0.4, tagColor: 'cyan' }
]

const chatVisible = ref(false)
const chatLoading = ref(false)
const currentAgent = ref(null)
const chatInput = ref('')
const chatResponse = ref('')

function openChat(agent) {
  currentAgent.value = agent; chatInput.value = ''; chatResponse.value = ''; chatVisible.value = true
}

async function sendChat() {
  if (!chatInput.value) return
  chatLoading.value = true
  try {
    const agentName = currentAgent.value.code.toLowerCase() + 'Agent'
    const res = await chatWithAgent(agentName, '测试主题', chatInput.value)
    chatResponse.value = res.data || '无响应'
  } catch (e) {
    chatResponse.value = '请求失败: ' + e.message
  } finally { chatLoading.value = false }
}
</script>

<style scoped>
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
