/**
 * 聊天 API — 同步 + SSE 流式
 *
 * SSE 流式对接说明：
 *   后端接口 GET /api/chat/stream?sessionId=xxx&message=xxx
 *   返回 EventSource 格式的事件流：
 *     - event: message       → 增量文本片段
 *     - event: done          → 流结束，data 为 "[DONE]"
 *     - event: error         → 错误，data 为错误信息
 *     - event: thinking      → 思考/规划阶段
 *     - event: task_plan     → 任务拆分计划
 *     - event: task_start    → 子任务开始
 *     - event: task_progress → 子任务进度（thought/action/observation）
 *     - event: task_complete → 子任务完成
 *     - event: final_answer  → 最终答案开始
 */

import request from './index'

/**
 * SSE 流式聊天
 *
 * @param {Object} options
 * @param {string} options.sessionId - 会话 ID
 * @param {string} options.message   - 用户消息
 * @param {Function} options.onMessage - 收到文本片段回调 (chunk: string)
 * @param {Function} options.onDone    - 流结束回调 (fullText: string)
 * @param {Function} options.onError   - 错误回调 (errorMsg: string)
 * @param {Function} [options.onThinking]    - 思考事件回调 (payload)
 * @param {Function} [options.onTaskPlan]    - 任务计划回调 (payload)
 * @param {Function} [options.onTaskStart]   - 子任务开始回调 (payload)
 * @param {Function} [options.onTaskProgress] - 子任务进度回调 (payload)
 * @param {Function} [options.onTaskComplete] - 子任务完成回调 (payload)
 * @param {Function} [options.onFinalAnswer]  - 最终答案开始回调 (payload)
 * @param {Function} [options.onConfirmationRequired] - 确认请求回调 (payload)
 * @returns {{ close: Function, getLastEventId: Function }} 可调用 close() 手动断开
 */
export function chatStream({
  sessionId, message, apiToken, model,
  onMessage, onDone, onError,
  onThinking, onTaskPlan, onTaskStart, onTaskProgress, onTaskComplete, onFinalAnswer,
  onConfirmationRequired
}) {
  let params = `sessionId=${encodeURIComponent(sessionId)}&message=${encodeURIComponent(message)}`
  if (apiToken) {
    params += `&apiToken=${encodeURIComponent(apiToken)}`
  }
  if (model) {
    params += `&model=${encodeURIComponent(model)}`
  }

  const url = `/api/chat/stream?${params}`
  const eventSource = new EventSource(url)
  let fullText = ''
  let lastEventId = null

  // 原有事件
  eventSource.addEventListener('message', (e) => {
    fullText += e.data
    lastEventId = e.lastEventId || lastEventId
    onMessage?.(e.data, fullText)
  })

  eventSource.addEventListener('done', (e) => {
    lastEventId = e.lastEventId || lastEventId
    eventSource.close()
    onDone?.(fullText)
  })

  eventSource.addEventListener('error', (e) => {
    eventSource.close()
    onError?.(e.data || '未知错误', fullText)
  })

  // 新增思维链事件
  eventSource.addEventListener('thinking', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onThinking?.(envelope.payload, envelope)
    } catch {
      onThinking?.({ content: e.data, phase: 'planning' }, null)
    }
  })

  eventSource.addEventListener('task_plan', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onTaskPlan?.(envelope.payload, envelope)
    } catch {
      onTaskPlan?.(null, null)
    }
  })

  eventSource.addEventListener('task_start', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onTaskStart?.(envelope.payload, envelope)
    } catch {
      onTaskStart?.(null, null)
    }
  })

  eventSource.addEventListener('task_progress', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onTaskProgress?.(envelope.payload, envelope)
    } catch {
      onTaskProgress?.(null, null)
    }
  })

  eventSource.addEventListener('task_complete', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onTaskComplete?.(envelope.payload, envelope)
    } catch {
      onTaskComplete?.(null, null)
    }
  })

  eventSource.addEventListener('final_answer', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onFinalAnswer?.(envelope.payload, envelope)
    } catch {
      onFinalAnswer?.(null, null)
    }
  })

  eventSource.addEventListener('confirmation_required', (e) => {
    lastEventId = e.lastEventId || lastEventId
    try {
      const envelope = JSON.parse(e.data)
      onConfirmationRequired?.(envelope.payload, envelope)
    } catch {
      onConfirmationRequired?.({ summary: e.data }, null)
    }
  })

  eventSource.onerror = () => {
    let errorMsg = '连接失败，请稍后重试'
    if (eventSource.readyState === EventSource.CONNECTING) {
      errorMsg = '连接中断，正在尝试重连...'
    }
    eventSource.close()
    onError?.(errorMsg, fullText)
  }

  return {
    close() {
      eventSource.close()
    },
    getLastEventId() {
      return lastEventId
    }
  }
}

/**
 * 重放 SSE 事件（页面刷新后恢复）。
 *
 * @param {Object} options
 * @param {string} options.sessionId - 会话 ID
 * @param {string} [options.lastEventId] - 上次消费的最后事件 ID
 * @param {Function} [options.onThinking]    - 思考事件回调
 * @param {Function} [options.onTaskPlan]    - 任务计划回调
 * @param {Function} [options.onTaskStart]   - 子任务开始回调
 * @param {Function} [options.onTaskProgress] - 子任务进度回调
 * @param {Function} [options.onTaskComplete] - 子任务完成回调
 * @param {Function} [options.onMessage]     - 增量文本回调
 * @param {Function} [options.onDone]        - 重放完成回调
 * @param {Function} [options.onError]       - 错误回调
 * @returns {{ close: Function }}
 */
export function replayEvents({
  sessionId, lastEventId,
  onThinking, onTaskPlan, onTaskStart, onTaskProgress, onTaskComplete,
  onMessage, onDone, onError
}) {
  let params = `sessionId=${encodeURIComponent(sessionId)}`
  if (lastEventId) {
    params += `&lastEventId=${encodeURIComponent(lastEventId)}`
  }

  const url = `/api/chat/stream/replay?${params}`
  const eventSource = new EventSource(url)

  eventSource.addEventListener('message', (e) => {
    onMessage?.(e.data)
  })

  eventSource.addEventListener('thinking', (e) => {
    try { onThinking?.(JSON.parse(e.data).payload) } catch { onThinking?.({ content: e.data }) }
  })

  eventSource.addEventListener('task_plan', (e) => {
    try { onTaskPlan?.(JSON.parse(e.data).payload) } catch { onTaskPlan?.(null) }
  })

  eventSource.addEventListener('task_start', (e) => {
    try { onTaskStart?.(JSON.parse(e.data).payload) } catch { onTaskStart?.(null) }
  })

  eventSource.addEventListener('task_progress', (e) => {
    try { onTaskProgress?.(JSON.parse(e.data).payload) } catch { onTaskProgress?.(null) }
  })

  eventSource.addEventListener('task_complete', (e) => {
    try { onTaskComplete?.(JSON.parse(e.data).payload) } catch { onTaskComplete?.(null) }
  })

  eventSource.addEventListener('done', () => {
    eventSource.close()
    onDone?.()
  })

  eventSource.addEventListener('error', (e) => {
    eventSource.close()
    onError?.(e.data || '重放失败')
  })

  eventSource.onerror = () => {
    eventSource.close()
    onError?.('重放连接失败')
  }

  return {
    close() {
      eventSource.close()
    }
  }
}

/**
 * 同步聊天（备用）
 */
export function chatSync(sessionId, userMessage) {
  return request.post('/api/chat', { sessionId, userMessage })
}
