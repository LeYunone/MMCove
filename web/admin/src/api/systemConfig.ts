import request from './index'

/**
 * 系统配置 API
 */

/** 获取所有系统配置 */
export function getSystemConfigs() {
  return request.get('/api/system-config')
}

/** 批量更新系统配置 */
export function updateSystemConfigs(configs) {
  return request.put('/api/system-config', { configs })
}
