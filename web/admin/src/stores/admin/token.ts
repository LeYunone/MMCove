import { defineStore } from 'pinia'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTokens,
  searchTokens,
  getToken,
  createToken,
  updateToken,
  deleteToken,
  getTokenStatus,
  login
} from '@/api/token'

export const useTokenStore = defineStore('token', () => {
  // 状态
  const tokens = ref([])
  const currentToken = ref(null)
  const loading = ref(false)
  const total = ref(0)
  const isLoggedIn = computed(() => !!localStorage.getItem('mmcove_access_token'))
  const currentUser = computed(() => {
    const userStr = localStorage.getItem('mmcove_user')
    return userStr ? JSON.parse(userStr) : null
  })

  // 初始化：检查登录状态
  function init() {
    // 监听登录成功事件
    window.addEventListener('admin:login-success', () => {
      fetchTokens()
    })

    // 监听需要登录事件
    window.addEventListener('admin:require-login', () => {
      // 触发登录弹窗（由组件处理）
    })
  }

  // 获取Token列表
  async function fetchTokens(params = {}) {
    loading.value = true
    try {
      const res = await listTokens(params)
      tokens.value = res.data || res || []
      return tokens.value
    } catch (error) {
      console.error('获取Token列表失败:', error)
      return []
    } finally {
      loading.value = false
    }
  }

  // 搜索Token
  async function search(params = {}) {
    loading.value = true
    try {
      const res = await searchTokens(params)
      tokens.value = res.data || res || []
      return tokens.value
    } catch (error) {
      console.error('搜索Token失败:', error)
      return []
    } finally {
      loading.value = false
    }
  }

  // 获取单个Token
  async function fetchToken(id) {
    loading.value = true
    try {
      const res = await getToken(id)
      currentToken.value = res.data || res
      return res.data || res
    } catch (error) {
      console.error('获取Token失败:', error)
      return null
    } finally {
      loading.value = false
    }
  }

  // 获取Token配额状态
  async function fetchTokenStatus(id) {
    try {
      const res = await getTokenStatus(id)
      return res.data || res
    } catch (error) {
      console.error('获取Token状态失败:', error)
      return null
    }
  }

  // 创建Token
  async function addToken(data) {
    loading.value = true
    try {
      const res = await createToken(data)
      ElMessage.success('创建成功')
      await fetchTokens()
      return res.data || res
    } catch (error) {
      console.error('创建Token失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  // 更新Token
  async function editToken(data) {
    loading.value = true
    try {
      const res = await updateToken(data)
      ElMessage.success('更新成功')
      await fetchTokens()
      return res.data || res
    } catch (error) {
      console.error('更新Token失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  // 删除Token
  async function removeToken(id) {
    try {
      await ElMessageBox.confirm('确定要删除这个Token吗？此操作不可恢复。', '删除确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })

      await deleteToken(id)
      ElMessage.success('删除成功')
      await fetchTokens()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除Token失败:', error)
      }
    }
  }

  // 切换Token状态
  async function toggleStatus(id, currentStatus) {
    const newStatus = currentStatus === 1 ? 2 : 1
    const action = newStatus === 1 ? '启用' : '禁用'

    try {
      await ElMessageBox.confirm(`确定要${action}这个Token吗？`, `${action}确认`, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })

      await updateToken({ id, status: newStatus })
      ElMessage.success(`${action}成功`)
      await fetchTokens()
    } catch (error) {
      if (error !== 'cancel') {
        console.error(`${action}Token失败:`, error)
      }
    }
  }

  // 退出登录
  function logout() {
    localStorage.removeItem('mmcove_access_token')
    localStorage.removeItem('mmcove_refresh_token')
    localStorage.removeItem('mmcove_user')
    tokens.value = []
    currentToken.value = null
  }

  return {
    // 状态
    tokens,
    currentToken,
    loading,
    total,
    isLoggedIn,
    currentUser,
    // 方法
    init,
    fetchTokens,
    search,
    fetchToken,
    fetchTokenStatus,
    addToken,
    editToken,
    removeToken,
    toggleStatus,
    logout
  }
})
