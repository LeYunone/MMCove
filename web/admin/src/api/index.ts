import axios from 'axios'
import { ElMessage } from 'element-plus'
import { refreshTokenApi } from './auth'

/**
 * Axios 实例 — MMCove 后端（baseURL ''，api 模块自带 /api 前缀；Vite 代理 /api → :8808）。
 *
 * 认证：自建用户体系的本地 JWT，Authorization: Bearer <accessToken>。
 * 401 自动用 refreshToken 换新 token 并重试（并发去重，最多重试一次）；失败跳登录。
 * 响应格式：{ code, message, data }，code !== 0 视为业务异常。返回整个信封（视图层取 .data）。
 */
const request = axios.create({
  baseURL: '',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let pendingRequests: ((token: string | null) => void)[] = []

function resolvePending(token: string) {
  pendingRequests.forEach((cb) => cb(token))
  pendingRequests = []
}
function rejectPending() {
  pendingRequests.forEach((cb) => cb(null))
  pendingRequests = []
}

function forceLogout() {
  localStorage.removeItem('mmcove_access_token')
  localStorage.removeItem('mmcove_refresh_token')
  localStorage.removeItem('mmcove_user')
  ElMessage.warning('登录已过期，请重新登录')
  window.dispatchEvent(new CustomEvent('admin:require-login'))
  window.dispatchEvent(new CustomEvent('auth:expired'))
}

export class BusinessError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'BusinessError'
  }
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('mmcove_access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (
      response.config?.responseType === 'text' ||
      response.headers?.['content-type']?.includes('text/event-stream')
    ) {
      return res
    }
    if (res && res.code !== undefined && res.code !== 0) {
      const errorMsg = res.message || '请求失败'
      if (!(response.config as any)?._silentError) {
        if (res.code === 404) ElMessage.warning(errorMsg)
        else ElMessage.error(errorMsg)
      }
      return Promise.reject(new BusinessError(res.code, errorMsg))
    }
    return res
  },
  async (error: any) => {
    const originalConfig = error.config
    if (
      error.response?.status === 401 &&
      originalConfig &&
      !originalConfig._skipRefresh &&
      !originalConfig._retried
    ) {
      originalConfig._retried = true
      const storedRefresh = localStorage.getItem('mmcove_refresh_token')
      if (!storedRefresh) {
        forceLogout()
        return Promise.reject(error)
      }
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingRequests.push((newToken) => {
            if (newToken) {
              originalConfig.headers.Authorization = `Bearer ${newToken}`
              resolve(request(originalConfig))
            } else {
              reject(error)
            }
          })
        })
      }
      isRefreshing = true
      try {
        const data = await refreshTokenApi(storedRefresh)
        localStorage.setItem('mmcove_access_token', data.accessToken)
        if (data.refreshToken) localStorage.setItem('mmcove_refresh_token', data.refreshToken)
        resolvePending(data.accessToken)
        originalConfig.headers.Authorization = `Bearer ${data.accessToken}`
        return request(originalConfig)
      } catch {
        rejectPending()
        forceLogout()
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }
    if (originalConfig && !originalConfig._silentError) {
      if (error.code === 'ECONNABORTED') ElMessage.error('请求超时，请稍后重试')
      else if (error.response) {
        const status = error.response.status
        const map: Record<number, string> = {
          400: '请求参数错误',
          403: '拒绝访问',
          404: '请求的资源不存在',
          500: '服务器内部错误',
          502: '网关错误',
          503: '服务不可用',
          504: '网关超时',
        }
        ElMessage.error(map[status] || `请求失败 (${status})`)
      } else if (error.message?.includes('Network Error')) {
        ElMessage.error('网络连接失败，请检查网络')
      } else {
        ElMessage.error(error.message || '未知错误')
      }
    }
    return Promise.reject(error)
  },
)

export default request
