<template>
  <el-dialog
    v-model="visible"
    title="Token 详情"
    width="700px"
    :close-on-click-modal="false"
    align-center
  >
    <el-descriptions :column="2" border>
      <el-descriptions-item label="Token ID">{{ token.id }}</el-descriptions-item>
      <el-descriptions-item label="Token名称">
        {{ token.name || '未命名' }}
      </el-descriptions-item>

      <el-descriptions-item label="所属用户">
        <span v-if="userInfo">
          {{ userInfo.username }} (ID: {{ token.userId }})
        </span>
        <span v-else>ID: {{ token.userId }}</span>
      </el-descriptions-item>

      <el-descriptions-item label="Token密钥">
        <el-tooltip
          :content="token.tokenKey"
          placement="top"
        >
          <span class="token-key">{{ token.maskedKey }}</span>
        </el-tooltip>
      </el-descriptions-item>

      <el-descriptions-item label="状态">
        <el-tag :type="getStatusType(token.status)">
          {{ getStatusText(token.status) }}
        </el-tag>
      </el-descriptions-item>

      <el-descriptions-item label="配额类型">
        <el-tag v-if="token.unlimitedQuota" type="success">无限配额</el-tag>
        <el-tag v-else type="info">有限配额</el-tag>
      </el-descriptions-item>

      <el-descriptions-item label="剩余配额">
        <span v-if="token.unlimitedQuota">∞</span>
        <span v-else>{{ token.remainQuota || 0 }}</span>
      </el-descriptions-item>

      <el-descriptions-item label="已用配额">
        {{ token.usedQuota || 0 }}
      </el-descriptions-item>

      <el-descriptions-item label="创建时间">
        {{ formatTime(token.createdTime) }}
      </el-descriptions-item>

      <el-descriptions-item label="最后访问">
        <span v-if="token.accessedTime">
          {{ formatTime(token.accessedTime) }}
        </span>
        <span v-else class="text-gray">暂未访问</span>
      </el-descriptions-item>

      <el-descriptions-item label="过期时间">
        <span v-if="token.expiredTime === -1" class="text-success">永不过期</span>
        <span v-else-if="token.expiredTime">
          {{ formatTime(token.expiredTime) }}
        </span>
        <span v-else class="text-gray">未设置</span>
      </el-descriptions-item>

      <el-descriptions-item label="分组名称" span="2">
        {{ token.groupName || '无' }}
      </el-descriptions-item>
    </el-descriptions>

    <!-- 模型限制 -->
    <el-card class="limit-card" shadow="never">
      <template #header>
        <span>使用限制</span>
      </template>

      <div class="limit-item">
        <span class="label">模型限制：</span>
        <span>
          <el-switch
            v-model="modelLimitsEnabled"
            disabled
            size="small"
          />
        </span>
        <span class="value">
          {{ modelLimitsEnabled ?
            (token.modelLimits || '无限制') :
            '未启用'
          }}
        </span>
      </div>

      <div class="limit-item">
        <span class="label">IP限制：</span>
        <span class="value">
          <template v-if="token.allowIps">
            <el-text type="primary">{{ token.allowIps }}</el-text>
            <el-button
              size="small"
              type="primary"
              link
              @click="showIpDetail = !showIpDetail"
            >
              {{ showIpDetail ? '收起' : '展开' }}
            </el-button>
          </template>
          <template v-else>
            <span class="text-gray">无限制</span>
          </template>
        </span>
      </div>

      <!-- IP详情（展开时显示） -->
      <div v-if="showIpDetail && token.allowIps" class="ip-detail">
        <el-divider />
        <div class="ip-list">
          <div v-for="ip in ipList" :key="ip" class="ip-item">
            <el-icon><Location /></el-icon>
            {{ ip }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- 使用统计 -->
    <el-card class="stats-card" shadow="never" style="margin-top: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>使用统计</span>
          <el-button
            size="small"
            type="primary"
            link
            :loading="statsLoading"
            @click="refreshStats"
          >
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div class="stats-content" v-loading="statsLoading">
        <div class="stat-item">
          <div class="stat-value">{{ totalRequests }}</div>
          <div class="stat-label">总请求次数</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ totalTokens }}</div>
          <div class="stat-label">Token消耗</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ successRate }}%</div>
          <div class="stat-label">成功率</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ avgResponse }}ms</div>
          <div class="stat-label">平均响应时间</div>
        </div>
        <div class="stat-item">
          <div class="stat-value stat-value-sm">{{ lastUsed }}</div>
          <div class="stat-label">最后使用时间</div>
        </div>
      </div>
    </el-card>

    <template #footer>
      <el-button type="primary" @click="copyToken">
        <el-icon><CopyDocument /></el-icon>
        复制 Token
      </el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Refresh, Location } from '@element-plus/icons-vue'
import request from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  token: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const showIpDetail = ref(false)
const statsLoading = ref(false)
const totalRequests = ref('-')
const totalTokens = ref('-')
const successRate = ref('-')
const lastUsed = ref('-')
const avgResponse = ref('-')

const modelLimitsEnabled = computed(() => props.token.modelLimitsEnabled)

const ipList = computed(() => {
  if (!props.token.allowIps) return []
  return props.token.allowIps.split('\n').filter(ip => ip.trim())
})

function getStatusText(status) {
  const statusMap = { 0: '待审核', 1: '启用', 2: '禁用', 3: '过期', 4: '额度用尽', 5: '已拒绝' }
  return statusMap[status] || '未知'
}

function getStatusType(status) {
  const typeMap = { 1: '', 2: 'warning', 3: 'danger', 4: 'info' }
  return typeMap[status] || 'info'
}

function formatTime(timestamp) {
  if (!timestamp) return '-'
  const ms = timestamp > 1e12 ? timestamp : timestamp * 1000
  return new Date(ms).toLocaleString('zh-CN')
}

function formatDateTime(dtStr) {
  if (!dtStr) return '-'
  return new Date(dtStr).toLocaleString('zh-CN')
}

function formatTokenCount(count) {
  if (!count) return '0'
  const n = Number(count)
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return n.toLocaleString()
}

function copyToken() {
  const token = props.token.tokenKey
  navigator.clipboard.writeText(token).then(() => {
    ElMessage.success('Token 已复制到剪贴板')
  })
}

async function refreshStats() {
  if (!props.token?.id) return
  statsLoading.value = true
  try {
    const res = await request.get(`/api/token/${props.token.id}/stats`)
    if (res.code === 0 && res.data) {
      const d = res.data
      totalRequests.value = d.totalRequests || 0
      totalTokens.value = formatTokenCount(d.totalTokens)
      successRate.value = d.successRate != null ? d.successRate : '-'
      avgResponse.value = d.avgResponseMs != null ? d.avgResponseMs : '-'
      lastUsed.value = d.lastUsedTime ? formatDateTime(d.lastUsedTime) : '暂无'
    }
  } catch {
    totalRequests.value = '-'
    totalTokens.value = '-'
    successRate.value = '-'
    avgResponse.value = '-'
    lastUsed.value = '-'
  } finally {
    statsLoading.value = false
  }
}

watch(() => props.token, (newToken) => {
  if (newToken) {
    refreshStats()
  }
}, { immediate: true })
</script>

<style scoped>
.token-key {
  font-family: monospace;
  color: #409eff;
  font-weight: 500;
  cursor: pointer;
}

.text-gray {
  color: #999;
}

.text-success {
  color: #67c23a;
}

.limit-card {
  margin-top: 20px;
}

.limit-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.label {
  font-weight: 500;
  color: #666;
  min-width: 80px;
}

.value {
  flex: 1;
}

.ip-detail {
  margin-top: 12px;
}

.ip-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}

.ip-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
}

.stats-card {
  margin-top: 20px;
}

.stats-content {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.stat-value-sm {
  font-size: 13px;
  font-weight: 500;
}

.stat-item {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 12px;
  color: #666;
}

</style>