<template>
  <div class="chat-input-area">
    <div class="input-wrapper">
      <div class="input-box">
        <!-- 快捷操作（折叠式） -->
        <div class="quick-actions" v-show="showQuickActions">
          <div class="quick-actions-inner">
            <button class="action-chip" @click="quickAction('查询用户列表')">查询用户</button>
            <button class="action-chip" @click="quickAction('查询我的用户')">我的用户</button>
            <button class="action-chip" @click="quickAction('获取可支配角色')">可支配角色</button>
            <button class="action-chip" @click="quickAction('获取用户机构数据')">机构数据</button>
            <button class="action-chip" @click="promptAction('查询用户详情', '请输入用户ID')">用户详情</button>
            <button class="action-chip danger" @click="promptAction('创建新用户', '请输入用户名')">创建用户</button>
            <button class="action-chip danger" @click="promptAction('删除用户', '请输入要删除的用户ID')">删除用户</button>
          </div>
        </div>

        <!-- 输入行 -->
        <div class="input-row">
          <!-- 快捷操作开关 -->
          <button class="tool-btn" :class="{ active: showQuickActions }" @click="showQuickActions = !showQuickActions" title="快捷操作">
            <el-icon :size="16"><SetUp /></el-icon>
          </button>

          <textarea
            ref="textareaRef"
            v-model="inputText"
            class="message-input"
            placeholder="输入消息..."
            rows="1"
            @keydown.enter.exact="handleEnter"
            @input="autoResize"
          />

          <button
            class="send-btn"
            :class="{ active: inputText.trim() && !chatStore.generating }"
            @click="chatStore.generating ? chatStore.stopGenerating() : handleSend()"
          >
            <el-icon :size="18" v-if="chatStore.generating"><VideoPause /></el-icon>
            <el-icon :size="18" v-else><Promotion /></el-icon>
          </button>
        </div>
      </div>

      <div class="input-footer">
        <span class="input-hint">Enter 发送，Shift+Enter 换行</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { Promotion, SetUp, VideoPause } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['show-login'])

const chatStore = useChatStore()
const sessionStore = useSessionStore()
const userStore = useUserStore()

const inputText = ref('')
const textareaRef = ref(null)
const showQuickActions = ref(false)

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function handleEnter(e) {
  if (chatStore.generating) return
  e.preventDefault()
  handleSend()
}

async function handleSend(confirmationToken) {
  const text = inputText.value.trim()
  if (!text && !confirmationToken) return

  // 检查是否已登录
  const apiToken = localStorage.getItem('api_token')
  if (!userStore.loggedIn && !apiToken) {
    ElMessage.warning('请先登录')
    emit('show-login')
    return
  }

  // 没有会话时自动创建
  if (!sessionStore.currentSessionId) {
    const title = text.substring(0, 20)
    const session = await sessionStore.addSession(title)
    if (!session) return
    // 用第一条消息内容更新会话标题
    await sessionStore.renameSession(session.sessionId, title)
  }

  const message = text || '确认执行操作'
  inputText.value = ''

  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })

  chatStore.sendMessage(message, confirmationToken)
}

function quickAction(message) {
  if (!userStore.loggedIn) {
    ElMessage.warning('请先登录')
    emit('show-login')
    return
  }
  inputText.value = message
  handleSend()
}

function promptAction(baseMessage, promptText) {
  if (!userStore.loggedIn) {
    ElMessage.warning('请先登录')
    emit('show-login')
    return
  }
  const value = window.prompt(promptText)
  if (value) {
    inputText.value = baseMessage + ' ' + value
    handleSend()
  }
}

onMounted(() => {
  // 初始化时无需额外操作
})

defineExpose({ handleSend })
</script>

<style scoped>
.chat-input-area {
  flex-shrink: 0;
  background: #fff;
  padding: 0 24px 16px;
}

.input-wrapper {
  max-width: 780px;
  margin: 0 auto;
}

.input-box {
  background: #f7f7f8;
  border-radius: 14px;
  border: 1px solid #e8e8ec;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-box:focus-within {
  border-color: #d0d0d5;
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.03);
}

/* 快捷操作 */
.quick-actions {
  padding: 8px 14px 0;
  border-bottom: 1px solid #eee;
}

.quick-actions-inner {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding-bottom: 8px;
}

.action-chip {
  padding: 4px 12px;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 14px;
  font-size: 12px;
  color: #555;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.action-chip:hover {
  background: #f0f0f2;
  border-color: #d0d0d0;
}

.action-chip.danger {
  color: #c53030;
  border-color: #fde8e8;
}

.action-chip.danger:hover {
  background: #fef7f7;
  border-color: #f5c5c5;
}

/* 输入行 */
.input-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 8px 10px 10px;
}

.tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: none;
  border-radius: 8px;
  color: #999;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.tool-btn:hover {
  background: #ececec;
  color: #666;
}

.tool-btn.active {
  background: #e3e3e5;
  color: #333;
}

.message-input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  font-size: 14px;
  line-height: 1.6;
  color: #1a1a1a;
  resize: none;
  font-family: inherit;
  min-height: 24px;
  max-height: 120px;
}

.message-input::placeholder {
  color: #bbb;
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: #e8e8ec;
  border-radius: 8px;
  color: #999;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn.active {
  background: #1a1a1a;
  color: #fff;
}

.send-btn.active:hover {
  background: #333;
}

/* 底部信息行 */
.input-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}

.input-hint {
  font-size: 11px;
  color: #ccc;
}
</style>
