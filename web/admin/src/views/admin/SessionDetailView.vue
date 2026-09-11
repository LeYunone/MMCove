<template>
  <div class="session-detail" v-loading="loading">
    <div v-if="session" class="info-card">
      <h3>会话信息</h3>
      <el-descriptions :column="2" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="会话 ID">{{ session.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ session.title }}</el-descriptions-item>
        <el-descriptions-item label="Agent">{{ session.agentType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="session.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ session.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(session.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(session.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="messages-card">
      <h3>消息历史 <span class="msg-count">({{ store.messages.length }} 条)</span></h3>
      <MessageTimeline :messages="store.messages" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAdminSessionStore } from '@/stores/admin/session'
import MessageTimeline from '@/components/admin/MessageTimeline.vue'

const route = useRoute()
const store = useAdminSessionStore()

const session = ref(null)
const loading = ref(false)

onMounted(async () => {
  const sessionId = route.params.id
  loading.value = true
  try {
    session.value = await store.fetchSession(sessionId)
    await store.fetchMessages(sessionId)
  } finally {
    loading.value = false
  }
})

function formatTime(time) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.session-detail {
  max-width: 1000px;
}

.info-card,
.messages-card {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.info-card {
  margin-bottom: 16px;
}

.info-card h3,
.messages-card h3 {
  margin: 0 0 4px 0;
  font-size: 15px;
  color: #303133;
}

.msg-count {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}
</style>
