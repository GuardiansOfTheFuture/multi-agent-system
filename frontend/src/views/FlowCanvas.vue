<template>
  <div class="flow-canvas-page">
    <ParticleBackground />

    <!-- ===== 顶部工具栏 ===== -->
    <header class="flow-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-title gradient-text">⚡ 流程画布</span>
        <a-switch v-model:checked="isEditMode" checked-children="编辑" un-children="只读" />
        <span class="toolbar-stats">
          <span class="stat-item">{{ nodes.length }} 节点</span>
          <span class="stat-sep">·</span>
          <span class="stat-item">{{ edges.length }} 连线</span>
        </span>
      </div>
      <div class="toolbar-center">
        <a-space :size="8">
          <a-button size="small" :disabled="!canUndo" @click="undo">↩ 撤销</a-button>
          <a-button size="small" :disabled="!canRedo" @click="redo">↪ 重做</a-button>
          <a-divider type="vertical" style="border-color:rgba(255,255,255,0.1);height:20px" />
          <a-button size="small" @click="validateFlow">✅ 校验</a-button>
          <a-button size="small" type="primary" @click="startExecution" :disabled="isExecuting">
            ▶ 执行
          </a-button>
          <a-button v-if="isExecuting" size="small" danger @click="stopExecution">⏹ 停止</a-button>
        </a-space>
      </div>
      <div class="toolbar-right">
        <div class="toolbar-actions">
          <button class="tb-btn" @click="zoomIn" title="放大">+</button>
          <button class="tb-btn" @click="zoomOut" title="缩小">−</button>
          <button class="tb-btn" @click="fitView" title="适应画布">⊡</button>
        </div>
      </div>
    </header>

    <!-- ===== 主体 ===== -->
    <div class="flow-body">
      <!-- 左侧节点面板（编辑模式可见） -->
      <aside class="flow-palette" v-if="isEditMode">
        <div class="palette-title">节点面板</div>
        <div class="palette-hint">拖拽到画布</div>
        <div
          v-for="item in paletteItems"
          :key="item.type"
          class="palette-item"
          :style="{ '--accent': item.color }"
          draggable="true"
          @dragstart="onDragStart($event, item)"
        >
          <span class="palette-icon">{{ item.icon }}</span>
          <span class="palette-label">{{ item.label }}</span>
        </div>
        <a-divider style="margin:8px 0;border-color:rgba(255,255,255,0.06)" />
        <div class="palette-title" style="font-size:11px">控制节点</div>
        <div class="palette-hint" style="font-size:10px">循环/分支</div>
        <div
          v-for="item in controlItems"
          :key="item.type"
          class="palette-item control-item"
          draggable="true"
          @dragstart="onDragStart($event, item)"
        >
          <span class="palette-icon">{{ item.icon }}</span>
          <span class="palette-label">{{ item.label }}</span>
        </div>
      </aside>

      <!-- 画布 -->
      <div
        class="flow-canvas-wrap"
        @drop="onDrop"
        @dragover.prevent
        @contextmenu.prevent="onCanvasContextMenu"
      >
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :default-edge-options="defaultEdgeOptions"
          :connection-mode="ConnectionMode.Strict"
          :default-viewport="{ x: 0, y: 0, zoom: 0.85 }"
          :min-zoom="0.2"
          :max-zoom="2.5"
          :snap-to-grid="true"
          :snap-grid="[15, 15]"
          :connection-line-style="{ stroke: 'rgba(146,84,222,0.5)', strokeWidth: 2 }"
          fit-view-on-init
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @connect="onConnect"
          @edge-click="onEdgeClick"
        >
          <Background pattern-color="rgba(255,255,255,0.04)" :gap="24" />
        </VueFlow>
      </div>

      <!-- 右侧配置面板 -->
      <aside class="flow-side-panel">
        <div class="panel-header-bar">
          <span class="panel-header-title">{{ isEditMode ? '⚙ 节点配置' : '节点详情' }}</span>
          <span class="panel-header-hint" v-if="!selectedNode">点击节点查看</span>
          <a-tag v-else color="purple" size="small">{{ selectedNode.data?.agentRole }}</a-tag>
        </div>

        <!-- 可编辑表单 -->
        <div v-if="selectedNode && isEditMode" class="edit-form">
          <a-form layout="vertical" size="small">
            <a-form-item label="节点名称">
              <a-input v-model:value="editingLabel" size="small" @change="applyEdit" />
            </a-form-item>
            <a-form-item label="Agent 角色">
              <a-select v-model:value="editingRole" size="small" :options="roleOptions" @change="applyEdit" />
            </a-form-item>
            <a-divider style="margin:8px 0;border-color:rgba(255,255,255,0.06)" />
            <a-form-item label="System Prompt">
              <a-textarea v-model:value="editingPrompt" :rows="4" size="small" @change="applyEdit" placeholder="自定义该节点的提示词..." />
            </a-form-item>
            <a-form-item label="模型">
              <a-select v-model:value="editingModel" size="small" :options="modelOptions" @change="applyEdit" />
            </a-form-item>
            <a-row :gutter="8">
              <a-col :span="12">
                <a-form-item label="温度">
                  <a-slider v-model:value="editingTemperature" :min="0" :max="1" :step="0.1" @change="applyEdit" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="超时(s)">
                  <a-input-number v-model:value="editingTimeout" :min="10" :max="600" size="small" @change="applyEdit" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="重试次数">
              <a-input-number v-model:value="editingRetry" :min="0" :max="5" size="small" @change="applyEdit" />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea v-model:value="editingNotes" :rows="2" size="small" @change="applyEdit" placeholder="节点备注、使用说明..." />
            </a-form-item>
          </a-form>
          <a-divider style="margin:8px 0;border-color:rgba(255,255,255,0.06)" />
          <a-space :size="8">
            <a-button size="small" danger @click="deleteSelectedNode">🗑 删除节点</a-button>
            <a-button size="small" @click="duplicateNode">📋 复制</a-button>
          </a-space>
        </div>

        <!-- 只读展示 -->
        <div v-else-if="selectedNode && !isEditMode" class="readonly-panel">
          <div class="ro-item"><span class="ro-label">角色</span><a-tag :color="roleColor(selectedNode.data?.agentRole)" size="small">{{ selectedNode.data?.roleName }}</a-tag></div>
          <div class="ro-item"><span class="ro-label">步骤</span><span style="color:rgba(255,255,255,0.7)">{{ selectedNode.data?.label }}</span></div>
          <div class="ro-item"><span class="ro-label">模型</span><span style="color:rgba(255,255,255,0.5)">{{ selectedNode.data?.config?.model || 'qwen-max' }}</span></div>
          <div class="ro-item"><span class="ro-label">温度</span><span style="color:rgba(255,255,255,0.5)">{{ selectedNode.data?.config?.temperature ?? 0.7 }}</span></div>
          <div class="ro-item" v-if="selectedNode.data?.config?.notes"><span class="ro-label">备注</span><span style="color:rgba(255,255,255,0.4);font-size:11px;max-width:140px;text-align:right">{{ selectedNode.data.config.notes }}</span></div>
        </div>

        <!-- 校验结果 -->
        <div v-if="validationResult" class="validation-panel" :class="{ 'val-ok': validationResult.valid, 'val-err': !validationResult.valid }">
          <div class="val-status">{{ validationResult.valid ? '✅ 流程校验通过' : '⚠ 流程存在问题' }}</div>
          <div v-if="validationResult.loopCount" class="val-loop-info">🔁 含 {{ validationResult.loopCount }} 条回退边，执行时将模拟循环</div>
          <div v-for="(err, i) in validationResult.errors" :key="i" class="val-error">• {{ err }}</div>
        </div>

        <a-empty v-if="!selectedNode && !validationResult" description="点击节点查看详情" :image-style="{ height: '40px' }" />
      </aside>
    </div>

    <!-- ===== 右键菜单 ===== -->
    <div
      v-if="contextMenu.show"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    >
      <template v-if="contextMenu.type === 'node'">
        <div class="ctx-item" @click="deleteNode(contextMenu.target)">🗑 删除节点</div>
        <div class="ctx-item" @click="duplicateNode(contextMenu.target)">📋 复制节点</div>
        <div class="ctx-sep" />
        <div class="ctx-item" @click="toggleNodeDisabled(contextMenu.target)">⏸ 禁用/启用</div>
      </template>
      <template v-if="contextMenu.type === 'edge'">
        <div class="ctx-item" @click="setEdgeType(contextMenu.target, 'normal')">→ 普通边</div>
        <div class="ctx-item" @click="setEdgeType(contextMenu.target, 'success')" style="color:#73d13d">✓ 条件边(通过)</div>
        <div class="ctx-item" @click="setEdgeType(contextMenu.target, 'failure')" style="color:#ff7875">✗ 条件边(不通过)</div>
        <div class="ctx-item" @click="setEdgeType(contextMenu.target, 'loop')" style="color:#85a5ff">↺ 回退边(循环)</div>
        <div class="ctx-sep" />
        <div class="ctx-item danger" @click="deleteEdge(contextMenu.target)">🗑 删除连线</div>
      </template>
      <template v-if="contextMenu.type === 'canvas'">
        <div class="ctx-item" @click="addNodeAt(contextMenu.x, contextMenu.y)">+ 在此添加节点</div>
        <div class="ctx-sep" />
        <div class="ctx-item danger" @click="clearCanvas">🗑 清空画布</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MarkerType, ConnectionMode } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import AgentNode from '@/components/flow/AgentNode.vue'
import ConditionNode from '@/components/flow/ConditionNode.vue'
import LoopNode from '@/components/flow/LoopNode.vue'
import ParticleBackground from '@/components/flow/ParticleBackground.vue'
import { getFlowList } from '@/api'
import { message } from 'ant-design-vue'

const nodeTypes = { agent: AgentNode, condition: ConditionNode, loop: LoopNode }
const { zoomIn, zoomOut, fitView, screenToFlowCoordinate, addNodes, addEdges, removeNodes, removeEdges } = useVueFlow()

// ===== 编辑模式 =====
const isEditMode = ref(true)
const isExecuting = ref(false)
let execTimer = null

// ===== 节点面板 =====
const paletteItems = [
  { type: 'SUPERVISOR', icon: '🧭', label: '导师 Agent', color: '#9254de' },
  { type: 'RESEARCHER', icon: '🔬', label: '研究员 Agent', color: '#1890ff' },
  { type: 'WRITER', icon: '✍️', label: '写作者 Agent', color: '#52c41a' },
  { type: 'REVIEWER', icon: '📝', label: '审稿人 Agent', color: '#fa8c16' },
  { type: 'POLISHER', icon: '✨', label: '润色师 Agent', color: '#13c2c2' }
]
const controlItems = [
  { type: 'CONDITION', icon: '⇢', label: '条件分支', color: '#faad14' },
  { type: 'LOOP', icon: '↺', label: '循环节点', color: '#597ef7' }
]

const ROLE_NAMES = { SUPERVISOR: '导师', RESEARCHER: '研究员', WRITER: '写作者', REVIEWER: '审稿人', POLISHER: '润色师' }
const roleOptions = Object.entries(ROLE_NAMES).map(([k, v]) => ({ value: k, label: v }))
const modelOptions = ['qwen-max', 'qwen-plus', 'qwen-turbo', 'deepseek-v3', 'deepseek-r1'].map(m => ({ value: m, label: m }))

// ===== 流程数据 =====
const availableFlows = ref([])
const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)
const selectedEdge = ref(null)

// ===== 编辑表单 =====
const editingLabel = ref('')
const editingRole = ref('SUPERVISOR')
const editingPrompt = ref('')
const editingModel = ref('qwen-max')
const editingTemperature = ref(0.7)
const editingTimeout = ref(120)
const editingRetry = ref(2)
const editingNotes = ref('')

watch(selectedNode, (n) => {
  if (!n) return
  editingLabel.value = (n.data?.label || '').replace(/^[^\s]+\s/, '')
  editingRole.value = n.data?.agentRole || 'SUPERVISOR'
  editingPrompt.value = n.data?.config?.systemPrompt || ''
  editingModel.value = n.data?.config?.model || 'qwen-max'
  editingTemperature.value = n.data?.config?.temperature ?? 0.7
  editingTimeout.value = n.data?.config?.timeout || 120
  editingRetry.value = n.data?.config?.retryCount ?? 2
  editingNotes.value = n.data?.config?.notes || ''
})

function applyEdit() {
  if (!selectedNode.value) return
  const n = selectedNode.value
  n.data = {
    ...n.data,
    agentRole: editingRole.value,
    label: paletteItems.find(p => p.type === editingRole.value)?.icon + ' ' + (editingLabel.value || n.data?.label || ''),
    roleName: ROLE_NAMES[editingRole.value],
    config: {
      systemPrompt: editingPrompt.value,
      model: editingModel.value,
      temperature: editingTemperature.value,
      timeout: editingTimeout.value,
      retryCount: editingRetry.value,
      notes: editingNotes.value
    }
  }
  pushHistory()
}

// ===== 拖拽创建节点 =====
let dragItem = null
function onDragStart(event, item) {
  dragItem = item
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', item.type)
}

function onDrop(event) {
  if (!dragItem) return
  const position = screenToFlowCoordinate({ x: event.clientX - 220, y: event.clientY - 100 })
  const id = `node-${Date.now()}`
  const isControl = controlItems.includes(dragItem)
  let nodeType, nodeData
  if (isControl) {
    if (dragItem.type === 'CONDITION') {
      nodeType = 'condition'
      nodeData = { label: '条件分支', stepIndex: nodes.value.length + 1, config: { condition: 'output.contains(\'严重问题\')', notes: '' }, status: 'pending' }
    } else {
      nodeType = 'loop'
      nodeData = { label: '审稿循环', stepIndex: nodes.value.length + 1, config: { maxIterations: 3, notes: '' }, status: 'pending' }
    }
  } else {
    nodeType = 'agent'
    const icon = dragItem.icon
    const role = dragItem.type
    nodeData = {
      agentRole: role,
      label: `${icon} ${dragItem.label.replace(' Agent', '')}`,
      roleName: ROLE_NAMES[role],
      stepIndex: nodes.value.length + 1,
      config: { systemPrompt: '', model: 'qwen-max', temperature: 0.7, timeout: 120, retryCount: 2, notes: '' },
      status: 'pending'
    }
  }
  pushHistory()
  nodes.value.push({ id, type: nodeType, position, data: nodeData })
  dragItem = null
}

// ===== 连线 =====
const defaultEdgeOptions = {
  type: 'smoothstep',
  animated: true,
  style: { stroke: 'rgba(255,255,255,0.18)', strokeWidth: 1.5 },
  markerEnd: { type: MarkerType.ArrowClosed, color: 'rgba(255,255,255,0.3)', width: 16, height: 16 }
}

function onConnect(connection) {
  pushHistory()
  const edge = {
    id: `edge-${connection.source}-${connection.target}-${Date.now()}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
    type: 'smoothstep',
    animated: true,
    style: { stroke: 'rgba(255,255,255,0.18)', strokeWidth: 1.5 },
    markerEnd: { type: MarkerType.ArrowClosed, color: 'rgba(255,255,255,0.3)', width: 16, height: 16 },
    data: { label: '', conditionType: 'normal' }
  }
  edges.value.push(edge)
}

// ===== 交互 =====
function onNodeClick({ node }) {
  closeContextMenu()
  selectedNode.value = node
  selectedEdge.value = null
  validationResult.value = null
}

function onPaneClick() {
  closeContextMenu()
  selectedNode.value = null
  selectedEdge.value = null
}

function onEdgeClick({ edge }) {
  closeContextMenu()
  selectedEdge.value = edge
  selectedNode.value = null
}

// ===== 右键菜单 =====
const contextMenu = reactive({ show: false, x: 0, y: 0, type: '', target: null })

function onCanvasContextMenu(event) {
  closeContextMenu()
  // check if clicked on node or edge
  const target = event.target
  const nodeEl = target.closest('.vue-flow__node')
  const edgeEl = target.closest('.vue-flow__edge')
  if (nodeEl) {
    const nodeId = nodeEl.getAttribute('data-id')
    const node = nodes.value.find(n => n.id === nodeId)
    contextMenu.type = 'node'
    contextMenu.target = node
  } else if (edgeEl) {
    const edgeId = edgeEl.getAttribute('data-id')
    const edge = edges.value.find(e => e.id === edgeId)
    contextMenu.type = 'edge'
    contextMenu.target = edge
  } else {
    contextMenu.type = 'canvas'
    contextMenu.target = null
  }
  contextMenu.x = event.clientX - 220
  contextMenu.y = event.clientY - 80
  contextMenu.show = true
}

function closeContextMenu() {
  contextMenu.show = false
}

// 点击空白关闭菜单
if (typeof document !== 'undefined') {
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.context-menu')) closeContextMenu()
  })
}

// ===== 节点操作 =====
function deleteNode(node) {
  closeContextMenu()
  pushHistory()
  removeNodes([node?.id || selectedNode.value?.id])
  selectedNode.value = null
}

function duplicateNode(node) {
  closeContextMenu()
  const src = node || selectedNode.value
  if (!src) return
  pushHistory()
  const id = `node-${Date.now()}`
  nodes.value.push({
    ...JSON.parse(JSON.stringify(src)),
    id,
    position: { x: src.position.x + 60, y: src.position.y + 60 },
    data: { ...src.data, stepIndex: nodes.value.length + 1 }
  })
}

function deleteSelectedNode() { deleteNode(selectedNode.value) }
function toggleNodeDisabled(node) {
  closeContextMenu()
  pushHistory()
  const n = node || selectedNode.value
  if (!n) return
  n.data = { ...n.data, disabled: !n.data?.disabled }
}

// ===== 边操作 =====
function deleteEdge(edge) {
  closeContextMenu()
  pushHistory()
  removeEdges([edge?.id || selectedEdge?.id])
}

function toggleEdgeCondition(edge) {
  closeContextMenu()
  pushHistory()
  const e = edge || selectedEdge
  if (!e) return
  e.data = { ...e.data, conditionType: e.data?.conditionType === 'success' ? 'normal' : 'success', label: e.data?.conditionType === 'success' ? '' : '通过' }
  applyEdgeStyle(e)
}

function setEdgeLabel(edge, label) {
  closeContextMenu()
  pushHistory()
  const e = edge || selectedEdge
  if (!e) return
  e.data = { ...e.data, conditionType: label === '通过' ? 'success' : 'failure', label }
  applyEdgeStyle(e)
}

function setLoopEdge(edge) {
  setEdgeType(edge || selectedEdge, 'loop')
}

function setEdgeType(edge, type) {
  closeContextMenu()
  pushHistory()
  const e = edge || selectedEdge
  if (!e) return
  if (type === 'loop') {
    e.data = { ...e.data, conditionType: 'loop', label: '↺ 回退' }
    e.type = 'default'
    e.animated = false
    e.style = { stroke: '#85a5ff', strokeWidth: 2.5, strokeDasharray: '8 5' }
    e.markerEnd = { type: MarkerType.ArrowClosed, color: '#85a5ff', width: 16, height: 16 }
  } else if (type === 'normal') {
    e.data = { ...e.data, conditionType: 'normal', label: '' }
    e.type = 'smoothstep'
    e.animated = true
    e.style = { stroke: 'rgba(255,255,255,0.18)', strokeWidth: 1.5 }
    e.markerEnd = { type: MarkerType.ArrowClosed, color: 'rgba(255,255,255,0.3)', width: 16, height: 16 }
  } else if (type === 'success') {
    e.data = { ...e.data, conditionType: 'success', label: '✓ 通过' }
    e.type = 'smoothstep'
    e.animated = true
    e.style = { stroke: '#52c41a', strokeWidth: 2.5 }
    e.markerEnd = { type: MarkerType.ArrowClosed, color: '#52c41a', width: 16, height: 16 }
  } else if (type === 'failure') {
    e.data = { ...e.data, conditionType: 'failure', label: '✗ 不通过' }
    e.type = 'smoothstep'
    e.animated = true
    e.style = { stroke: '#ff4d4f', strokeWidth: 2.5 }
    e.markerEnd = { type: MarkerType.ArrowClosed, color: '#ff4d4f', width: 16, height: 16 }
  }
}

function isConditionEdge(edge) {
  return edge?.data?.conditionType === 'success' || edge?.data?.conditionType === 'failure'
}

function applyEdgeStyle(e) {
  const ct = e.data?.conditionType || 'normal'
  if (ct === 'success') setEdgeType(e, 'success')
  else if (ct === 'failure') setEdgeType(e, 'failure')
  else if (ct === 'loop') setEdgeType(e, 'loop')
  else setEdgeType(e, 'normal')
}

// ===== 画布操作 =====
function clearCanvas() {
  closeContextMenu()
  pushHistory()
  nodes.value = []
  edges.value = []
  selectedNode.value = null
}

function addNodeAt(x, y) {
  closeContextMenu()
  pushHistory()
  const id = `node-${Date.now()}`
  nodes.value.push({
    id, type: 'agent',
    position: { x: x - 220, y: y - 120 },
    data: { agentRole: 'WRITER', label: '✍️ 新节点', roleName: '写作者', stepIndex: nodes.value.length + 1, config: { model: 'qwen-max', temperature: 0.7, timeout: 120, retryCount: 2, notes: '' }, status: 'pending' }
  })
}

// ===== 撤销/重做 =====
const history = ref([])
const historyIndex = ref(-1)
const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value < history.value.length - 1)

function pushHistory() {
  const snapshot = JSON.stringify({ nodes: nodes.value, edges: edges.value })
  if (historyIndex.value < history.value.length - 1) {
    history.value = history.value.slice(0, historyIndex.value + 1)
  }
  history.value.push(snapshot)
  historyIndex.value = history.value.length - 1
}

function undo() {
  if (!canUndo.value) return
  historyIndex.value--
  restoreSnapshot()
}

function redo() {
  if (!canRedo.value) return
  historyIndex.value++
  restoreSnapshot()
}

function restoreSnapshot() {
  const data = JSON.parse(history.value[historyIndex.value])
  nodes.value = data.nodes
  edges.value = data.edges
  selectedNode.value = null
}

// ===== 流程校验 =====
const validationResult = ref(null)

function validateFlow() {
  const errors = []
  // 孤立节点检测
  const connectedIds = new Set()
  edges.value.forEach(e => { connectedIds.add(e.source); connectedIds.add(e.target) })
  const orphans = nodes.value.filter(n => !connectedIds.has(n.id))
  if (orphans.length) {
    errors.push(`${orphans.length} 个孤立节点未连线`)
  }
  // 环检测：跳过回退边
  const loopEdges = edges.value.filter(e => e.data?.conditionType === 'loop')
  const normalEdges = edges.value.filter(e => e.data?.conditionType !== 'loop')
  const adj = {}
  nodes.value.forEach(n => { adj[n.id] = [] })
  normalEdges.forEach(e => { if (adj[e.source]) adj[e.source].push(e.target) })
  const visiting = new Set()
  const visited = new Set()
  let hasCycle = false
  function dfs(id) {
    if (visiting.has(id)) { hasCycle = true; return }
    if (visited.has(id)) return
    visiting.add(id)
    ;(adj[id] || []).forEach(dfs)
    visiting.delete(id)
    visited.add(id)
  }
  nodes.value.forEach(n => { if (!visited.has(n.id)) dfs(n.id) })
  if (hasCycle) {
    errors.push('存在意外的循环依赖 — 如需设计循环请将回退边标记为"回退边"')
  }
  // 起始节点检测
  const hasStart = nodes.value.some(n => !edges.value.some(e => e.target === n.id && e.data?.conditionType !== 'loop'))
  if (!hasStart && nodes.value.length > 0) {
    errors.push('未找到起始节点')
  }
  // 回退边统计
  if (loopEdges.length > 0) {
    validationResult.value = { valid: errors.length === 0, errors, loopCount: loopEdges.length }
  } else {
    validationResult.value = { valid: errors.length === 0, errors, loopCount: 0 }
  }
  if (errors.length === 0) {
    const msg = loopEdges.length ? `校验通过 (含 ${loopEdges.length} 条回退边)` : '流程校验通过'
    message.success(msg)
  } else {
    message.warning(`发现 ${errors.length} 个问题`)
  }
}

// ===== 执行模拟 =====
function resetAllStatus() {
  nodes.value.forEach(n => { n.data = { ...n.data, status: 'pending' } })
}

function startExecution() {
  if (isExecuting.value) return
  validationResult.value = null
  resetAllStatus()
  isExecuting.value = true

  // 拓扑排序 — 跳过回退边
  const loopEdges = edges.value.filter(e => e.data?.conditionType === 'loop')
  const forwardEdges = edges.value.filter(e => e.data?.conditionType !== 'loop')
  const inDegree = {}
  const adj = {}
  const loopBacks = {} // 记录回退边: target -> [source]
  nodes.value.forEach(n => { inDegree[n.id] = 0; adj[n.id] = []; loopBacks[n.id] = [] })
  forwardEdges.forEach(e => {
    if (adj[e.source]) { adj[e.source].push(e.target); inDegree[e.target] = (inDegree[e.target] || 0) + 1 }
  })
  loopEdges.forEach(e => {
    if (loopBacks[e.target]) loopBacks[e.target].push(e.source)
  })
  const queue = nodes.value.filter(n => inDegree[n.id] === 0).map(n => n.id)
  const order = []
  while (queue.length) {
    const id = queue.shift()
    order.push(id)
    ;(adj[id] || []).forEach(t => { inDegree[t]--; if (inDegree[t] === 0) queue.push(t) })
  }

  // 按拓扑序模拟执行，回退边触发循环动画
  let idx = 0
  let iterationCount = 0
  const maxIterations = 10 // 安全上限

  function step() {
    if (!isExecuting.value) return
    if (idx >= order.length || iterationCount >= maxIterations) {
      // 所有节点设为完成
      nodes.value.forEach(n => { n.data = { ...n.data, status: 'completed' } })
      isExecuting.value = false
      message.success('执行完成')
      return
    }
    const nodeId = order[idx]
    if (idx > 0) {
      const prev = nodes.value.find(n => n.id === order[idx - 1])
      if (prev) prev.data = { ...prev.data, status: 'completed' }
    }
    const cur = nodes.value.find(n => n.id === nodeId)
    if (cur) cur.data = { ...cur.data, status: 'in_progress' }

    // 检查当前节点是否有回退边
    const backs = loopBacks[nodeId] || []
    if (backs.length > 0 && iterationCount < maxIterations - 1) {
      // 模拟一次回退：把回退边的 source 节点排到前面重来
      const backIdx = order.indexOf(backs[0])
      if (backIdx >= 0 && backIdx < idx) {
        // 回退：将后续节点重置，从回退目标重新开始
        for (let i = backIdx; i < order.length; i++) {
          const n = nodes.value.find(nn => nn.id === order[i])
          if (n) n.data = { ...n.data, status: 'pending' }
        }
        idx = backIdx
        iterationCount++
        execTimer = setTimeout(step, 1200)
        return
      }
    }

    idx++
    execTimer = setTimeout(step, 800)
  }
  step()
}

function stopExecution() {
  isExecuting.value = false
  clearTimeout(execTimer)
  resetAllStatus()
}

// ===== 工具 =====
function roleColor(role) {
  const m = { SUPERVISOR: 'purple', RESEARCHER: 'blue', WRITER: 'green', REVIEWER: 'orange', POLISHER: 'cyan' }
  return m[role] || 'default'
}

// ===== 初始化 =====
onMounted(async () => {
  try { const res = await getFlowList(); availableFlows.value = res.data || [] } catch (_) {}
  // 加载默认流程作为起点
  const DEFAULT_STEPS = [
    { role: 'SUPERVISOR', label: '🧭 选题评估' },
    { role: 'RESEARCHER', label: '🔬 文献调研' },
    { role: 'SUPERVISOR', label: '📋 大纲审阅' },
    { role: 'WRITER', label: '✍️ 引言' },
    { role: 'WRITER', label: '✍️ 方法' },
    { role: 'WRITER', label: '✍️ 实验' },
    { role: 'WRITER', label: '✍️ 结论' },
    { role: 'REVIEWER', label: '📝 审稿迭代' },
    { role: 'POLISHER', label: '✨ 润色定稿' },
    { role: 'SUPERVISOR', label: '✅ 最终审核' }
  ]
  const ns = DEFAULT_STEPS.map((s, i) => ({
    id: `node-${i}`,
    type: 'agent',
    position: { x: 280, y: i * 110 + 40 },
    data: { agentRole: s.role, label: s.label, roleName: ROLE_NAMES[s.role], stepIndex: i + 1, config: { systemPrompt: '', model: 'qwen-max', temperature: 0.7, timeout: 120, retryCount: 2, notes: '' }, status: 'pending' }
  }))
  const es = []
  for (let i = 0; i < ns.length - 1; i++) {
    es.push({
      id: `edge-${i}-${i + 1}`,
      source: ns[i].id, target: ns[i + 1].id,
      type: 'smoothstep', animated: true,
      style: { stroke: 'rgba(255,255,255,0.18)', strokeWidth: 1.5 },
      markerEnd: { type: MarkerType.ArrowClosed, color: 'rgba(255,255,255,0.3)', width: 16, height: 16 },
      data: { label: '', conditionType: 'normal' }
    })
  }
  nodes.value = ns
  edges.value = es
  pushHistory()
})
</script>

<style scoped>
.flow-canvas-page {
  position: relative; display: flex; flex-direction: column;
  height: calc(100vh - 120px); overflow: hidden;
  background: linear-gradient(135deg, #0a0e17 0%, #111827 30%, #0f1729 60%, #0a0f1a 100%);
}

/* ===== 工具栏 ===== */
.flow-toolbar {
  position: relative; z-index: 10;
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 16px;
  background: rgba(15,20,35,0.75);
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0; gap: 12px;
}
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.toolbar-center { display: flex; align-items: center; }
.toolbar-right { display: flex; align-items: center; }
.toolbar-title { font-size: 15px; font-weight: 700; letter-spacing: 0.5px; }
.toolbar-stats { display: flex; align-items: center; gap: 6px; font-size: 11px; color: rgba(255,255,255,0.35); font-family: 'SF Mono','Consolas',monospace; }
.stat-sep { color: rgba(255,255,255,0.15); }

.toolbar-actions {
  display: flex; gap: 2px;
  background: rgba(255,255,255,0.04); border-radius: 8px; padding: 2px;
  border: 1px solid rgba(255,255,255,0.06);
}
.tb-btn {
  width: 30px; height: 28px; display: flex; align-items: center; justify-content: center;
  background: transparent; border: none; border-radius: 6px;
  color: rgba(255,255,255,0.5); font-size: 16px; cursor: pointer; transition: all 0.2s;
}
.tb-btn:hover { background: rgba(255,255,255,0.08); color: rgba(255,255,255,0.9); }

/* ===== 左侧面板 ===== */
.flow-palette {
  width: 160px; flex-shrink: 0;
  background: rgba(15,20,38,0.7); backdrop-filter: blur(16px);
  border-right: 1px solid rgba(255,255,255,0.06);
  padding: 12px 8px; overflow-y: auto;
  z-index: 5;
}
.palette-title { font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.7); margin-bottom: 4px; padding: 0 4px; }
.palette-hint { font-size: 10px; color: rgba(255,255,255,0.25); margin-bottom: 8px; padding: 0 4px; }
.palette-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; margin-bottom: 4px;
  border-radius: 8px; cursor: grab;
  border: 1px solid rgba(255,255,255,0.04);
  background: rgba(255,255,255,0.02);
  transition: all 0.2s;
}
.palette-item:hover {
  border-color: var(--accent);
  background: rgba(255,255,255,0.04);
  box-shadow: 0 0 12px color-mix(in srgb, var(--accent) 30%, transparent);
}
.palette-item:active { cursor: grabbing; transform: scale(0.96); }
.palette-icon { font-size: 16px; }
.palette-label { font-size: 11px; color: rgba(255,255,255,0.7); font-weight: 500; white-space: nowrap; }
.control-item { border-style: dashed; }

/* ===== 主体 ===== */
.flow-body { flex: 1; display: flex; overflow: hidden; position: relative; }
.flow-canvas-wrap { flex: 1; min-width: 0; position: relative; }
.flow-canvas-wrap :deep(.vue-flow__pane) { cursor: grab; }
.flow-canvas-wrap :deep(.vue-flow__pane:active) { cursor: grabbing; }

/* ===== 右侧面板 ===== */
.flow-side-panel {
  width: 270px; flex-shrink: 0;
  background: rgba(15,20,38,0.7); backdrop-filter: blur(16px);
  border-left: 1px solid rgba(255,255,255,0.06);
  overflow-y: auto; display: flex; flex-direction: column;
}
.panel-header-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 14px 10px; flex-shrink: 0;
}
.panel-header-title { font-size: 13px; font-weight: 600; color: rgba(255,255,255,0.7); }
.panel-header-hint { font-size: 10px; color: rgba(255,255,255,0.2); }
.flow-side-panel::-webkit-scrollbar { width: 3px; }
.flow-side-panel::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.06); border-radius: 2px; }

/* 编辑表单 */
.edit-form { padding: 0 14px 14px; }
.edit-form :deep(.ant-form-item) { margin-bottom: 10px; }
.edit-form :deep(.ant-form-item-label > label) { color: rgba(255,255,255,0.5) !important; font-size: 11px; }

/* 只读面板 */
.readonly-panel { padding: 0 14px; }
.ro-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid rgba(255,255,255,0.04); font-size: 12px; }
.ro-label { color: rgba(255,255,255,0.35); }

/* 校验面板 */
.validation-panel { margin: 12px 14px; padding: 12px; border-radius: 8px; font-size: 12px; }
.val-ok { background: rgba(82,196,26,0.08); border: 1px solid rgba(82,196,26,0.2); }
.val-err { background: rgba(255,77,79,0.08); border: 1px solid rgba(255,77,79,0.2); }
.val-status { font-weight: 600; margin-bottom: 4px; }
.val-ok .val-status { color: #52c41a; }
.val-err .val-status { color: #ff7875; }
.val-error { color: rgba(255,255,255,0.55); line-height: 1.8; }
.val-loop-info { color: #85a5ff; font-size: 11px; margin: 4px 0; }

/* ===== 右键菜单 ===== */
.context-menu {
  position: fixed; z-index: 100;
  background: rgba(20,28,48,0.96); backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 10px; padding: 4px;
  min-width: 160px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
}
.ctx-item {
  padding: 7px 14px; font-size: 12px;
  color: rgba(255,255,255,0.7); cursor: pointer; border-radius: 6px;
  transition: all 0.15s;
}
.ctx-item:hover { background: rgba(146,84,222,0.15); color: #fff; }
.ctx-item.danger:hover { background: rgba(255,77,79,0.2); color: #ff7875; }
.ctx-sep { height: 1px; background: rgba(255,255,255,0.06); margin: 4px 8px; }
</style>
