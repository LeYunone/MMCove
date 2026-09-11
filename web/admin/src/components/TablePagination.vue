<template>
  <div class="pagination-card">
    <div class="pagination-info">
      第 <strong>{{ currentPage }}</strong> / {{ totalPages }} 页
    </div>
    <div class="pagination-actions">
      <button
        class="btn-page"
        :disabled="currentPage <= 1"
        @click="handlePageChange(currentPage - 1)"
      >上一页</button>
      <button
        class="btn-page"
        :disabled="currentPage >= totalPages"
        @click="handlePageChange(currentPage + 1)"
      >下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps({
  currentPage: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    required: true
  },
  queryContext: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['page-change'])

function handlePageChange(page) {
  if (page < 1 || page > props.totalPages) return
  emit('page-change', { page, queryContext: props.queryContext })
}
</script>

<style scoped>
.pagination-card {
  margin-top: 12px;
  padding: 10px 14px;
  background: #f0f4ff;
  border: 1px solid #c8d8f0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-info {
  font-size: 13px;
  color: #555;
}

.pagination-info strong {
  color: #6366f1;
}

.pagination-actions {
  display: flex;
  gap: 8px;
}

.btn-page {
  padding: 5px 14px;
  border: 1px solid #6366f1;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  background: #fff;
  color: #6366f1;
  transition: all 0.15s;
}

.btn-page:hover:not(:disabled) {
  background: #6366f1;
  color: #fff;
}

.btn-page:disabled {
  border-color: #ccc;
  color: #ccc;
  cursor: not-allowed;
  background: #f9f9f9;
}
</style>
