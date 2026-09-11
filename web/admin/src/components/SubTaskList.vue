<template>
  <div class="subtask-list">
    <div class="subtask-list-header">
      <span class="header-icon">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <rect x="1" y="1" width="5" height="5" rx="1" stroke="#666" stroke-width="1.5"/>
          <rect x="8" y="1" width="5" height="5" rx="1" stroke="#666" stroke-width="1.5"/>
          <rect x="1" y="8" width="5" height="5" rx="1" stroke="#666" stroke-width="1.5"/>
          <rect x="8" y="8" width="5" height="5" rx="1" stroke="#666" stroke-width="1.5"/>
        </svg>
      </span>
      <span class="header-title">任务规划</span>
    </div>

    <SubTaskCard
      v-for="task in tasks"
      :key="task.taskIndex"
      :task="task"
    />
  </div>
</template>

<script setup lang="ts">
import SubTaskCard from './SubTaskCard.vue'

defineProps({
  tasks: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.subtask-list {
  margin: 8px 0;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
}

.subtask-list-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e5e7eb;
}

.header-icon {
  flex-shrink: 0;
}

.header-title {
  font-size: 13px;
  font-weight: 500;
  color: #555;
}
</style>
