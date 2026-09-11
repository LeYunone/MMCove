<template>
  <div class="run-panel">
    <div class="panel-toolbar">
      <span class="tip">本产品线交给 AI 的每一次编码任务:进行到哪一步、每步交了什么作业、最后完成没有</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-table :data="runs" v-loading="loading" border stripe>
      <el-table-column label="任务单号" prop="runCode" width="130">
        <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.runCode }}</el-tag></template>
      </el-table-column>
      <el-table-column label="用户交给 AI 的任务" prop="taskText" min-width="240" show-overflow-tooltip />
      <el-table-column label="任务类型" width="100">
        <template #default="{ row }">
          <el-tag :type="intentTagType(row.intentType)" size="small">{{ intentLabel(row.intentType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="80" align="center">
        <template #default="{ row }">{{ progressOf(row) }}</template>
      </el-table-column>
      <el-table-column label="当前工序" prop="currentStageCode" width="140" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发起时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > query.size" style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next" :total="total" :page-size="query.size"
      :current-page="query.page + 1" @current-change="(p) => { query.page = p - 1; load() }" />

    <el-drawer v-model="detailVisible" :title="`任务 ${detail?.run?.runCode || ''}`" size="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="任务" :span="2">{{ detail.run.taskText }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ intentLabel(detail.run.intentType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.run.status)" size="small">{{ statusLabel(detail.run.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="sec-title">工序进度</div>
        <el-timeline>
          <el-timeline-item v-for="(s, i) in detailStages" :key="s.stageCode"
            :type="timelineType(s)" :hollow="!isCompleted(s)">
            <div class="tl-stage">
              <span class="tl-name">{{ i + 1 }}. {{ s.name }}</span>
              <el-tag size="small" :type="isCompleted(s) ? 'success' : s.stageCode === detail.run.currentStageCode ? 'primary' : 'info'">
                {{ isCompleted(s) ? '已完成' : s.stageCode === detail.run.currentStageCode ? '进行中' : '待执行' }}
              </el-tag>
            </div>
            <div class="tl-deliverable">{{ s.deliverable }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="sec-title">各工序交出的作业({{ detail.artifacts.length }})</div>
        <div v-if="!detail.artifacts.length" class="empty-tip">暂无产出</div>
        <div v-for="a in detail.artifacts" :key="a.id" class="artifact-item">
          <div class="artifact-head" @click="toggleArtifact(a.id)">
            <el-tag size="small" effect="plain">{{ a.stageCode }}</el-tag>
            <span class="artifact-title">{{ a.title }}</span>
            <span v-if="a.summary" class="artifact-summary">— {{ a.summary }}</span>
            <span class="artifact-toggle">{{ expandedArtifact === a.id ? '收起' : '查看内容' }}</span>
          </div>
          <pre v-if="expandedArtifact === a.id" class="artifact-content">{{ a.content }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { pageRuns, getRunDetail } from '@/api/vibe'

const props = defineProps({ lineId: { type: Number, required: true } })
const runs = ref([])
const loading = ref(false)
const total = ref(0)
const query = ref({ page: 0, size: 20 })
const detailVisible = ref(false)
const detail = ref(null)
const expandedArtifact = ref(null)
const detailStages = ref([])

function progressOf(row) {
  try {
    const done = JSON.parse(row.completedStages || '[]').length
    const all = JSON.parse(row.effectiveStages || '[]').length
    return all ? `${done}/${all}` : '-'
  } catch { return '-' }
}
function isCompleted(s) { return detail.value?.completedCodes?.includes(s.stageCode) }
function timelineType(s) { return isCompleted(s) ? 'success' : 'primary' }

async function openDetail(row) {
  const res = await getRunDetail(row.runCode)
  detail.value = res.data
  try { detail.value.completedCodes = JSON.parse(detail.value.run.completedStages || '[]') } catch { detail.value.completedCodes = [] }
  try {
    const eff = JSON.parse(detail.value.run.effectiveStages || '[]')
    detailStages.value = eff.map(e => typeof e === 'string' ? { stageCode: e, name: e } : { stageCode: e.stageCode, name: e.name, deliverable: e.deliverable })
  } catch { detailStages.value = [] }
  expandedArtifact.value = null
  detailVisible.value = true
}
function toggleArtifact(id) { expandedArtifact.value = expandedArtifact.value === id ? null : id }

function statusLabel(s) {
  return { COMPLETED: '已完成', RUNNING: '进行中', ABORTED: '已中止', FAILED: '失败' }[s] || s
}
function statusTagType(s) {
  return { COMPLETED: 'success', RUNNING: 'primary', ABORTED: 'info', FAILED: 'danger' }[s] || 'info'
}
function intentLabel(t) {
  return { BUG_FIX: '修 Bug', FEATURE_DEV: '做新功能', REFACTOR: '重构优化', TEST_ENHANCE: '补测试' }[t] || t
}
function intentTagType(t) {
  return { BUG_FIX: 'danger', FEATURE_DEV: 'success', REFACTOR: 'warning', TEST_ENHANCE: 'primary' }[t] || 'info'
}
function formatTime(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

async function load() {
  loading.value = true
  try {
    const res = await pageRuns({ page: query.value.page, size: query.value.size, productLineId: props.lineId })
    runs.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } finally { loading.value = false }
}
onMounted(load)
watch(() => props.lineId, load)
</script>

<style scoped>
.panel-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; }
.tip { font-size: 12px; color: #909399; }
.detail-desc { margin-bottom: 16px; }
.sec-title { font-weight: 600; font-size: 13px; margin: 18px 0 10px; }
.tl-stage { display: flex; align-items: center; gap: 8px; }
.tl-name { font-weight: 500; font-size: 13px; }
.tl-deliverable { font-size: 12px; color: #909399; }
.artifact-item { border: 1px solid #eee; border-radius: 6px; margin-bottom: 8px; overflow: hidden; }
.artifact-head { display: flex; align-items: center; gap: 6px; padding: 8px 10px; cursor: pointer; background: #fafafa; }
.artifact-head:hover { background: #f0f7ff; }
.artifact-title { font-size: 13px; font-weight: 500; }
.artifact-summary { font-size: 12px; color: #909399; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.artifact-toggle { font-size: 12px; color: #409eff; flex-shrink: 0; }
.artifact-content { font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-all;
  background: #fff; padding: 10px 12px; margin: 0; max-height: 360px; overflow-y: auto; }
.empty-tip { color: #909399; font-size: 12px; }
</style>
