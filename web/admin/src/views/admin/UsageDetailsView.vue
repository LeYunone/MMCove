<template>
  <div class="usage-details" v-loading="loading">
    <!-- 筛选区 -->
    <div class="filter-bar">
      <el-select
        v-model="filterTokenId"
        placeholder="选择 Token"
        clearable
        filterable
        style="width: 220px"
      >
        <el-option
          v-for="item in tokenOptions"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>
      <el-input
        v-model="filterModel"
        placeholder="模型名称"
        clearable
        style="width: 200px"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 260px"
      />
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 明细表格 -->
    <el-table :data="list" stripe style="width: 100%" size="small">
      <el-table-column label="Token" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="token-key">{{ maskKey(row.tokenKey) }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="model" label="模型" min-width="140" show-overflow-tooltip />
      <el-table-column label="请求开始时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.requestStartTime) }}
        </template>
      </el-table-column>
      <el-table-column label="请求结束时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.requestEndTime) }}
        </template>
      </el-table-column>
      <el-table-column label="所用时长" width="110" align="right">
        <template #default="{ row }">
          {{ row.responseTimeMs != null ? row.responseTimeMs + ' ms' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="输入长度" width="100" align="right">
        <template #default="{ row }">
          {{ row.inputTextLength ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="返回长度" width="100" align="right">
        <template #default="{ row }">
          {{ row.outputTextLength ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="请求方 IP" width="140" show-overflow-tooltip />
      <el-table-column label="状态码" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.statusCode)" size="small">
            {{ row.statusCode ?? '-' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import request from '@/api/index'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filterTokenId = ref(null)
const filterModel = ref('')
const dateRange = ref([])

const tokenOptions = ref([])

async function fetchTokenOptions() {
  try {
    const res = await request.get('/api/dashboard/quota-details')
    tokenOptions.value = (res.data || []).map(t => ({ id: t.id, name: t.name }))
  } catch {
    tokenOptions.value = []
  }
}

async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value
    }
    if (filterTokenId.value != null) params.tokenId = filterTokenId.value
    if (filterModel.value) params.model = filterModel.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.start = dateRange.value[0]
      params.end = dateRange.value[1]
    }
    const res = await request.get('/api/dashboard/usage/logs', { params })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  filterTokenId.value = null
  filterModel.value = ''
  dateRange.value = []
  page.value = 1
  fetchData()
}

function handleSizeChange() {
  page.value = 1
  fetchData()
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function maskKey(key) {
  if (!key) return '-'
  if (key.length < 10) return '***'
  return key.substring(0, 8) + '***' + key.substring(key.length - 4)
}

function getStatusTagType(code) {
  if (code == null) return 'info'
  if (code >= 200 && code < 300) return 'success'
  return 'danger'
}

onMounted(() => {
  fetchTokenOptions()
  fetchData()
})
</script>

<style scoped>
.usage-details {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.token-key {
  font-size: 12px;
  color: #666;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
