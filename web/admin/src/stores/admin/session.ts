import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listSessions, getSession, getMessages, deleteSession } from '@/api/session'
import { ElMessage } from 'element-plus'

export const useAdminSessionStore = defineStore('admin-session', () => {
  const sessions = ref([])
  const currentSession = ref(null)
  const messages = ref([])
  const loading = ref(false)

  async function fetchSessions() {
    loading.value = true
    try {
      const res = await listSessions()
      sessions.value = res.data || []
    } catch {
      // 拦截器已处理
    } finally {
      loading.value = false
    }
  }

  async function fetchSession(sessionId) {
    loading.value = true
    try {
      const res = await getSession(sessionId)
      currentSession.value = res.data
      return res.data
    } catch {
      return null
    } finally {
      loading.value = false
    }
  }

  async function fetchMessages(sessionId) {
    loading.value = true
    try {
      const res = await getMessages(sessionId)
      messages.value = res.data || []
    } catch {
      messages.value = []
    } finally {
      loading.value = false
    }
  }

  async function removeSession(sessionId) {
    try {
      await deleteSession(sessionId)
      ElMessage.success('会话已删除')
      await fetchSessions()
    } catch {
      // 拦截器已处理
    }
  }

  return {
    sessions,
    currentSession,
    messages,
    loading,
    fetchSessions,
    fetchSession,
    fetchMessages,
    removeSession
  }
})
