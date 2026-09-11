<template>
  <div class="agent-list" v-loading="store.loading">
    <div class="list-header">
      <router-link to="/admin/agents/create">
        <el-button type="primary" :icon="Plus">新增 Agent</el-button>
      </router-link>
    </div>

    <el-table :data="store.agents" stripe style="width: 100%">
      <el-table-column prop="agentId" label="Agent ID" width="180" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="temperature" label="温度" width="80" />
      <el-table-column prop="maxTokens" label="Token 数" width="100" />
      <el-table-column label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <router-link :to="`/admin/agents/${row.agentId}`">
            <el-button type="primary" link size="small">编辑</el-button>
          </router-link>
          <el-button
            :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
            link
            size="small"
            @click="handleToggle(row)"
          >
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useAdminAgentStore } from '@/stores/admin/agent'

const store = useAdminAgentStore()

onMounted(() => {
  store.fetchAgents()
})

function handleToggle(row) {
  const newStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  store.switchStatus(row.agentId, newStatus)
}
</script>

<style scoped>
.agent-list {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.list-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
