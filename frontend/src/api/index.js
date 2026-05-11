import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 300000 // 5 分钟，因为 Agent 调用 LLM 比较慢
})

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200 && res.code !== 0) {
      console.error('API Error:', res.message)
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    console.error('Request Error:', error.message)
    return Promise.reject(error)
  }
)

// ===== 论文写作 =====

/** 全流程论文写作 */
export function writePaper(data) {
  return request.post('/paper/write', data)
}

/** 构建 SSE 流连接 — 实时接收每步推送 */
export function createWriteStream(data) {
  const abort = new AbortController()
  const stream = {
    onStep: null,
    onError: null,
    onDone: null,
    abort: () => abort.abort(),
    start: async () => {
      try {
        const resp = await fetch('/api/paper/write/stream', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data),
          signal: abort.signal
        })
        if (!resp.ok) throw new Error('SSE 连接失败: ' + resp.status)

        const reader = resp.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          for (const line of lines) {
            if (line.startsWith('data:')) {
              try {
                stream.onStep?.(JSON.parse(line.slice(5).trim()))
              } catch (_) { /* skip non-JSON */ }
            }
          }
        }
        stream.onDone?.()
      } catch (e) {
        if (e.name !== 'AbortError') stream.onError?.(e)
      }
    }
  }
  return stream
}

/** 单步文献调研 */
export function doResearch(data) {
  return request.post('/paper/research', data)
}

/** 获取论文列表 */
export function getPaperList() {
  return request.get('/paper/list')
}

/** 获取论文详情 */
export function getPaperDetail(id) {
  return request.get(`/paper/${id}`)
}

/** 获取论文的任务记录 */
export function getPaperTasks(id) {
  return request.get(`/paper/${id}/tasks`)
}

/** 删除论文 */
export function deletePaper(id) {
  return request.delete(`/paper/${id}`)
}

// ===== Agent 调试 =====

/** 获取 Agent 列表 */
export function getAgentList() {
  return request.get('/agent/list')
}

/** 单 Agent 对话 */
export function chatWithAgent(agentName, topic, message) {
  return request.post(`/agent/${agentName}/chat`, null, {
    params: { topic, message }
  })
}

// ===== 健康检查 =====

export function healthCheck() {
  return request.get('/paper/health')
}

export default request
