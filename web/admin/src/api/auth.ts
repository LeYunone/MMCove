/**
 * 自建用户体系认证 API（本地 /api/auth）。响应信封 { code, message, data }。
 */
export interface AuthUser {
  id: string
  username: string
  role: number
}
export interface AuthData {
  accessToken: string
  refreshToken: string
  user: AuthUser
}

async function parseEnvelope(res: Response): Promise<any> {
  const env = await res.json().catch(() => null)
  if (!res.ok || !env || (env.code !== undefined && env.code !== 0)) {
    throw new Error(env?.message || `HTTP ${res.status}`)
  }
  return env.data
}

/** 登录：返回 { accessToken, refreshToken, user } */
export async function loginApi(account: string, password: string): Promise<AuthData> {
  const data = await parseEnvelope(
    await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: account, password }),
    }),
  )
  if (!data?.accessToken) throw new Error('登录返回的 accessToken 为空')
  return data
}

/** 刷新 accessToken */
export async function refreshTokenApi(refreshToken: string): Promise<AuthData> {
  const data = await parseEnvelope(
    await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    }),
  )
  if (!data?.accessToken) throw new Error('刷新 token 失败')
  return data
}

/** 登出（无状态 JWT，客户端清状态即可；后端调用失败静默） */
export async function logoutApi(_accessToken?: string): Promise<void> {
  try {
    await fetch('/api/auth/logout', { method: 'POST' })
  } catch {
    /* 静默 */
  }
}
