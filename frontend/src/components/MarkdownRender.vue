<template>
  <div class="markdown-render" v-html="renderedHtml" />
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import katex from 'katex'
import markdownItKatex from 'markdown-it-katex'
import 'highlight.js/styles/github.css'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' }
})

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

const renderedHtml = computed(() => {
  const { text, placeholders } = protectFormulas(props.content || '')
  let html = md.render(text)
  html = restoreFormulas(html, placeholders)
  return html
})
</script>

<style scoped>
.markdown-render {
  line-height: 1.8;
  font-size: 14px;
  color: #333;
  word-wrap: break-word;
  overflow-wrap: break-word;
}
.markdown-render :deep(h1) { font-size: 24px; margin: 20px 0 10px; border-bottom: 2px solid #eee; padding-bottom: 6px; }
.markdown-render :deep(h2) { font-size: 20px; margin: 18px 0 8px; border-bottom: 1px solid #eee; padding-bottom: 4px; }
.markdown-render :deep(h3) { font-size: 16px; margin: 14px 0 6px; }
.markdown-render :deep(p) { margin: 8px 0; }
.markdown-render :deep(ul), .markdown-render :deep(ol) { padding-left: 24px; margin: 8px 0; }
.markdown-render :deep(li) { margin: 4px 0; }
.markdown-render :deep(strong) { font-weight: 600; }
.markdown-render :deep(em) { font-style: italic; }
.markdown-render :deep(code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}
.markdown-render :deep(pre) {
  background: #f6f8fa;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
}
.markdown-render :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 13px;
  line-height: 1.5;
}
/* 表格样式 */
.markdown-render :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}
.markdown-render :deep(th), .markdown-render :deep(td) {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: left;
}
.markdown-render :deep(th) {
  background: #f5f5f5;
  font-weight: 600;
}
.markdown-render :deep(tr:nth-child(even)) {
  background: #fafafa;
}
/* 引用块 */
.markdown-render :deep(blockquote) {
  border-left: 4px solid #1890ff;
  padding: 8px 16px;
  margin: 12px 0;
  background: #f8f9ff;
  color: #555;
}
/* 水平线 */
.markdown-render :deep(hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 20px 0;
}
</style>
