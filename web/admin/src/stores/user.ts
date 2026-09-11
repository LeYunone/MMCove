import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, type AuthUser } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<AuthUser | null>(null)
  const token = ref('')
  const loggedIn = computed(() => !!token.value)

  function init() {
    const savedToken = localStorage.getItem('mmcove_access_token')
    const savedUser = localStorage.getItem('mmcove_user')
    if (savedToken) token.value = savedToken
    if (savedUser) {
      try {
        userInfo.value = JSON.parse(savedUser)
      } catch {
        userInfo.value = null
      }
    }
    const clear = () => {
      token.value = ''
      userInfo.value = null
    }
    window.addEventListener('auth:expired', clear)
    window.addEventListener('admin:require-login', clear)
  }

  async function loginAction(account: string, password: string) {
    const data = await loginApi(account, password)
    localStorage.setItem('mmcove_access_token', data.accessToken)
    if (data.refreshToken) localStorage.setItem('mmcove_refresh_token', data.refreshToken)
    localStorage.setItem('mmcove_user', JSON.stringify(data.user))
    userInfo.value = data.user
    token.value = data.accessToken
  }

  async function logout() {
    try {
      await logoutApi(token.value)
    } catch {
      /* 静默 */
    }
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('mmcove_access_token')
    localStorage.removeItem('mmcove_refresh_token')
    localStorage.removeItem('mmcove_user')
  }

  return { userInfo, token, loggedIn, init, loginAction, logout }
})
