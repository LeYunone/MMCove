<template>
  <div class="confirm-card">
    <div class="card-icon">
      <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
        <path d="M9 1v6M9 11v1M17 9A8 8 0 1 1 1 9a8 8 0 0 1 16 0z" stroke="#e67e22" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
    </div>
    <div class="card-body">{{ summary }}</div>
    <div class="card-actions">
      <button class="btn-confirm" @click="$emit('confirm')">确认执行</button>
      <button class="btn-cancel" @click="$emit('cancel')">取消</button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  summary: {
    type: String,
    default: '即将执行一个需要确认的操作'
  }
})

defineEmits(['confirm', 'cancel'])
</script>

<style scoped>
.confirm-card {
  margin-top: 12px;
  padding: 12px 14px;
  background: #fffcf5;
  border: 1px solid #f5e6c8;
  border-radius: 10px;
}

.card-icon {
  margin-bottom: 6px;
}

.card-body {
  font-size: 13px;
  color: #555;
  margin-bottom: 12px;
  line-height: 1.6;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.btn-confirm {
  padding: 6px 16px;
  background: #e67e22;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-confirm:hover {
  background: #d35400;
}

.btn-cancel {
  padding: 6px 16px;
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-cancel:hover {
  background: #eee;
}
</style>
