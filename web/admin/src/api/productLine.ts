import request from './index'

// ===== 产品线 CRUD(知识库体系顶层维度) =====
export function listProductLines() {
  return request.get('/api/product-lines')
}

export function createProductLine(data) {
  return request.post('/api/product-lines', data)
}

export function updateProductLine(id, data) {
  return request.put(`/api/product-lines/${id}`, data)
}

export function deleteProductLine(id) {
  return request.delete(`/api/product-lines/${id}`)
}
