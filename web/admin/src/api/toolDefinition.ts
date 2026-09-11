import request from './index'

/**
 * 工具定义管理 API
 */

/** 同步已注册的 @Tool 方法到数据库 */
export function syncToolDefinitions() {
  return request.post('/api/tool-definitions/sync')
}

/** 分页查询工具定义 */
export function listToolDefinitionsPaged(params) {
  return request.get('/api/tool-definitions/page', { params })
}

/** 获取单个工具定义 */
export function getToolDefinition(id) {
  return request.get(`/api/tool-definitions/${id}`)
}

/** 更新工具定义 */
export function updateToolDefinition(id, data) {
  return request.put(`/api/tool-definitions/${id}`, data)
}

/** 重置为原始描述 */
export function resetToolDefinition(id) {
  return request.post(`/api/tool-definitions/${id}/reset`)
}

/** 获取来源类列表（筛选用） */
export function getSourceClasses() {
  return request.get('/api/tool-definitions/source-classes')
}

/** 刷新缓存 */
export function refreshToolDefinitionCache() {
  return request.post('/api/tool-definitions/refresh')
}

// ==================== 分组工具配置 ====================

/** 查询分组已配置的工具 */
export function getGroupTools(groupName) {
  return request.get(`/api/tool-definitions/group/${groupName}`)
}

/** 设置分组的工具列表（全量替换） */
export function setGroupTools(groupName, toolNames) {
  return request.put(`/api/tool-definitions/group/${groupName}`, { toolNames })
}

/** 查询可分配的工具列表 */
export function getAvailableTools(groupName, config) {
  return request.get(`/api/tool-definitions/group/${groupName}/available`, config)
}

/** 列出所有工具分组(聚合:工具数/危险数/绑定 Agent) */
export function listGroups() {
  return request.get('/api/tool-definitions/groups')
}

/** 删除分组(清空该组工具配置) */
export function deleteGroup(groupName) {
  return request.delete(`/api/tool-definitions/group/${groupName}`)
}
