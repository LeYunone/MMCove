<template>
  <div class="chat-layout">
    <!-- 侧边栏 -->
    <SessionSidebar @show-login="showLoginDialog = true" @show-api-token="showApiTokenDialog = true" @show-token-request="showTokenRequestDialog = true" />

    <!-- 聊天区域 -->
    <div class="chat-area">
      <ChatMessages @confirm-action="handleConfirmAction" @quick-action="handleQuickAction" @page-change="handlePageChange" />
      <ChatInput ref="chatInputRef" @show-login="showLoginDialog = true" />
    </div>

    <!-- 登录弹窗 -->
    <LoginDialog v-model:visible="showLoginDialog" />
    <!-- API Token 弹窗 -->
    <ApiTokenDialog v-model:visible="showApiTokenDialog" @confirm="handleApiTokenConfirm" @use-free="handleUseFree" />
    <!-- 申请 Token 弹窗 -->
    <TokenRequestDialog v-model:visible="showTokenRequestDialog" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, provide } from 'vue'
import { useChatStore } from '@/stores/chat'
import SessionSidebar from '@/components/SessionSidebar.vue'
import ChatMessages from '@/components/ChatMessages.vue'
import ChatInput from '@/components/ChatInput.vue'
import LoginDialog from '@/components/LoginDialog.vue'
import ApiTokenDialog from '@/components/ApiTokenDialog.vue'
import TokenRequestDialog from '@/components/TokenRequestDialog.vue'
import { useSessionStore } from '@/stores/session'
import { useUserStore } from '@/stores/user'

const sessionStore = useSessionStore()
const userStore = useUserStore()
const chatStore = useChatStore()
const chatInputRef = ref(null)
const showLoginDialog = ref(false)
const showApiTokenDialog = ref(false)
const showTokenRequestDialog = ref(false)
const quotaRefreshKey = ref(0)

// 通过 provide 让 SessionSidebar 监听配额刷新
provide('quotaRefreshKey', quotaRefreshKey)

onMounted(() => {
  userStore.init()
  sessionStore.fetchSessions()
})

function handleConfirmAction(confirmationToken) {
  if (chatInputRef.value) {
    chatInputRef.value.handleSend(confirmationToken)
  }
}

async function handleQuickAction(message) {
  const title = message.substring(0, 20)
  const session = await sessionStore.addSession(title)
  if (session) {
    chatStore.clearMessages()
    await sessionStore.renameSession(session.sessionId, title)
    chatStore.sendMessage(message)
  }
}

function handlePageChange({ page, queryContext }) {
  const message = queryContext
    ? queryContext + '，第' + page + '页'
    : '查看第' + page + '页'
  chatStore.sendMessage(message)
}

function handleApiTokenConfirm({ apiToken }) {
  // 保存 API Token 到本地
  localStorage.setItem('api_token', apiToken)
  // 触发配额刷新
  quotaRefreshKey.value++
}

function handleUseFree() {
  // 触发配额刷新
  quotaRefreshKey.value++
}
</script>

<style scoped>
.chat-layout {
  height: 100vh;
  width: 100%;
  display: flex;
  background: #fff;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}
</style>
