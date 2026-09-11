<template>
  <div class="pipeline-panel">
    <!-- ==================== 列表视图(产品线视角) ==================== -->
    <template v-if="!editor">
      <div class="panel-toolbar">
        <span class="tip">全局共享模板所有产品线可用(可复制为本线私有后随意改);本线私有副本只影响本产品线。「本产品线启用」的流水线才会发给 AI</span>
        <div>
          <el-button size="small" @click="openDebug">试一试(生成提示词)</el-button>
          <el-button size="small" @click="openYamlImport">YAML 导入</el-button>
          <el-button size="small" type="primary" @click="openCreate">新建流水线</el-button>
        </div>
      </div>

      <el-table :data="pipelines" v-loading="loading" border stripe>
        <el-table-column label="流水线" min-width="150">
          <template #default="{ row }">
            <div class="pl-name">{{ row.name }}</div>
            <el-tag size="small" effect="plain">{{ row.pipelineCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="适用任务" width="130">
          <template #default="{ row }">
            <el-tag :type="intentTagType(row.intentType)" size="small">{{ intentLabel(row.intentType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工序流程" min-width="280">
          <template #default="{ row }">
            <div class="stage-chips">
              <template v-for="(s, i) in row.steps" :key="i">
                <el-tag v-if="i > 0" class="arrow" size="small" type="info">→</el-tag>
                <el-tag size="small" :effect="i === 0 ? 'dark' : 'light'">{{ stageNameOf(s.stageCode) }}</el-tag>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="归属" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.productLineId" size="small" type="warning" effect="plain">本线私有</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">全局共享</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本产品线启用" width="110" align="center">
          <template #default="{ row }">
            <el-switch :model-value="isLineEnabled(row)" @change="(v) => toggleLine(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <el-button v-if="row.productLineId" link type="primary" size="small" @click="openEditor(row)">编排</el-button>
            <el-button v-else link type="warning" size="small" @click="forkToLine(row)">复制为本线私有</el-button>
            <el-button link size="small" @click="openCustomize(row)">本线定制</el-button>
            <el-button link size="small" @click="exportYaml(row)">YAML</el-button>
            <el-button v-if="row.productLineId" link type="danger" size="small" @click="removePipeline(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ==================== 编排画布 ==================== -->
    <template v-else>
      <div class="canvas-topbar">
        <el-button @click="closeEditor">← 返回</el-button>
        <div class="title-block">
          <el-input v-model="editor.name" class="title-input" placeholder="流水线名称" />
          <el-tag size="small" effect="plain">{{ editor.pipelineCode }}</el-tag>
          <el-select v-model="editor.intentType" size="small" style="width: 170px">
            <el-option v-for="t in intentTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </div>
        <div>
          <el-button @click="exportYaml(editor)">导出 YAML</el-button>
          <el-button type="primary" :loading="saving" @click="saveEditor">保存流水线</el-button>
        </div>
      </div>

      <div class="canvas-body">
        <div class="stage-library">
          <div class="lib-head">
            <span>工序模板</span>
            <el-input v-model="libSearch" size="small" placeholder="搜索工序" clearable />
          </div>
          <div class="lib-list">
            <div v-for="s in filteredStages" :key="s.stageCode" class="lib-item" draggable="true"
              @dragstart="onLibDragStart($event, s)" @click="appendStep(s)" :title="s.deliverable">
              <div class="lib-item-name">
                <span>{{ s.name }}</span>
                <el-tag v-if="s.subAgentCount > 1" type="warning" size="small">×{{ s.subAgentCount }}</el-tag>
              </div>
              <div class="lib-item-code">{{ s.stageCode }}</div>
            </div>
            <el-empty v-if="!filteredStages.length" description="没有匹配的工序" :image-size="50" />
          </div>
          <div class="lib-tip">点击加到最后,或直接拖到右侧想要的位置</div>
        </div>

        <div class="node-flow" @dragover.prevent="onFlowDragOver" @drop="onFlowDrop" @dragleave="onFlowDragLeave">
          <div v-if="!!editor.steps.length === false" class="flow-empty">
            从左侧「工序模板」点击或拖入,像搭积木一样编排 AI 的工作流程
          </div>
          <template v-for="(step, i) in editor.steps" :key="step.stageCode + i">
            <div v-if="dragOverIndex === i" class="drop-indicator" />
            <div class="node-card" draggable="true" @dragstart="onNodeDragStart($event, i)"
              :class="{ selected: selectedStepIndex === i, dragging: dragIndex === i }" @click="selectStep(i)">
              <div class="node-handle" title="上下拖动调整顺序">⠿</div>
              <div class="node-index">{{ i + 1 }}</div>
              <div class="node-main">
                <div class="node-title">
                  <span class="node-name">{{ stageNameOf(step.stageCode) }}</span>
                  <el-tag v-if="stepOverrideCount(step) > 0" type="warning" size="small">本线已定制</el-tag>
                  <el-tag v-if="effectiveSubAgents(step) > 1" type="warning" size="small" effect="plain">
                    多AI×{{ effectiveSubAgents(step) }}
                  </el-tag>
                </div>
                <div class="node-deliverable">{{ stageOf(step.stageCode)?.deliverable }}</div>
                <div class="node-gate" :title="stageOf(step.stageCode)?.gateRule">
                  过关条件: {{ stageOf(step.stageCode)?.gateRule }}
                </div>
              </div>
              <div class="node-actions" @click.stop>
                <el-button link size="small" @click="moveStep(i, -1)" :disabled="i === 0">↑</el-button>
                <el-button link size="small" @click="moveStep(i, 1)" :disabled="i === editor.steps.length - 1">↓</el-button>
                <el-button link type="danger" size="small" @click="removeStep(i)">✕</el-button>
              </div>
            </div>
          </template>
          <div v-if="dragOverIndex === editor.steps.length" class="drop-indicator" />
          <div v-if="editor.steps.length" class="flow-end">
            <el-tag type="success" effect="dark" size="small">✔ 结束(每步都过关,任务才算完成)</el-tag>
          </div>
        </div>

        <div class="node-config">
          <template v-if="selectedStep">
            <div class="cfg-head">
              <span>工序设置</span>
              <el-tag size="small" effect="plain">{{ selectedStep.stageCode }}</el-tag>
            </div>
            <div class="cfg-section">
              <div class="cfg-label">这道工序给 AI 的指令(全局定义)</div>
              <pre class="cfg-readonly">{{ stageOf(selectedStep.stageCode)?.rolePrompt || '(未定义)' }}</pre>
            </div>
            <div class="cfg-section">
              <div class="cfg-label">本流水线 · 派几个 AI 并行做</div>
              <el-input-number v-model="stepParamNum" :min="0" :max="5" size="small" />
              <div class="cfg-tip">0 = 用工序默认({{ stageOf(selectedStep.stageCode)?.subAgentCount || 1 }});大于1=多AI并行互查</div>
            </div>
            <div class="cfg-section">
              <div class="cfg-label">本流水线 · 追加要求</div>
              <el-input v-model="stepExtraPrompt" type="textarea" :rows="4"
                placeholder="只在这条流水线的这道工序生效(如:侧重并发安全视角)" />
            </div>
            <el-button size="small" @click="goStageLib(selectedStep.stageCode)">编辑这道工序的模板(全局生效)</el-button>
          </template>
          <el-empty v-else description="点击节点设置这道工序" :image-size="60" />

          <el-collapse class="baseinfo">
            <el-collapse-item title="流水线基本信息" name="base">
              <el-form label-width="90px" size="small">
                <el-form-item label="任务关键词">
                  <el-input v-model="editor.keywords" placeholder="逗号分隔,用于自动识别该走哪条流水线" />
                </el-form-item>
                <el-form-item label="说明">
                  <el-input v-model="editor.description" placeholder="给 AI 看的任务类型描述" />
                </el-form-item>
                <el-form-item label="执行约定">
                  <el-input v-model="editor.executionConvention" type="textarea" :rows="4"
                    placeholder="追加在提示词尾部的约定(留空用系统默认)" />
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </template>

    <!-- ==================== 新建/本线定制/调试/YAML ==================== -->
    <el-dialog v-model="createVisible" title="新建流水线" width="520px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="编码" required>
          <el-input v-model="createForm.pipelineCode" placeholder="如 bug-fix-strict(全局唯一,创建后不可改)" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如 严格缺陷修复流水线" />
        </el-form-item>
        <el-form-item label="适用任务" required>
          <el-select v-model="createForm.intentType" style="width: 100%">
            <el-option v-for="t in intentTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label=" ">
          <el-checkbox v-model="createForm.shared">作为全局共享模板(勾选后所有产品线可用;不勾选=只有本产品线可用)</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate">创建并编排</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="customVisible" :title="`本产品线定制:${customPipeline?.name || ''}`" width="640px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
        title="只影响本产品线;其他产品线不受影响。不定制 = 跟随全局流水线" />
      <div class="cz-stage" v-for="s in customStages" :key="s.stageCode">
        <div class="cz-name">{{ s.name }}</div>
        <el-switch v-model="s.enabled" active-text="本线启用" inactive-text="本线跳过" style="margin: 0 16px" />
        <span class="cz-ai">AI 数:</span>
        <el-input-number v-model="s.subAgentCount" :min="0" :max="5" size="small" style="width: 110px" />
        <span class="cz-ai-tip">(0=默认)</span>
      </div>
      <el-form label-width="110px" style="margin-top: 12px">
        <el-form-item label="本线背景补充">
          <el-input v-model="customExtra" type="textarea" :rows="3"
            placeholder="本产品线的技术栈/目录约定/特别注意的历史坑等,会写进给 AI 的提示词" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="customVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCustomize">保存定制</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="debugVisible" title="试一试:看看 AI 会拿到什么指令" width="860px" destroy-on-close>
      <el-form inline>
        <el-form-item label="任务">
          <el-input v-model="debugTask" style="width: 480px" placeholder="如 修复登录页弱网偶发超时的bug" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="debugging" @click="runDebug">生成提示词</el-button>
        </el-form-item>
      </el-form>
      <div v-if="debugMeta" class="debug-meta">{{ debugMeta }}</div>
      <pre v-if="debugPrompt" class="debug-prompt">{{ debugPrompt }}</pre>
    </el-dialog>

    <el-dialog v-model="yamlVisible" :title="yamlMode === 'export' ? 'YAML 导出' : 'YAML 导入'" width="720px">
      <template v-if="yamlMode === 'export'">
        <pre class="yaml-pre">{{ yamlText }}</pre>
      </template>
      <template v-else>
        <el-input v-model="yamlText" type="textarea" :rows="14" placeholder="粘贴流水线 YAML(支持 extends 继承语法)" />
        <div class="hint">导入的流水线全局可用,再回到列表用「本产品线」开关启用于本线</div>
      </template>
      <template #footer>
        <el-button v-if="yamlMode === 'export'" @click="copyYaml">复制</el-button>
        <el-button v-else type="primary" :loading="saving" @click="confirmYamlImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listPipelines, createPipeline, updatePipeline, forkPipeline, deletePipeline,
  listStages, exportPipelineYaml, importPipelineYaml, composePreview,
  listBindings, replaceBindings
} from '@/api/vibe'

const props = defineProps({ lineId: { type: Number, required: true } })
const router = useRouter()

const pipelines = ref([])
const stages = ref([])
const bindings = ref([])
const loading = ref(false)
const saving = ref(false)
const intentTypes = [
  { value: 'BUG_FIX', label: '修 Bug' },
  { value: 'FEATURE_DEV', label: '做新功能' },
  { value: 'REFACTOR', label: '重构优化' },
  { value: 'TEST_ENHANCE', label: '补测试' }
]

// ==================== 产品线绑定 ====================
function bindingOf(pipelineId) {
  return bindings.value.find(b => b.pipelineId === pipelineId)
}
function isLineEnabled(row) {
  const b = bindingOf(row.id)
  return !!(b && b.enabled === 1)
}
async function toggleLine(row, enabled) {
  const existing = bindingOf(row.id)
  const next = bindings.value.map(b => ({ ...b }))
  if (enabled && !existing) {
    next.push({ productLineId: props.lineId, pipelineId: row.id, stageOverrides: null, extraContext: null, enabled: 1 })
  } else if (!enabled && existing) {
    const target = next.find(b => b.pipelineId === row.id)
    target.enabled = 0
  }
  await replaceBindings(props.lineId, next)
  ElMessage.success(enabled ? `「${row.name}」已启用于本产品线` : `「${row.name}」已停用于本产品线`)
  await loadBindings()
}

// ==================== 本线定制 ====================
const customVisible = ref(false)
const customPipeline = ref(null)
const customStages = ref([])
const customExtra = ref('')
function openCustomize(row) {
  customPipeline.value = row
  customExtra.value = bindingOf(row.id)?.extraContext || ''
  let overrides = {}
  try { overrides = JSON.parse(bindingOf(row.id)?.stageOverrides || '{}') } catch { overrides = {} }
  customStages.value = (row.steps || []).map(s => {
    const o = overrides[s.stageCode] || {}
    return { stageCode: s.stageCode, name: stageNameOf(s.stageCode), enabled: o.enabled !== false, subAgentCount: o.subAgentCount || 0 }
  })
  customVisible.value = true
}
async function saveCustomize() {
  const overrides = {}
  let any = false
  for (const s of customStages.value) {
    const entry = {}
    if (!s.enabled) { entry.enabled = false; any = true }
    if (s.subAgentCount > 0) { entry.subAgentCount = s.subAgentCount; any = true }
    if (Object.keys(entry).length) overrides[s.stageCode] = entry
  }
  const row = customPipeline.value
  const existing = bindingOf(row.id)
  const next = bindings.value.map(b => ({ ...b }))
  const target = next.find(b => b.pipelineId === row.id) ||
    { productLineId: props.lineId, pipelineId: row.id }
  if (!next.includes(target)) next.push(target)
  target.enabled = 1
  target.stageOverrides = any ? JSON.stringify(overrides) : null
  target.extraContext = customExtra.value || null
  await replaceBindings(props.lineId, next)
  ElMessage.success('本产品线定制已保存')
  customVisible.value = false
  await loadBindings()
}

// ==================== 编排画布(全局本体) ====================
const editor = ref(null)
const libSearch = ref('')
const selectedStepIndex = ref(-1)
const dragIndex = ref(-1)
const dragOverIndex = ref(-1)
const dragStageCode = ref(null)

const stageMap = computed(() => Object.fromEntries(stages.value.map(s => [s.stageCode, s])))
const filteredStages = computed(() => {
  const kw = libSearch.value.trim().toLowerCase()
  if (!kw) return stages.value
  return stages.value.filter(s => s.name.toLowerCase().includes(kw) || s.stageCode.toLowerCase().includes(kw))
})
const selectedStep = computed(() => selectedStepIndex.value >= 0 ? editor.value?.steps[selectedStepIndex.value] : null)
const stepParamNum = computed({
  get: () => selectedStep.value?.stepParams?.subAgentCount || 0,
  set: (v) => ensureStepParams().subAgentCount = Number(v) || undefined
})
const stepExtraPrompt = computed({
  get: () => selectedStep.value?.stepParams?.extraPrompt || '',
  set: (v) => ensureStepParams().extraPrompt = v || undefined
})
function ensureStepParams() {
  const step = editor.value.steps[selectedStepIndex.value]
  if (!step.stepParams) step.stepParams = {}
  return step.stepParams
}
function stageOf(code) { return stageMap.value[code] || {} }
function stageNameOf(code) { return stageMap.value[code]?.name || code }
function effectiveSubAgents(step) { return step.stepParams?.subAgentCount || stageOf(step.stageCode).subAgentCount || 1 }
function stepOverrideCount(step) {
  const p = step.stepParams
  return p ? Object.keys(p).filter(k => p[k] !== undefined && p[k] !== '' && p[k] !== 0).length : 0
}

function openEditor(row) {
  editor.value = {
    id: row.id, pipelineCode: row.pipelineCode, name: row.name, intentType: row.intentType,
    description: row.description, keywords: row.keywords, executionConvention: row.executionConvention,
    status: row.status,
    steps: (row.steps || []).map(s => ({ stageCode: s.stageCode, stepParams: parseStepParams(s.stepParams) }))
  }
  selectedStepIndex.value = -1
}
function closeEditor() { editor.value = null; load() }
function appendStep(s) { editor.value.steps.push({ stageCode: s.stageCode, stepParams: null }) }
function removeStep(i) { editor.value.steps.splice(i, 1); if (selectedStepIndex.value === i) selectedStepIndex.value = -1 }
function moveStep(i, dir) {
  const steps = editor.value.steps
  const [item] = steps.splice(i, 1)
  steps.splice(i + dir, 0, item)
  if (selectedStepIndex.value === i) selectedStepIndex.value = i + dir
}
function selectStep(i) { selectedStepIndex.value = i }
function onLibDragStart(e, s) {
  dragIndex.value = -1
  dragStageCode.value = s.stageCode
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', 'lib:' + s.stageCode)
}
function onNodeDragStart(e, i) {
  dragStageCode.value = null
  dragIndex.value = i
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', 'node:' + i)
}
function onFlowDragOver(e) {
  const cards = [...e.currentTarget.querySelectorAll('.node-card')]
  dragOverIndex.value = cards.findIndex(c => {
    const rect = c.getBoundingClientRect()
    return e.clientY < rect.top + rect.height / 2
  })
  if (dragOverIndex.value < 0) dragOverIndex.value = editor.value.steps.length
  e.dataTransfer.dropEffect = dragStageCode.value ? 'copy' : 'move'
}
function onFlowDragLeave() { dragOverIndex.value = -1 }
function onFlowDrop() {
  const at = dragOverIndex.value < 0 ? editor.value.steps.length : dragOverIndex.value
  if (dragStageCode.value) {
    editor.value.steps.splice(at, 0, { stageCode: dragStageCode.value, stepParams: null })
  } else if (dragIndex.value >= 0) {
    const [item] = editor.value.steps.splice(dragIndex.value, 1)
    const adjusted = dragIndex.value < at ? at - 1 : at
    editor.value.steps.splice(adjusted, 0, item)
  }
  dragIndex.value = -1
  dragStageCode.value = null
  dragOverIndex.value = -1
}
function goStageLib(code) {
  router.push({ path: `/admin/vibe/${props.lineId}/stages`, query: { highlight: code } })
}
async function saveEditor() {
  if (!editor.value.steps.length) {
    ElMessage.warning('流水线至少要有一道工序')
    return
  }
  const payload = {
    pipelineCode: editor.value.pipelineCode, name: editor.value.name, intentType: editor.value.intentType,
    description: editor.value.description, keywords: editor.value.keywords,
    executionConvention: editor.value.executionConvention, status: editor.value.status,
    steps: editor.value.steps.map((s, i) => ({
      stageCode: s.stageCode, seq: (i + 1) * 10,
      stepParams: stepOverrideCount(s) > 0 ? JSON.stringify(cleanParams(s.stepParams)) : null
    }))
  }
  saving.value = true
  try {
    await updatePipeline(editor.value.id, payload)
    ElMessage.success(`流水线已保存(${payload.steps.length} 道工序)`)
  } finally { saving.value = false }
}
function cleanParams(p) {
  const out = {}
  if (p.subAgentCount) out.subAgentCount = p.subAgentCount
  if (p.extraPrompt) out.extraPrompt = p.extraPrompt
  return out
}
function parseStepParams(raw) {
  if (!raw || raw === 'null') return null
  try { return JSON.parse(raw) } catch { return null }
}

// ==================== 新建/YAML/调试 ====================
const createVisible = ref(false)
const createForm = ref({})
function openCreate() {
  createForm.value = { pipelineCode: '', name: '', intentType: 'BUG_FIX', shared: false }
  createVisible.value = true
}
async function forkToLine(row) {
  await ElMessageBox.confirm(
    `把「${row.name}」复制为本产品线私有副本?之后可以随意修改,不影响其他产品线;本产品线的 AI 将优先使用私有副本`, '复制为本线私有', { type: 'info' })
  const res = await forkPipeline(row.id, props.lineId)
  ElMessage.success('已复制为本线私有,点「编排」开始定制')
  await load()
  openEditor(res.data)
}
async function removePipeline(row) {
  await ElMessageBox.confirm(`删除本线私有流水线「${row.name}」?删后本产品线将回退使用全局共享模板`, '确认', { type: 'warning' })
  await deletePipeline(row.id)
  ElMessage.success('已删除')
  await load()
}
async function confirmCreate() {
  if (!createForm.value.pipelineCode || !createForm.value.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  const payload = { ...createForm.value, productLineId: createForm.value.shared ? null : props.lineId }
  const res = await createPipeline(payload)
  createVisible.value = false
  ElMessage.success('已创建,开始编排;编完后记得在「本产品线」列启用它')
  await load()
  openEditor(res.data)
}
function intentLabel(t) { return intentTypes.find(x => x.value === t)?.label || t }
function intentTagType(t) {
  return { BUG_FIX: 'danger', FEATURE_DEV: 'success', REFACTOR: 'warning', TEST_ENHANCE: 'primary' }[t] || 'info'
}

const yamlVisible = ref(false)
const yamlMode = ref('export')
const yamlText = ref('')
async function exportYaml(row) {
  const res = await exportPipelineYaml(row.id)
  yamlText.value = res.data
  yamlMode.value = 'export'
  yamlVisible.value = true
}
function openYamlImport() {
  yamlText.value = ''
  yamlMode.value = 'import'
  yamlVisible.value = true
}
async function confirmYamlImport() {
  saving.value = true
  try {
    await importPipelineYaml(yamlText.value)
    ElMessage.success('导入成功')
    yamlVisible.value = false
    await load()
  } finally { saving.value = false }
}
async function copyYaml() {
  await navigator.clipboard.writeText(yamlText.value)
  ElMessage.success('已复制')
}

const debugVisible = ref(false)
const debugging = ref(false)
const debugTask = ref('')
const debugPrompt = ref('')
const debugMeta = ref('')
function openDebug() {
  debugPrompt.value = ''
  debugMeta.value = ''
  debugVisible.value = true
}
async function runDebug() {
  if (!debugTask.value) {
    ElMessage.warning('请输入一个任务试试')
    return
  }
  debugging.value = true
  try {
    const res = await composePreview({ task: debugTask.value, productLineId: props.lineId })
    const d = res.data
    debugPrompt.value = d.prompt
    debugMeta.value = `判定: ${intentLabel(d.intentType)}(${d.intentSource}) | 流水线: ${d.pipelineName} | 工序: ${d.stageCount} | 知识参考: ${d.knowledgeChunks} 条${d.degraded ? ' | ⚠ 降级' : ''}`
  } finally { debugging.value = false }
}

// ==================== 加载 ====================
async function loadBindings() {
  const res = await listBindings(props.lineId)
  bindings.value = res.data || []
}
async function load() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([listPipelines(props.lineId), listStages()])
    pipelines.value = p.data || []
    stages.value = s.data || []
    await loadBindings()
  } finally { loading.value = false }
}
onMounted(load)
watch(() => props.lineId, load)
</script>

<style scoped>
.pipeline-panel { }
.panel-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; }
.tip { font-size: 12px; color: #909399; }
.pl-name { font-weight: 600; margin-bottom: 2px; }
.stage-chips { display: flex; flex-wrap: wrap; gap: 2px; align-items: center; }
.arrow { margin: 0 1px; }

.canvas-topbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; }
.title-block { display: flex; align-items: center; gap: 8px; flex: 1; }
.title-input { max-width: 280px; }
.title-input :deep(.el-input__wrapper) { font-weight: 600; }
.canvas-body { display: flex; gap: 12px; height: calc(100vh - 300px); min-height: 460px; }

.stage-library { width: 230px; flex-shrink: 0; border: 1px solid #e4e7ed; border-radius: 8px; display: flex; flex-direction: column; background: #fafafa; }
.lib-head { padding: 10px; display: flex; flex-direction: column; gap: 6px; font-weight: 600; font-size: 13px; }
.lib-list { flex: 1; overflow-y: auto; padding: 0 8px 8px; display: flex; flex-direction: column; gap: 6px; }
.lib-item { border: 1px dashed #d9d9d9; border-radius: 6px; padding: 8px 10px; cursor: grab; background: #fff; transition: all .15s; }
.lib-item:hover { border-color: #409eff; box-shadow: 0 1px 4px rgba(64, 158, 255, .15); }
.lib-item-name { display: flex; justify-content: space-between; align-items: center; font-size: 13px; font-weight: 500; }
.lib-item-code { font-size: 11px; color: #909399; margin-top: 2px; }
.lib-tip { padding: 8px 10px; font-size: 11px; color: #909399; border-top: 1px solid #eee; }

.node-flow { flex: 1; border: 1px solid #e4e7ed; border-radius: 8px; background:
  linear-gradient(90deg, rgba(0,0,0,.02) 1px, transparent 0) 0 0 / 22px 22px,
  linear-gradient(rgba(0,0,0,.02) 1px, transparent 0) 0 0 / 22px 22px, #fff;
  overflow-y: auto; padding: 20px 24px; }
.flow-empty { color: #909399; text-align: center; margin-top: 80px; }
.flow-end { text-align: center; margin-top: 12px; }
.drop-indicator { height: 0; border-top: 2px dashed #409eff; margin: 4px 0; border-radius: 2px; }

.node-card { display: flex; align-items: stretch; gap: 10px; border: 1px solid #dcdfe6; border-radius: 8px;
  background: #fff; padding: 10px 12px; margin-bottom: 4px; cursor: pointer; transition: all .15s;
  box-shadow: 0 1px 3px rgba(0,0,0,.05); position: relative; }
.node-card:hover { border-color: #409eff; }
.node-card.selected { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64, 158, 255, .2); }
.node-card.dragging { opacity: .45; }
.node-handle { cursor: grab; color: #c0c4cc; user-select: none; padding: 0 2px; font-size: 16px; }
.node-index { width: 26px; height: 26px; border-radius: 50%; background: #409eff; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; flex-shrink: 0; align-self: center; }
.node-main { flex: 1; min-width: 0; }
.node-title { display: flex; align-items: center; gap: 6px; }
.node-name { font-weight: 600; font-size: 14px; }
.node-deliverable { font-size: 12px; color: #606266; margin-top: 2px; }
.node-gate { font-size: 12px; color: #c45656; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-actions { display: flex; flex-direction: column; justify-content: center; gap: 0; }
.node-actions .el-button { margin: 0; height: 18px; }

.node-config { width: 320px; flex-shrink: 0; border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px;
  overflow-y: auto; background: #fafafa; display: flex; flex-direction: column; gap: 10px; }
.cfg-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 13px; }
.cfg-section { background: #fff; border: 1px solid #eee; border-radius: 6px; padding: 8px 10px; }
.cfg-label { font-size: 12px; color: #606266; margin-bottom: 6px; font-weight: 500; }
.cfg-readonly { font-size: 11px; white-space: pre-wrap; word-break: break-all; color: #606266;
  background: #f5f7fa; border-radius: 4px; padding: 8px; max-height: 160px; overflow-y: auto; margin: 0; line-height: 1.5; }
.cfg-tip { font-size: 11px; color: #909399; margin-top: 4px; }
.baseinfo { margin-top: auto; }

.cz-stage { display: flex; align-items: center; padding: 8px 0; border-bottom: 1px dashed #eee; }
.cz-name { width: 130px; font-size: 13px; font-weight: 500; }
.cz-ai { font-size: 12px; color: #606266; margin-left: auto; }
.cz-ai-tip { font-size: 11px; color: #909399; }

.debug-meta { background: #f0f9eb; color: #67c23a; padding: 8px 12px; border-radius: 4px; font-size: 12px; margin-bottom: 8px; }
.debug-prompt { background: #1e1e1e; color: #d4d4d4; padding: 14px; border-radius: 6px; font-size: 12px;
  line-height: 1.6; max-height: 460px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; margin: 0; }
.yaml-pre { background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 12px; max-height: 460px; overflow-y: auto; margin: 0; }
.hint { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
