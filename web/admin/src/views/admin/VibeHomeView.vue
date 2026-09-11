<template>
  <div class="vibe-home">
    <div class="page-header">
      <div>
        <h2>AI 编程流水线</h2>
        <p class="sub">先选择一个产品,进入它的工作区:编排 AI 干活的工序流程、积累工序模板、查看任务执行记录、管理知识库</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div v-loading="loading" class="home-wrapper">
      <el-empty v-if="!loading && !lines.length"
        description="还没有产品。去「产品线管理」创建产品后,这里就会出现对应的工作区" />
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" v-for="line in lines" :key="line.id" class="line-col">
          <el-card shadow="hover" class="line-card" @click="enter(line)">
            <div class="card-title">
              <span class="line-name">{{ line.name }}</span>
              <el-tag size="small" effect="plain">{{ line.code }}</el-tag>
            </div>
            <div class="card-desc">{{ line.description || '暂无描述,可在产品线管理中补充' }}</div>
            <div class="card-meta">
              <span>🧩 流水线: {{ stats[line.id]?.pipelines ?? '-' }} 条</span>
              <span>📚 知识库: {{ stats[line.id]?.kbs ?? '-' }} 个</span>
              <span>🤖 进行中任务: {{ stats[line.id]?.running ?? '-' }}</span>
            </div>
            <div class="card-actions">
              <el-button size="small" type="primary" @click.stop="enter(line)">进入工作区 →</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProductLines } from '@/api/productLine'
import { listPipelines, listBindings, pageRuns } from '@/api/vibe'
import { listKnowledgeBases } from '@/api/knowledgeBase'

const router = useRouter()
const lines = ref([])
const stats = ref({})
const loading = ref(false)

function enter(line) {
  router.push(`/admin/vibe/${line.id}/pipelines`)
}

async function load() {
  loading.value = true
  try {
    const [lineRes, kbRes] = await Promise.all([listProductLines(), listKnowledgeBases()])
    lines.value = lineRes.data || []
    const kbs = kbRes.data || []
    const statsMap = {}
    for (const line of lines.value) {
      statsMap[line.id] = {
        kbs: kbs.filter(k => k.productLineId === line.id).length,
        pipelines: 0,
        running: 0
      }
    }
    // 绑定流水线数 + 任务数逐线查(产品线数量有限)
    await Promise.all(lines.value.map(async (line) => {
      const [bindRes, runRes] = await Promise.all([
        listBindings(line.id),
        pageRuns({ productLineId: line.id, page: 0, size: 1 })
      ])
      statsMap[line.id].pipelines = (bindRes.data || []).filter(b => b.enabled === 1).length
      statsMap[line.id].running = runRes.data?.totalElements || 0
    }))
    stats.value = statsMap
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.vibe-home { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.page-header h2 { margin: 0 0 4px; font-size: 18px; }
.sub { margin: 0; color: #909399; font-size: 12px; }
.line-card { cursor: pointer; transition: all .15s; margin-bottom: 16px; }
.line-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,.1); }
.card-title { display: flex; justify-content: space-between; align-items: center; }
.line-name { font-weight: 600; font-size: 15px; }
.card-desc { color: #909399; font-size: 12px; margin: 8px 0; min-height: 32px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.card-meta { display: flex; flex-wrap: wrap; gap: 10px; font-size: 12px; color: #606266; margin-bottom: 10px; }
.card-actions { display: flex; justify-content: flex-end; }
</style>
