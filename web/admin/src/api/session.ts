import request from './index'

/**
 * 会话管理 API
 */

/** 创建会话 */
export function createSession(title = 'New Chat') {
  return request.post('/api/sessions', { title })
}

/** 获取会话列表 */
export function listSessions() {
  return request.get('/api/sessions')
}

/** 获取会话详情 */
export function getSession(sessionId) {
  return request.get(`/api/sessions/${sessionId}`)
}

/** 获取会话消息历史 */
export function getMessages(sessionId) {
  return request.get(`/api/sessions/${sessionId}/messages`)
}

/** 删除会话 */
export function deleteSession(sessionId) {
  return request.delete(`/api/sessions/${sessionId}`)
}

/** 更新会话标题 */
export function updateSessionTitle(sessionId, title) {
  return request.put(`/api/sessions/${sessionId}/title`, { title })
}
