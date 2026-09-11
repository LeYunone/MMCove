import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listAgents } from '@/api/agent'
import { listTemplates } from '@/api/responseTemplate'
import { listSessions } from '@/api/session'

export const useDashboardStore = defineStore('admin-dashboard', () => {
  const agentCount = ref(0)
  const templateCount = ref(0)
  const sessionCount = ref(0)
  const activeSessionCount = ref(0)
  const recentSessions = ref([])
  const loading = ref(false)

  async function fetchStats() {
    loading.value = true
    try {
      const [agentsRes, templatesRes, sessionsRes] = await Promise.all([
        listAgents(),
        listTemplates(),
        listSessions()
      ])
      const agents = agentsRes.data || []
      const sessions = sessionsRes.data || []

      agentCount.value = agents.length
      templateCount.value = (templatesRes.data || []).length
      sessionCount.value = sessions.length
      activeSessionCount.value = sessions.filter(s => s.status === 'ACTIVE').length
      recentSessions.value = sessions.slice(0, 10)
    } catch {
      // 拦截器已处理
    } finally {
      loading.value = false
    }
  }

  return {
    agentCount,
    templateCount,
    sessionCount,
    activeSessionCount,
    recentSessions,
    loading,
    fetchStats
  }
})
