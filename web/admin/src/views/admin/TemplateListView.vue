<template>
  <div class="template-list" v-loading="store.loading">
    <div class="list-header">
      <div class="filters">
        <el-select v-model="scopeFilter" placeholder="Scope 筛选" clearable style="width: 160px" @change="loadData">
          <el-option label="SCENE" value="SCENE" />
          <el-option label="TOOL" value="TOOL" />
        </el-select>
        <el-input
          v-model="searchText"
          placeholder="搜索模板名称..."
          clearable
          style="width: 220px"
          @input="onSearchInput"
        />
      </div>
      <div class="actions">
        <el-button @click="handleRefreshCache">刷新缓存</el-button>
        <router-link to="/admin/templates/create">
          <el-button type="primary" :icon="Plus">新增模板</el-button>
        </router-link>
      </div>
    </div>

    <el-table :data="store.templates" stripe style="width: 100%">
      <el-table-column prop="templateCode" label="模板编码" width="180" show-overflow-tooltip />
      <el-table-column prop="templateName" label="模板名称" width="160" show-overflow-tooltip />
      <el-table-column prop="scope" label="Scope" width="90">
        <template #default="{ row }">
          <el-tag :type="row.scope === 'SCENE' ? '' : 'warning'" size="small">{{ row.scope }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sceneType" label="场景类型" width="120" show-overflow-tooltip />
      <el-table-column prop="toolName" label="工具方法" width="120" show-overflow-tooltip />
      <el-table-column prop="agentId" label="Agent" width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.agentId || '全局' }}
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <router-link :to="`/admin/templates/${row.id}`">
            <el-button type="primary" link size="small">编辑</el-button>
          </router-link>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="store.pageNum"
        v-model:page-size="store.pageSize"
        :total="store.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useAdminTemplateStore } from '@/stores/admin/template'
import { refreshTemplateCache } from '@/api/responseTemplate'
import { ElMessage, ElMessageBox } from 'element-plus'

const store = useAdminTemplateStore()

const scopeFilter = ref('')
const searchText = ref('')

let searchTimer = null

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.pageNum = 1
    loadData()
  }, 300)
}

function loadData() {
  store.fetchTemplates(scopeFilter.value, searchText.value)
}

function onSizeChange() {
  store.pageNum = 1
  loadData()
}

function onPageChange() {
  loadData()
}

async function handleRefreshCache() {
  try {
    await refreshTemplateCache()
    ElMessage.success('缓存已刷新')
  } catch {
    // handled
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.templateName}」？`, '确认', {
      type: 'warning'
    })
    await store.removeTemplate(row.id)
  } catch {
    // cancelled
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.template-list {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 12px;
}

.actions {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
