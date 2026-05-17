<template>
  <div class="markdown-render" ref="containerRef" v-html="renderedHtml" />
</template>

<script setup>
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import katex from 'katex'
import markdownItKatex from 'markdown-it-katex'
import 'highlight.js/styles/github-dark.css'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' }
})

const containerRef = ref(null)

/** 将标题文本转为稳定的 anchor id */
function slugify(text) {
  return text
    .toLowerCase()
    .replace(/['']/g, '')         // 去掉引号
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .replace(/^(\d)/, 'h-$1')    // 数字开头的加前缀
    || 'heading'
}

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch (_) { /* fall through */ }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})

// ===== 给所有 <h1>~<h6> 自动注入 id（用于目录定位） =====
const headingIds = new Set()
const defaultHeadingRender = md.renderer.rules.heading_open || function(tokens, idx, options, env, self) {
  return self.renderToken(tokens, idx, options)
}

md.renderer.rules.heading_open = function(tokens, idx, options, env, self) {
  const token = tokens[idx]
  // 找到对应的 inline token 获取标题文本
  const nextToken = tokens[idx + 1]
  const text = nextToken ? nextToken.content : ''
  let id = slugify(text)
  // 处理重复 id
  if (headingIds.has(id)) {
    let counter = 2
    while (headingIds.has(`${id}-${counter}`)) counter++
    id = `${id}-${counter}`
  }
  headingIds.add(id)
  token.attrSet('id', id)
  return defaultHeadingRender(tokens, idx, options, env, self)
}

// 用 markdown-it-katex 插件处理 $...$ 和 $$...$$
md.use(markdownItKatex, {
  throwOnError: false,
  errorColor: '#cc0000'
})

/**
 * 核心：在 markdown-it 解析前把复杂公式用占位符保护起来
 * 防止 markdown-it 把公式中的换行、空格、特殊字符全部搅乱
 */
function protectFormulas(text) {
  const placeholders = []
  let idx = 0

  const replaceMap = [
    // 块级公式: $$...$$ 或 \[...\] — 可跨多行
    { regex: /\$\$([\s\S]*?)\$\$/g, block: true },
    { regex: /\\\[([\s\S]*?)\\\]/g, block: true },
    // 行内公式: $...$ 或 \(...\) — 不跨行
    { regex: /\\\((.+?)\\\)/g, block: false },
    { regex: /\$(.+?)\$/g, block: false }
  ]

  for (const { regex, block } of replaceMap) {
    text = text.replace(regex, (_, formula) => {
      const key = block ? `⟨⟨FORMULA_BLOCK_${idx++}⟩⟩` : `⟨⟨FORMULA_INLINE_${idx++}⟩⟩`
      placeholders.push({ key, formula: formula.trim(), block })
      return key
    })
  }

  return { text, placeholders }
}

function restoreFormulas(html, placeholders) {
  for (const { key, formula, block } of placeholders) {
    try {
      const rendered = katex.renderToString(formula, { throwOnError: false, displayMode: block })
      html = html.replace(key, rendered)
    } catch {
      html = html.replace(key, `<span class="katex-error">公式解析错误: ${md.utils.escapeHtml(formula)}</span>`)
    }
  }
  return html
}

// ===== Mermaid 图表支持 =====
let mermaidModule = null
async function loadMermaid() {
  if (mermaidModule) return mermaidModule
  try {
    const mod = await import('mermaid')
    mermaidModule = mod.default
    mermaidModule.initialize({ startOnLoad: false, theme: 'dark', securityLevel: 'loose' })
    return mermaidModule
  } catch { return null }
}

async function renderMermaidBlocks(container) {
  if (!container) return
  const mermaidBlocks = container.querySelectorAll('pre code.language-mermaid')
  if (!mermaidBlocks.length) return
  const mermaid = await loadMermaid()
  if (!mermaid) {
    for (const b of mermaidBlocks) b.parentElement.insertAdjacentHTML('beforebegin', '<div style="color:#faad14;padding:8px;font-size:12px">无法加载 Mermaid 库，请刷新页面</div>')
    return
  }
  for (const block of mermaidBlocks) {
    const code = block.textContent.trim()
    if (!code) continue
    const id = 'm-' + Math.random().toString(36).slice(2, 9)
    try {
      const { svg } = await mermaid.render(id, code)
      const wrapper = document.createElement('div')
      wrapper.className = 'mermaid-chart'
      wrapper.innerHTML = svg
      block.parentElement.replaceWith(wrapper)
    } catch (e) {
      block.parentElement.insertAdjacentHTML('beforebegin',
        '<div style="color:#ff4d4f;padding:8px;font-size:12px;border:1px solid rgba(255,77,79,0.3);border-radius:4px;margin:8px 0">Mermaid 语法错误: ' + (e.message || '未知').slice(0, 100) + '</div>')
    }
  }
}

// ===== Chart 图表支持（ECharts JSON 配置） =====
let echartsModule = null
async function loadEcharts() {
  if (echartsModule) return echartsModule
  try {
    const mod = await import('echarts')
    echartsModule = mod
    return echartsModule
  } catch { return null }
}

async function renderChartBlocks(container) {
  if (!container) return
  const chartBlocks = container.querySelectorAll('pre code.language-chart, pre code.language-chart-json')
  if (!chartBlocks.length) return
  await loadEcharts()
  if (!echartsModule) {
    for (const b of chartBlocks) b.parentElement.insertAdjacentHTML('beforebegin', '<div style="color:#faad14;padding:8px;font-size:12px">无法加载 ECharts 库，请刷新页面</div>')
    return
  }
  for (const block of chartBlocks) {
    const code = block.textContent.trim()
    if (!code) continue
    try {
      const option = JSON.parse(code)
      const wrapper = document.createElement('div')
      wrapper.className = 'echarts-chart'
      wrapper.style.width = '100%'
      wrapper.style.height = '400px'
      wrapper.style.margin = '12px 0'
      block.parentElement.replaceWith(wrapper)
      const chart = echartsModule.init(wrapper)
      chart.setOption(option)
      const ro = new ResizeObserver(() => { try { chart.resize() } catch(_){} })
      ro.observe(wrapper)
    } catch (e) {
      block.parentElement.insertAdjacentHTML('beforebegin',
        '<div style="color:#ff4d4f;padding:8px;font-size:12px;border:1px solid rgba(255,77,79,0.3);border-radius:4px;margin:8px 0">Chart JSON 解析失败: ' + (e.message || '未知').slice(0, 100) + '</div>')
    }
  }
}

async function renderDiagrams() {
  // v-html 可能在 nextTick 后还未完全渲染，加 requestAnimationFrame 确保 DOM ready
  await nextTick()
  await new Promise(r => requestAnimationFrame(r))
  await new Promise(r => setTimeout(r, 50))
  const container = containerRef.value
  if (container) {
    await renderMermaidBlocks(container)
    await renderChartBlocks(container)
  }
}

const renderedHtml = computed(() => {
  headingIds.clear()
  const { text, placeholders } = protectFormulas(props.content || '')
  let html = md.render(text)
  html = restoreFormulas(html, placeholders)
  return html
})

let renderTimer = null
watch(() => props.content, () => {
  clearTimeout(renderTimer)
  renderTimer = setTimeout(() => renderDiagrams(), 100)
})
onMounted(() => { renderDiagrams() })
</script>

<style scoped>
.markdown-render {
  line-height: 1.8;
  font-size: 14px;
  color: rgba(255,255,255,0.85);
  word-wrap: break-word;
  overflow-wrap: break-word;
}
.markdown-render :deep(h1) { font-size: 24px; margin: 24px 0 12px; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 8px; color: rgba(255,255,255,0.9); }
.markdown-render :deep(h2) { font-size: 20px; margin: 20px 0 10px; border-bottom: 1px solid rgba(255,255,255,0.06); padding-bottom: 6px; color: rgba(255,255,255,0.85); }
.markdown-render :deep(h3) { font-size: 16px; margin: 16px 0 8px; color: rgba(255,255,255,0.8); }
.markdown-render :deep(p) { margin: 10px 0; }
.markdown-render :deep(ul), .markdown-render :deep(ol) { padding-left: 24px; margin: 10px 0; }
.markdown-render :deep(li) { margin: 4px 0; }
.markdown-render :deep(strong) { font-weight: 600; color: rgba(255,255,255,0.9); }
.markdown-render :deep(em) { font-style: italic; }
.markdown-render :deep(code) {
  background: rgba(255,255,255,0.06);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.9em;
  color: #b0d0ff;
}
.markdown-render :deep(pre) {
  background: rgba(0,0,0,0.3);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  border: 1px solid rgba(255,255,255,0.06);
}
.markdown-render :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255,255,255,0.8);
}
.markdown-render :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}
.markdown-render :deep(th), .markdown-render :deep(td) {
  border: 1px solid rgba(255,255,255,0.1);
  padding: 8px 14px;
  text-align: left;
}
.markdown-render :deep(th) {
  background: rgba(255,255,255,0.04);
  color: rgba(255,255,255,0.7);
  font-weight: 600;
}
.markdown-render :deep(tr:nth-child(even)) {
  background: rgba(255,255,255,0.02);
}
.markdown-render :deep(blockquote) {
  border-left: 3px solid rgba(24,144,255,0.5);
  padding: 8px 16px;
  margin: 12px 0;
  background: rgba(24,144,255,0.05);
  color: rgba(255,255,255,0.6);
}
.markdown-render :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255,255,255,0.06);
  margin: 20px 0;
}
.markdown-render :deep(.mermaid-chart) {
  display: flex;
  justify-content: center;
  margin: 16px 0;
  padding: 12px;
  background: rgba(255,255,255,0.02);
  border-radius: 8px;
  overflow-x: auto;
}
.markdown-render :deep(.mermaid-chart svg) {
  max-width: 100%;
}
.markdown-render :deep(.echarts-chart) {
  border-radius: 8px;
  background: rgba(0,0,0,0.15);
  padding: 8px;
}
</style>
