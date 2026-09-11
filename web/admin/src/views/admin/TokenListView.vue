<template>
  <div class="token-list" v-loading="store.loading">
    <div class="list-header">
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索Token名称"
          style="width: 200px"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <router-link to="/admin/tokens/create">
        <el-button type="primary" :icon="Plus">新建 Token</el-button>
      </router-link>
    </div>

    <el-table :data="store.tokens" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" width="150" show-overflow-tooltip />
      <el-table-column label="Token Key" width="200">
        <template #default="{ row }">
          <code class="token-key">{{ maskKey(row.key) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="剩余配额" width="120" align="right">
        <template #default="{ row }">
          <span v-if="row.unlimitedQuota" class="unlimited-tag">无限</span>
          <span v-else>{{ formatQuota(row.remainQuota) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="已用配额" width="100" align="right">
        <template #default="{ row }">
          {{ formatQuota(row.usedQuota) }}
        </template>
      </el-table-column>
      <el-table-column label="模型限制" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.modelLimitsEnabled" type="info" size="small">已启用</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" width="160">
        <template #default="{ row }">
          <span v-if="row.expiredTime === -1">永不过期</span>
          <span v-else>{{ formatTime(row.expiredTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后访问" width="160">
        <template #default="{ row }">
          {{ formatTime(row.accessedTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleCopy(row)">复制Key</el-button>
          <router-link :to="`/admin/tokens/${row.id}`">
            <el-button type="primary" link size="small">编辑</el-button>
          </router-link>
          <el-button
            :type="row.status === 1 ? 'warning' : 'success'"
            link
            size="small"
            @click="handleToggle(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="store.tokens.length > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="store.tokens.length"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { useTokenStore } from '@/stores/admin/token'
import { ElMessage } from 'element-plus'
import { useClipboard } from '@vueuse/core'

const store = useTokenStore()
const { copy } = useClipboard()

const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

// 加载数据
onMounted(() => {
  store.fetchTokens()
})

// 搜索
function handleSearch() {
  if (searchKeyword.value) {
    store.search({ keyword: searchKeyword.value })
  } else {
    store.fetchTokens()
  }
}

// 分页
function handlePageChange() {
  store.fetchTokens({ p: currentPage.value - 1, size: pageSize.value })
}

function handleSizeChange() {
  currentPage.value = 1
  store.fetchTokens({ p: 0, size: pageSize.value })
}

// 复制Key
async function handleCopy(row) {
  try {
    await copy(row.key)
    ElMessage.success('Token Key 已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

// 切换状态
function handleToggle(row) {
  store.toggleStatus(row.id, row.status)
}

// 删除
function handleDelete(row) {
  store.removeToken(row.id)
}

// 格式化配额
function formatQuota(quota) {
  if (quota === null || quota === undefined) return '-'
  return quota.toLocaleString()
}

// 格式化时间
function formatTime(timestamp) {
  if (!timestamp || timestamp <= 0) return '-'
  const date = new Date(timestamp * 1000)
  return date.toLocaleString('zh-CN')
}

// 脱敏Key
function maskKey(key) {
  if (!key) return '-'
  if (key.length < 10) return '***'
  return key.substring(0, 8) + '***' + key.substring(key.length - 4)
}

// 状态类型
function getStatusType(status) {
  const types = {
    1: 'success',
    2: 'warning',
    3: 'info',
    4: 'danger'
  }
  return types[status] || 'info'
}

// 状态文本
function getStatusText(status) {
  const texts = {
    1: '启用',
    2: '禁用',
    3: '已过期',
    4: '额度用尽'
  }
  return texts[status] || '未知'
}
</script>

<style scoped>
.token-list {
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

.search-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.token-key {
  font-size: 12px;
  color: #666;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
}

.unlimited-tag {
  color: #67c23a;
  font-weight: 500;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
