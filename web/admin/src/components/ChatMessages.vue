<template>
  <div class="chat-messages" ref="scrollContainer">
    <!-- 未选中任何对话：欢迎页 -->
    <div v-if="!sessionStore.currentSessionId" class="welcome-state">
      <div class="welcome-center">
        <div class="welcome-greeting">有什么我能帮你的吗？</div>
        <div class="welcome-cards">
          <div
            v-for="card in quickCards"
            :key="card.label"
            class="welcome-card"
            @click="handleCardClick(card)"
          >
            <div class="card-icon">{{ card.icon }}</div>
            <div class="card-label">{{ card.label }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 选中对话但无消息：轻提示 -->
    <div v-else-if="chatStore.messages.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <rect width="48" height="48" rx="12" fill="#f0f0f0"/>
          <path d="M16 20h16M16 24h10M16 28h12" stroke="#ccc" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="empty-title">开始对话</div>
      <div class="empty-desc">输入消息开始与 AI 交流</div>
    </div>

    <!-- 消息列表 -->
    <div v-else class="messages-container">
      <MessageBubble
        v-for="msg in chatStore.messages"
        :key="msg.id"
        :msg="msg"
        @confirm="handleConfirm"
        @cancel="handleCancel"
        @page-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import MessageBubble from './MessageBubble.vue'

const chatStore = useChatStore()
const sessionStore = useSessionStore()
const userStore = useUserStore()
const scrollContainer = ref(null)

const emit = defineEmits(['confirm-action', 'quick-action', 'page-change'])

const quickCards = [
  { icon: '👤', label: '查询用户列表', message: '查询用户列表' },
  { icon: '📋', label: '查看可支配角色', message: '获取可支配角色' },
  { icon: '🏢', label: '查询机构数据', message: '获取用户机构数据' },
  { icon: '📖', label: '系统使用指南', message: '请告诉我这个系统有哪些功能，如何使用' }
]

watch(
  () => chatStore.messages.map(m => m.content).join(''),
  () => {
    nextTick(() => scrollToBottom())
  }
)

watch(
  () => chatStore.messages.length,
  () => {
    nextTick(() => scrollToBottom())
  }
)

function scrollToBottom() {
  const el = scrollContainer.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function handleConfirm(confirmationToken) {
  emit('confirm-action', confirmationToken)
}

function handleCancel() {
  // 取消确认操作
}

function handlePageChange(payload) {
  emit('page-change', payload)
}

async function handleCardClick(card) {
  if (!userStore.loggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  emit('quick-action', card.message)
}
</script>

<style scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

/* ========= 欢迎页（未选中对话） ========= */
.welcome-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding-bottom: 60px;
}

.welcome-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  max-width: 560px;
  width: 100%;
  padding: 0 24px;
}

.welcome-greeting {
  font-size: 26px;
  font-weight: 600;
  color: #1a1a1a;
  letter-spacing: -0.5px;
}

.welcome-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  width: 100%;
}

.welcome-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: #f7f7f8;
  border: 1px solid #eee;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.welcome-card:hover {
  background: #f0f0f2;
  border-color: #ddd;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card-icon {
  font-size: 20px;
  flex-shrink: 0;
  width: 28px;
  text-align: center;
}

.card-label {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

/* ========= 空状态（选中对话无消息） ========= */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding-bottom: 80px;
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 6px;
}

.empty-desc {
  font-size: 13px;
  color: #999;
}

.messages-container {
  max-width: 780px;
  margin: 0 auto;
  padding: 24px 24px 16px;
}
</style>
