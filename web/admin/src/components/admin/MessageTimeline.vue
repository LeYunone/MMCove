<template>
  <div class="message-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="msg in messages"
        :key="msg.id"
        :timestamp="formatTime(msg.createdAt)"
        :type="getRoleType(msg.role)"
        placement="top"
      >
        <div class="message-card" :class="msg.role">
          <div class="role-label">{{ roleLabels[msg.role] || msg.role }}</div>
          <div class="message-content" v-html="renderContent(msg.content)"></div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-if="!messages.length" description="暂无消息" />
  </div>
</template>

<script setup lang="ts">
import { renderMarkdown } from '@/utils/markdown'

defineProps({
  messages: { type: Array, default: () => [] }
})

const roleLabels = {
  user: '用户',
  assistant: 'AI 助手',
  system: '系统',
  tool: '工具'
}

function getRoleType(role) {
  const map = {
    user: 'primary',
    assistant: 'success',
    system: 'warning',
    tool: 'info'
  }
  return map[role] || ''
}

function formatTime(time) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}

function renderContent(content) {
  if (!content) return ''
  return renderMarkdown(content)
}
</script>

<style scoped>
.message-timeline {
  padding: 8px 0;
}

.message-card {
  background: #f9f9fb;
  border-radius: 6px;
  padding: 12px 16px;
}

.message-card.user {
  background: #ecf5ff;
}

.message-card.assistant {
  background: #f0f9eb;
}

.message-card.system {
  background: #fdf6ec;
}

.role-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 600;
}

.message-content {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  word-break: break-word;
}

.message-content :deep(pre) {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}

.message-content :deep(code) {
  background: #f0f0f0;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 12px;
}

.message-content :deep(pre code) {
  background: none;
  padding: 0;
}
</style>
