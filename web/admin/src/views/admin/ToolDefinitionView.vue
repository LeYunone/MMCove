<template>
  <div class="tool-definition-page">
    <el-tabs v-model="activeTab">
      <!-- 工具定义 Tab -->
      <el-tab-pane label="工具定义" name="definitions">
        <div class="tool-definition-list" v-loading="store.loading">
          <div class="list-header">
            <div class="filters">
              <el-select v-model="sourceClassFilter" placeholder="来源类筛选" clearable style="width: 200px" @change="loadData">
                <el-option v-for="cls in store.sourceClasses" :key="cls" :label="cls" :value="cls" />
              </el-select>
              <el-input
                v-model="searchText"
                placeholder="搜索工具名或描述..."
                clearable
                style="width: 240px"
                @input="onSearchInput"
              />
            </div>
            <div class="actions">
              <el-button @click="handleRefreshCache">刷新缓存</el-button>
              <el-button type="primary" @click="handleSync">同步工具</el-button>
            </div>
          </div>

          <el-table :data="store.tools" stripe style="width: 100%">
            <el-table-column prop="toolName" label="工具名称" width="200" show-overflow-tooltip />
            <el-table-column prop="sourceClass" label="来源类" width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="280" show-overflow-tooltip />
            <el-table-column label="危险操作" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.dangerous ? 'danger' : 'success'" size="small">
                  {{ row.dangerous ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">
                {{ formatTime(row.updatedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
                <el-button type="warning" link size="small" @click="handleReset(row)">重置</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="store.pageNum"
              v-model:page-size="store.pageSize"
              :total="store.total"
              :page-sizes="[10, 15, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="onSizeChange"
              @current-change="onPageChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 分组工具配置 Tab -->
      <el-tab-pane label="分组工具配置" name="groupTools">
        <div class="group-tool-config">
          <div class="group-selector">
            <el-input
              v-model="selectedGroup"
              placeholder="输入分组名称（如 default、vip）"
              clearable
              style="width: 280px"
              @keyup.enter="loadGroupTools"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
            <el-select v-model="groupSourceFilter" placeholder="来源类筛选" clearable style="width: 200px" @change="loadGroupTools">
              <el-option v-for="cls in store.sourceClasses" :key="cls" :label="cls" :value="cls" />
            </el-select>
            <el-button type="primary" @click="loadGroupTools" :loading="groupToolsLoading">
              查询
            </el-button>
          </div>

          <div v-if="groupToolsLoaded" class="group-tool-content">
            <div class="group-tool-header">
              <span class="group-name-label">分组：<strong>{{ selectedGroup }}</strong>，共 {{ availableTools.length }} 个工具</span>
              <div class="group-tool-actions">
                <el-button size="small" @click="toggleAllTools">全选/取消</el-button>
                <el-button type="primary" size="small" @click="saveGroupTools" :loading="groupToolsSaving">
                  保存配置
                </el-button>
              </div>
            </div>

            <el-alert
              v-if="availableTools.length === 0"
              title="暂无可用工具，请先在「工具定义」中同步工具"
              type="warning"
              :closable="false"
              show-icon
              style="margin-bottom: 16px"
            />

            <el-table :data="availableTools" stripe style="width: 100%" max-height="500">
              <el-table-column label="启用" width="60" align="center">
                <template #default="{ row }">
                  <el-checkbox
                    :model-value="enabledToolNames.includes(row.name)"
                    @change="(val) => toggleTool(row.name, val)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="name" label="工具名称" width="200" show-overflow-tooltip />
              <el-table-column prop="sourceClass" label="来源类" width="180" show-overflow-tooltip />
              <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑工具定义" width="680px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="工具名称">
          <el-input :model-value="editForm.toolName" readonly />
        </el-form-item>
        <el-form-item label="来源类">
          <el-input :model-value="editForm.sourceClass" readonly />
        </el-form-item>
        <el-form-item label="工具描述">
          <el-input v-model="editForm.description" type="textarea" :rows="8" placeholder="输入自定义工具描述..." />
        </el-form-item>
        <el-collapse>
          <el-collapse-item title="查看原始描述">
            <div class="original-desc">{{ editForm.originalDescription }}</div>
          </el-collapse-item>
        </el-collapse>
        <el-form-item label="危险操作" style="margin-top: 12px">
          <el-switch v-model="editForm.dangerous" active-text="是" inactive-text="否" />
          <span class="switch-hint">标记后自动追加确认提示</span>
        </el-form-item>
        <!-- 参数描述编辑区 -->
        <div v-if="paramDescriptions.length > 0" class="param-desc-section">
          <div class="section-label">参数描述</div>
          <div
            v-for="(param, index) in paramDescriptions"
            :key="param.name"
            class="param-desc-row"
          >
            <el-input
              v-model="param.name"
              disabled
              class="param-name-input"
              placeholder="参数名"
            />
            <el-input
              v-model="param.description"
              class="param-desc-input"
              placeholder="参数描述"
            />
            <el-select
              v-model="param.type"
              disabled
              class="param-type-select"
              placeholder="类型"
            >
              <el-option label="string" value="string" />
              <el-option label="number" value="number" />
              <el-option label="integer" value="integer" />
              <el-option label="boolean" value="boolean" />
              <el-option label="array" value="array" />
              <el-option label="object" value="object" />
            </el-select>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAdminToolDefinitionStore } from '@/stores/admin/toolDefinition'
import { getAvailableTools, setGroupTools } from '@/api/toolDefinition'

const store = useAdminToolDefinitionStore()

const activeTab = ref('definitions')
const sourceClassFilter = ref('')
const searchText = ref('')
let searchTimer = null

const editVisible = ref(false)
const editForm = ref({
  id: null,
  toolName: '',
  sourceClass: '',
  description: '',
  originalDescription: '',
  dangerous: false,
  inputSchema: ''
})

/** 参数描述列表：[{name, description, type, required}] */
const paramDescriptions = ref([])

// ==================== 分组工具配置 ====================
const selectedGroup = ref('')
const groupSourceFilter = ref('')
const availableTools = ref([])
const enabledToolNames = ref([])
const groupToolsLoading = ref(false)
const groupToolsSaving = ref(false)
const groupToolsLoaded = ref(false)

async function loadGroupTools() {
  if (!selectedGroup.value.trim()) {
    ElMessage.warning('请输入分组名称')
    return
  }
  groupToolsLoading.value = true
  try {
    const params = {}
    if (groupSourceFilter.value) {
      params.sourceClass = groupSourceFilter.value
    }
    const res = await getAvailableTools(selectedGroup.value.trim(), { params })
    availableTools.value = res.data || []
    enabledToolNames.value = availableTools.value
      .filter(t => t.enabled)
      .map(t => t.name)
    groupToolsLoaded.value = true
  } catch (e) {
    ElMessage.error('加载分组工具失败: ' + (e.message || '未知错误'))
  } finally {
    groupToolsLoading.value = false
  }
}

function toggleAllTools() {
  if (enabledToolNames.value.length === availableTools.value.length) {
    enabledToolNames.value = []
  } else {
    enabledToolNames.value = availableTools.value.map(t => t.name)
  }
}

function toggleTool(toolName, enabled) {
  if (enabled) {
    if (!enabledToolNames.value.includes(toolName)) {
      enabledToolNames.value.push(toolName)
    }
  } else {
    enabledToolNames.value = enabledToolNames.value.filter(n => n !== toolName)
  }
}

async function saveGroupTools() {
  groupToolsSaving.value = true
  try {
    await setGroupTools(selectedGroup.value.trim(), enabledToolNames.value)
    ElMessage.success('保存成功')
    await loadGroupTools()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    groupToolsSaving.value = false
  }
}

// ==================== 工具定义 ====================

/** 从 inputSchema JSON 中解析参数描述列表 */
function parseParamDescriptions(inputSchema) {
  if (!inputSchema) return []
  try {
    const schema = JSON.parse(inputSchema)
    const props = schema.properties || {}
    const required = schema.required || []
    return Object.entries(props).map(([name, def]) => ({
      name,
      description: def.description || '',
      type: def.type || 'string',
      required: required.includes(name)
    }))
  } catch {
    return []
  }
}

/** 将参数描述列表回写到 inputSchema JSON */
function buildInputSchemaFromParams(originalSchema, params) {
  if (!originalSchema) return ''
  try {
    const schema = JSON.parse(originalSchema)
    const props = schema.properties || {}
    for (const param of params) {
      if (props[param.name]) {
        props[param.name].description = param.description
      }
    }
    schema.properties = props
    return JSON.stringify(schema)
  } catch {
    return originalSchema
  }
}

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.pageNum = 1
    loadData()
  }, 300)
}

function loadData() {
  store.fetchTools(sourceClassFilter.value, searchText.value)
}

function onSizeChange() {
  store.pageNum = 1
  loadData()
}

function onPageChange() {
  loadData()
}

function formatTime(val) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN')
}

function openEdit(row) {
  editForm.value = {
    id: row.id,
    toolName: row.toolName,
    sourceClass: row.sourceClass || '',
    description: row.description || '',
    originalDescription: row.originalDescription || '',
    dangerous: !!row.dangerous,
    inputSchema: row.inputSchema || ''
  }
  paramDescriptions.value = parseParamDescriptions(row.inputSchema)
  editVisible.value = true
}

async function handleSave() {
  // 将参数描述回写到 inputSchema
  if (paramDescriptions.value.length > 0) {
    editForm.value.inputSchema = buildInputSchemaFromParams(
      editForm.value.inputSchema,
      paramDescriptions.value
    )
  }
  const data = {
    description: editForm.value.description,
    dangerous: editForm.value.dangerous,
    status: 'ACTIVE'
  }
  if (editForm.value.inputSchema && editForm.value.inputSchema.trim()) {
    data.inputSchema = editForm.value.inputSchema.trim()
  }
  const result = await store.editTool(editForm.value.id, data)
  if (result) {
    editVisible.value = false
    loadData()
  }
}

async function handleReset(row) {
  try {
    await ElMessageBox.confirm(`确定重置「${row.toolName}」为原始描述？`, '确认', { type: 'warning' })
    const result = await store.resetTool(row.id)
    if (result) loadData()
  } catch {
    // cancelled
  }
}

async function handleSync() {
  const result = await store.syncTools()
  if (result) {
    store.fetchSourceClasses()
    loadData()
  }
}

async function handleRefreshCache() {
  await store.refreshCache()
}

onMounted(() => {
  store.fetchSourceClasses()
  loadData()
})
</script>

<style scoped>
.tool-definition-page {
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

.filters {
  display: flex;
  gap: 12px;
}

.actions {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.original-desc {
  padding: 10px;
  background: #f9fafb;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #555;
  white-space: pre-wrap;
  word-break: break-word;
}

.switch-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #999;
}

.param-desc-section {
  margin-top: 16px;
}

.section-label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 10px;
}

.param-desc-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.param-name-input {
  width: 160px;
  flex-shrink: 0;
}

.param-desc-input {
  flex: 1;
}

.param-type-select {
  width: 120px;
  flex-shrink: 0;
}

/* 分组工具配置 */
.group-tool-config {
  padding-top: 16px;
}

.group-selector {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.group-tool-content {
  border-top: 1px solid #ebeef5;
  padding-top: 16px;
}

.group-tool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.group-name-label {
  font-size: 14px;
  color: #606266;
}

.group-tool-actions {
  display: flex;
  gap: 8px;
}
</style>
