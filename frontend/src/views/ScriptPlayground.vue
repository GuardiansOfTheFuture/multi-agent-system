<template>
  <div class="playground">
    <header class="pg-header">
      <span class="pg-logo">🔧 脚本解析器</span>
      <span class="pg-desc">输入 Mermaid 流程图 / ECharts 图表脚本，实时预览并导出图片</span>
      <span style="flex:1" />
      <a-space :size="8">
        <a-button size="small" @click="runScript" :loading="running">
          <play-circle-outlined /> 运行
        </a-button>
        <a-button size="small" @click="exportPNG" :disabled="!previewReady">
          <download-outlined /> 导出 PNG
        </a-button>
        <a-divider type="vertical" style="border-color:rgba(255,255,255,0.1);height:18px;margin:0 4px" />
        <a-button size="small" @click="zoomOut" :disabled="zoom <= 0.25">
          <minus-outlined />
        </a-button>
        <span style="font-size:12px;color:rgba(255,255,255,0.6);min-width:42px;text-align:center;font-variant-numeric:tabular-nums">{{ Math.round(zoom * 100) }}%</span>
        <a-button size="small" @click="zoomIn" :disabled="zoom >= 3">
          <plus-outlined />
        </a-button>
        <a-button size="small" @click="zoomReset" :disabled="zoom === 1">1:1</a-button>
        <a-button size="small" @click="clear">
          <delete-outlined /> 清空
        </a-button>
      </a-space>
    </header>

    <div class="pg-body">
      <!-- 左侧：脚本输入 + 模板 -->
      <aside class="pg-left">
        <a-tabs v-model:activeKey="activeTab" size="small">
          <a-tab-pane key="mermaid" tab="Mermaid 流程图" />
          <a-tab-pane key="echarts" tab="ECharts 图表" />
        </a-tabs>

        <div class="pg-templates">
          <span class="pg-tmpl-label">模板：</span>
          <a-button
            v-for="t in currentTemplates"
            :key="t.label"
            size="small"
            type="dashed"
            @click="script = t.code"
            style="margin:2px"
          >{{ t.label }}</a-button>
        </div>

        <a-textarea
          v-model:value="script"
          :placeholder="activeTab === 'mermaid' ? mermaidPlaceholder : echartsPlaceholder"
          class="pg-editor"
          :rows="14"
          spellcheck="false"
          @keydown.ctrl.enter="runScript"
        />
        <div class="pg-hint">Ctrl+Enter 运行</div>
      </aside>

      <!-- 右侧：预览 -->
      <main class="pg-right">
        <div class="pg-preview-label">
          预览
          <a-tag v-if="errorMsg" color="red" size="small">{{ errorMsg }}</a-tag>
          <a-tag v-else-if="previewReady" color="green" size="small">就绪</a-tag>
        </div>
        <div class="pg-preview" ref="previewRef"
          @wheel.prevent="handleWheel"
          @mousedown.prevent="handleMouseDown"
          @mousemove="handleMouseMove"
          @mouseup="handleMouseUp"
          @mouseleave="handleMouseUp"
        >
          <div class="pg-preview-zoom" :style="{ transform: `scale(${zoom})`, transformOrigin: transformOrigin }">
            <!-- Mermaid SVG -->
            <div v-if="activeTab === 'mermaid' && previewReady" v-html="mermaidSvg" ref="mermaidSvgRef" class="pg-mermaid-wrap" />
            <!-- ECharts Canvas -->
            <div v-if="activeTab === 'echarts'" ref="chartRef" class="pg-chart-wrap" :style="{ width: chartWidth + 'px', height: chartHeight + 'px' }" />
          </div>
          <!-- 初始提示 -->
          <div v-if="!script.trim() && !previewReady" class="pg-empty">
            <div class="pg-empty-icon">🛠️</div>
            <div>选择模板或输入脚本，点击运行</div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlayCircleOutlined, DownloadOutlined, DeleteOutlined, PlusOutlined, MinusOutlined } from '@ant-design/icons-vue'

const activeTab = ref('mermaid')
const script = ref('')
const previewReady = ref(false)
const errorMsg = ref('')
const mermaidSvg = ref('')
const chartHeight = ref(500)
const chartWidth = ref(700)

const previewRef = ref(null)
const mermaidSvgRef = ref(null)
const chartRef = ref(null)
let chartInstance = null

// ===== 缩放 =====
const zoom = ref(1)
const transformOrigin = ref('center center')
let isPanning = false
let panStart = { x: 0, y: 0 }

function zoomIn() { zoom.value = Math.min(3, +(zoom.value + 0.1).toFixed(1)) }
function zoomOut() { zoom.value = Math.max(0.25, +(zoom.value - 0.1).toFixed(1)) }
function zoomReset() { zoom.value = 1 }

function handleWheel(e) {
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    const delta = e.deltaY < 0 ? 0.1 : -0.1
    zoom.value = Math.max(0.25, Math.min(3, +(zoom.value + delta).toFixed(1)))
  }
}
function handleMouseDown(e) {
  if (e.button === 1 || (e.button === 0 && e.altKey)) {  // 中键 或 Alt+左键
    isPanning = true
    panStart = { x: e.clientX, y: e.clientY }
  }
}
function handleMouseMove(e) {
  if (!isPanning) return
  const dx = e.clientX - panStart.x
  const dy = e.clientY - panStart.y
  previewRef.value.scrollLeft -= dx
  previewRef.value.scrollTop -= dy
  panStart = { x: e.clientX, y: e.clientY }
}
function handleMouseUp() { isPanning = false }

// ===== 模板 =====
const mermaidTemplates = [
  { label: '流程图', code: 'graph TD\n  A[开始] --> B{条件?}\n  B -->|是| C[处理]\n  B -->|否| D[结束]\n  C --> D' },
  { label: 'GNN架构', code: 'graph LR\n  Input[输入特征] --> GCN[GCN层]\n  GCN --> GAT[GAT注意力]\n  GAT --> Pool[图池化]\n  Pool --> Output[分类结果]' },
  { label: '实验流程', code: 'graph TD\n  Data[数据集划分] --> Train[训练]\n  Data --> Val[验证]\n  Data --> Test[测试]\n  Train --> Eval[评估]\n  Val --> Eval\n  Test --> Eval\n  Eval --> Result[结果分析]' },
  { label: '时序图', code: 'sequenceDiagram\n  participant U as 用户\n  participant A as Agent\n  participant L as LLM\n  U->>A: 提交主题\n  A->>L: 调用模型\n  L-->>A: 返回内容\n  A-->>U: 展示结果' },
  { label: '类图', code: 'classDiagram\n  class Paper {\n    +String title\n    +String abstract\n    +publish()\n  }\n  class Agent {\n    +String role\n    +execute()\n  }\n  Paper <|-- Agent' }
]

const echartsTemplates = [
  { label: '柱状图', code: '{"title":{"text":"模型准确率对比"},"tooltip":{},"xAxis":{"data":["GCN","GAT","GraphSAGE"]},"yAxis":{},"series":[{"type":"bar","data":[81.5,83.7,79.2]}]}' },
  { label: '折线图', code: '{"title":{"text":"训练Loss曲线"},"xAxis":{"data":["1","5","10","20","50","100"]},"yAxis":{},"series":[{"type":"line","data":[0.8,0.5,0.3,0.15,0.08,0.03],"smooth":true}]}' },
  { label: '饼图', code: '{"title":{"text":"方法使用分布"},"tooltip":{"trigger":"item"},"series":[{"type":"pie","data":[{"name":"GCN","value":35},{"name":"GAT","value":28},{"name":"GraphSAGE","value":22},{"name":"其他","value":15}]}]}' },
  { label: '散点图', code: '{"title":{"text":"参数-性能散点"},"xAxis":{"name":"参数量(万)"},"yAxis":{"name":"准确率(%)"},"series":[{"type":"scatter","data":[[10,72],[25,78],[50,81],[100,83],[200,84]]}]}' },
  { label: '雷达图', code: '{"title":{"text":"模型多维评估"},"radar":{"indicator":[{"name":"准确率","max":100},{"name":"速度","max":100},{"name":"内存","max":100},{"name":"鲁棒性","max":100},{"name":"可解释性","max":100}]},"series":[{"type":"radar","data":[{"value":[85,70,60,75,65]}]}]}' }
]

const currentTemplates = computed(() => activeTab.value === 'mermaid' ? mermaidTemplates : echartsTemplates)

const mermaidPlaceholder = `graph TD
  A[开始] --> B[处理]
  B --> C[结束]`

const echartsPlaceholder = `{"title":{"text":"图表标题"},"xAxis":{"data":["A","B","C"]},"yAxis":{},"series":[{"type":"bar","data":[1,2,3]}]}`

// ===== Mermaid 渲染 =====
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

// ===== ECharts =====
let echartsModule = null
async function loadEcharts() {
  if (echartsModule) return echartsModule
  try {
    const mod = await import('echarts')
    echartsModule = mod
    return echartsModule
  } catch { return null }
}

async function runScript() {
  if (!script.value.trim()) { message.warn('请输入脚本'); return }
  errorMsg.value = ''
  running.value = true
  previewReady.value = false

  try {
    if (activeTab.value === 'mermaid') {
      await renderMermaid()
    } else {
      await renderChart()
    }
    previewReady.value = true
  } catch (e) {
    errorMsg.value = e.message?.slice(0, 80) || '渲染失败'
  } finally {
    running.value = false
  }
}

const running = ref(false)

async function renderMermaid() {
  const mermaid = await loadMermaid()
  if (!mermaid) { errorMsg.value = '未安装 Mermaid 库'; return }
  const id = 'pg-m-' + Date.now()
  const { svg } = await mermaid.render(id, script.value.trim())
  mermaidSvg.value = svg
}

async function renderChart() {
  const echarts = await loadEcharts()
  if (!echarts) { errorMsg.value = '未安装 ECharts 库'; return }
  await nextTick()
  if (!chartRef.value) return

  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)

  const option = JSON.parse(script.value.trim())
  chartInstance.setOption(option)
  chartHeight.value = Math.max(300, Math.min(800, chartRef.value.offsetHeight || 500))
}

// ===== 导出 PNG =====
async function exportPNG() {
  if (!previewReady.value) return
  try {
    let dataUrl
    if (activeTab.value === 'mermaid') {
      dataUrl = await svgToPng(mermaidSvg.value)
    } else if (chartInstance) {
      dataUrl = chartInstance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#0f1426' })
    }
    if (dataUrl) {
      const a = document.createElement('a')
      a.href = dataUrl
      a.download = `playground-${Date.now()}.png`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      message.success('已导出')
    }
  } catch (e) {
    message.error('导出失败: ' + (e.message || '未知错误'))
  }
}

/** SVG 字符串转 PNG DataURL */
function svgToPng(svgStr) {
  return new Promise((resolve, reject) => {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    const img = new Image()
    const blob = new Blob([svgStr], { type: 'image/svg+xml;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    img.onload = () => {
      canvas.width = img.width * 2
      canvas.height = img.height * 2
      ctx.scale(2, 2)
      ctx.fillStyle = '#0f1426'  // 暗色背景
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/png'))
    }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('SVG 转 PNG 失败')) }
    img.src = url
  })
}

function clear() {
  script.value = ''
  previewReady.value = false
  errorMsg.value = ''
  mermaidSvg.value = ''
  zoom.value = 1
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
}

// 切换 tab 清空预览
watch(activeTab, () => {
  clear()
})
</script>

<style scoped>
.playground { height:100%; display:flex; flex-direction:column; overflow:hidden; }
.pg-header {
  display:flex; align-items:center; gap:12px; padding:12px 24px;
  background:rgba(15,20,38,0.7); border-bottom:1px solid rgba(255,255,255,0.06); flex-shrink:0;
}
.pg-logo { font-size:16px; font-weight:600; color:rgba(255,255,255,0.9); }
.pg-desc { font-size:12px; color:rgba(255,255,255,0.4); }
.pg-body { flex:1; display:flex; gap:12px; padding:12px 16px; overflow:hidden; min-height:0; }
.pg-left {
  width:420px; flex-shrink:0; display:flex; flex-direction:column;
  background:rgba(15,20,38,0.5); border-radius:8px; padding:12px;
  border:1px solid rgba(255,255,255,0.05);
}
.pg-templates { padding:8px 0; display:flex; flex-wrap:wrap; gap:4px; align-items:center; }
.pg-tmpl-label { font-size:11px; color:rgba(255,255,255,0.35); margin-right:4px; }
.pg-editor {
  flex:1; min-height:200px; margin-top:8px;
  background:rgba(0,0,0,0.3) !important; color:rgba(255,255,255,0.85) !important;
  border:1px solid rgba(255,255,255,0.08) !important; border-radius:6px;
  font-family:'Cascadia Code','Fira Code',Consolas,monospace; font-size:12px;
  line-height:1.6; resize:none;
}
.pg-hint { font-size:10px; color:rgba(255,255,255,0.2); text-align:right; margin-top:4px; }
.pg-right { flex:1; min-width:0; display:flex; flex-direction:column; }
.pg-preview-label {
  font-size:13px; color:rgba(255,255,255,0.5); margin-bottom:8px;
  display:flex; align-items:center; gap:8px;
}
.pg-preview {
  flex:1; min-height:0; overflow:auto;
  background:rgba(15,20,38,0.5); border-radius:8px;
  border:1px solid rgba(255,255,255,0.05); padding:16px;
  display:flex; align-items:center; justify-content:center;
  position:relative; cursor:grab;
}
.pg-preview:active { cursor:grabbing; }
.pg-preview-zoom { transition: transform 0.15s ease; display:flex; align-items:center; justify-content:center; }
.pg-mermaid-wrap { max-width:100%; overflow:auto; }
.pg-mermaid-wrap :deep(svg) { max-width:100%; }
.pg-chart-wrap { min-height:300px; }
.pg-empty { text-align:center; color:rgba(255,255,255,0.25); }
.pg-empty-icon { font-size:48px; margin-bottom:12px; }
</style>
