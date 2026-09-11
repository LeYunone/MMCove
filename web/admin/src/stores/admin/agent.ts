import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listAgents, getAgent, createAgent, updateAgent, toggleAgentStatus } from '@/api/agent'
import { ElMessage } from 'element-plus'

export const useAdminAgentStore = defineStore('admin-agent', () => {
  const agents = ref([])
  const currentAgent = ref(null)
  const loading = ref(false)

  async function fetchAgents() {
    loading.value = true
    try {
      const res = await listAgents()
      agents.value = res.data || []
    } catch {
      // 拦截器已处理
    } finally {
      loading.value = false
    }
  }

  async function fetchAgent(agentId) {
    loading.value = true
    try {
      const res = await getAgent(agentId)
      currentAgent.value = res.data
      return res.data
    } catch {
      return null
    } finally {
      loading.value = false
    }
  }

  async function addAgent(data) {
    try {
      const res = await createAgent(data)
      ElMessage.success('Agent 创建成功')
      return res.data
    } catch {
      return null
    }
  }

  async function editAgent(agentId, data) {
    try {
      const res = await updateAgent(agentId, data)
      ElMessage.success('Agent 更新成功')
      return res.data
    } catch {
      return null
    }
  }

  async function switchStatus(agentId, status) {
    try {
      await toggleAgentStatus(agentId, status)
      ElMessage.success(`Agent 已${status === 'ACTIVE' ? '启用' : '禁用'}`)
      await fetchAgents()
    } catch {
      // 拦截器已处理
    }
  }

  return {
    agents,
    currentAgent,
    loading,
    fetchAgents,
    fetchAgent,
    addAgent,
    editAgent,
    switchStatus
  }
})
