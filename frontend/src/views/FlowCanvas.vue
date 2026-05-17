<template>
  <div class="flow-canvas-page">
    <ParticleBackground />

    <!-- ===== 顶部工具栏 ===== -->
    <header class="flow-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-title gradient-text">⚡ 流程画布</span>
        <a-select
          v-model:value="currentFlowId"
          :options="flowSelectOptions"
          style="width: 220px"
          class="flow-select"
          placeholder="选择流程..."
          @change="onFlowSelect"
        >
          <template #option="{ value, label, category, source }">
            <div style="display:flex;align-items:center;gap:6px">
              <span>{{ label }}</span>
              <a-tag v-if="source === 'preset'" color="purple" size="small" style="font-size:9px">预设</a-tag>
              <a-tag v-else color="blue" size="small" style="font-size:9px">我的</a-tag>
            </div>
          </template>
        </a-select>
        <a-switch v-model:checked="isEditMode" checked-children="编辑" un-children="只读" size="small" />
        <span class="toolbar-stats">
          <span class="stat-item">{{ nodes.length }} 节点</span>
          <span class="stat-sep">·</span>
          <span class="stat-item">{{ edges.length }} 连线</span>
        </span>
      </div>
      <div class="toolbar-center">
        <a-space :size="6">
          <a-button size="small" :disabled="!canUndo" @click="undo" title="撤销">↩</a-button>
          <a-button size="small" :disabled="!canRedo" @click="redo" title="重做">↪</a-button>
          <a-divider type="vertical" style="border-color:rgba(255,255,255,0.1);height:20px" />
          <a-button size="small" @click="handleNewFlow">➕ 新建</a-button>
          <a-button size="small" @click="handleSaveFlow" v-if="isEditMode">💾 保存</a-button>
          <a-button size="small" @click="handleSaveAsFlow" v-if="isEditMode">📋 另存为</a-button>
          <a-button size="small" danger @click="handleDeleteFlow" v-if="flowSource === 'custom' && flowDbId">🗑</a-button>
          <a-divider type="vertical" style="border-color:rgba(255,255,255,0.1);height:20px" />
          <a-button size="small" @click="validateFlow">✅ 校验</a-button>
          <a-button size="small" type="primary" @click="startExecution" :disabled="isExecuting">▶ 执行</a-button>
          <a-button v-if="isExecuting" size="small" danger @click="stopExecution">⏹</a-button>
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

    <!-- 执行状态栏 -->
    <div v-if="isExecuting" class="exec-bar">
      <span class="exec-bar-dot" />
      <span>执行中 — {{ execStatusText }}</span>
      <span class="exec-bar-hint">节点实时染色中</span>
    </div>

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
          @node-double-click="onNodeClick"
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
            <a-form-item label="Agent 角色" v-if="selectedNode.type !== 'paper'">
              <a-select v-model:value="editingRole" size="small" :options="roleOptions" @change="applyEdit" />
            </a-form-item>
            <!-- 论文节点 → 选择已创建的论文任务 -->
            <a-form-item label="关联论文任务" v-if="selectedNode.type === 'paper'">
              <a-select
                v-model:value="editingPaperId"
                size="small"
                placeholder="选择待执行的论文"
                @change="applyPaperSelect"
              >
                <a-select-option v-for="p in canvasPaperTasks" :key="p.id" :value="p.id">
                  {{ p.title }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-divider style="margin:8px 0;border-color:rgba(255,255,255,0.06)" />
            <a-form-item label="System Prompt" v-if="selectedNode.type !== 'paper'">
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
        <div class="ctx-item" @click="configureNode(contextMenu.target)" style="color:#b37feb">⚙ 配置节点</div>
        <div class="ctx-sep" />
        <div class="ctx-item" @click="duplicateNode(contextMenu.target)">📋 复制节点</div>
        <div class="ctx-item" @click="toggleNodeDisabled(contextMenu.target)">⏸ 禁用/启用</div>
        <div class="ctx-sep" />
        <div class="ctx-item danger" @click="deleteNode(contextMenu.target)">🗑 删除节点</div>
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

    <!-- 保存流程弹窗 -->
    <a-modal
      v-model:open="saveModalShow"
      :closable="false"
      :mask-closable="false"
      width="420px"
      :footer="null"
      wrap-class-name="save-flow-modal"
    >
      <div class="save-modal-content">
        <div class="save-modal-icon">💾</div>
        <div class="save-modal-title">保存流程</div>
        <div class="save-modal-sub">输入流程名称和描述</div>
        <a-input v-model:value="saveModalName" placeholder="流程名称..." size="large" class="save-modal-input" @press-enter="confirmSaveAs" />
        <a-textarea v-model:value="saveModalDesc" placeholder="流程描述（可选）..." :rows="2" size="large" class="save-modal-textarea" style="margin-bottom:20px" />
        <div class="save-modal-actions">
          <a-button size="large" @click="saveModalShow = false" class="cancel-btn">取消</a-button>
          <a-button size="large" type="primary" @click="confirmSaveAs" :disabled="!saveModalName.trim()">确认保存</a-button>
        </div>
      </div>
    </a-modal>

    <!-- 删除确认弹窗 -->
    <a-modal
      v-model:open="deleteModalShow"
      :closable="false"
      :mask-closable="false"
      width="380px"
      :footer="null"
      wrap-class-name="save-flow-modal"
    >
      <div class="save-modal-content">
        <div class="save-modal-icon" style="color:#ff7875">🗑</div>
        <div class="save-modal-title">删除流程</div>
        <div class="save-modal-sub">确定要删除 "<strong style="color:#ff7875">{{ flowName }}</strong>" 吗？此操作不可恢复。</div>
        <div class="save-modal-actions">
          <a-button size="large" @click="deleteModalShow = false" class="cancel-btn">取消</a-button>
          <a-button size="large" danger @click="confirmDeleteFlow">确认删除</a-button>
        </div>
      </div>
    </a-modal>

    <!-- 执行配置弹窗 -->
    <a-modal
      v-model:open="execModalShow"
      :closable="false"
      :mask-closable="false"
      width="460px"
      :footer="null"
      wrap-class-name="save-flow-modal"
    >
      <div class="save-modal-content" style="text-align:left">
        <div class="save-modal-icon">🚀</div>
        <div class="save-modal-title">配置并执行</div>
        <div class="save-modal-sub">填写论文信息后开始执行流程</div>
        <a-form layout="vertical" size="middle">
          <a-form-item label="论文主题" required style="margin-bottom:12px">
            <a-input v-model:value="execForm.topic" placeholder="如：深度学习在医疗影像分割中的应用" />
          </a-form-item>
          <a-form-item label="详细描述" style="margin-bottom:12px">
            <a-textarea v-model:value="execForm.description" placeholder="研究方向、背景、预期目标..." :rows="2" />
          </a-form-item>
          <a-form-item label="关键词" style="margin-bottom:12px">
            <a-input v-model:value="execForm.keywords" placeholder="用逗号分隔，如：深度学习,医疗影像" />
          </a-form-item>
        </a-form>
        <div class="save-modal-actions">
          <a-button size="large" @click="execModalShow = false" class="cancel-btn">取消</a-button>
          <a-button size="large" type="primary" @click="confirmExecution" :disabled="!execForm.topic.trim()">🚀 开始执行</a-button>
        </div>
      </div>
    </a-modal>
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
import PaperNode from '@/components/flow/PaperNode.vue'
import ParticleBackground from '@/components/flow/ParticleBackground.vue'
import { listFlows, getFlow, createFlow, updateFlow, deleteFlow, createPaper, startWriting, getPaperList, getPaperDetail, listCustomAgents } from '@/api'
import { message } from 'ant-design-vue'

const nodeTypes = { agent: AgentNode, condition: ConditionNode, loop: LoopNode, paper: PaperNode }
const { zoomIn, zoomOut, fitView, screenToFlowCoordinate, addNodes, addEdges, removeNodes, removeEdges } = useVueFlow()

// ===== 编辑模式 =====
const isEditMode = ref(true)
const isExecuting = ref(false)
let execTimer = null

// ===== 节点面板 =====
const paletteItems = reactive([
  { type: 'PAPER', icon: '📄', label: '论文任务', color: '#9254de', custom: false },
  { type: 'SUPERVISOR', icon: '🧭', label: '导师 Agent', color: '#9254de', custom: false },
  { type: 'RESEARCHER', icon: '🔬', label: '研究员 Agent', color: '#1890ff', custom: false },
  { type: 'WRITER', icon: '✍️', label: '写作者 Agent', color: '#52c41a', custom: false },
  { type: 'REVIEWER', icon: '📝', label: '审稿人 Agent', color: '#fa8c16', custom: false },
  { type: 'POLISHER', icon: '✨', label: '润色师 Agent', color: '#13c2c2', custom: false }
])
let paletteCustomStartIdx = 6  // 自定义 agent 从第7个开始
const controlItems = [
  { type: 'CONDITION', icon: '⇢', label: '条件分支', color: '#faad14' },
  { type: 'LOOP', icon: '↺', label: '循环节点', color: '#597ef7' }
]

const ROLE_NAMES = reactive({ SUPERVISOR: '导师', RESEARCHER: '研究员', WRITER: '写作者', REVIEWER: '审稿人', POLISHER: '润色师' })
const CUSTOM_ROLE_ENTRIES = ref([])  // [{code, name, icon, model, temperature, systemPrompt, color}]
const roleOptions = computed(() => {
  const base = Object.entries(ROLE_NAMES).map(([k, v]) => ({ value: k, label: v }))
  const custom = CUSTOM_ROLE_ENTRIES.value.map(c => ({ value: c.code, label: c.icon + ' ' + c.name }))
  return [...base, ...custom]
})
const modelOptions = ['qwen-max', 'qwen-plus', 'qwen-turbo', 'deepseek-v3', 'deepseek-r1'].map(m => ({ value: m, label: m }))

// ===== 流程管理 =====
const availableFlows = ref([])
const currentFlowId = ref('standard')
const flowSource = ref('preset')    // 'preset' | 'custom' | 'new'
const flowDbId = ref(null)          // DB 中的 id
const flowName = ref('')
const flowDirty = ref(false)
const saveModalShow = ref(false)
const saveModalName = ref('')
const saveModalDesc = ref('')
const deleteModalShow = ref(false)
const execModalShow = ref(false)
const execForm = reactive({ topic: '', description: '', keywords: '' })

const flowSelectOptions = computed(() => {
  const groups = []
  const presets = availableFlows.value.filter(f => f.source === 'preset')
  const customs = availableFlows.value.filter(f => f.source === 'custom')
  if (presets.length) {
    groups.push({ label: '预设流程', options: presets.map(f => ({ value: f.id, label: f.name, category: f.category, source: 'preset' })) })
  }
  if (customs.length) {
    groups.push({ label: '我的流程', options: customs.map(f => ({ value: f.id, label: f.name, category: f.category, source: 'custom' })) })
  }
  return groups
})

// ===== 流程数据 =====
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
const editingPaperId = ref(null)
const canvasPaperTasks = ref([])

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

async function applyPaperSelect(paperId) {
  if (!selectedNode.value || selectedNode.value.type !== 'paper' || !paperId) return
  try {
    const res = await getPaperDetail(paperId)
    const paper = res?.data
    if (paper) {
      selectedNode.value.data = {
        ...selectedNode.value.data,
        label: '📄 ' + (paper.title || '论文任务'),
        config: { ...selectedNode.value.data?.config, paperId, paperTitle: paper.title }
      }
      pushHistory()
      message.success('已关联: ' + paper.title)
    }
  } catch (_) { message.error('获取论文信息失败') }
}

function applyEdit() {
  if (!selectedNode.value) return
  const n = selectedNode.value
  // 论文节点单独处理
  if (n.type === 'paper') {
    n.data = { ...n.data, label: editingLabel.value || n.data?.label }
    pushHistory()
    return
  }
  n.data = {
    ...n.data,
    agentRole: editingRole.value,
    label: (paletteItems.find(p => p.type === editingRole.value)?.icon || '🤖') + ' ' + (editingLabel.value || n.data?.label || ''),
    roleName: ROLE_NAMES[editingRole.value] || CUSTOM_ROLE_ENTRIES.value.find(c => c.code === editingRole.value)?.name || editingRole.value,
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
  if (dragItem.type === 'PAPER') {
    nodeType = 'paper'
    nodeData = { label: '📄 论文任务', stepIndex: 0, config: { paperId: null, paperTitle: '点击选择任务' }, status: 'pending' }
  } else if (isControl) {
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
    const custom = CUSTOM_ROLE_ENTRIES.value.find(c => c.code === role)
    nodeData = {
      agentRole: role,
      label: `${icon} ${dragItem.label.replace(' Agent', '')}`,
      roleName: ROLE_NAMES[role] || custom?.name || role,
      stepIndex: nodes.value.length + 1,
      config: { systemPrompt: custom?.systemPrompt || '', model: custom?.model || 'qwen-max', temperature: custom?.temperature ?? 0.7, timeout: 120, retryCount: 2, notes: '' },
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
    selectedNode.value = node  // 右击直接选中，右侧面板同步显示
    selectedEdge.value = null
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
function configureNode(node) {
  const target = node || selectedNode.value
  closeContextMenu()
  if (target) {
    selectedNode.value = null
    nextTick(() => { selectedNode.value = target })
  }
}

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
  markDirty()
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
  // 孤立节点
  const connectedIds = new Set()
  edges.value.forEach(e => { connectedIds.add(e.source); connectedIds.add(e.target) })
  nodes.value.filter(n => !connectedIds.has(n.id)).forEach(n => {
    errors.push(`节点 "${n.data?.label || n.id}" 未连接到任何边`)
  })
  // 环检测 — 跳过 loop 边
  const normalEdges = edges.value.filter(e => e.data?.conditionType !== 'loop')
  const adj = {}; nodes.value.forEach(n => { adj[n.id] = [] })
  normalEdges.forEach(e => { if (adj[e.source]) adj[e.source].push(e.target) })
  const state = {}; nodes.value.forEach(n => { state[n.id] = 0 }) // 0=unvisited, 1=visiting, 2=visited
  let cycleNodeId = null
  function dfs(id) {
    if (state[id] === 1) { cycleNodeId = id; return }
    if (state[id] === 2) return
    state[id] = 1
    ;(adj[id] || []).forEach(dfs)
    state[id] = 2
  }
  nodes.value.forEach(n => { if (state[n.id] === 0) dfs(n.id) })
  if (cycleNodeId) {
    const n = nodes.value.find(nn => nn.id === cycleNodeId)
    errors.push(`存在意外的循环依赖: "${n?.data?.label || cycleNodeId}" — 请将回退边标记为"回退边"`)
  }
  // 起始节点 — 所有边（含 loop）都算"有入边"
  const hasInEdge = new Set()
  edges.value.forEach(e => hasInEdge.add(e.target))
  const starts = nodes.value.filter(n => !hasInEdge.has(n.id))
  if (starts.length === 0 && nodes.value.length > 0) errors.push('未找到起始节点')
  if (starts.length > 1) {
    const names = starts.map(n => n.data?.label || n.id).join(', ')
    errors.push(`${starts.length} 个入口: ${names} — 建议只保留一个`)
  }

  const loopEdges = edges.value.filter(e => e.data?.conditionType === 'loop')
  validationResult.value = { valid: errors.length === 0, errors, loopCount: loopEdges.length }
  if (errors.length === 0) message.success(loopEdges.length ? `校验通过 (含 ${loopEdges.length} 条回退边)` : '流程校验通过')
  else message.warning(`发现 ${errors.length} 个问题`)
}

// ===== 执行引擎 =====
let sseSource = null

const execStatusText = computed(() => {
  const total = nodes.value.length
  const done = nodes.value.filter(n => n.data?.status === 'completed').length
  const active = nodes.value.find(n => n.data?.status === 'in_progress')
  if (active) return `${done}/${total} · ${active.data?.label || '执行中'}`
  return `${done}/${total}`
})

function resetAllStatus() {
  nodes.value.forEach(n => { n.data = { ...n.data, status: 'pending' } })
}

async function startExecution() {
  if (isExecuting.value) return
  if (nodes.value.length === 0) { message.warning('画布为空'); return }
  validationResult.value = null

  // 画布上有论文节点且已关联论文 → 直接用，不弹窗
  const paperNode = nodes.value.find(n => n.type === 'paper' && n.data?.config?.paperId)
  if (paperNode && flowSource.value === 'custom' && flowDbId.value) {
    execForm.topic = paperNode.data.config.paperTitle || ''
    execForm.description = ''
    execForm.keywords = ''
    confirmExecution(paperNode.data.config.paperId)
    return
  }

  // 自定义流程 → 弹出配置弹窗（没有论文节点时需要用户填写）
  if (flowSource.value === 'custom' && flowDbId.value) {
    execForm.topic = ''
    execForm.description = ''
    execForm.keywords = ''
    execModalShow.value = true
    return
  }

  // 预设流程 → 直接模拟执行
  resetAllStatus()
  isExecuting.value = true
  runSimulation()
}

async function confirmExecution(existingPaperId) {
  // 传入了已有论文 ID → 直接执行，不创建新论文
  if (existingPaperId) {
    execModalShow.value = false
    resetAllStatus()
    isExecuting.value = true
    try {
      const data = { topic: execForm.topic || '执行论文', flowId: 'custom-' + flowDbId.value }
      await startWriting(existingPaperId, data)
      message.info('已提交 FlowEngine 执行')
      connectSSE(existingPaperId)
      return
    } catch (e) {
      message.error('启动失败: ' + (e.message || ''))
      isExecuting.value = false
    }
    return
  }

  // 没有已有论文 → 弹窗填写并创建
  const topic = execForm.topic.trim()
  if (!topic) return
  execModalShow.value = false
  resetAllStatus()
  isExecuting.value = true

  try {
    const data = {
      topic,
      description: execForm.description.trim(),
      keywords: execForm.keywords.trim(),
      flowId: 'custom-' + flowDbId.value
    }
    const createRes = await createPaper(data)
    const paperId = createRes?.data?.paperId
    if (!paperId) throw new Error('创建论文失败')
    await startWriting(paperId, data)
    message.info('已提交 FlowEngine 执行')
    connectSSE(paperId)
    return
  } catch (e) {
    message.error('启动失败: ' + (e.message || ''))
    isExecuting.value = false
  }
}

function connectSSE(paperId) {
  const token = localStorage.getItem('paperai_token') || ''
  sseSource = new EventSource(`/api/paper/write/${paperId}/stream?token=${encodeURIComponent(token)}`)
  sseSource.addEventListener('node', (e) => {
    try {
      const d = JSON.parse(e.data)
      const n = nodes.value.find(nn => nn.id === d.nodeId)
      if (n) n.data = { ...n.data, status: d.status === 'in_progress' ? 'in_progress' : d.status === 'completed' ? 'completed' : d.status === 'failed' ? 'failed' : 'pending' }
    } catch (_) {}
  })
  sseSource.addEventListener('complete', () => {
    nodes.value.forEach(n => { n.data = { ...n.data, status: 'completed' } })
    isExecuting.value = false; sseSource.close(); sseSource = null
    message.success('FlowEngine 执行完成')
  })
  sseSource.addEventListener('error', () => {
    isExecuting.value = false; sseSource.close(); sseSource = null
    message.error('执行出错')
  })
}

function runSimulation() {
  // 将边按类型分类
  const forwardOut = {}  // nodeId -> [targetId]  正向边（normal + success + failure）
  const loopBack = {}    // nodeId -> [targetId]  回退边（loop）
  const edgeMap = {}     // (source,target) -> edge
  nodes.value.forEach(n => { forwardOut[n.id] = []; loopBack[n.id] = [] })
  edges.value.forEach(e => {
    edgeMap[e.source + '->' + e.target] = e
    if (e.data?.conditionType === 'loop') {
      if (loopBack[e.source]) loopBack[e.source].push(e.target)
    } else {
      if (forwardOut[e.source]) forwardOut[e.source].push(e.target)
    }
  })

  // 入口节点：没有任何入边的节点（含 loop 边）
  const allInEdges = new Set()
  edges.value.forEach(e => allInEdges.add(e.target))
  const entries = nodes.value.filter(n => !allInEdges.has(n.id))
  if (entries.length === 0) { message.warning('找不到起始节点'); isExecuting.value = false; return }

  // ── 执行状态 ──
  const loopCounter = {}     // nodeId -> 已循环次数
  const completed = new Set()
  let currentNodeId = entries[0].id
  let stepCount = 0
  const MAX_STEPS = 40

  function findNode(id) { return nodes.value.find(n => n.id === id) }

  function executeStep() {
    if (!isExecuting.value) return
    if (stepCount >= MAX_STEPS) { finishExecution('达最大步数限制'); return }

    const node = findNode(currentNodeId)
    if (!node) { finishExecution('节点丢失'); return }

    // 判断当前节点是否所有前置都已满足
    const predecessors = [] // 正常流程不需要检查 — 我们从拓扑入口顺序走

    // 标记执行中
    node.data = { ...node.data, status: 'in_progress' }
    stepCount++

    execTimer = setTimeout(() => {
      if (!isExecuting.value) return

      // 标记完成
      node.data = { ...node.data, status: 'completed' }
      completed.add(currentNodeId)

      // ── 决定下一个节点 ──
      let nextId = null

      if (node.type === 'condition') {
        // 条件节点：根据 conditionType 边的标签选择
        const passEdge = edges.value.find(e => e.source === node.id && e.data?.conditionType === 'success')
        const failEdge = edges.value.find(e => e.source === node.id && e.data?.conditionType === 'failure')
        // 模拟条件评估: 交替走 pass/fail 以演示两支
        const goPass = Math.random() > 0.4
        if (goPass && passEdge) {
          nextId = passEdge.target
          message.info('条件判断: ✓ 通过 → ' + (findNode(nextId)?.data?.label || nextId))
        } else if (failEdge) {
          nextId = failEdge.target
          message.info('条件判断: ✗ 不通过 → ' + (findNode(nextId)?.data?.label || nextId))
        } else if (passEdge) {
          nextId = passEdge.target
        }
      } else if (node.type === 'loop') {
        // 循环节点: track iterations
        const maxIter = node.data?.config?.maxIterations || 3
        const curIter = loopCounter[node.id] || 0
        const backTargets = loopBack[node.id] || []
        const forwardTargets = forwardOut[node.id] || []

        if (curIter < maxIter && backTargets.length > 0) {
          loopCounter[node.id] = curIter + 1
          nextId = backTargets[0]
          message.info(`循环: 第 ${curIter + 1}/${maxIter} 轮 → 回退到 "${findNode(nextId)?.data?.label || nextId}"`)
          // 重置回退路径上节点的状态
          resetPath(nextId, node.id)
        } else if (forwardTargets.length > 0) {
          nextId = forwardTargets[0]
          message.info(`循环结束: 已完成 ${curIter} 轮 → 继续前进`)
        }
      } else {
        // 普通 Agent 节点: 优先走正向边，其次走回退边
        const forwardTargets = forwardOut[node.id] || []
        const loopTargets = loopBack[node.id] || []
        if (forwardTargets.length > 0) {
          nextId = forwardTargets[0]
        } else if (loopTargets.length > 0) {
          nextId = loopTargets[0]
        }
      }

      if (!nextId) { finishExecution('流程到达终点'); return }
      currentNodeId = nextId
      executeStep()
    }, 800)
  }

  function resetPath(fromId, toId) {
    // 将 fromId 到 toId 之间所有节点的状态重置为 pending
    // 简化：找到 fromId 及其正向可达节点（在 toId 之前）
    const toReset = new Set()
    const queue = [fromId]
    while (queue.length) {
      const id = queue.shift()
      if (toReset.has(id) || completed.has(id) === false) continue
      if (id === toId) continue
      toReset.add(id)
      ;(forwardOut[id] || []).forEach(t => { if (t !== toId) queue.push(t) })
    }
    toReset.forEach(id => {
      const n = findNode(id)
      if (n && completed.has(id)) {
        n.data = { ...n.data, status: 'pending' }
        completed.delete(id)
      }
    })
  }

  function finishExecution(reason) {
    // 已完成节点保持 completed，其他保持 pending
    nodes.value.forEach(n => {
      if (!completed.has(n.id)) {
        // 那些从入口可达但未完成的，标记为 pending（已经是 default）
      }
    })
    isExecuting.value = false
    message.success(reason || '执行完成')
  }

  executeStep()
}

function stopExecution() {
  isExecuting.value = false
  clearTimeout(execTimer)
  if (sseSource) { sseSource.close(); sseSource = null }
  resetAllStatus()
}

// ===== 流程管理操作 =====
async function onFlowSelect(flowId) {
  if (!flowId) return
  currentFlowId.value = flowId
  flowDirty.value = false

  // 预设流程
  const presetFlow = availableFlows.value.find(f => f.id === flowId && f.source === 'preset')
  if (presetFlow) {
    flowSource.value = 'preset'
    flowDbId.value = null
    flowName.value = presetFlow.name
    loadPresetGraph(flowId)
    return
  }

  // 自定义流程 — 从 API 加载
  const customFlow = availableFlows.value.find(f => f.id === flowId && f.source === 'custom')
  if (customFlow && customFlow.dbId) {
    flowSource.value = 'custom'
    flowDbId.value = customFlow.dbId
    flowName.value = customFlow.name
    try {
      const res = await getFlow(customFlow.dbId)
      if (res.data?.graphData) {
        const data = JSON.parse(res.data.graphData)
        nodes.value = data.nodes || []
        edges.value = data.edges || []
        selectedNode.value = null
        history.value = []
        historyIndex.value = -1
        pushHistory()
        nextTick(() => fitView())
      }
    } catch (e) {
      message.error('加载流程失败')
    }
    return
  }
}

function loadPresetGraph(flowId) {
  const steps = DEFAULT_STEPS_MAP[flowId] || DEFAULT_STEPS_MAP.standard
  const ns = steps.map((s, i) => ({
    id: `node-${i}`,
    type: 'agent',
    position: { x: 280, y: i * 110 + 40 },
    data: { agentRole: s.role, label: s.label, roleName: ROLE_NAMES[s.role] || s.role, stepIndex: i + 1, config: { systemPrompt: '', model: 'qwen-max', temperature: 0.7, timeout: 120, retryCount: 2, notes: '' }, status: 'pending' }
  }))
  const es = []
  for (let i = 0; i < ns.length - 1; i++) {
    es.push({
      id: `edge-${i}-${i + 1}`, source: ns[i].id, target: ns[i + 1].id,
      type: 'smoothstep', animated: true,
      style: { stroke: 'rgba(255,255,255,0.18)', strokeWidth: 1.5 },
      markerEnd: { type: MarkerType.ArrowClosed, color: 'rgba(255,255,255,0.3)', width: 16, height: 16 },
      data: { label: '', conditionType: 'normal' }
    })
  }
  nodes.value = ns
  edges.value = es
  selectedNode.value = null
  history.value = []
  historyIndex.value = -1
  pushHistory()
  nextTick(() => fitView())
}

const DEFAULT_STEPS_MAP = {
  standard: [
    { role: 'SUPERVISOR', label: '🧭 选题评估' }, { role: 'RESEARCHER', label: '🔬 文献调研' },
    { role: 'SUPERVISOR', label: '📋 大纲审阅' }, { role: 'WRITER', label: '✍️ 引言' },
    { role: 'WRITER', label: '✍️ 方法' }, { role: 'WRITER', label: '✍️ 实验' }, { role: 'WRITER', label: '✍️ 结论' },
    { role: 'REVIEWER', label: '📝 审稿迭代' }, { role: 'POLISHER', label: '✨ 润色定稿' }, { role: 'SUPERVISOR', label: '✅ 最终审核' }
  ],
  quick_draft: [
    { role: 'RESEARCHER', label: '🔬 文献调研' }, { role: 'SUPERVISOR', label: '📋 大纲审阅' },
    { role: 'WRITER', label: '✍️ 引言' }, { role: 'WRITER', label: '✍️ 方法' }, { role: 'WRITER', label: '✍️ 实验' }, { role: 'WRITER', label: '✍️ 结论' },
    { role: 'POLISHER', label: '✨ 润色定稿' }, { role: 'SUPERVISOR', label: '✅ 最终审核' }
  ],
  deep_research: [
    { role: 'SUPERVISOR', label: '🧭 选题评估' }, { role: 'RESEARCHER', label: '🔬 深度文献调研' },
    { role: 'SUPERVISOR', label: '📋 大纲审阅' }, { role: 'WRITER', label: '✍️ 引言' },
    { role: 'WRITER', label: '✍️ 方法' }, { role: 'WRITER', label: '✍️ 实验' }, { role: 'WRITER', label: '✍️ 结论' },
    { role: 'REVIEWER', label: '📝 审稿迭代 ×5' }, { role: 'POLISHER', label: '✨ 润色定稿' }, { role: 'SUPERVISOR', label: '✅ 最终审核' }
  ],
  write_only: [
    { role: 'WRITER', label: '✍️ 引言' }, { role: 'WRITER', label: '✍️ 方法' }, { role: 'WRITER', label: '✍️ 实验' }, { role: 'WRITER', label: '✍️ 结论' },
    { role: 'POLISHER', label: '✨ 润色定稿' }, { role: 'SUPERVISOR', label: '✅ 最终审核' }
  ],
  review_paper: [
    { role: 'RESEARCHER', label: '🔬 深度文献调研' }, { role: 'SUPERVISOR', label: '📋 大纲审阅' },
    { role: 'WRITER', label: '✍️ 引言' }, { role: 'WRITER', label: '✍️ 方法' }, { role: 'WRITER', label: '✍️ 实验' }, { role: 'WRITER', label: '✍️ 结论' },
    { role: 'POLISHER', label: '✨ 润色定稿' }, { role: 'SUPERVISOR', label: '✅ 最终审核' }
  ]
}

function handleNewFlow() {
  flowSource.value = 'new'
  flowDbId.value = null
  flowName.value = ''
  currentFlowId.value = null
  flowDirty.value = false
  nodes.value = []
  edges.value = []
  selectedNode.value = null
  history.value = []
  historyIndex.value = -1
}

async function handleSaveFlow() {
  if (nodes.value.length === 0) { message.warning('画布为空，请先添加节点'); return }
  // 已有 DB 记录 → 直接更新
  if (flowDbId.value) {
    try {
      const data = { name: flowName.value, graphData: JSON.stringify({ nodes: nodes.value, edges: edges.value }) }
      await updateFlow(flowDbId.value, data)
      flowDirty.value = false
      message.success('流程已保存')
      return
    } catch (e) {
      message.error('保存失败: ' + (e.message || ''))
      return
    }
  }
  // 没有 DB 记录 → 走另存为
  handleSaveAsFlow()
}

function handleSaveAsFlow() {
  if (nodes.value.length === 0) { message.warning('画布为空，请先添加节点'); return }
  saveModalName.value = flowDbId.value ? flowName.value + ' (副本)' : (flowName.value || '')
  saveModalDesc.value = ''
  saveModalShow.value = true
}

async function confirmSaveAs() {
  const name = saveModalName.value.trim()
  if (!name) return
  saveModalShow.value = false
  try {
    const data = {
      name, description: saveModalDesc.value.trim(),
      graphData: JSON.stringify({ nodes: nodes.value, edges: edges.value }),
      category: 'custom'
    }
    const res = await createFlow(data)
    const dbId = res.data?.id
    flowSource.value = 'custom'
    flowDbId.value = dbId
    flowName.value = name
    flowDirty.value = false
    currentFlowId.value = 'custom-' + dbId
    const flowRes = await listFlows()
    availableFlows.value = flowRes.data || []
    saveModalName.value = ''
    saveModalDesc.value = ''
    message.success('流程已保存: ' + name)
  } catch (e) {
    message.error('保存失败: ' + (e.message || ''))
  }
}

function handleDeleteFlow() {
  if (!flowDbId.value) return
  deleteModalShow.value = true
}

async function confirmDeleteFlow() {
  deleteModalShow.value = false
  try {
    await deleteFlow(flowDbId.value)
    message.success('流程已删除')
    flowSource.value = 'preset'
    flowDbId.value = null
    currentFlowId.value = 'standard'
    loadPresetGraph('standard')
    const flowRes = await listFlows()
    availableFlows.value = flowRes.data || []
  } catch (e) {
    message.error('删除失败: ' + (e.message || ''))
  }
}

// 标记脏数据
function markDirty() {
  if (flowSource.value === 'preset' && nodes.value.length > 0) {
    flowSource.value = 'new'
    flowDbId.value = null
    currentFlowId.value = null
  }
  flowDirty.value = true
}

// ===== 工具 =====
function roleColor(role) {
  const m = { SUPERVISOR: 'purple', RESEARCHER: 'blue', WRITER: 'green', REVIEWER: 'orange', POLISHER: 'cyan' }
  if (m[role]) return m[role]
  const c = CUSTOM_ROLE_ENTRIES.value.find(x => x.code === role)
  return c?.color || 'default'
}

// ===== 初始化 =====
onMounted(async () => {
  try {
    const res = await listFlows()
    availableFlows.value = res.data || []
  } catch (_) {}
  // 加载自定义 Agent 到面板和角色列表
  try {
    const caRes = await listCustomAgents()
    const customAgents = (caRes.data || []).filter(a => a.enabled !== 0)
    CUSTOM_ROLE_ENTRIES.value = customAgents.map(a => ({
      code: 'CUSTOM_' + a.id,
      name: a.name,
      icon: a.icon || '🤖',
      model: a.model || 'qwen-max',
      temperature: a.temperature != null ? a.temperature : 0.7,
      systemPrompt: a.systemPrompt || '',
      color: '#8b5cf6',
      id: a.id
    }))
    // 添加到面板
    paletteItems.splice(paletteCustomStartIdx)  // 移除旧的
    CUSTOM_ROLE_ENTRIES.value.forEach(c => {
      paletteItems.push({ type: c.code, icon: c.icon, label: c.name, color: c.color, custom: true })
    })
  } catch (_) {}

  // 加载论文任务（供论文节点选择）
  try {
    const paperRes = await getPaperList()
    canvasPaperTasks.value = (paperRes.data || []).filter(p => p.status === 'DRAFT' || p.status === 'FAILED')
  } catch (_) {}

  // 恢复上次编辑的流程，否则加载默认标准流程
  const savedFlowId = localStorage.getItem('paperai_flow_id')
  const savedFlowSource = localStorage.getItem('paperai_flow_source')
  const savedGraphData = localStorage.getItem('paperai_flow_graph')

  if (savedFlowId && savedFlowSource) {
    currentFlowId.value = savedFlowId
    flowSource.value = savedFlowSource

    if (savedFlowSource === 'preset') {
      loadPresetGraph(savedFlowId)
    } else if (savedFlowSource === 'custom' && savedGraphData) {
      try {
        const data = JSON.parse(savedGraphData)
        nodes.value = data.nodes || []
        edges.value = data.edges || []
        pushHistory()
      } catch (_) {
        loadPresetGraph('standard')
      }
    }
    // 尝试从 API 刷新自定义流程
    if (savedFlowSource === 'custom') {
      const match = savedFlowId.match(/^custom-(\d+)$/)
      if (match) {
        try {
          const res = await getFlow(Number(match[1]))
          if (res.data?.graphData) {
            const data = JSON.parse(res.data.graphData)
            nodes.value = data.nodes || []
            edges.value = data.edges || []
            flowDbId.value = Number(match[1])
            pushHistory()
          }
        } catch (_) {}
      }
    }
  } else {
    loadPresetGraph('standard')
  }
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

/* 执行状态栏 */
.exec-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 20px;
  background: rgba(89,126,247,0.1);
  border-bottom: 1px solid rgba(89,126,247,0.2);
  flex-shrink: 0; z-index: 9;
  font-size: 12px; color: #85a5ff; font-weight: 500;
}
.exec-bar-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #597ef7;
  animation: execDotPulse 0.8s ease-in-out infinite;
}
@keyframes execDotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.5); }
}
.exec-bar-hint { margin-left: auto; font-size: 10px; color: rgba(89,126,247,0.4); }

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

<style>
/* ===== 保存流程弹窗（全局样式） ===== */
.save-flow-modal .ant-modal-content {
  background: linear-gradient(145deg, rgba(18,24,42,0.98) 0%, rgba(15,20,38,0.96) 100%) !important;
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255,255,255,0.08) !important;
  border-radius: 16px !important;
  overflow: hidden;
}
.save-flow-modal .ant-modal-body {
  padding: 0 !important;
}
.save-modal-content {
  padding: 36px 32px 28px;
  text-align: center;
}
.save-modal-icon { font-size: 40px; margin-bottom: 8px; }
.save-modal-title { font-size: 18px; font-weight: 700; color: #fff; margin-bottom: 4px; }
.save-modal-sub { font-size: 12px; color: rgba(255,255,255,0.35); margin-bottom: 20px; }
.save-modal-input, .save-modal-textarea {
  background: rgba(255,255,255,0.04) !important;
  border: 1px solid rgba(255,255,255,0.08) !important;
  border-radius: 10px !important;
  color: #fff !important;
  font-size: 14px !important;
  margin-bottom: 14px;
}
.save-modal-textarea textarea {
  background: transparent !important;
  color: #fff !important;
}
.save-modal-input:focus, .save-modal-textarea:focus,
.save-modal-input:hover, .save-modal-textarea:hover {
  border-color: rgba(146,84,222,0.4) !important;
}
.save-modal-actions {
  display: flex; gap: 12px; justify-content: center;
}
.save-modal-actions .cancel-btn {
  background: rgba(255,255,255,0.04) !important;
  border-color: rgba(255,255,255,0.1) !important;
  color: rgba(255,255,255,0.6) !important;
}
.save-modal-actions .cancel-btn:hover {
  background: rgba(255,255,255,0.08) !important;
  color: #fff !important;
}
</style>
