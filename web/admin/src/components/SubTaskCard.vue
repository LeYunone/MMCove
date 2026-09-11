<template>
  <div class="subtask-card" :class="statusClass">
    <!-- 任务头部 -->
    <div class="subtask-header">
      <span class="status-icon">{{ statusIcon }}</span>
      <span class="subtask-title">{{ task.title }}</span>
      <span v-if="task.parallel" class="parallel-badge">可并行</span>
      <span class="status-text">{{ statusLabel }}</span>
    </div>

    <!-- 思维链 -->
    <ThinkingChain
      v-if="task.thinkingChain && task.thinkingChain.length > 0"
      :chain="task.thinkingChain"
    />

    <!-- 任务结果摘要 -->
    <div v-if="task.result && task.status === 'completed'" class="subtask-result">
      {{ truncateText(task.result, 100) }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ThinkingChain from './ThinkingChain.vue'

const props = defineProps({
  task: {
    type: Object,
    required: true
  }
})

const statusClass = computed(() => {
  return `status-${props.task.status || 'pending'}`
})

const statusIcon = computed(() => {
  switch (props.task.status) {
    case 'running': return '\u25B6'
    case 'completed': return '\u2713'
    case 'failed': return '\u2717'
    default: return '\u25CB'
  }
})

const statusLabel = computed(() => {
  switch (props.task.status) {
    case 'running': return '执行中'
    case 'completed': return '已完成'
    case 'failed': return '失败'
    default: return '等待中'
  }
})

function truncateText(text, maxLen) {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}
</script>

<style scoped>
.subtask-card {
  padding: 8px 12px;
  margin: 6px 0;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fafafa;
}

.subtask-card.status-running {
  border-color: #6366f1;
  background: #f5f3ff;
}

.subtask-card.status-completed {
  border-color: #22c55e;
  background: #f0fdf4;
}

.subtask-card.status-failed {
  border-color: #ef4444;
  background: #fef2f2;
}

.subtask-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.status-icon {
  font-size: 12px;
}

.status-completed .status-icon {
  color: #22c55e;
}

.status-running .status-icon {
  color: #6366f1;
}

.status-failed .status-icon {
  color: #ef4444;
}

.subtask-title {
  font-weight: 500;
  color: #333;
  flex: 1;
}

.parallel-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #ede9fe;
  color: #6366f1;
}

.status-text {
  font-size: 12px;
  color: #888;
}

.subtask-result {
  margin-top: 4px;
  font-size: 12px;
  color: #666;
  padding-left: 18px;
}
</style>
