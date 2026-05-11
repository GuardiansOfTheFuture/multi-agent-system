import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/write',
    children: [
      {
        path: 'write',
        name: 'WritePaper',
        component: () => import('@/views/WritePaper.vue'),
        meta: { title: '论文写作' }
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
