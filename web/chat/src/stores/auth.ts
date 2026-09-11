import { defineStore } from 'pinia'
import { authApi, type UserInfo } from '@/api/auth'

const ACCESS_KEY = 'mmcove_access_token'
const REFRESH_KEY = 'mmcove_refresh_token'
const USER_KEY = 'mmcove_user'

interface State {
  user: UserInfo | null
  accessToken: string | null
  refreshToken: string | null
  ready: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): State => ({
    user: null,
    accessToken: null,
    refreshToken: null,
    ready: false,
  }),
  getters: {
    isLoggedIn: (s) => !!s.accessToken,
    isAdmin: (s) => (s.user?.role ?? 0) >= 10,
  },
  actions: {
    init() {
      this.accessToken = localStorage.getItem(ACCESS_KEY)
      this.refreshToken = localStorage.getItem(REFRESH_KEY)
      const u = localStorage.getItem(USER_KEY)
      this.user = u ? JSON.parse(u) : null
      this.ready = true
      window.addEventListener('mmcove:auth-expired', () => this.clear())
    },
    privateStore(a: { accessToken: string; refreshToken: string; user: UserInfo }) {
      this.accessToken = a.accessToken
      this.refreshToken = a.refreshToken
      this.user = a.user
      localStorage.setItem(ACCESS_KEY, a.accessToken)
      localStorage.setItem(REFRESH_KEY, a.refreshToken)
      localStorage.setItem(USER_KEY, JSON.stringify(a.user))
    },
    async login(username: string, password: string) {
      const res = await authApi.login(username, password)
      this.privateStore(res)
    },
    async register(username: string, password: string) {
      const res = await authApi.register(username, password)
      this.privateStore(res)
    },
    clear() {
      this.user = null
      this.accessToken = null
      this.refreshToken = null
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        /* ignore */
      }
      this.clear()
    },
  },
})
