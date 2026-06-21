<template>
  <div class="chat-msg" :class="['msg-' + message.role]">
    <!-- 用户消息 -->
    <template v-if="message.role === 'user'">
      <div class="msg-avatar user-avatar">
        <span>👤</span>
      </div>
      <div class="msg-body">
        <div class="msg-content user-content">{{ message.content }}</div>
      </div>
    </template>

    <!-- AI 消息 -->
    <template v-else-if="message.role === 'agent'">
      <div class="msg-avatar ai-avatar">
        <span>🤖</span>
      </div>
      <div class="msg-body">
        <div class="msg-label">PaperAI</div>

        <!-- 思考过程（可折叠） -->
        <div v-if="message.thinking" class="thinking-block">
          <div class="thinking-header" @click="toggleThinking">
            <span class="thinking-icon">🧠</span>
            <span class="thinking-title">思考过程</span>
            <span class="thinking-arrow" :class="{ expanded: thinkingExpanded }">›</span>
          </div>
          <div v-if="thinkingExpanded" class="thinking-content">
            <MarkdownRender :content="message.thinking" />
          </div>
        </div>

        <!-- 正式回复 -->
        <div class="msg-content ai-content">
          <div v-if="message.loading" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
          <MarkdownRender v-else :content="message.content || '...'" />
        </div>
      </div>
    </template>

    <!-- 系统消息 -->
    <template v-else-if="message.role === 'system'">
      <div class="system-msg">
        <span>{{ message.content }}</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MarkdownRender from '@/components/MarkdownRender.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const thinkingExpanded = ref(false)

function toggleThinking() {
  thinkingExpanded.value = !thinkingExpanded.value
}
</script>

<style scoped>
.chat-msg {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== 头像 ===== */
.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.user-avatar {
  background: linear-gradient(135deg, #597ef7, #36cfc9);
}

.ai-avatar {
  background: linear-gradient(135deg, #9254de, #597ef7);
}

/* ===== 消息体 ===== */
.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  margin-bottom: 6px;
  font-weight: 500;
}

.msg-content {
  line-height: 1.7;
  font-size: 14px;
}

.user-content {
  background: rgba(146, 84, 222, 0.12);
  border: 1px solid rgba(146, 84, 222, 0.2);
  padding: 12px 16px;
  border-radius: 12px;
  border-top-left-radius: 4px;
  color: rgba(255, 255, 255, 0.9);
  display: inline-block;
  max-width: 85%;
}

.ai-content {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  padding: 16px 20px;
  border-radius: 12px;
  border-top-left-radius: 4px;
  color: rgba(255, 255, 255, 0.85);
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

.thinking-header:hover {
  background: rgba(146, 84, 222, 0.08);
}

.thinking-icon {
  font-size: 16px;
}

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

.thinking-arrow.expanded {
  transform: rotate(90deg);
}

.thinking-content {
  padding: 12px 14px;
  border-top: 1px solid rgba(146, 84, 222, 0.08);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.7;
  max-height: 400px;
  overflow-y: auto;
}

.thinking-content :deep(p) {
  margin: 4px 0;
  color: rgba(255, 255, 255, 0.55);
}

.thinking-content :deep(code) {
  background: rgba(146, 84, 222, 0.1);
  color: #d4b8ff;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 0.88em;
}

.thinking-content :deep(pre) {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  padding: 10px;
  margin: 6px 0;
}

.thinking-content :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 12px;
}

/* ===== 系统消息 ===== */
.system-msg {
  width: 100%;
  text-align: center;
  padding: 8px 0;
}

.system-msg span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.03);
  padding: 4px 14px;
  border-radius: 20px;
}

/* ===== 打字动画 ===== */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  animation: bounce 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ===== AI 内容样式 ===== */
.ai-content :deep(h1),
.ai-content :deep(h2),
.ai-content :deep(h3) {
  color: rgba(255, 255, 255, 0.9);
  margin: 16px 0 8px;
}

.ai-content :deep(h1) { font-size: 20px; }
.ai-content :deep(h2) { font-size: 17px; }
.ai-content :deep(h3) { font-size: 15px; }

.ai-content :deep(p) {
  margin: 8px 0;
  color: rgba(255, 255, 255, 0.8);
}

.ai-content :deep(code) {
  background: rgba(146, 84, 222, 0.1);
  color: #d4b8ff;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}

.ai-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 14px;
  margin: 12px 0;
}

.ai-content :deep(pre code) {
  background: none;
  padding: 0;
  color: #c8d0e0;
  font-size: 12px;
}

.ai-content :deep(ul),
.ai-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.ai-content :deep(li) {
  margin: 4px 0;
  color: rgba(255, 255, 255, 0.8);
}

.ai-content :deep(blockquote) {
  border-left: 3px solid #9254de;
  padding: 8px 14px;
  margin: 12px 0;
  background: rgba(146, 84, 222, 0.04);
  border-radius: 0 4px 4px 0;
  color: rgba(255, 255, 255, 0.7);
}

.ai-content :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}

.ai-content :deep(th),
.ai-content :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 6px 12px;
}

.ai-content :deep(th) {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.7);
}
</style>
