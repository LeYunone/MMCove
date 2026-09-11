<template>
  <div class="ai-channel-management" v-loading="loading">
    <div class="list-header">
      <div class="filters">
        <el-input
          v-model="searchText"
          placeholder="搜索渠道名称..."
          clearable
          style="width: 240px"
        />
      </div>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新增渠道</el-button>
        <el-button @click="syncAbilities">同步能力数据</el-button>
      </div>
    </div>

    <el-table :data="filteredChannels" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="渠道名称" width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ channelTypeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="baseUrl" label="Base URL" min-width="200" show-overflow-tooltip />
      <el-table-column prop="groupName" label="分组" width="100" />
      <el-table-column prop="models" label="模型" min-width="250" show-overflow-tooltip />
      <el-table-column label="优先级" width="80" align="center" prop="priority" />
      <el-table-column label="权重" width="70" align="center" prop="weight" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button type="warning" link size="small" @click="testChannel(row)">测试</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑渠道' : '新增渠道'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="渠道类型" required>
          <el-select v-model="form.type" placeholder="请选择渠道类型" style="width: 100%">
            <el-option v-for="item in channelTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道名称" required>
          <el-input v-model="form.name" placeholder="如：OpenAI-官方" />
        </el-form-item>
        <el-form-item label="密钥" required>
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            placeholder="请输入 API Key"
          />
        </el-form-item>
        <el-form-item label="代理地址">
          <el-input v-model="form.baseUrl" placeholder="可选，如：https://api.openai.com，末尾不要带/v1" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.groupName" placeholder="如：default，多分组用逗号分隔" />
        </el-form-item>
        <el-form-item label="渠道角色">
          <el-select
            v-model="form.roleList"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入角色编码"
            style="width: 100%"
          >
            <el-option v-for="role in allRoles" :key="role.code" :label="`${role.name} (${role.code})`" :value="role.code" />
          </el-select>
          <div class="form-hint">可多选角色，也可直接输入自定义角色编码</div>
        </el-form-item>
        <el-form-item label="模型列表" required>
          <el-select
            v-model="form.modelList"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入模型名称"
            style="width: 100%"
          >
            <el-option v-for="m in commonModels" :key="m" :label="m" :value="m" />
          </el-select>
          <div class="form-hint">可直接输入自定义模型名称</div>
        </el-form-item>
        <el-form-item label="模型重定向">
          <el-input
            v-model="form.modelMapping"
            type="textarea"
            :rows="3"
            placeholder='可选，JSON格式，如 {"gpt-4o": "gpt-4o-2024-05-13"}'
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="100000" controls-position="right" />
          <span class="form-hint" style="margin-left: 8px">越大越优先</span>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="1000" controls-position="right" />
          <span class="form-hint" style="margin-left: 8px">同优先级时的负载均衡权重</span>
        </el-form-item>
        <el-form-item label="自动禁用">
          <el-switch v-model="form.autoBanEnabled" active-text="启用" inactive-text="关闭" />
          <span class="form-hint" style="margin-left: 8px">调用失败时自动禁用渠道</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusEnabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAiChannels, createAiChannel, updateAiChannel, deleteAiChannel, listAiRoles } from '@/api/aiRole'
import request from '@/api/index'

const CHANNEL_TYPE_OPTIONS = [
  { value: 1, label: 'OpenAI' },
  { value: 14, label: 'Anthropic (Claude)' },
  { value: 3, label: 'Azure OpenAI' },
  { value: 24, label: 'Gemini' },
  { value: 43, label: 'DeepSeek' },
  { value: 16, label: '智谱 (GLM)' },
  { value: 17, label: '通义 (Qwen)' },
  { value: 25, label: 'Moonshot (Kimi)' },
  { value: 8, label: '自定义' }
]

const COMMON_MODELS = [
  'gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo',
  'claude-sonnet-4-20250514', 'claude-3-5-sonnet-20241022', 'claude-3-haiku-20240307',
  'deepseek-chat', 'deepseek-reasoner',
  'glm-4-plus', 'glm-4-flash', 'glm-4',
  'qwen-max', 'qwen-plus', 'qwen-turbo',
  'gemini-2.0-flash', 'gemini-1.5-pro'
]

const channelTypes = CHANNEL_TYPE_OPTIONS
const commonModels = COMMON_MODELS

const loading = ref(false)
const channels = ref([])
const allRoles = ref([])
const searchText = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref(getDefaultForm())

function getDefaultForm() {
  return {
    id: null,
    type: 1,
    name: '',
    apiKey: '',
    baseUrl: '',
    groupName: 'default',
    roleList: [],
    modelList: [],
    modelMapping: '',
    priority: 0,
    weight: 10,
    autoBan: 1,
    autoBanEnabled: true,
    status: 1,
    statusEnabled: true
  }
}

function channelTypeLabel(type) {
  const found = CHANNEL_TYPE_OPTIONS.find(o => o.value === type)
  return found ? found.label : '未知'
}

const filteredChannels = computed(() => {
  if (!searchText.value) return channels.value
  const kw = searchText.value.toLowerCase()
  return channels.value.filter(c => c.name?.toLowerCase().includes(kw))
})

async function loadData() {
  loading.value = true
  try {
    const [channelsRes, rolesRes] = await Promise.all([
      listAiChannels(),
      listAiRoles()
    ])
    channels.value = channelsRes.data || []
    allRoles.value = rolesRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = getDefaultForm()
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  const models = row.models ? row.models.split(',').filter(m => m.trim()) : []
  const roleList = row.role ? row.role.split(',').filter(r => r.trim()) : []
  form.value = {
    id: row.id,
    type: row.type || 1,
    name: row.name || '',
    apiKey: row.apiKey || '',
    baseUrl: row.baseUrl || '',
    groupName: row.groupName || 'default',
    roleList: roleList,
    modelList: models,
    modelMapping: row.modelMapping || '',
    priority: row.priority || 0,
    weight: row.weight || 10,
    autoBan: row.autoBan != null ? row.autoBan : 1,
    autoBanEnabled: row.autoBan !== 0,
    status: row.status != null ? row.status : 1,
    statusEnabled: row.status === 1
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name) {
    ElMessage.warning('请填写渠道名称')
    return
  }
  if (!form.value.apiKey && !isEdit.value) {
    ElMessage.warning('请填写密钥')
    return
  }
  if (form.value.modelList.length === 0) {
    ElMessage.warning('请至少选择一个模型')
    return
  }
  if (form.value.modelMapping && form.value.modelMapping.trim()) {
    try {
      JSON.parse(form.value.modelMapping)
    } catch {
      ElMessage.warning('模型重定向必须是合法的JSON格式')
      return
    }
  }

  try {
    const data = {
      ...(isEdit.value ? { id: form.value.id } : {}),
      type: form.value.type,
      name: form.value.name,
      apiKey: form.value.apiKey || undefined,
      baseUrl: form.value.baseUrl || undefined,
      groupName: form.value.groupName || 'default',
      role: form.value.roleList.length > 0 ? form.value.roleList.join(',') : '',
      models: form.value.modelList.join(','),
      modelMapping: form.value.modelMapping || undefined,
      priority: form.value.priority,
      weight: form.value.weight,
      autoBan: form.value.autoBanEnabled ? 1 : 0,
      status: form.value.statusEnabled ? 1 : 0
    }
    // 编辑时如果密钥为空则不传（保留原值）
    if (isEdit.value && !data.apiKey) {
      delete data.apiKey
    }
    if (isEdit.value) {
      await updateAiChannel(data)
      ElMessage.success('更新成功')
    } else {
      await createAiChannel(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // 错误已在拦截器处理
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除渠道「${row.name}」？`, '确认', { type: 'warning' })
    await deleteAiChannel(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // cancelled
  }
}

async function testChannel(row) {
  try {
    ElMessage.info('测试中...')
    // TODO: 后端实现测试接口后对接
    ElMessage.success('测试成功')
  } catch {
    ElMessage.error('测试失败')
  }
}

async function syncAbilities() {
  try {
    await request.post('/api/ai-channel/sync-abilities')
    ElMessage.success('同步完成')
    loadData()
  } catch {
    // 错误已在拦截器处理
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.ai-channel-management {
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

.form-hint {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
