<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import ChatMessage from './ChatMessage.vue'

const chat = useChatStore()
const input = ref('')
const listEl = ref<HTMLElement>()

async function send() {
  const text = input.value
  if (!text.trim() || chat.streaming) return
  input.value = ''
  await chat.send(text)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function scrollBottom() {
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}

watch(() => chat.currentMessages.length, scrollBottom)
watch(() => chat.currentMessages.at(-1)?.content, scrollBottom)

async function newChat() {
  await chat.newSession()
}
</script>

<template>
  <div class="panel">
    <div ref="listEl" class="msg-list">
      <div v-if="!chat.currentMessages.length" class="empty">
        <div class="big">MMCove AI</div>
        <div class="sub">{{ chat.currentSessionId ? '开始输入你的问题' : '点击「新建对话」开始' }}</div>
        <el-button v-if="!chat.currentSessionId" type="primary" @click="newChat">新建对话</el-button>
      </div>
      <ChatMessage v-for="(m, i) in chat.currentMessages" :key="i" :msg="m" />
    </div>
    <div class="input-bar">
      <el-input
        v-model="input"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        resize="none"
        placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
        :disabled="!chat.currentSessionId"
        @keydown="onKeydown"
      />
      <el-button
        type="primary"
        :loading="chat.streaming"
        :disabled="!input.trim() || !chat.currentSessionId"
        @click="send"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px clamp(16px, 6vw, 64px);
}
.empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--muted);
}
.empty .big {
  font-size: 32px;
  font-weight: 800;
  background: linear-gradient(90deg, #6fb0ff, #b18cff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.empty .sub {
  margin-bottom: 10px;
}
.input-bar {
  flex: 0 0 auto;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px clamp(16px, 6vw, 64px) 18px;
  border-top: 1px solid var(--border);
  background: var(--surface);
}
</style>
