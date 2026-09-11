import request from './index'

/**
 * AI 角色管理 API
 */

/** 角色列表 */
export function listAiRoles(params) {
  return request.get('/api/ai-role/list', { params })
}

/** 搜索角色 */
export function searchAiRoles(params) {
  return request.get('/api/ai-role/search', { params })
}

/** 新增角色 */
export function createAiRole(data) {
  return request.post('/api/ai-role', data)
}

/** 编辑角色 */
export function updateAiRole(data) {
  return request.put('/api/ai-role', data)
}

/** 删除角色 */
export function deleteAiRole(id) {
  return request.delete(`/api/ai-role/${id}`)
}

/** 刷新角色缓存 */
export function refreshAiRoleCache() {
  return request.post('/api/ai-role/refresh-cache')
}

/**
 * AI 渠道管理 API
 */

/** 渠道列表 */
export function listAiChannels() {
  return request.get('/api/ai-channel/list')
}

/** 获取所有可用分组（从渠道配置中提取） */
export function listAvailableGroups() {
  return request.get('/api/ai-channel/groups')
}

/** 新增渠道 */
export function createAiChannel(data) {
  return request.post('/api/ai-channel', data)
}

/** 编辑渠道 */
export function updateAiChannel(data) {
  return request.put('/api/ai-channel', data)
}

/** 删除渠道 */
export function deleteAiChannel(id) {
  return request.delete(`/api/ai-channel/${id}`)
}

/**
 * 角色-渠道关联管理 API
 */

/** 关联列表 */
export function listAiRoleChannels() {
  return request.get('/api/ai-role-channel/list')
}

/** 新增关联 */
export function createAiRoleChannel(data) {
  return request.post('/api/ai-role-channel', data)
}

/** 编辑关联 */
export function updateAiRoleChannel(data) {
  return request.put('/api/ai-role-channel', data)
}

/** 删除关联 */
export function deleteAiRoleChannel(id) {
  return request.delete(`/api/ai-role-channel/${id}`)
}
