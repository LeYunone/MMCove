<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

interface Msg {
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: string
  pending?: boolean
  error?: boolean
}
const props = defineProps<{ msg: Msg }>()

const html = computed(() => (props.msg.role === 'assistant' ? renderMarkdown(props.msg.content) : ''))
</script>

<template>
  <div class="row" :class="msg.role">
    <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
    <div class="bubble" :class="{ err: msg.error }">
      <template v-if="msg.role === 'assistant'">
        <details v-if="msg.thinking" class="thinking">
          <summary>思考过程</summary>
          <div class="md" v-html="msg.thinking"></div>
        </details>
        <div v-if="msg.content" class="md" v-html="html"></div>
        <div v-else-if="msg.pending" class="typing"><span></span><span></span><span></span></div>
      </template>
      <template v-else>{{ msg.content }}</template>
    </div>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin: 18px 0;
}
.row.user {
  flex-direction: row-reverse;
}
.avatar {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: var(--surface-2);
  color: var(--muted);
}
.row.assistant .avatar {
  background: linear-gradient(135deg, #4f8cff, #8b5cff);
  color: #fff;
}
.bubble {
  max-width: 76%;
  padding: 12px 16px;
  border-radius: 14px;
  background: var(--surface-2);
  line-height: 1.6;
}
.row.user .bubble {
  background: var(--user-bubble);
}
.bubble.err {
  border: 1px solid var(--danger);
  color: var(--danger);
}
.thinking {
  margin-bottom: 8px;
  padding: 8px 10px;
  border-left: 2px solid var(--border);
  color: var(--muted);
  font-size: 13px;
  border-radius: 0 6px 6px 0;
}
.thinking summary {
  cursor: pointer;
  user-select: none;
}
.typing {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--muted);
  animation: blink 1.2s infinite ease-in-out both;
}
.typing span:nth-child(2) {
  animation-delay: 0.2s;
}
.typing span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}
</style>
