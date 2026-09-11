<template>
  <div class="session-sidebar">
    <!-- 品牌区域 -->
    <div class="sidebar-brand">
      <div class="brand-text">
        <span class="brand-name">MMCove</span>
        <span class="brand-sub">智能助手</span>
      </div>
    </div>

    <!-- 新建对话 -->
    <div class="sidebar-action">
      <button class="new-chat-btn" @click="handleCreate">
        <el-icon :size="16"><Plus /></el-icon>
        <span>新的对话</span>
      </button>
    </div>

    <!-- 会话列表 -->
    <div class="session-list" v-loading="sessionStore.loading">
      <div
        v-for="session in sessionStore.sessions"
        :key="session.sessionId"
        class="session-item"
        :class="{ active: session.sessionId === sessionStore.currentSessionId }"
        @click="handleSelect(session.sessionId)"
        @mouseenter="session._hover = true"
        @mouseleave="session._hover = false"
      >
        <span class="session-title">{{ session.title || session.sessionId }}</span>
        <transition name="fade">
          <button
            v-show="session._hover || session.sessionId === sessionStore.currentSessionId"
            class="session-delete"
            @click.stop="handleDelete(session.sessionId)"
          >
            <el-icon :size="14"><Delete /></el-icon>
          </button>
        </transition>
      </div>

      <div v-if="!sessionStore.loading && sessionStore.sessions.length === 0" class="empty-sessions">
        开始新的对话吧
      </div>
    </div>

    <!-- 底部用户区域 -->
    <div class="sidebar-user">
      <!-- API Token 快捷入口 -->
      <button class="api-token-btn" @click="$emit('show-api-token')">
        <el-icon :size="14"><Key /></el-icon>
        <span>API Token</span>
      </button>

      <!-- 申请 Token 入口 -->
      <button class="apply-token-btn" @click="$emit('show-token-request')">
        <el-icon :size="14"><Plus /></el-icon>
        <span>申请 Token</span>
      </button>

      <!-- 配额进度条（登录后显示） -->
      <div v-if="userStore.loggedIn && quotaInfo" class="quota-bar" :class="{ warning: isQuotaWarning }">
        <div class="quota-bar-header">
          <span class="quota-bar-label" v-if="quotaInfo.mode === 'free'">今日已用 {{ quotaInfo.used }}/{{ quotaInfo.limit }} 次</span>
          <span class="quota-bar-label" v-else-if="quotaInfo.unlimited">Token · 无限配额</span>
          <span class="quota-bar-label" v-else>Token · 剩余 {{ quotaInfo.remain ?? (quotaInfo.limit - (quotaInfo.used || 0)) }} 次</span>
        </div>
        <div class="quota-bar-track" v-if="!quotaInfo.unlimited">
          <div class="quota-bar-fill" :style="{ width: quotaPercent + '%' }"></div>
        </div>
      </div>

      <template v-if="userStore.loggedIn">
        <div class="user-info" @click="showUserMenu = !showUserMenu">
          <div class="user-avatar">
            {{ avatarText }}
          </div>
          <div class="user-meta">
            <span class="user-name">{{ displayName }}</span>
            <span class="user-status">已登录</span>
          </div>
          <el-icon :size="14" class="user-arrow"><ArrowRight /></el-icon>
        </div>
        <transition name="slide">
          <div v-if="showUserMenu" class="user-menu">
            <button class="menu-item" @click="handleLogout">
              <el-icon :size="14"><SwitchButton /></el-icon>
              <span>退出登录</span>
            </button>
          </div>
        </transition>
      </template>
      <template v-else>
        <button class="login-btn" @click="$emit('show-login')">
          <el-icon :size="14"><UserFilled /></el-icon>
          <span>登录</span>
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, inject } from 'vue'
import { Plus, Delete, ArrowRight, SwitchButton, UserFilled, Key } from '@element-plus/icons-vue'
import { useSessionStore } from '@/stores/session'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { getChatQuota } from '@/api/token'
import { ElMessageBox } from 'element-plus'

defineEmits(['show-login', 'show-api-token', 'show-token-request'])

const sessionStore = useSessionStore()
const chatStore = useChatStore()
const userStore = useUserStore()
const showUserMenu = ref(false)
const quotaRefreshKey = inject('quotaRefreshKey', ref(0))
const quotaInfo = ref(null)

const quotaPercent = computed(() => {
  if (!quotaInfo.value) return 0
  const info = quotaInfo.value
  // 无限配额不显示进度条
  if (info.unlimited) return 100
  if (info.mode === 'free') {
    // 免费模式：used/limit，已用越多进度条越满
    return info.limit > 0 ? Math.min((info.used / info.limit) * 100, 100) : 0
  }
  // Token 模式：剩余占比，remain / (remain + used)
  const remain = info.remain ?? (info.limit - (info.used || 0))
  const total = remain + (info.used || 0)
  if (total > 0) {
    return Math.min((remain / total) * 100, 100)
  }
  return 0
})

const isQuotaWarning = computed(() => {
  if (!quotaInfo.value) return false
  const info = quotaInfo.value
  if (info.mode === 'free') return info.used >= info.limit
  if (!info.unlimited) {
    const remain = info.remain ?? (info.limit - (info.used || 0))
    return remain <= 0 || remain <= (info.limit || 0) * 0.1
  }
  return false
})

async function fetchQuota() {
  if (!userStore.loggedIn && !localStorage.getItem('mmcove_access_token')) {
    quotaInfo.value = null
    return
  }
  try {
    const apiToken = localStorage.getItem('api_token')
    // 如果有 api_token 先尝试用它获取配额，失败则回退免费配额
    if (apiToken) {
      try {
        const res = await getChatQuota({ apiToken })
        if (res.data) {
          quotaInfo.value = res.data
          return
        }
      } catch {
        // token 无效，回退到免费配额
        localStorage.removeItem('api_token')
        localStorage.removeItem('api_model')
      }
    }
    const res = await getChatQuota()
    if (res.data) {
      quotaInfo.value = res.data
    }
  } catch {
    // 配额查询失败不影响正常使用
  }
}

watch(() => userStore.loggedIn, (val) => {
  if (val) fetchQuota()
  else quotaInfo.value = null
})

// 消息发送完成后自动刷新配额
watch(() => chatStore.generating, (generating, oldGenerating) => {
  if (oldGenerating && !generating && userStore.loggedIn) {
    fetchQuota()
  }
})

// token 切换时刷新配额
watch(quotaRefreshKey, () => {
  fetchQuota()
})

onMounted(() => {
  // 父组件 ChatView 的 onMounted 调用 userStore.init() 晚于子组件，
  // 所以这里需要直接检查 localStorage 来判断是否已登录
  if (localStorage.getItem('mmcove_access_token')) {
    fetchQuota()
  }
})

const displayName = computed(() => {
  const info = userStore.userInfo
  if (!info) return ''
  return info.nickname || info.name || info.account || '用户'
})

const avatarText = computed(() => {
  const name = displayName.value
  return name ? name.charAt(0).toUpperCase() : 'U'
})

function handleLogout() {
  showUserMenu.value = false
  userStore.logout()
}

async function handleCreate() {
  const session = await sessionStore.addSession('新的对话')
  if (session) {
    chatStore.clearMessages()
  }
}

async function handleSelect(sessionId) {
  if (sessionId === sessionStore.currentSessionId) return

  // 保存当前对话的消息（含正在生成的内容）
  if (sessionStore.currentSessionId) {
    chatStore.cacheCurrentMessages(sessionStore.currentSessionId)
  }

  sessionStore.selectSession(sessionId)

  // 优先从缓存恢复，无缓存则从服务端加载
  if (!chatStore.restoreCachedMessages(sessionId)) {
    chatStore.clearMessages()
    const messages = await sessionStore.fetchMessages(sessionId)
    chatStore.loadHistory(messages)
  }
}

async function handleDelete(sessionId) {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      customStyle: {
        borderRadius: '12px'
      }
    })
    await sessionStore.removeSession(sessionId)
    chatStore.clearMessages()
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.session-sidebar {
  width: 260px;
  height: 100%;
  background: #f7f7f8;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: none;
}

/* 品牌区域 */
.sidebar-brand {
  padding: 20px 16px 12px;
}

.brand-text {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  letter-spacing: -0.3px;
}

.brand-sub {
  font-size: 12px;
  color: #999;
}

/* 新建对话按钮 */
.sidebar-action {
  padding: 0 12px 12px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  font-size: 13px;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}

.new-chat-btn:hover {
  background: #fff;
  border-color: #d0d0d0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

/* 会话列表 */
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.session-item {
  padding: 10px 10px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.session-item:hover {
  background: #ececec;
}

.session-item.active {
  background: #e3e3e5;
}

.session-title {
  flex: 1;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-delete {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  background: none;
  border: none;
  border-radius: 6px;
  color: #999;
  cursor: pointer;
  transition: all 0.15s;
}

.session-delete:hover {
  background: #ddd;
  color: #666;
}

.empty-sessions {
  padding: 60px 0;
  text-align: center;
  color: #bbb;
  font-size: 13px;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 底部用户区域 */
.sidebar-user {
  padding: 8px 12px 12px;
  border-top: 1px solid #e8e8ec;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-info:hover {
  background: #ececec;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #1a1a1a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-meta {
  flex: 1;
  min-width: 0;
}

.user-name {
  display: block;
  font-size: 13px;
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-status {
  display: block;
  font-size: 11px;
  color: #999;
  margin-top: 1px;
}

.user-arrow {
  color: #bbb;
  flex-shrink: 0;
}

/* 用户菜单 */
.user-menu {
  margin-top: 4px;
  padding: 4px;
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: none;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  color: #c53030;
  cursor: pointer;
  transition: background 0.15s;
}

.menu-item:hover {
  background: #fef2f2;
}

/* 登录按钮 */
.login-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  background: #1a1a1a;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  color: #fff;
  cursor: pointer;
  transition: background 0.2s;
}

.login-btn:hover {
  background: #333;
}

/* API Token 按钮 */
.api-token-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 0;
  background: #f0f0f0;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 8px;
}

.api-token-btn:hover {
  background: #e0e0e0;
  color: #333;
}

/* 申请 Token 按钮 */
.apply-token-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 0;
  background: #4CAF50;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 8px;
}

.apply-token-btn:hover {
  background: #45a049;
  color: #fff;
}

/* 配额进度条 */
.quota-bar {
  padding: 6px 10px 8px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: #f0f0f0;
}

.quota-bar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
}

.quota-bar-label {
  font-size: 11px;
  color: #888;
}

.quota-bar-track {
  width: 100%;
  height: 4px;
  background: #ddd;
  border-radius: 2px;
  overflow: hidden;
}

.quota-bar-fill {
  height: 100%;
  background: #1a1a1a;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.quota-bar.warning .quota-bar-label {
  color: #e6a23c;
  font-weight: 500;
}

.quota-bar.warning .quota-bar-fill {
  background: #e6a23c;
}

.quota-bar.warning .quota-bar-label {
  color: #e6a23c;
  font-weight: 500;
}

/* 菜单展开动画 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.15s ease;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
