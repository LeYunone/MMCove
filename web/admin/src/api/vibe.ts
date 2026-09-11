import request from './index'

// ===== 流水线管理 =====
export function listPipelines(productLineId) {
  return request.get('/api/vibe/pipelines', { params: productLineId ? { productLineId } : {} })
}

/** 把(共享)流水线复制为本产品线私有副本 */
export function forkPipeline(id, productLineId) {
  return request.post(`/api/vibe/pipelines/${id}/fork`, null, { params: { productLineId } })
}

export function getPipeline(id) {
  return request.get(`/api/vibe/pipelines/${id}`)
}

export function createPipeline(data) {
  return request.post('/api/vibe/pipelines', data)
}

export function updatePipeline(id, data) {
  return request.put(`/api/vibe/pipelines/${id}`, data)
}

export function deletePipeline(id) {
  return request.delete(`/api/vibe/pipelines/${id}`)
}

// ===== YAML 导入导出 =====
export function exportPipelineYaml(id) {
  return request.get(`/api/vibe/pipelines/${id}/export`)
}

export function importPipelineYaml(yamlText) {
  return request.post('/api/vibe/pipelines/import', yamlText)
}

// ===== 全局阶段库 =====
export function listStages() {
  return request.get('/api/vibe/stages')
}

export function createStage(data) {
  return request.post('/api/vibe/stages', data)
}

export function updateStage(id, data) {
  return request.put(`/api/vibe/stages/${id}`, data)
}

export function deleteStage(id) {
  return request.delete(`/api/vibe/stages/${id}`)
}

// ===== 产品线流水线配套(绑定+覆盖) =====
export function listBindings(productLineId) {
  return request.get(`/api/vibe/product-lines/${productLineId}/pipeline-bindings`)
}

export function replaceBindings(productLineId, bindings) {
  return request.put(`/api/vibe/product-lines/${productLineId}/pipeline-bindings`, bindings)
}

// ===== 执行实例轨迹 =====
export function pageRuns(params) {
  return request.get('/api/vibe/runs', { params })
}

export function getRunDetail(runCode) {
  return request.get(`/api/vibe/runs/${runCode}`)
}

// ===== compose 全链路调试预览 =====
export function composePreview(data) {
  return request.post('/api/vibe/compose-preview', data)
}

// ===== 缓存刷新 =====
export function refreshVibeCache() {
  return request.post('/api/vibe/refresh')
}
