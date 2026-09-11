<template>
  <div class="token-management">
    <el-card class="page-header">
      <template #header>
        <div class="header-content">
          <h2>Token 管理</h2>
          <div class="header-actions">
            <div class="auth-switch">
              <span class="switch-label">接口认证</span>
              <el-switch
                v-model="authSwitchEnabled"
                active-text="开启"
                inactive-text="关闭"
                :loading="authSwitchLoading"
                @change="handleAuthSwitchChange"
              />
              <el-tooltip content="关闭后，/v1/chat/completions 接口无需 Token 即可访问" placement="bottom">
                <el-icon class="switch-tip"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <el-button type="primary" @click="showCreateDialog = true">
              <el-icon><Plus /></el-icon>
              创建 Token
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :model="searchForm" inline>
        <el-form-item label="用户ID">
          <el-input
            v-model="searchForm.userId"
            placeholder="输入用户ID"
            clearable
            style="width: 120px"
          />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="输入用户名"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索名称或Token"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchTokens">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetSearch">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Token列表 -->
    <el-card class="token-list">
      <el-table
        :data="tokenList"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="用户" width="150">
          <template #default="{ row }">
            <div>ID: {{ row.userId }}</div>
            <div v-if="row.userInfo">{{ row.userInfo.username }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Token" min-width="120">
          <template #default="{ row }">
            <el-tooltip
              placement="top"
              :content="row.tokenKey"
              v-if="row.tokenKey"
            >
              <span class="token-key">
                {{ row.tokenKey ? row.maskedKey : '-' }}
              </span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="100">
          <template #default="{ row }">
            <span>{{ row.name || '未命名' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status-tag" :class="getStatusClass(row.status)">
              {{ getStatusText(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="分组" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.groupName || 'default' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="剩余额度" width="120" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.unlimitedQuota" content="无限额度" placement="top">
              <span style="color: #67c23a; font-weight: 600">∞</span>
            </el-tooltip>
            <el-tooltip v-else :content="`= ${(row.remainQuota || 0).toLocaleString()} tokens`" placement="top">
              <span :class="{ 'text-danger': (row.remainQuota || 0) < 100000 }">
                {{ formatQuota(row.remainQuota) }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="已用额度" width="120" align="center">
          <template #default="{ row }">
            <el-tooltip :content="`= ${(row.usedQuota || 0).toLocaleString()} tokens`" placement="top">
              <span>{{ formatQuota(row.usedQuota) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="模型限制" width="150">
          <template #default="{ row }">
            <span v-if="row.modelLimitsEnabled">
              {{ row.modelLimits || '无限制' }}
            </span>
            <span v-else class="text-gray">未启用</span>
          </template>
        </el-table-column>
        <el-table-column prop="groupName" label="分组" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.groupName" size="small" type="info">{{ row.groupName }}</el-tag>
            <span v-else class="text-gray">default</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="最后访问" width="180">
          <template #default="{ row }">
            <span v-if="row.accessedTime">
              {{ formatTime(row.accessedTime) }}
            </span>
            <span v-else class="text-gray">暂未访问</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              @click="viewTokenDetail(row)"
              text
            >
              查看
            </el-button>
            <el-button
              size="small"
              @click="editToken(row)"
              text
            >
              编辑
            </el-button>
            <el-dropdown>
              <el-button size="small" text type="primary">
                <el-icon><More /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="refreshToken(row)">
                    <el-icon><Refresh /></el-icon>
                    刷新Token
                  </el-dropdown-item>
                  <el-dropdown-item @click="showDeleteConfirm(row)">
                    <el-icon><Delete /></el-icon>
                    删除Token
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 创建Token对话框 -->
    <CreateTokenDialog
      v-model="showCreateDialog"
      @created="loadTokens"
    />

    <!-- 编辑Token对话框 -->
    <EditTokenDialog
      v-model="showEditDialog"
      :token="selectedToken"
      @updated="loadTokens"
    />

    <!-- Token详情对话框 -->
    <TokenDetailDialog
      v-model="showDetailDialog"
      :token="selectedToken"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, More, Delete, QuestionFilled } from '@element-plus/icons-vue'
import CreateTokenDialog from './CreateTokenDialog.vue'
import EditTokenDialog from './EditTokenDialog.vue'
import TokenDetailDialog from './TokenDetailDialog.vue'
import request from '@/api'
import { getAuthSwitch, updateAuthSwitch } from '@/api/token'

// 数据状态
const loading = ref(false)
const tokenList = ref([])
const selectedToken = ref(null)

// 搜索表单
const searchForm = reactive({
  userId: '',
  username: '',
  keyword: ''
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 弹窗控制
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const showDetailDialog = ref(false)

// 认证开关
const authSwitchEnabled = ref(true)
const authSwitchLoading = ref(false)

// 格式化配额为虚拟额度（1 额度 = 1000 tokens）
function formatQuota(quota) {
  if (!quota) return '0 额度'
  const credits = quota / 1000
  if (credits >= 10000) return (credits / 1000).toFixed(1) + 'K 额度'
  if (credits >= 1) return credits.toFixed(1) + ' 额度'
  return credits.toFixed(2) + ' 额度'
}

// 状态映射
const statusMap = {
  0: '待审核',
  1: '启用',
  2: '禁用',
  3: '过期',
  4: '额度用尽',
  5: '已拒绝'
}

const statusTypeMap = {
  0: 'info',
  1: 'success',
  2: 'warning',
  3: 'info',
  4: 'danger',
  5: 'danger'
}

// 获取Token列表
async function loadTokens() {
  loading.value = true
  try {
    const params = {
      p: pagination.page - 1,
      size: pagination.pageSize
    }

    // 添加搜索条件
    if (searchForm.userId) params.userId = searchForm.userId
    if (searchForm.username) params.username = searchForm.username
    if (searchForm.keyword) params.keyword = searchForm.keyword

    const res = await request.get('/api/token/admin', { params })
    if (res.code === 0) {
      tokenList.value = res.data.map(token => ({
        ...token,
        // 添加脱敏Key
        maskedKey: getMaskedKey(token.tokenKey)
      }))
      // 如果用户信息存在，直接使用
      if (res.userInfo) {
        tokenList.value.forEach(token => {
          if (token.userInfo) {
            // 使用返回的用户信息
          }
        })
      }
    } else {
      ElMessage.error(res.message || '获取Token列表失败')
    }
  } catch (error) {
    console.error('加载Token列表失败:', error)
    ElMessage.error('加载Token列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索Token
async function searchTokens() {
  pagination.page = 1
  await loadTokens()
}

// 重置搜索
function resetSearch() {
  Object.assign(searchForm, {
    userId: '',
    username: '',
    keyword: ''
  })
  searchTokens()
}

// 分页处理
function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadTokens()
}

function handleCurrentChange(page) {
  pagination.page = page
  loadTokens()
}

// 状态处理
function getStatusText(status) {
  return statusMap[status] || '未知'
}

function getStatusType(status) {
  return statusTypeMap[status] || 'info'
}

function getStatusClass(status) {
  const classMap = {
    0: 'tag-pending',
    1: 'tag-enabled',
    2: 'tag-disabled',
    3: 'tag-expired',
    4: 'tag-exhausted',
    5: 'tag-rejected'
  }
  return classMap[status] || ''
}

// 格式化时间
function formatTime(timestamp) {
  if (!timestamp) return '-'
  const date = new Date(timestamp * 1000)
  return date.toLocaleString('zh-CN')
}

// Token脱敏
function getMaskedKey(key) {
  if (!key || key.length < 10) return '***'
  return key.substring(0, 6) + '***' + key.substring(key.length - 4)
}

// 查看 Token 详情
function viewTokenDetail(token) {
  selectedToken.value = token
  showDetailDialog.value = true
}

// 编辑 Token
function editToken(token) {
  selectedToken.value = token
  showEditDialog.value = true
}

// 删除确认
function showDeleteConfirm(token) {
  ElMessageBox.confirm(
    `确定要删除Token "${token.name}"吗？删除后无法恢复。`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    await deleteToken(token.id)
  })
}

// 删除 Token
async function deleteToken(tokenId) {
  try {
    const res = await request.delete(`/api/token/admin/${tokenId}`)
    if (res.code === 0) {
      ElMessage.success('Token删除成功')
      loadTokens()
    } else {
      ElMessage.error(res.message || '删除Token失败')
    }
  } catch (error) {
    console.error('删除Token失败:', error)
    ElMessage.error('删除Token失败')
  }
}

// 刷新 Token
async function refreshToken(token) {
  try {
    ElMessageBox.confirm(
      `确定要为用户 "${token.userInfo?.username || token.userId}" 刷新Token吗？`,
      '确认刷新',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    ).then(async () => {
      // 这里可以调用刷新Token的API
      // const res = await request.post(`/api/token/admin/${token.id}/refresh`)
      ElMessage.success('Token刷新功能正在开发中')
    })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消操作')
    }
  }
}

// 加载认证开关状态
async function loadAuthSwitch() {
  try {
    const res = await getAuthSwitch()
    if (res.code === 0 && res.data) {
      authSwitchEnabled.value = res.data.enabled
    }
  } catch (e) {
    console.error('加载认证开关失败:', e)
  }
}

// 切换认证开关
async function handleAuthSwitchChange(val) {
  authSwitchLoading.value = true
  try {
    const res = await updateAuthSwitch(val)
    if (res.code === 0) {
      ElMessage.success(val ? '已开启接口认证' : '已关闭接口认证，接口无需Token即可访问')
    } else {
      authSwitchEnabled.value = !val
      ElMessage.error(res.message || '更新失败')
    }
  } catch (e) {
    authSwitchEnabled.value = !val
    ElMessage.error('更新认证开关失败')
  } finally {
    authSwitchLoading.value = false
  }
}

// 初始化
onMounted(() => {
  loadTokens()
  loadAuthSwitch()
})
</script>

<style scoped>
.token-management {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.auth-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.switch-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.switch-tip {
  color: #909399;
  cursor: help;
}

.token-list {
  margin-top: 20px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.token-key {
  font-family: monospace;
  color: #409eff;
  font-weight: 500;
}

.text-gray {
  color: #999;
}

:deep(.el-table__row:hover .token-key) {
  color: #606266;
}

/* 状态标签 */
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.tag-pending {
  background: #ecf5ff;
  color: #409eff;
}

.tag-enabled {
  background: #f0f9eb;
  color: #67c23a;
}

.tag-disabled {
  background: #fdf6ec;
  color: #e6a23c;
}

.tag-expired {
  background: #f4f4f5;
  color: #909399;
}

.tag-exhausted {
  background: #fef0f0;
  color: #f56c6c;
}

.tag-rejected {
  background: #fef0f0;
  color: #f56c6c;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}
</style>