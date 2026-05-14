<template>
  <div class="markdown-render" v-html="renderedHtml" />
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import katex from 'katex'
import markdownItKatex from 'markdown-it-katex'
import 'highlight.js/styles/github-dark.css'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' }
})

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

const renderedHtml = computed(() => {
  // 每次渲染前清空 heading id 集合
  headingIds.clear()
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
</style>
