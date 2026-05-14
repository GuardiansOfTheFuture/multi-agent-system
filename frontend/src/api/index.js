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
      // 提取友好错误消息
      const friendlyMsg = extractErrorMessage(res)
      return Promise.reject(new Error(friendlyMsg))
    }
    return res
  },
  error => {
    console.error('Request Error:', error.message)
    // 网络错误或 HTTP 错误
    let userMsg = '网络请求失败，请检查网络连接'
    if (error.response) {
      const res = error.response.data
      if (res && res.message) {
        userMsg = res.message
      } else if (error.response.status === 404) {
        userMsg = '请求的资源不存在'
      } else if (error.response.status === 500) {
        userMsg = '服务器内部错误，请稍后重试'
      } else if (error.response.status >= 400) {
        userMsg = '请求失败 (' + error.response.status + ')'
      }
    } else if (error.code === 'ECONNABORTED') {
      userMsg = '请求超时，AI 服务响应较慢，请稍后重试'
    }
    return Promise.reject(new Error(userMsg))
  }
)

/**
 * 从后端返回的 Result 对象中提取对用户友好的错误消息
 */
function extractErrorMessage(res) {
  if (!res) return '未知错误'
  let msg = res.message || '请求失败'

  // 去掉 HTTP 状态码前缀（如 "400 - "）
  msg = msg.replace(/^\d{3}\s*-?\s*/, '').trim()

  // 尝试解析 DashScope JSON 格式的错误
  const dashScopeMatch = msg.match(/"code"\s*:\s*"([^"]+)"\s*,\s*"message"\s*:\s*"([^"]+)"/
  )
  if (dashScopeMatch) {
    const dCode = dashScopeMatch[1]
    const dMsg = dashScopeMatch[2]
    if (dCode === 'InvalidParameter') {
      return 'AI 模型参数错误，请检查模型名称和配置'
    }
    if (dCode === 'InvalidApiKey') {
      return 'AI 服务密钥无效，请联系管理员'
    }
    return 'AI 服务错误：' + dMsg
  }

  // 截断过长的错误消息
  if (msg.length > 150) {
    msg = msg.substring(0, 150) + '...'
  }
  return msg
}

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

// ===== 流程管理 =====
export function getFlowList() { return request.get('/flow/list') }  // 兼容旧调用
export function listFlows() { return request.get('/flow/list') }
export function getFlow(id) { return request.get(`/flow/${id}`) }
export function createFlow(data) { return request.post('/flow', data) }
export function updateFlow(id, data) { return request.put(`/flow/${id}`, data) }
export function deleteFlow(id) { return request.delete(`/flow/${id}`) }
export function duplicateFlow(id) { return request.post(`/flow/${id}/duplicate`) }

// ===== 版本管理 =====
export function getPaperVersions(paperId) { return request.get(`/paper/${paperId}/versions`) }
export function getPaperVersion(paperId, versionNo) { return request.get(`/paper/${paperId}/versions/${versionNo}`) }
export function getLatestVersion(paperId) { return request.get(`/paper/${paperId}/versions/latest`) }

// ===== 手动编辑 & Agent 修改 =====
export function updatePaperContent(paperId, versionNo, content) {
  return request.put(`/paper/${paperId}/content`, { versionNo, content })
}
export function agentEditPaper(paperId, selectedText, instruction) {
  return request.post(`/paper/${paperId}/agent-edit`, { selectedText, instruction })
}
export function savePaperVersion(paperId, content, summary, editType = 'MANUAL', changeSummary = '') {
  return request.post(`/paper/${paperId}/versions`, { content, summary, editType, changeSummary })
}

export default request
