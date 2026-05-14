import dagre from 'dagre'

const ROLE_NAME_MAP = {
  SUPERVISOR: '导师',
  RESEARCHER: '研究员',
  WRITER: '写作者',
  REVIEWER: '审稿人',
  POLISHER: '润色师'
}

// 5 个流程的步骤定义 — 与 WritePaper.vue 中 FLOW_STEP_MAP 保持一致
const FLOW_STEP_MAP = {
  standard: [
    { icon: '🧭', name: '选题评估', role: 'SUPERVISOR' },
    { icon: '🔬', name: '文献调研', role: 'RESEARCHER' },
    { icon: '📋', name: '大纲审阅', role: 'SUPERVISOR' },
    { icon: '✍️', name: '引言', role: 'WRITER' },
    { icon: '✍️', name: '相关工作', role: 'WRITER' },
    { icon: '✍️', name: '方法', role: 'WRITER' },
    { icon: '✍️', name: '实验', role: 'WRITER' },
    { icon: '✍️', name: '结论', role: 'WRITER' },
    { icon: '📝', name: '审稿迭代', role: 'REVIEWER' },
    { icon: '✨', name: '润色定稿', role: 'POLISHER' },
    { icon: '✅', name: '最终审核', role: 'SUPERVISOR' }
  ],
  quick_draft: [
    { icon: '🔬', name: '文献调研', role: 'RESEARCHER' },
    { icon: '📋', name: '大纲审阅', role: 'SUPERVISOR' },
    { icon: '✍️', name: '引言', role: 'WRITER' },
    { icon: '✍️', name: '相关工作', role: 'WRITER' },
    { icon: '✍️', name: '方法', role: 'WRITER' },
    { icon: '✍️', name: '实验', role: 'WRITER' },
    { icon: '✍️', name: '结论', role: 'WRITER' },
    { icon: '✨', name: '润色定稿', role: 'POLISHER' },
    { icon: '✅', name: '最终审核', role: 'SUPERVISOR' }
  ],
  deep_research: [
    { icon: '🧭', name: '选题评估', role: 'SUPERVISOR' },
    { icon: '🔬', name: '深度文献调研', role: 'RESEARCHER' },
    { icon: '📋', name: '大纲审阅', role: 'SUPERVISOR' },
    { icon: '✍️', name: '引言', role: 'WRITER' },
    { icon: '✍️', name: '相关工作', role: 'WRITER' },
    { icon: '✍️', name: '方法', role: 'WRITER' },
    { icon: '✍️', name: '实验', role: 'WRITER' },
    { icon: '✍️', name: '结论', role: 'WRITER' },
    { icon: '📝', name: '审稿迭代 ×5', role: 'REVIEWER' },
    { icon: '✨', name: '润色定稿', role: 'POLISHER' },
    { icon: '✅', name: '最终审核', role: 'SUPERVISOR' }
  ],
  write_only: [
    { icon: '✍️', name: '引言', role: 'WRITER' },
    { icon: '✍️', name: '相关工作', role: 'WRITER' },
    { icon: '✍️', name: '方法', role: 'WRITER' },
    { icon: '✍️', name: '实验', role: 'WRITER' },
    { icon: '✍️', name: '结论', role: 'WRITER' },
    { icon: '✨', name: '润色定稿', role: 'POLISHER' },
    { icon: '✅', name: '最终审核', role: 'SUPERVISOR' }
  ],
  review_paper: [
    { icon: '🔬', name: '深度文献调研', role: 'RESEARCHER' },
    { icon: '📋', name: '大纲审阅', role: 'SUPERVISOR' },
    { icon: '✍️', name: '引言', role: 'WRITER' },
    { icon: '✍️', name: '相关工作', role: 'WRITER' },
    { icon: '✍️', name: '方法', role: 'WRITER' },
    { icon: '✍️', name: '实验', role: 'WRITER' },
    { icon: '✍️', name: '结论', role: 'WRITER' },
    { icon: '✨', name: '润色定稿', role: 'POLISHER' },
    { icon: '✅', name: '最终审核', role: 'SUPERVISOR' }
  ]
}

const ROLE_COLORS = {
  SUPERVISOR: '#722ed1',
  RESEARCHER: '#1890ff',
  WRITER: '#52c41a',
  REVIEWER: '#fa8c16',
  POLISHER: '#13c2c2'
}

/**
 * 用 dagre 对流程步骤做自动布局，返回 Vue Flow 可用的 nodes + edges
 * @param {string} flowId - 流程 ID
 * @returns {{ nodes: Array, edges: Array }}
 */
export function buildFlowGraph(flowId) {
  const steps = FLOW_STEP_MAP[flowId] || FLOW_STEP_MAP.standard

  // dagre 图
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: 'TB', nodesep: 60, ranksep: 80, marginx: 40, marginy: 40 })

  // 添加节点
  const nodeIds = []
  steps.forEach((step, i) => {
    const id = `node-${i}`
    nodeIds.push(id)
    g.setNode(id, { width: 140, height: 60 })
  })

  // 添加边：串行连接
  for (let i = 0; i < steps.length - 1; i++) {
    g.setEdge(nodeIds[i], nodeIds[i + 1])
  }

  // 执行布局
  dagre.layout(g)

  // 转换为 Vue Flow 格式
  const nodes = steps.map((step, i) => {
    const id = nodeIds[i]
    const dagreNode = g.node(id)
    return {
      id,
      type: 'agent',
      position: {
        x: dagreNode.x - 70,  // 居中（宽度 140 的一半）
        y: dagreNode.y - 30    // 居中（高度 60 的一半）
      },
      data: {
        agentRole: step.role,
        label: `${step.icon} ${step.name}`,
        roleName: ROLE_NAME_MAP[step.role] || step.role,
        color: ROLE_COLORS[step.role] || '#666',
        stepIndex: i + 1
      }
    }
  })

  const edges = []
  for (let i = 0; i < steps.length - 1; i++) {
    edges.push({
      id: `edge-${i}-${i + 1}`,
      source: nodeIds[i],
      target: nodeIds[i + 1],
      type: 'smoothstep',
      animated: false,
      style: { stroke: '#b0b0b0', strokeWidth: 2 }
    })
  }

  return { nodes, edges }
}

/**
 * 获取流程的步骤列表（供配置面板使用）
 */
export function getFlowSteps(flowId) {
  return FLOW_STEP_MAP[flowId] || FLOW_STEP_MAP.standard
}
