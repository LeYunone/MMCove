import request from './index'

// ===== 知识库 CRUD =====
export function listKnowledgeBases() {
  return request.get('/api/knowledge-bases')
}

export function createKnowledgeBase(data) {
  return request.post('/api/knowledge-bases', data)
}

export function updateKnowledgeBase(id, data) {
  return request.put(`/api/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBase(id) {
  return request.delete(`/api/knowledge-bases/${id}`)
}

// ===== 文档管理 =====
// 上传文档:body=正文(text/plain),title/mime 走 query
export function uploadDocument(kbId, { title, content, mime = 'markdown' }) {
  return request.post(`/api/knowledge-bases/${kbId}/documents`, content, {
    params: { title, mime },
    headers: { 'Content-Type': 'text/plain' }
  })
}

export function listDocuments(kbId) {
  return request.get(`/api/knowledge-bases/${kbId}/documents`)
}

export function deleteDocument(kbId, docId) {
  return request.delete(`/api/knowledge-bases/${kbId}/documents/${docId}`)
}

// ===== Agent ↔ 知识库 关联 =====
export function getAgentBindings(agentId) {
  return request.get(`/api/knowledge-bases/agent-bindings/${agentId}`)
}

export function setAgentBindings(agentId, kbIds) {
  return request.put(`/api/knowledge-bases/agent-bindings/${agentId}`, { kbIds })
}
