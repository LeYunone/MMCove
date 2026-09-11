<template>
  <div class="tool-group-page">
    <!-- 顶部 -->
    <div class="page-header">
      <div class="header-left">
        <h3>工具分组管理</h3>
        <span class="header-hint">路由命中 Agent 后,只装载其绑定分组的工具(意图收敛,56→按组收敛)</span>
      </div>
      <div class="header-actions">
        <el-button @click="loadGroups" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="openCreateDialog">+ 新建分组</el-button>
      </div>
    </div>

    <!-- 分组卡片网格 -->
    <div v-loading="loading" class="groups-wrapper">
      <el-empty v-if="!loading && groups.length === 0" description="暂无分组,点击「新建分组」创建" />
      <el-row v-else :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" v-for="g in groups" :key="g.groupName" class="group-col">
          <el-card shadow="hover" class="group-card">
            <div class="card-title">
              <span class="group-name">{{ g.groupName }}</span>
            </div>

            <div class="card-stats">
              <div class="stat">
                <div class="stat-num">{{ g.toolCount }}</div>
                <div class="stat-label">工具数</div>
              </div>
              <div class="stat">
                <div class="stat-num" :class="{ danger: g.dangerousCount > 0 }">{{ g.dangerousCount }}</div>
                <div class="stat-label">危险工具</div>
              </div>
              <div class="stat">
                <div class="stat-num">{{ (g.boundAgents || []).length }}</div>
                <div class="stat-label">绑定 Agent</div>
              </div>
            </div>

            <div class="card-agents" v-if="(g.boundAgents || []).length">
              <span class="agents-label">绑定 Agent</span>
              <el-tag
                v-for="a in g.boundAgents"
                :key="a"
                size="small"
                type="info"
                effect="plain"
                class="agent-tag"
              >{{ a }}</el-tag>
            </div>
            <div class="card-agents empty-agents" v-else>
              <span class="agents-label">未绑定 Agent(此组工具暂无 Agent 路由进来)</span>
            </div>

            <div class="card-actions">
              <el-button size="small" type="primary" @click="openConfigDialog(g)">配置工具</el-button>
              <el-button size="small" type="danger" plain @click="handleDelete(g)">删除分组</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 配置工具弹窗 -->
    <el-dialog
      v-model="configVisible"
      :title="`配置分组工具:${configGroup}`"
      width="820px"
      destroy-on-close
    >
      <div v-loading="configLoading">
        <div class="config-header">
          <span class="config-summary">
            共 {{ availableTools.length }} 个可选工具,已启用 <strong>{{ enabledToolNames.length }}</strong> 个
          </span>
          <el-button size="small" @click="toggleAll">全选/取消</el-button>
        </div>
        <el-table :data="availableTools" stripe max-height="500">
          <el-table-column label="启用" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox
                :model-value="enabledToolNames.includes(row.name)"
                @change="(val) => toggleTool(row.name, val)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="name" label="工具名" width="200" show-overflow-tooltip />
          <el-table-column prop="sourceClass" label="来源类" width="170" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig" :loading="configSaving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建分组弹窗 -->
    <el-dialog v-model="createVisible" title="新建分组" width="440px">
      <el-form label-position="top">
        <el-form-item label="分组名">
          <el-input v-model="newGroupName" placeholder="如 device-control / vip / default" @keyup.enter="confirmCreate" />
        </el-form-item>
        <div class="create-hint">创建后将直接进入「配置工具」,勾选该分组启用的工具。</div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate">创建并配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGroups, getAvailableTools, setGroupTools, deleteGroup } from '@/api/toolDefinition'

const groups = ref([])
const loading = ref(false)

// ===== 配置工具弹窗 =====
const configVisible = ref(false)
const configGroup = ref('')
const availableTools = ref([])
const enabledToolNames = ref([])
const configLoading = ref(false)
const configSaving = ref(false)

// ===== 新建分组弹窗 =====
const createVisible = ref(false)
const newGroupName = ref('')

async function loadGroups() {
  loading.value = true
  try {
    const res = await listGroups()
    groups.value = res.data || []
  } catch {
    // 拦截器已处理
  } finally {
    loading.value = false
  }
}

async function openConfigDialog(group) {
  configGroup.value = group.groupName
  configVisible.value = true
  await loadAvailableTools(group.groupName)
}

async function loadAvailableTools(groupName) {
  configLoading.value = true
  try {
    const res = await getAvailableTools(groupName, { params: {} })
    availableTools.value = res.data || []
    enabledToolNames.value = availableTools.value.filter(t => t.enabled).map(t => t.name)
  } catch {
    ElMessage.error('加载工具失败')
    availableTools.value = []
    enabledToolNames.value = []
  } finally {
    configLoading.value = false
  }
}

function toggleTool(name, checked) {
  if (checked) {
    if (!enabledToolNames.value.includes(name)) enabledToolNames.value.push(name)
  } else {
    enabledToolNames.value = enabledToolNames.value.filter(n => n !== name)
  }
}

function toggleAll() {
  if (enabledToolNames.value.length === availableTools.value.length) {
    enabledToolNames.value = []
  } else {
    enabledToolNames.value = availableTools.value.map(t => t.name)
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    await setGroupTools(configGroup.value, enabledToolNames.value)
    ElMessage.success('保存成功')
    configVisible.value = false
    await loadGroups()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    configSaving.value = false
  }
}

function openCreateDialog() {
  newGroupName.value = ''
  createVisible.value = true
}

async function confirmCreate() {
  const name = newGroupName.value.trim()
  if (!name) {
    ElMessage.warning('请输入分组名')
    return
  }
  if (groups.value.some(g => g.groupName === name)) {
    ElMessage.warning('分组已存在,请直接配置')
    createVisible.value = false
    return
  }
  createVisible.value = false
  // 创建即首次配置:直接打开配置弹窗(保存时 setGroupTools 会自动创建该分组)
  configGroup.value = name
  configVisible.value = true
  await loadAvailableTools(name)
}

async function handleDelete(group) {
  const boundNote = (group.boundAgents || []).length
    ? `注意:绑定该组的 Agent(${group.boundAgents.join(', ')})的绑定不会被自动清除,需到 Agent 编辑页另行解绑。`
    : ''
  try {
    await ElMessageBox.confirm(
      `确定删除分组「${group.groupName}」?将清空该组的工具配置。${boundNote}`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteGroup(group.groupName)
    ElMessage.success('已删除')
    await loadGroups()
  } catch {
    // cancelled
  }
}

onMounted(() => {
  loadGroups()
})
</script>

<style scoped>
.tool-group-page {
  background: #fff;
  border-radius: 6px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.header-left h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: #303133;
}

.header-hint {
  font-size: 12px;
  color: #909399;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.groups-wrapper {
  min-height: 200px;
}

.group-col {
  margin-bottom: 16px;
}

.group-card {
  display: flex;
  flex-direction: column;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.group-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}

.card-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

.stat {
  flex: 1;
  text-align: center;
  background: #f7f8fa;
  border-radius: 6px;
  padding: 10px 4px;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-num.danger {
  color: #f56c6c;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.card-agents {
  margin-bottom: 14px;
  min-height: 28px;
}

.agents-label {
  font-size: 12px;
  color: #909399;
  margin-right: 6px;
}

.empty-agents .agents-label {
  color: #c0c4cc;
}

.agent-tag {
  margin: 2px 4px 2px 0;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.config-summary {
  font-size: 13px;
  color: #606266;
}

.config-summary strong {
  color: #409eff;
}

.create-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
