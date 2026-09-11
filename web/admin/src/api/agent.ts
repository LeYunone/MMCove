import request from './index'

/**
 * Agent 管理 API
 */

/** 获取所有 Agent 定义 */
export function listAgents() {
  return request.get('/api/agents')
}

/** 获取单个 Agent */
export function getAgent(agentId) {
  return request.get(`/api/agents/${agentId}`)
}

/** 新增 Agent */
export function createAgent(data) {
  return request.post('/api/agents', data)
}

/** 更新 Agent */
export function updateAgent(agentId, data) {
  return request.put(`/api/agents/${agentId}`, data)
}

/** 切换 Agent 状态 */
export function toggleAgentStatus(agentId, status) {
  return request.put(`/api/agents/${agentId}/status`, { status })
}

/** 刷新 Agent 缓存 */
export function refreshAgentCache() {
  return request.post('/api/agents/refresh')
}
