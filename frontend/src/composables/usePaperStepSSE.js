import { ref, watch, onUnmounted } from 'vue'

/**
 * 通过 SSE (Server-Sent Events) 订阅论文写作进度
 * 替代原来的 WebSocket STOMP 方案，更简单可靠
 *
 * @param {import('vue').Ref<number|null>} paperIdRef paperId 的 ref
 * @param {object} callbacks { onStep, onComplete, onError, onStream }
 */
export function usePaperStepSSE(paperIdRef, { onStep, onComplete, onError, onStream }) {
  const connected = ref(false)
  let eventSource = null

  function doConnect(pid) {
    doDisconnect()
    if (!pid) return

    const token = localStorage.getItem('paperai_token')
    const url = `/api/paper/write/${pid}/stream?token=${encodeURIComponent(token || '')}`

    eventSource = new EventSource(url)

    eventSource.addEventListener('connected', (e) => {
      connected.value = true
      console.log('[SSE] 已连接 paperId=', pid)
    })

    eventSource.addEventListener('step', (e) => {
      try {
        const step = JSON.parse(e.data)
        onStep?.(step)
      } catch (_) {}
    })

    eventSource.addEventListener('stream', (e) => {
      try {
        const data = JSON.parse(e.data)
        onStream?.(data)
      } catch (_) {}
    })

    eventSource.addEventListener('complete', (e) => {
      onComplete?.()
      doDisconnect()
    })

    eventSource.addEventListener('error', (e) => {
      if (e.data) {
        try {
          const data = JSON.parse(e.data)
          onError?.(data.error || '未知错误')
        } catch (_) {
          onError?.('未知错误')
        }
      }
      // EventSource 会自动重连，但如果 paperId 已失效则断开
      connected.value = false
      doDisconnect()
    })

    // EventSource 自身错误（网络断开等）
    eventSource.onerror = () => {
      connected.value = false
    }
  }

  function doDisconnect() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    connected.value = false
  }

  // 监听 paperIdRef 变化
  watch(paperIdRef, (val, old) => {
    if (val && val !== old) doConnect(val)
    if (!val) doDisconnect()
  }, { immediate: true })

  onUnmounted(() => doDisconnect())

  return { connected }
}
