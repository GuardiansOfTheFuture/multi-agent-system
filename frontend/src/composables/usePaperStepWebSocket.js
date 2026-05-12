import { ref, watch } from 'vue'
import { Client } from '@stomp/stompjs'

/**
 * 订阅论文写作步骤的 WebSocket STOMP hook
 * 接收响应的 paperId ref，自动连接/断开
 *
 * @param {import('vue').Ref<number|null>} paperIdRef paperId 的 ref
 * @param {object} callbacks { onStep, onComplete, onError, onStream }
 */
export function usePaperStepWebSocket(paperIdRef, { onStep, onComplete, onError, onStream }) {
  const connected = ref(false)
  let client = null

  function doConnect(pid) {
    if (client) doDisconnect()
    if (!pid) return

    const wsUrl = `ws://${window.location.hostname}:8081/ws`

    client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (str) => console.log('[STOMP]', str),
      onConnect: () => {
        connected.value = true

        client.subscribe(`/topic/paper/${pid}/step`, (msg) => {
          try { onStep?.(JSON.parse(msg.body)) } catch (_) {}
        })

        // 新增：流式 token 推送
        client.subscribe(`/topic/paper/${pid}/stream`, (msg) => {
          try { onStream?.(JSON.parse(msg.body)) } catch (_) {}
        })

        client.subscribe(`/topic/paper/${pid}/complete`, () => {
          onComplete?.()
        })

        client.subscribe(`/topic/paper/${pid}/error`, (msg) => {
          try { onError?.(JSON.parse(msg.body).error || '未知错误') } catch (_) { onError?.('未知错误') }
        })
      },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => {
        const errMsg = frame.headers?.message || 'STOMP 连接失败'
        if (frame.headers?.message?.includes('Connection closed')) return
        onError?.(errMsg)
      }
    })
    client.activate()
  }

  function doDisconnect() {
    if (client) { client.deactivate(); client = null }
    connected.value = false
  }

  // 监听 paperIdRef 变化：有值就连接，null 就断开
  watch(paperIdRef, (val, old) => {
    if (val && val !== old) doConnect(val)
    if (!val) doDisconnect()
  })

  // 组件卸载时断开
  if (typeof window !== 'undefined') {
    const orig = window.onbeforeunload
    window.onbeforeunload = () => { doDisconnect(); return orig?.() }
  }

  return { connected }
}
