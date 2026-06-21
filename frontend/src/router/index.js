import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noLayout: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', noLayout: true }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'ChatView',
        component: () => import('@/views/ChatView.vue'),
        meta: { title: 'AI 写作' }
      },
      {
        path: 'write',
        name: 'WritePaper',
        component: () => import('@/views/WritePaper.vue'),
        meta: { title: '论文写作（表单）' }
      },
      {
        path: 'papers',
        name: 'PaperList',
        component: () => import('@/views/PaperList.vue'),
        meta: { title: '论文列表' }
      },
      {
        path: 'paper/:id',
        name: 'PaperDetail',
        component: () => import('@/views/PaperDetail.vue'),
        meta: { title: '论文详情' }
      },
      {
        path: 'agents',
        name: 'AgentList',
        component: () => import('@/views/AgentList.vue'),
        meta: { title: 'Agent 配置' }
      },
      {
        path: 'flow',
        name: 'FlowCanvas',
        component: () => import('@/views/FlowCanvas.vue'),
        meta: { title: '流程画布' }
      },
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '知识库' }
      },
      {
        path: 'knowledge-graph',
        name: 'KnowledgeGraph',
        component: () => import('@/views/KnowledgeGraph.vue'),
        meta: { title: '知识图谱' }
      },
      {
        path: 'script-playground',
        name: 'ScriptPlayground',
        component: () => import('@/views/ScriptPlayground.vue'),
        meta: { title: '脚本解析器' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('paperai_token')
  if (to.meta.noLayout) {
    next()
  } else if (!token) {
    next('/login')
  } else {
    next()
  }
})

export default router
