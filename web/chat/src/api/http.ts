import axios, { type AxiosRequestConfig } from 'axios'

/** 后端统一信封 { code, message, data }；code !== 0 视为业务错误。 */
export class BusinessError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

const ACCESS_KEY = 'mmcove_access_token'
const REFRESH_KEY = 'mmcove_refresh_token'

export const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 注入 JWT
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshing: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (!refresh) return null
  try {
    const { data } = await axios.post('/api/auth/refresh', { refreshToken: refresh })
    if (data?.code === 0 && data.data?.accessToken) {
      localStorage.setItem(ACCESS_KEY, data.data.accessToken)
      if (data.data.refreshToken) localStorage.setItem(REFRESH_KEY, data.data.refreshToken)
      return data.data.accessToken
    }
  } catch {
    /* fall through */
  }
  return null
}

// 解包信封 + 401 续期
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) throw new BusinessError(body.code, body.message || '请求失败')
      return body.data
    }
    return body
  },
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original.__retried && !original.url?.includes('/auth/')) {
      original.__retried = true
      refreshing = refreshing || doRefresh()
      const newToken = await refreshing
      refreshing = null
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`
        return http(original)
      }
      // 续期失败：清登录态
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      window.dispatchEvent(new CustomEvent('mmcove:auth-expired'))
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new BusinessError(error.response?.status ?? 500, msg))
  },
)

/** 便捷封装，保留 axios 类型推断 */
export const request = {
  get: <T = any>(url: string, config?: AxiosRequestConfig) => http.get<any, T>(url, config),
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => http.post<any, T>(url, data, config),
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig) => http.put<any, T>(url, data, config),
  delete: <T = any>(url: string, config?: AxiosRequestConfig) => http.delete<any, T>(url, config),
}
