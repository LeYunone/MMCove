<template>
  <div class="stage-panel">
    <div class="panel-toolbar">
      <span class="tip">每道工序 = AI 工作流程里的一步(比如「复现问题」「写修复代码」「多AI互查」)。定义一次,所有产品线的流水线都能复用;已被流水线使用的工序不能删</span>
      <el-button size="small" type="primary" @click="openCreate">新建工序</el-button>
    </div>

    <el-table :data="stages" v-loading="loading" border stripe :row-class-name="rowClassName">
      <el-table-column label="工序" width="150">
        <template #default="{ row }">
          <div class="st-name">{{ row.name }}</div>
          <el-tag size="small" effect="plain">{{ row.stageCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="说明" prop="description" min-width="150" show-overflow-tooltip />
      <el-table-column label="AI 数" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.subAgentCount > 1" type="warning" size="small">×{{ row.subAgentCount }} 多AI互查</el-tag>
          <span v-else>1</span>
        </template>
      </el-table-column>
      <el-table-column label="这一步要交出什么" prop="deliverable" min-width="180" show-overflow-tooltip />
      <el-table-column label="过关条件" min-width="180" show-overflow-tooltip>
        <template #default="{ row }"><span class="gate">{{ row.gateRule }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑工序' : '新建工序'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="工序编码" required>
              <el-input v-model="form.stageCode" :disabled="!!form.id" placeholder="如 security-audit(创建后不可改)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" required>
              <el-input v-model="form.name" placeholder="如 安全审计" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="给 AI 的指令">
          <el-input v-model="form.rolePrompt" type="textarea" :rows="8" placeholder="这道工序让 AI 扮演什么角色、具体做什么。可用占位符:{{task}} 任务原文 / {{knowledge}} 知识参考全文(慎用,建议写'见上文知识参考段') / {{product_line_name}} 产品线 / {{prev_outputs}} 上一步交出的东西" />
          <div class="hint">占位符在生成提示词时自动替换</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="AI 数">
              <el-input-number v-model="form.subAgentCount" :min="1" :max="5" />
              <div class="hint">大于 1 = 派多个 AI 并行做再互相对答案</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="要交出什么">
          <el-input v-model="form.deliverable" placeholder="这一步做完要拿出什么东西(如:问题清单,按严重程度分级)" />
        </el-form-item>
        <el-form-item label="过关条件">
          <el-input v-model="form.gateRule" placeholder="做到什么程度才能进入下一步(由 AI 自查)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listStages, createStage, updateStage, deleteStage } from '@/api/vibe'

defineProps({ lineId: { type: Number, required: true } })
const route = useRoute()
const stages = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({})

function rowClassName({ row }) {
  return route.query.highlight && row.stageCode === route.query.highlight ? 'highlight-row' : ''
}
function emptyForm() {
  return { id: null, stageCode: '', name: '', description: '', rolePrompt: '', subAgentCount: 1, deliverable: '', gateRule: '', status: 1 }
}
async function load() {
  loading.value = true
  try {
    const res = await listStages()
    stages.value = res.data || []
  } finally { loading.value = false }
}
function openCreate() { form.value = emptyForm(); dialogVisible.value = true }
function openEdit(row) { form.value = { ...row }; dialogVisible.value = true }
async function save() {
  if (!form.value.stageCode || !form.value.name) {
    ElMessage.warning('工序编码与名称必填')
    return
  }
  saving.value = true
  try {
    if (form.value.id) await updateStage(form.value.id, form.value)
    else await createStage(form.value)
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}
async function remove(row) {
  await ElMessageBox.confirm(`删除工序「${row.name}」?已被流水线使用的工序会被拒绝`, '确认', { type: 'warning' })
  await deleteStage(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.panel-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; }
.tip { font-size: 12px; color: #909399; }
.st-name { font-weight: 600; margin-bottom: 2px; }
.gate { color: #c45656; font-size: 12px; }
.hint { font-size: 12px; color: #909399; line-height: 1.4; margin-top: 4px; }
:deep(.highlight-row) { background: #fdf6ec !important; }
:deep(.highlight-row td) { background: #fdf6ec !important; }
</style>
