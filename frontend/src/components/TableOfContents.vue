<template>
  <div class="toc-container">
    <div class="toc-header">
      <span class="toc-title">📑 目录</span>
      <a-tag v-if="items.length" class="toc-count">{{ items.length }} 节</a-tag>
    </div>
    <a-divider style="margin: 8px 0" />
    <div class="toc-list" v-if="items.length">
      <div
        v-for="(item, index) in items"
        :key="index"
        class="toc-item"
        :class="[
          `toc-level-${item.level}`,
          { 'toc-active': activeId === item.id }
        ]"
        @click="scrollToHeading(item.id)"
      >
        <a class="toc-link" :title="item.text">
          <span class="toc-bullet" :style="{ background: levelColor(item.level) }" />
          {{ item.text }}
        </a>
      </div>
    </div>
    <div v-else class="toc-empty">
      <a-empty description="暂无目录" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps({
  content: { type: String, default: '' },
  /** 滚动容器的 CSS 选择器或 DOM 元素，默认为 window */
  scrollContainer: { type: [String, Object], default: null }
})

const emit = defineEmits(['heading-click'])

const items = ref([])
const activeId = ref('')

/** 获取滚动容器 */
function getScrollContainer() {
  if (!props.scrollContainer) return window
  if (typeof props.scrollContainer === 'string') {
    return document.querySelector(props.scrollContainer)
  }
  return props.scrollContainer
}

/** 与 MarkdownRender 一致的 slugify 算法 */
function slugify(text) {
  return text
    .toLowerCase()
    .replace(/['']/g, '')
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .replace(/^(\d)/, 'h-$1')
    || 'heading'
}

/** 解析 Markdown 标题，生成目录项（id 必须与 MarkdownRender 一致） */
function parseTOC(content) {
  if (!content) { items.value = []; return }
  const lines = content.split('\n')
  const result = []
  // 处理重复 id
  const usedIds = new Set()

  for (const line of lines) {
    const match = line.match(/^(#{1,6})\s+(.+)$/)
    if (match) {
      const level = match[1].length
      const text = match[2].trim()
      // 跳过纯数字/符号标题
      if (!text || /^[\d\s.]+$/.test(text)) continue

      // 使用与 MarkdownRender 一致的 slugify 算法
      let id = slugify(text)
      if (usedIds.has(id)) {
        let counter = 2
        while (usedIds.has(`${id}-${counter}`)) counter++
        id = `${id}-${counter}`
      }
      usedIds.add(id)

      result.push({ id, level, text })
    }
  }
  items.value = result
}

/** 滚动到指定标题 */
function scrollToHeading(id) {
  const el = document.getElementById(id)
  if (!el) return
  const container = getScrollContainer()
  const offset = 80

  if (container === window || container === document.documentElement || container === document.body) {
    // 页面级滚动
    const top = el.getBoundingClientRect().top + window.scrollY - offset
    window.scrollTo({ top, behavior: 'smooth' })
  } else {
    // 容器内滚动
    const containerRect = container.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const top = elRect.top - containerRect.top + container.scrollTop - offset
    container.scrollTo({ top, behavior: 'smooth' })
  }
  activeId.value = id
  emit('heading-click', id)
}

/** 监听滚动，高亮当前章节 */
let observer = null
function setupScrollObserver() {
  // 先清理旧的 observer
  if (observer) observer.disconnect()

  const headingElements = items.value
    .map(item => document.getElementById(item.id))
    .filter(Boolean)

  if (!headingElements.length) return

  const container = getScrollContainer()
  const root = container === window ? null : container

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeId.value = entry.target.id
        }
      }
    },
    { root, rootMargin: '-60px 0px -60% 0px', threshold: 0.1 }
  )

  headingElements.forEach(el => observer.observe(el))
}

/** 不同层级的颜色 */
function levelColor(level) {
  const colors = ['#1890ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2']
  return colors[Math.min(level - 1, 5)]
}

watch(() => props.content, () => {
  parseTOC(props.content)
  nextTick(() => setupScrollObserver())
})

onMounted(() => {
  parseTOC(props.content)
  nextTick(() => setupScrollObserver())
})

onUnmounted(() => {
  if (observer) observer.disconnect()
})
</script>

<style scoped>
.toc-container {
  background: transparent;
  border-radius: 8px;
  padding: 12px 0;
}
.toc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}
.toc-title {
  font-size: 15px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}
.toc-count {
  font-size: 11px;
}
.toc-list {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
  padding: 0 8px;
}
.toc-list::-webkit-scrollbar {
  width: 4px;
}
.toc-list::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,0.1);
  border-radius: 2px;
}
.toc-item {
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}
.toc-item:hover {
  background: rgba(146,84,222,0.06);
}
.toc-item.toc-active {
  background: rgba(146,84,222,0.1);
  border-right: 2px solid #9254de;
}
.toc-link {
  color: rgba(255,255,255,0.55);
  font-size: 13px;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 6px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s;
}
.toc-active .toc-link {
  color: #b37feb;
  font-weight: 500;
}
.toc-bullet {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.toc-level-2 { padding-left: 8px; }
.toc-level-3 { padding-left: 20px; }
.toc-level-3 .toc-link { font-size: 12.5px; }
.toc-level-4 { padding-left: 32px; }
.toc-level-4 .toc-link { font-size: 12px; }
.toc-level-5 { padding-left: 44px; }
.toc-level-5 .toc-link { font-size: 11.5px; }
.toc-level-6 { padding-left: 56px; }
.toc-level-6 .toc-link { font-size: 11px; }
.toc-empty {
  padding: 24px 16px;
}
</style>
