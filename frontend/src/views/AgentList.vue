<template>
  <div class="agent-list">
    <a-card title="🤖 Agent 角色管理" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :span="8" v-for="agent in agents" :key="agent.code">
          <a-card :title="agent.displayName" :bordered="false" hoverable>
            <template #extra>
              <a-tag>{{ agent.code }}</a-tag>
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
    </a-card>

    <!-- 测试对话弹窗 -->
    <a-modal
      v-model:open="chatVisible"
      :title="`与 ${currentAgent?.displayName} 对话`"
      @ok="sendChat"
      :confirmLoading="chatLoading"
    >
      <a-input
        v-model:value="chatInput"
        placeholder="输入你想问的问题..."
        :disabled="chatLoading"
      />
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
  { code: 'SUPERVISOR', displayName: '👨‍🏫 导师 Agent', description: '把控研究方向，审阅大纲，给出修改意见', temperature: 0.5 },
  { code: 'RESEARCHER', displayName: '🔍 研究员 Agent', description: '文献调研，信息收集，综述撰写', temperature: 0.3 },
  { code: 'WRITER', displayName: '✍️ 写手 Agent', description: '撰写论文各章节，组织语言', temperature: 0.7 },
  { code: 'REVIEWER', displayName: '🔎 审稿人 Agent', description: '批判性审阅，找漏洞，提改进意见', temperature: 0.6 },
  { code: 'POLISHER', displayName: '✨ 润色 Agent', description: '语法校对，格式规范，引用检查', temperature: 0.4 }
]

const chatVisible = ref(false)
const chatLoading = ref(false)
const currentAgent = ref(null)
const chatInput = ref('')
const chatResponse = ref('')

function openChat(agent) {
  currentAgent.value = agent
  chatInput.value = ''
  chatResponse.value = ''
  chatVisible.value = true
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
  } finally {
    chatLoading.value = false
  }
}
</script>

<style scoped>
.agent-desc {
  color: #666;
  min-height: 40px;
  line-height: 1.6;
}
.chat-response {
  background: #f9f9f9;
  padding: 12px;
  border-radius: 6px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
