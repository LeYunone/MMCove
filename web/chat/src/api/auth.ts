import { request } from './http'

export interface UserInfo {
  id: string
  username: string
  role: number
}

export interface AuthResult {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export const authApi = {
  login: (username: string, password: string) =>
    request.post<AuthResult>('/auth/login', { username, password }),
  register: (username: string, password: string) =>
    request.post<AuthResult>('/auth/register', { username, password }),
  me: () => request.get<UserInfo>('/auth/me'),
  logout: () => request.post<void>('/auth/logout'),
}
