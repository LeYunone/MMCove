<template>
  <div class="admin-layout">
    <!-- 未登录状态：显示登录弹窗 -->
    <AdminLoginDialog v-model:visible="showLoginDialog" />

    <template v-if="isLoggedIn">
      <div class="sidebar">
        <div class="sidebar-logo">
          <h2>AI 管理平台</h2>
        </div>
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#1d1e2c"
          text-color="#bfcbd9"
          active-text-color="#409eff"
        >
          <el-menu-item index="/admin/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/admin/templates">
            <el-icon><Document /></el-icon>
            <span>模板管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/sessions">
            <el-icon><ChatDotRound /></el-icon>
            <span>会话管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/tools">
            <el-icon><Setting /></el-icon>
            <span>工具管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/tool-groups">
            <el-icon><Files /></el-icon>
            <span>工具分组</span>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge-bases">
            <el-icon><Collection /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/vibe">
            <el-icon><SetUp /></el-icon>
            <span>AI 编程流水线</span>
          </el-menu-item>
          <el-menu-item index="/admin/agents">
            <el-icon><Robot /></el-icon>
            <span>Agent 管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/tokens">
            <el-icon><Key /></el-icon>
            <span>Token 管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/api-doc">
            <el-icon><Document /></el-icon>
            <span>API 文档</span>
          </el-menu-item>
          <el-sub-menu index="ai-role-group">
            <template #title>
              <el-icon><MagicStick /></el-icon>
              <span>AI 角色配置</span>
            </template>
            <el-menu-item index="/admin/ai-roles">角色管理</el-menu-item>
            <el-menu-item index="/admin/ai-channels">渠道管理</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/admin/system-config">
            <el-icon><Operation /></el-icon>
            <span>系统配置</span>
          </el-menu-item>
          <el-menu-item index="/admin/usage-details">
            <el-icon><List /></el-icon>
            <span>使用明细</span>
          </el-menu-item>

          <!-- 底部用户信息 -->
          <el-divider style="margin: 8px 0; border-color: rgba(255,255,255,0.1);" />
          <el-menu-item class="user-menu-item">
            <el-icon><UserFilled /></el-icon>
            <span>{{ currentUser?.username || '用户' }}</span>
            <el-button link type="danger" size="small" @click="handleLogout" style="margin-left: auto;">
              退出
            </el-button>
          </el-menu-item>
        </el-menu>
      </div>
      <div class="main-area">
        <div class="top-bar">
          <span class="page-title">{{ pageTitle }}</span>
          <router-link to="/" class="back-link">
            <el-icon><Back /></el-icon>
            返回聊天
          </router-link>
        </div>
        <div class="content-area">
          <router-view />
        </div>
      </div>
    </template>

    <!-- 未登录状态 -->
    <div v-else class="not-logged-in">
      <div class="login-prompt">
        <el-icon :size="64" class="login-icon"><Key /></el-icon>
        <h2>请先登录</h2>
        <p>登录后即可访问管理后台</p>
        <el-button type="primary" size="large" @click="showLoginDialog = true">
          立即登录
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Key, UserFilled, MagicStick, Operation, List } from '@element-plus/icons-vue'
import AdminLoginDialog from '@/components/AdminLoginDialog.vue'

const route = useRoute()

const showLoginDialog = ref(false)

// 使用响应式变量管理登录状态(平台 JWT mmcove_access_token)
const authToken = ref(localStorage.getItem('mmcove_access_token'))
const sessionUserData = ref(null)

// 响应式登录状态
const isLoggedIn = computed(() => !!authToken.value)
const currentUser = computed(() => sessionUserData.value)

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/templates')) return '/admin/templates'
  if (path.startsWith('/admin/sessions')) return '/admin/sessions'
  if (path.startsWith('/admin/tools')) return '/admin/tools'
  if (path.startsWith('/admin/tool-groups')) return '/admin/tool-groups'
  if (path.startsWith('/admin/knowledge-bases')) return '/admin/knowledge-bases'
  if (path.startsWith('/admin/vibe')) return '/admin/vibe'
  if (path.startsWith('/admin/agents')) return '/admin/agents'
  if (path.startsWith('/admin/tokens')) return '/admin/tokens'
  if (path.startsWith('/admin/api-doc')) return '/admin/api-doc'
  if (path.startsWith('/admin/ai-roles')) return '/admin/ai-roles'
  if (path.startsWith('/admin/ai-channels')) return '/admin/ai-channels'
  if (path.startsWith('/admin/system-config')) return '/admin/system-config'
  if (path.startsWith('/admin/usage-details')) return '/admin/usage-details'
  return '/admin/dashboard'
})

const pageTitle = computed(() => route.meta?.title || '管理后台')

// 退出登录
function handleLogout() {
  localStorage.removeItem('mmcove_access_token')
  localStorage.removeItem('mmcove_refresh_token')
  localStorage.removeItem('mmcove_user')
  authToken.value = null
  sessionUserData.value = null
  showLoginDialog.value = false
}

// 更新登录状态
function updateLoginState() {
  authToken.value = localStorage.getItem('mmcove_access_token')
  const userStr = localStorage.getItem('mmcove_user')
  sessionUserData.value = userStr ? JSON.parse(userStr) : null
}

// 监听登录成功事件
onMounted(() => {
  // 初始化登录状态
  updateLoginState()

  window.addEventListener('admin:require-login', () => {
    showLoginDialog.value = true
  })

  window.addEventListener('admin:login-success', () => {
    showLoginDialog.value = false
    updateLoginState()
  })

  // 监听 storage 变化（跨页面同步）
  window.addEventListener('storage', (e) => {
    if (e.key === 'mmcove_access_token' || e.key === 'mmcove_user') {
      updateLoginState()
    }
  })
})

onUnmounted(() => {
  window.removeEventListener('admin:require-login', () => {})
  window.removeEventListener('admin:login-success', () => {})
  window.removeEventListener('storage', () => {})
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  width: 100%;
}

.sidebar {
  width: 220px;
  background-color: #1d1e2c;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-logo h2 {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.sidebar .el-menu {
  border-right: none;
  flex: 1;
}

.user-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 12px !important;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f7fa;
}

.top-bar {
  height: 50px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}

.back-link:hover {
  color: #66b1ff;
}

.content-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* 未登录状态 */
.not-logged-in {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-prompt {
  text-align: center;
  padding: 48px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-icon {
  color: #667eea;
  margin-bottom: 24px;
}

.login-prompt h2 {
  margin: 0 0 12px;
  font-size: 24px;
  color: #333;
}

.login-prompt p {
  margin: 0 0 24px;
  color: #999;
  font-size: 14px;
}
</style>
