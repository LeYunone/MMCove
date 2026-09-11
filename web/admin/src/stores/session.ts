import { defineStore } from 'pinia'
import { ref } from 'vue'
import { createSession, listSessions, deleteSession, getMessages, updateSessionTitle } from '@/api/session'
import { ElMessage } from 'element-plus'

export const useSessionStore = defineStore('session', () => {
  /** 会话列表 */
  const sessions = ref([])
  /** 当前选中的会话 ID */
  const currentSessionId = ref(null)
  /** 加载状态 */
  const loading = ref(false)

  /** 加载会话列表 */
  async function fetchSessions() {
    loading.value = true
    try {
      const res = await listSessions()
      sessions.value = res.data || []
    } catch {
      // 拦截器已处理错误提示
    } finally {
      loading.value = false
    }
  }

  /** 创建新会话 */
  async function addSession(title = 'New Chat') {
    try {
      const res = await createSession(title)
      if (res.data) {
        currentSessionId.value = res.data.sessionId
        await fetchSessions()
        return res.data
      }
    } catch {
      // 拦截器已处理
    }
    return null
  }

  /** 选择会话 */
  function selectSession(sessionId) {
    currentSessionId.value = sessionId
  }

  /** 删除会话 */
  async function removeSession(sessionId) {
    try {
      await deleteSession(sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
      }
      await fetchSessions()
    } catch {
      // 拦截器已处理
    }
  }

  /** 更新会话标题 */
  async function renameSession(sessionId, title) {
    try {
      await updateSessionTitle(sessionId, title)
      await fetchSessions()
    } catch {
      // 静默处理
    }
  }

  /** 获取会话消息 */
  async function fetchMessages(sessionId) {
    try {
      const res = await getMessages(sessionId)
      return res.data || []
    } catch {
      return []
    }
  }

  return {
    sessions,
    currentSessionId,
    loading,
    fetchSessions,
    addSession,
    selectSession,
    removeSession,
    renameSession,
    fetchMessages
  }
})
