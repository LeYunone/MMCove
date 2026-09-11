<template>
  <div class="session-list" v-loading="store.loading">
    <div class="list-header">
      <span class="total-label">共 {{ store.sessions.length }} 个会话</span>
    </div>

    <el-table :data="store.sessions" stripe style="width: 100%">
      <el-table-column prop="sessionId" label="会话 ID" width="280" show-overflow-tooltip />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="agentType" label="Agent" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <router-link :to="`/admin/sessions/${row.sessionId}`">
            <el-button type="primary" link size="small">查看</el-button>
          </router-link>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAdminSessionStore } from '@/stores/admin/session'
import { ElMessageBox } from 'element-plus'

const store = useAdminSessionStore()

onMounted(() => {
  store.fetchSessions()
})

function formatTime(time) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${row.title || row.sessionId}」？`, '确认', {
      type: 'warning'
    })
    await store.removeSession(row.sessionId)
  } catch {
    // cancelled
  }
}
</script>

<style scoped>
.session-list {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.list-header {
  margin-bottom: 16px;
}

.total-label {
  font-size: 13px;
  color: #909399;
}
</style>
