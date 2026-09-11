<template>
  <div class="vibe-workspace">
    <div class="ws-header">
      <el-button size="small" @click="router.push('/admin/vibe')">← 换个产品</el-button>
      <div class="ws-title">
        <h2>{{ line?.name || '...' }}</h2>
        <el-tag v-if="line" size="small" effect="plain">{{ line.code }}</el-tag>
        <span class="ws-desc">{{ line?.description }}</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="ws-tabs" @tab-change="onTabChange">
      <el-tab-pane label="流水线编排" name="pipelines">
        <PipelinePanel v-if="activeTab === 'pipelines'" :line-id="lineId" />
      </el-tab-pane>
      <el-tab-pane label="工序模板库" name="stages">
        <StagePanel v-if="activeTab === 'stages'" :line-id="lineId" />
      </el-tab-pane>
      <el-tab-pane label="任务执行记录" name="runs">
        <RunPanel v-if="activeTab === 'runs'" :line-id="lineId" />
      </el-tab-pane>
      <el-tab-pane label="知识库" name="knowledge">
        <KnowledgeBaseView v-if="activeTab === 'knowledge'" :fixed-line-id="lineId" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listProductLines } from '@/api/productLine'
import PipelinePanel from '@/components/vibe/PipelinePanel.vue'
import StagePanel from '@/components/vibe/StagePanel.vue'
import RunPanel from '@/components/vibe/RunPanel.vue'
import KnowledgeBaseView from '@/views/admin/KnowledgeBaseView.vue'

const route = useRoute()
const router = useRouter()
const line = ref(null)
const activeTab = ref('pipelines')

const lineId = computed(() => Number(route.params.lineId))

function onTabChange(tab) {
  router.replace(`/admin/vibe/${lineId.value}/${tab}`)
}

async function loadLine() {
  const res = await listProductLines()
  line.value = (res.data || []).find(l => l.id === lineId.value) || null
}

function syncFromRoute() {
  const tab = route.params.tab
  if (['pipelines', 'stages', 'runs', 'knowledge'].includes(tab)) {
    activeTab.value = tab
  }
  loadLine()
}
onMounted(syncFromRoute)
watch(() => route.params.lineId, syncFromRoute)
watch(() => route.params.tab, (tab) => {
  if (['pipelines', 'stages', 'runs', 'knowledge'].includes(tab) && tab !== activeTab.value) {
    activeTab.value = tab
  }
})
</script>

<style scoped>
.vibe-workspace { padding: 16px; }
.ws-header { display: flex; align-items: center; gap: 14px; margin-bottom: 4px; }
.ws-title { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.ws-title h2 { margin: 0; font-size: 18px; }
.ws-desc { font-size: 12px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ws-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
</style>
