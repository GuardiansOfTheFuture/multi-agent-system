<template>
  <div class="chat-input-wrapper">
    <div class="chat-input-container">
      <div class="input-box" :class="{ focused: isFocused }">
        <textarea
          ref="textareaRef"
          v-model="localValue"
          :placeholder="loading ? 'AI 正在写作中...' : '输入论文主题或写作需求...'"
          :disabled="disabled"
          @focus="isFocused = true"
          @blur="isFocused = false"
          @keydown.enter.exact.prevent="handleSend"
          @input="autoResize"
          rows="1"
        />
        <div class="input-actions">
          <button
            v-if="loading"
            class="stop-btn"
            @click="$emit('stop')"
            title="停止写作"
          >
            <span class="stop-icon">■</span>
          </button>
          <button
            v-else
            class="send-btn"
            :disabled="!localValue.trim() || disabled"
            @click="handleSend"
            title="发送"
          >
            <span class="send-icon">↑</span>
          </button>
        </div>
      </div>
      <div class="input-hint">
        <span v-if="loading" class="hint-loading">
          <span class="dot-pulse"></span>
          AI 正在执行中...
        </span>
        <span v-else class="hint-text">
          Enter 发送 · Shift+Enter 换行
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'send', 'stop'])

const localValue = ref(props.modelValue)
const isFocused = ref(false)
const textareaRef = ref(null)

watch(() => props.modelValue, (val) => {
  localValue.value = val
})

watch(localValue, (val) => {
  emit('update:modelValue', val)
})

function handleSend() {
  if (localValue.value.trim() && !props.disabled) {
    emit('send', localValue.value)
    localValue.value = ''
    nextTick(() => autoResize())
  }
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}
</script>

<style scoped>
.chat-input-wrapper {
  padding: 0 24px 20px;
  background: linear-gradient(to top, #0a0e17 60%, transparent);
}

.chat-input-container {
  max-width: 800px;
  margin: 0 auto;
}

/* ===== 输入框 ===== */
.input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 12px 12px 12px 18px;
  transition: all 0.2s;
}

.input-box.focused {
  border-color: rgba(146, 84, 222, 0.4);
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 3px rgba(146, 84, 222, 0.1);
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
  max-height: 200px;
  font-family: inherit;
}

.input-box textarea::placeholder {
  color: rgba(255, 255, 255, 0.25);
}

.input-box textarea:disabled {
  opacity: 0.5;
}

/* ===== 按钮 ===== */
.input-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.send-btn,
.stop-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.send-btn {
  background: linear-gradient(135deg, #9254de, #597ef7);
  color: white;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(146, 84, 222, 0.4);
}

.send-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.stop-btn {
  background: rgba(255, 77, 79, 0.2);
  color: #ff4d4f;
  border: 1px solid rgba(255, 77, 79, 0.3);
}

.stop-btn:hover {
  background: rgba(255, 77, 79, 0.3);
}

.send-icon {
  font-size: 18px;
  font-weight: bold;
  line-height: 1;
}

.stop-icon {
  font-size: 12px;
  font-weight: bold;
}

/* ===== 提示 ===== */
.input-hint {
  text-align: center;
  padding: 8px 0 0;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
}

.hint-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: rgba(146, 84, 222, 0.6);
}

.dot-pulse {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(146, 84, 222, 0.6);
  animation: pulse 1.4s infinite ease-in-out;
}

@keyframes pulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}
</style>
