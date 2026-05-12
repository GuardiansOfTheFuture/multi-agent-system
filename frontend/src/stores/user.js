import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getProfile } from '@/api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('paperai_token') || '')
  const user = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  async function doLogin(username, password) {
    const res = await login({ username, password })
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('paperai_token', res.data.token)
    localStorage.setItem('paperai_user', JSON.stringify(res.data.user))
  }

  async function doRegister(username, password, email) {
    const res = await register({ username, password, email })
    token.value = res.data.token
    user.value = res.data.user
    localStorage.setItem('paperai_token', res.data.token)
    localStorage.setItem('paperai_user', JSON.stringify(res.data.user))
  }

  async function fetchProfile() {
    try {
      const res = await getProfile()
      user.value = res.data
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('paperai_token')
    localStorage.removeItem('paperai_user')
  }

  // 初始化：从 localStorage 恢复用户信息
  function init() {
    const saved = localStorage.getItem('paperai_user')
    if (saved) {
      try { user.value = JSON.parse(saved) } catch {}
    }
  }
  init()

  return { token, user, isLoggedIn, doLogin, doRegister, fetchProfile, logout }
})
