import { ref, watch } from 'vue'
import { Client } from '@stomp/stompjs'

/**
 * 订阅论文写作步骤的 WebSocket STOMP hook
 * 接收响应的 paperId ref，自动连接/断开
 *
 * @param {import('vue').Ref<number|null>} paperIdRef paperId 的 ref
 * @param {object} callbacks { onStep, onComplete, onError }
 */
export function usePaperStepWebSocket(paperIdRef, { onStep, onComplete, onError }) {
  const connected = ref(false)
  let client = null

  function doConnect(pid) {
    if (client) doDisconnect()
    if (!pid) return

    client = new Client({
      brokerURL: `ws://${window.location.hostname}:8081/ws`,
      webSocketFactory: () => new WebSocket(`ws://${window.location.hostname}:8081/ws`),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connected.value = true

        client.subscribe(`/topic/paper/${pid}/step`, (msg) => {
          try { onStep?.(JSON.parse(msg.body)) } catch (_) {}
        })

        client.subscribe(`/topic/paper/${pid}/complete`, () => {
          onComplete?.()
        })

        client.subscribe(`/topic/paper/${pid}/error`, (msg) => {
          try { onError?.(JSON.parse(msg.body).error || '未知错误') } catch (_) { onError?.('未知错误') }
        })
      },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => { onError?.(frame.headers?.message || 'STOMP 连接失败') }
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
