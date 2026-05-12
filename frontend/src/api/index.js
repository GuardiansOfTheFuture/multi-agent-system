import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 300000
})

// 请求拦截器：自动带 Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('paperai_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200 && res.code !== 0) {
      if (res.code === 401) {
        localStorage.removeItem('paperai_token')
        localStorage.removeItem('paperai_user')
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    console.error('Request Error:', error.message)
    return Promise.reject(error)
  }
)

// ===== 认证 =====
export function login(data) { return request.post('/auth/login', data) }
export function register(data) { return request.post('/auth/register', data) }
export function getProfile() { return request.get('/user/me') }

// ===== 论文 =====
export function createPaper(data) { return request.post('/paper/create', data) }
export function startWriting(paperId, data) { return request.post(`/paper/write/${paperId}`, data) }
export function stopWriting(paperId) { return request.post(`/paper/write/${paperId}/stop`) }
export function writePaper(data) { return request.post('/paper/write', data) }
export function doResearch(data) { return request.post('/paper/research', data) }
export function getPaperList() { return request.get('/paper/list') }
export function getPaperDetail(id) { return request.get(`/paper/${id}`) }
export function getPaperTasks(id) { return request.get(`/paper/${id}/tasks`) }
export function deletePaper(id) { return request.delete(`/paper/${id}`) }
export function getAgentList() { return request.get('/agent/list') }
export function chatWithAgent(agentName, topic, message) {
  return request.post(`/agent/${agentName}/chat`, null, { params: { topic, message } })
}
export function healthCheck() { return request.get('/paper/health') }

export default request
