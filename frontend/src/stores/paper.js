import { defineStore } from 'pinia'
import { writePaper, getPaperList, getPaperDetail, deletePaper } from '@/api'

export const usePaperStore = defineStore('paper', {
  state: () => ({
    // 论文列表
    paperList: [],
    // 当前查看的论文
    currentPaper: null,
    // 当前论文的任务记录
    currentTasks: [],
    // 写作执行状态
    isWriting: false,
    // 当前写作执行步骤
    currentSteps: [],
    // SSE 消息流
    streamMessages: [],
    // 加载状态
    loading: false
  }),

  actions: {
    /** 提交论文写作任务 */
    async submitWriting(data) {
      this.isWriting = true
      this.currentSteps = []
      this.streamMessages = []
      try {
        const res = await writePaper(data)
        if (res.data) {
          this.currentSteps = res.data.steps || []
          return res.data
        }
        return null
      } finally {
        this.isWriting = false
      }
    },

    /** 获取论文列表 */
    async fetchPaperList() {
      this.loading = true
      try {
        const res = await getPaperList()
        this.paperList = res.data || []
      } finally {
        this.loading = false
      }
    },

    /** 获取论文详情 */
    async fetchPaperDetail(id) {
      this.loading = true
      try {
        const res = await getPaperDetail(id)
        this.currentPaper = res.data
        return res.data
      } finally {
        this.loading = false
      }
    },

    /** 删除论文 */
    async removePaper(id) {
      await deletePaper(id)
      this.paperList = this.paperList.filter(p => p.id !== id)
    }
  }
})
