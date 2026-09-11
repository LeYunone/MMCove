import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatStream, replayEvents } from '@/api/chat'
import { useSessionStore } from './session'

export const useChatStore = defineStore('chat', () => {
  /** 消息列表 */
  const messages = ref([])
  /** 是否正在生成 */
  const generating = ref(false)
  /** 当前 SSE 连接句柄 */
  let currentSse = null
  /** 按会话ID缓存消息，切换对话时保留未完成的生成内容 */
  const messagesCache = new Map()

  /** 加载历史消息 */
  function loadHistory(historyMessages) {
    messages.value = historyMessages.map(m => ({
      id: m.id,
      role: m.role,
      content: m.content,
      isStreaming: false,
      isError: false,
      errorMsg: '',
      // 思维链和子任务字段
      phase: 'done',
      thinkingChain: [],
      taskPlan: null
    }))
  }

  /** 清空消息 */
  function clearMessages() {
    messages.value = []
    stopGenerating()
  }

  /**
   * 保存当前消息到缓存（切换对话前调用）。
   * 正在流式生成的消息标记为非流式，保留已生成的内容。
   */
  function cacheCurrentMessages(sessionId) {
    if (!sessionId || messages.value.length === 0) {
      stopGenerating()
      return
    }
    const snapshot = messages.value.map(m => ({ ...m }))
    snapshot.forEach(m => {
      if (m.isStreaming) {
        m.isStreaming = false
      }
    })
    messagesCache.set(sessionId, snapshot)
    stopGenerating()
  }

  /**
   * 从缓存恢复消息。返回 true 表示恢复成功，false 表示无缓存。
   */
  function restoreCachedMessages(sessionId) {
    const cached = messagesCache.get(sessionId)
    if (cached) {
      messagesCache.delete(sessionId)
      messages.value = cached
      return true
    }
    return false
  }

  /** 停止生成 */
  function stopGenerating() {
    if (currentSse) {
      currentSse.close()
      currentSse = null
    }
    generating.value = false
    messages.value.forEach(m => {
      if (m.isStreaming) {
        m.isStreaming = false
      }
    })
  }

  /**
   * 获取或创建指定子任务的思维链轮次。
   */
  function getOrCreateTurn(msg, taskIndex, turnIndex) {
    const chain = msg.taskPlan && msg.taskPlan.tasks[taskIndex]
      ? msg.taskPlan.tasks[taskIndex].thinkingChain
      : msg.thinkingChain
    let turn = chain.find(t => t.turnIndex === turnIndex)
    if (!turn) {
      turn = { turnIndex, thought: '', action: null, observation: '', status: 'THINKING' }
      chain.push(turn)
    }
    return turn
  }

  /** 发送消息（SSE 流式 - 增强版） */
  function sendMessage(text, confirmationToken) {
    const sessionStore = useSessionStore()
    const sessionId = sessionStore.currentSessionId
    if (!sessionId) return

    let message = text.trim()
    if (!message) return

    // 附加确认令牌
    if (confirmationToken) {
      message = message + ' [token:' + confirmationToken + ']'
    }

    // 添加用户消息
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: message,
      isStreaming: false,
      isError: false,
      errorMsg: ''
    })

    // 添加空的 assistant 消息占位（含思维链字段）
    const assistantIdx = messages.value.length
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: '',
      isStreaming: true,
      isError: false,
      errorMsg: '',
      // 新增字段
      phase: 'thinking', // thinking | planning | executing | answering | done
      thinkingChain: [],
      taskPlan: null
    })

    generating.value = true

    const apiToken = localStorage.getItem('api_token') || ''

    currentSse = chatStream({
      sessionId,
      message,
      apiToken,
      onMessage(chunk, fullText) {
        const msg = messages.value[assistantIdx]
        if (msg) msg.content = fullText
      },
      onThinking(payload) {
        const msg = messages.value[assistantIdx]
        if (msg && payload) {
          msg.phase = payload.phase || 'thinking'
        }
      },
      onTaskPlan(payload) {
        const msg = messages.value[assistantIdx]
        if (msg && payload) {
          msg.phase = 'planning'
          msg.taskPlan = {
            planId: payload.planId,
            tasks: (payload.tasks || []).map(t => ({
              taskIndex: t.index,
              title: t.title,
              parallel: t.parallel,
              status: 'pending',
              result: '',
              thinkingChain: []
            }))
          }
        }
      },
      onTaskStart(payload) {
        const msg = messages.value[assistantIdx]
        if (msg && msg.taskPlan && payload) {
          const task = msg.taskPlan.tasks.find(t => t.taskIndex === payload.taskIndex)
          if (task) task.status = 'running'
          msg.phase = 'executing'
        }
      },
      onTaskProgress(payload) {
        const msg = messages.value[assistantIdx]
        if (!msg || !payload) return

        const { turnIndex, step, content, toolName, toolArgs } = payload
        // 如果没有 taskPlan，记录到顶层思维链
        if (!msg.taskPlan) {
          const turn = getOrCreateTurn(msg, -1, turnIndex)
          if (step === 'thought') turn.thought = content
          if (step === 'action') turn.action = { toolName, toolArgs }
          if (step === 'observation') turn.observation = content
          turn.status = step === 'observation' ? 'COMPLETED' : 'RUNNING'
        } else {
          // 有 taskPlan 时，尝试找到当前 running 的子任务
          const runningTask = msg.taskPlan.tasks.find(t => t.status === 'running')
          if (runningTask) {
            const turn = getOrCreateTurnFromChain(runningTask.thinkingChain, turnIndex)
            if (step === 'thought') turn.thought = content
            if (step === 'action') turn.action = { toolName, toolArgs }
            if (step === 'observation') turn.observation = content
            turn.status = step === 'observation' ? 'COMPLETED' : 'RUNNING'
          }
        }
        msg.phase = 'executing'
      },
      onTaskComplete(payload) {
        const msg = messages.value[assistantIdx]
        if (msg && msg.taskPlan && payload) {
          const task = msg.taskPlan.tasks.find(t => t.taskIndex === payload.taskIndex)
          if (task) {
            task.status = payload.status === 'completed' ? 'completed' : 'failed'
            task.result = payload.result || ''
          }
        }
      },
      onFinalAnswer() {
        const msg = messages.value[assistantIdx]
        if (msg) msg.phase = 'answering'
      },
      onConfirmationRequired(payload) {
        const msg = messages.value[assistantIdx]
        if (msg && payload) {
          msg.confirmationData = {
            confirmationToken: payload.confirmationToken,
            summary: payload.summary || '即将执行一个需要确认的操作'
          }
        }
      },
      onDone(fullText) {
        const msg = messages.value[assistantIdx]
        if (msg) {
          msg.content = fullText
          msg.isStreaming = false
          msg.phase = 'done'
        }
        generating.value = false
        currentSse = null
        sessionStore.fetchSessions()
      },
      onError(errorMsg, fullText) {
        const msg = messages.value[assistantIdx]
        if (msg) {
          msg.isStreaming = false
          msg.phase = 'done'
        }
        generating.value = false
        currentSse = null
        if (msg) {
          if (!fullText) {
            msg.isError = true
            msg.errorMsg = errorMsg
          } else {
            msg.content = fullText
            msg.inlineError = errorMsg
          }
        }
      }
    })
  }

  /**
   * 从子任务思维链中获取或创建轮次。
   */
  function getOrCreateTurnFromChain(chain, turnIndex) {
    let turn = chain.find(t => t.turnIndex === turnIndex)
    if (!turn) {
      turn = { turnIndex, thought: '', action: null, observation: '', status: 'THINKING' }
      chain.push(turn)
    }
    return turn
  }

  /**
   * 尝试从 sessionStorage 恢复未完成的事件流。
   */
  function tryReplay(sessionId) {
    const lastEventId = sessionStorage.getItem(`sse_last_event_${sessionId}`)
    if (!lastEventId) return false

    replayEvents({
      sessionId,
      lastEventId,
      onThinking(payload) { /* 静默处理，不更新 UI */ },
      onTaskPlan(payload) { /* 静默处理 */ },
      onTaskStart(payload) { /* 静默处理 */ },
      onTaskProgress(payload) { /* 静默处理 */ },
      onTaskComplete(payload) { /* 静默处理 */ },
      onMessage(data) { /* 静默处理 */ },
      onDone() {
        sessionStorage.removeItem(`sse_last_event_${sessionId}`)
      },
      onError() {
        sessionStorage.removeItem(`sse_last_event_${sessionId}`)
      }
    })

    return true
  }

  /**
   * 保存当前 SSE 的 lastEventId 到 sessionStorage。
   */
  function saveEventId(sessionId) {
    if (currentSse && currentSse.getLastEventId) {
      const eid = currentSse.getLastEventId()
      if (eid) {
        sessionStorage.setItem(`sse_last_event_${sessionId}`, eid)
      }
    }
  }

  return {
    messages,
    generating,
    loadHistory,
    clearMessages,
    stopGenerating,
    cacheCurrentMessages,
    restoreCachedMessages,
    sendMessage,
    tryReplay,
    saveEventId
  }
})
