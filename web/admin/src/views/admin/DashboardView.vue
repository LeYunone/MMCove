<template>
  <div class="dashboard" v-loading="loading">
    <!-- 顶部统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card" style="border-left-color: #409eff">
        <div class="stat-info">
          <div class="stat-label">总请求数</div>
          <div class="stat-value">{{ overview.totalRequests?.toLocaleString() || '0' }}</div>
        </div>
      </div>
      <div class="stat-card" style="border-left-color: #67c23a">
        <div class="stat-info">
          <div class="stat-label">Token 消耗总量</div>
          <div class="stat-value">{{ formatTokenCount(overview.totalTokens) }}</div>
        </div>
      </div>
      <div class="stat-card" style="border-left-color: #e6a23c">
        <div class="stat-info">
          <div class="stat-label">活跃 Token 数</div>
          <div class="stat-value">{{ overview.activeTokens || 0 }}</div>
        </div>
      </div>
      <div class="stat-card" style="border-left-color: #f56c6c">
        <div class="stat-info">
          <div class="stat-label">活跃渠道数</div>
          <div class="stat-value">{{ overview.activeChannels || 0 }}</div>
        </div>
      </div>
    </div>

    <!-- 配额概览 -->
    <div class="stats-grid" style="grid-template-columns: repeat(3, 1fr);">
      <div class="stat-card" style="border-left-color: #409eff">
        <div class="stat-info">
          <div class="stat-label">已分配配额总额</div>
          <div class="stat-value">{{ formatQuota((overview.totalQuotaRemain || 0) + (overview.totalQuotaUsed || 0)) }}</div>
        </div>
      </div>
      <div class="stat-card" style="border-left-color: #67c23a">
        <div class="stat-info">
          <div class="stat-label">剩余配额</div>
          <div class="stat-value">{{ formatQuota(overview.totalQuotaRemain) }}</div>
        </div>
      </div>
      <div class="stat-card" style="border-left-color: #f56c6c">
        <div class="stat-info">
          <div class="stat-label">已使用配额</div>
          <div class="stat-value">{{ formatQuota(overview.totalQuotaUsed) }}</div>
        </div>
      </div>
    </div>

    <!-- 图表区域：每日趋势 + 分组消耗饼图 -->
    <div class="charts-grid">
      <div class="chart-card">
        <div class="chart-header">
          <h3>Token 使用趋势</h3>
          <el-select v-model="dailyDays" size="small" style="width: 100px" @change="fetchDailyStats">
            <el-option :value="7" label="最近7天" />
            <el-option :value="14" label="最近14天" />
            <el-option :value="30" label="最近30天" />
          </el-select>
        </div>
        <div ref="trendChartRef" style="height: 300px;"></div>
      </div>

      <div class="chart-card">
        <h3>按分组消耗分布</h3>
        <div ref="groupPieRef" style="height: 300px;"></div>
      </div>
    </div>

    <!-- 图表区域：模型消耗 + Token排行 -->
    <div class="charts-grid">
      <div class="chart-card">
        <h3>按模型消耗分布</h3>
        <div ref="modelChartRef" style="height: 300px;"></div>
      </div>

      <div class="chart-card">
        <h3>Token 消耗 Top 10</h3>
        <el-table
          v-if="tokenRanking.length > 0"
          :data="tokenRanking"
          stripe
          size="small"
          style="width: 100%"
        >
          <el-table-column type="index" label="#" width="40" />
          <el-table-column prop="tokenName" label="Token 名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="消耗量" min-width="100" align="right">
            <template #default="{ row }">
              {{ formatTokenCount(row.tokenUsage) }}
            </template>
          </el-table-column>
          <el-table-column label="请求次数" width="90" align="right">
            <template #default="{ row }">
              {{ row.requestCount?.toLocaleString() || 0 }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>

    <!-- 配额使用详情表格 -->
    <div class="chart-card" style="margin-bottom: 24px;">
      <h3>Token 配额使用详情</h3>
      <el-table :data="quotaDetails" stripe size="small" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="user_id" label="用户ID" width="120" show-overflow-tooltip />
        <el-table-column label="分组" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.group_name || 'default' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已用配额" width="120" align="right">
          <template #default="{ row }">
            {{ formatQuota(row.used_quota) }}
          </template>
        </el-table-column>
        <el-table-column label="剩余配额" width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.unlimited_quota" style="color: #67c23a">无限</span>
            <span v-else>{{ formatQuota(row.remain_quota) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="使用率" width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="row.usage_percent || 0"
              :stroke-width="14"
              :color="getProgressColor(row.usage_percent || 0)"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 最近会话 -->
    <div class="recent-section">
      <h3>最近会话</h3>
      <el-table :data="recentSessions" stripe style="width: 100%">
        <el-table-column prop="sessionId" label="会话 ID" width="280" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="agentType" label="Agent" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '@/api/index'

const loading = ref(false)
const dailyDays = ref(7)

const overview = ref({
  totalRequests: 0,
  totalTokens: 0,
  activeTokens: 0,
  activeChannels: 0,
  totalQuotaUsed: 0,
  totalQuotaRemain: 0
})

const dailyData = ref([])
const modelUsage = ref([])
const tokenRanking = ref([])
const groupUsage = ref([])
const quotaDetails = ref([])
const recentSessions = ref([])

const trendChartRef = ref(null)
const groupPieRef = ref(null)
const modelChartRef = ref(null)

let trendChart = null
let groupPieChart = null
let modelBarChart = null

function formatTokenCount(count) {
  if (!count) return '0'
  const n = Number(count)
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return n.toLocaleString()
}

function formatQuota(val) {
  if (!val) return '0 额度'
  const n = Number(val)
  const credits = n / 1000
  if (credits >= 10000) return (credits / 1000).toFixed(1) + 'K 额度'
  if (credits >= 1) return credits.toFixed(1) + ' 额度'
  return credits.toFixed(2) + ' 额度'
}

function getProgressColor(pct) {
  if (pct >= 80) return '#f56c6c'
  if (pct >= 50) return '#e6a23c'
  return '#409eff'
}

function initCharts() {
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
  }
  if (groupPieRef.value) {
    groupPieChart = echarts.init(groupPieRef.value)
  }
  if (modelChartRef.value) {
    modelBarChart = echarts.init(modelChartRef.value)
  }
}

function renderTrendChart() {
  if (!trendChart || dailyData.value.length === 0) return
  const dates = dailyData.value.map(d => d.stat_date || d.date || '')
  const requests = dailyData.value.map(d => Number(d.requests) || 0)
  const tokens = dailyData.value.map(d => Number(d.tokens) || Number(d.total_tokens) || 0)

  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['请求数', 'Token数'], top: 0 },
    grid: { top: 36, left: 50, right: 50, bottom: 30 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '请求数', position: 'left' },
      { type: 'value', name: 'Token数', position: 'right' }
    ],
    series: [
      {
        name: '请求数', type: 'bar', data: requests,
        itemStyle: { color: '#409eff', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 30
      },
      {
        name: 'Token数', type: 'line', yAxisIndex: 1, data: tokens,
        smooth: true, itemStyle: { color: '#67c23a' },
        lineStyle: { width: 2 }, areaStyle: { color: 'rgba(103,194,58,0.1)' }
      }
    ]
  })
}

function renderGroupPie() {
  if (!groupPieChart || groupUsage.value.length === 0) return
  const data = groupUsage.value.map(g => ({
    name: g.group_name || 'default',
    value: Number(g.total_tokens) || Number(g.tokenUsage) || 0
  }))
  groupPieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} tokens ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['40%', '50%'],
      data: data,
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.3)' } },
      label: { show: true, formatter: '{b}\n{d}%' },
      itemStyle: {
        color: (params) => {
          const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6']
          return colors[params.dataIndex % colors.length]
        }
      }
    }]
  })
}

function renderModelChart() {
  if (!modelBarChart || modelUsage.value.length === 0) return
  const models = modelUsage.value.map(m => m.model)
  const values = modelUsage.value.map(m => Number(m.tokenUsage) || 0)
  modelBarChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 10, left: 120, right: 40, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: models, inverse: true, axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar', data: values,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#79bbff' }
        ]),
        borderRadius: [0, 3, 3, 0]
      },
      barMaxWidth: 24,
      label: { show: true, position: 'right', formatter: (p) => formatTokenCount(p.value), fontSize: 11 }
    }]
  })
}

function handleResize() {
  trendChart?.resize()
  groupPieChart?.resize()
  modelBarChart?.resize()
}

async function fetchOverview() {
  try {
    const res = await request.get('/api/dashboard/overview')
    if (res.data) {
      overview.value = { ...overview.value, ...res.data }
    }
  } catch { /* empty */ }
}

async function fetchDailyStats() {
  try {
    const res = await request.get('/api/dashboard/usage/daily', { params: { days: dailyDays.value } })
    dailyData.value = res.data || []
    await nextTick()
    renderTrendChart()
  } catch {
    dailyData.value = []
  }
}

async function fetchModelUsage() {
  try {
    const res = await request.get('/api/dashboard/usage/by-model')
    const data = res.data || []
    if (data.length > 0) {
      const maxUsage = Math.max(...data.map(d => Number(d.total_tokens) || 0), 1)
      modelUsage.value = data.map(d => ({
        model: d.model,
        tokenUsage: Number(d.total_tokens) || 0,
        percentage: Math.round((Number(d.total_tokens) || 0) / maxUsage * 100)
      }))
    }
    await nextTick()
    renderModelChart()
  } catch { /* empty */ }
}

async function fetchTokenRanking() {
  try {
    const res = await request.get('/api/dashboard/usage/by-token')
    const data = res.data || []
    tokenRanking.value = data.slice(0, 10).map(d => ({
      tokenName: d.token_key || d.tokenName || d.token_id,
      tokenUsage: Number(d.total_tokens) || 0,
      requestCount: Number(d.requests) || 0
    }))
  } catch { /* empty */ }
}

async function fetchGroupUsage() {
  try {
    const res = await request.get('/api/dashboard/usage/by-group')
    groupUsage.value = res.data || []
    await nextTick()
    renderGroupPie()
  } catch { /* empty */ }
}

async function fetchQuotaDetails() {
  try {
    const res = await request.get('/api/dashboard/quota-details')
    quotaDetails.value = res.data || []
  } catch { /* empty */ }
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([
      fetchOverview(),
      fetchDailyStats(),
      fetchModelUsage(),
      fetchTokenRanking(),
      fetchGroupUsage(),
      fetchQuotaDetails()
    ])
    await nextTick()
    initCharts()
    renderTrendChart()
    renderGroupPie()
    renderModelChart()
    window.addEventListener('resize', handleResize)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  groupPieChart?.dispose()
  modelBarChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

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

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.chart-card h3 {
  margin: 0 0 16px 0;
  font-size: 15px;
  color: #303133;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.chart-header h3 {
  margin: 0;
}

.recent-section {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.recent-section h3 {
  margin: 0 0 16px 0;
  font-size: 15px;
  color: #303133;
}
</style>
