import request from './index'

/**
 * API Token 管理 API
 */

/** 获取当前用户的所有Token列表 */
export function listTokens(params) {
  return request.get('/api/token', { params })
}

/** 搜索Token */
export function searchTokens(params) {
  return request.get('/api/token/search', { params })
}

/** 获取单个Token */
export function getToken(id) {
  return request.get(`/api/token/${id}`)
}

/** 获取Token配额状态 */
export function getTokenStatus(id) {
  return request.get(`/api/token/${id}/status`)
}

/** 创建新Token */
export function createToken(data) {
  return request.post('/api/token', data)
}

/** 更新Token */
export function updateToken(data) {
  return request.put('/api/token', data)
}

/** 删除Token */
export function deleteToken(id) {
  return request.delete(`/api/token/${id}`)
}

/** 用户登录 */
export function login(data) {
  return request.post('/api/user/login', data)
}

/** 用户注册 */
export function register(data) {
  return request.post('/api/user/register', data)
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return request.get('/api/user/self')
}

/** 获取支持的模型列表 */
export function listModels() {
  return request.get('/v1/models')
}

/** 获取当前用户聊天配额信息 */
export function getChatQuota(params) {
  return request.get('/api/chat/quota', { params, _silentError: true })
}

/** 校验 API Token 是否可用 */
export function verifyApiToken(tokenKey) {
  return request.post('/api/chat/token-verify', { tokenKey }, { _silentError: true })
}

/** 获取聊天接口认证开关状态 */
export function getAuthSwitch() {
  return request.get('/api/token/admin/auth-switch')
}

/** 更新聊天接口认证开关 */
export function updateAuthSwitch(enabled) {
  return request.put('/api/token/admin/auth-switch', { enabled })
}
