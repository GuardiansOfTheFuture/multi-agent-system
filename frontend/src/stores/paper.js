import { defineStore } from 'pinia'
import { writePaper, getPaperList, getPaperDetail, deletePaper } from '@/api'

export const usePaperStore = defineStore('paper', {
  state: () => ({
    paperList: [],
    currentPaper: null,
    currentTasks: [],
    isWriting: false,
    currentSteps: [],
    streamMessages: [],
    loading: false,
    paperTotal: 0,
    paperPage: 1,
    paperSize: 10
  }),

  actions: {
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

    async fetchPaperList(page = 1) {
      this.loading = true
      try {
        const res = await getPaperList(page, this.paperSize)
        const data = res.data || {}
        this.paperList = data.records || []
        this.paperTotal = data.total || 0
        this.paperPage = page
      } finally {
        this.loading = false
      }
    },

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

    async removePaper(id) {
      await deletePaper(id)
      this.paperList = this.paperList.filter(p => p.id !== id)
      this.paperTotal = Math.max(0, this.paperTotal - 1)
    }
  }
})
