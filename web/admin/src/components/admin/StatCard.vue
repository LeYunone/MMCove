<template>
  <div class="stat-card" :style="{ borderLeftColor: color }">
    <div class="stat-info">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value">{{ value }}</div>
    </div>
    <div class="stat-icon" :style="{ color: color }">
      <el-icon :size="32"><component :is="icon" /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], required: true },
  icon: { type: String, required: true },
  color: { type: String, default: '#409eff' }
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-left: 4px solid;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-icon {
  opacity: 0.8;
}
</style>
