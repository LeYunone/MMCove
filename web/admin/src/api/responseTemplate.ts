import request from './index'

/**
 * 响应模板管理 API
 */

/** 分页查询模板（管理后台用） */
export function listTemplatesPaged(params) {
  return request.get('/api/response-templates/page', { params })
}

/** 获取所有模板 */
export function listTemplates() {
  return request.get('/api/response-templates')
}

/** 获取单个模板 */
export function getTemplate(id) {
  return request.get(`/api/response-templates/${id}`)
}

/** 新增模板 */
export function createTemplate(data) {
  return request.post('/api/response-templates', data)
}

/** 更新模板 */
export function updateTemplate(id, data) {
  return request.put(`/api/response-templates/${id}`, data)
}

/** 删除模板 */
export function deleteTemplate(id) {
  return request.delete(`/api/response-templates/${id}`)
}

/** 刷新模板缓存 */
export function refreshTemplateCache() {
  return request.post('/api/response-templates/refresh')
}
