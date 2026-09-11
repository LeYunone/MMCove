<template>
  <div class="ai-role-management" v-loading="loading">
    <div class="list-header">
      <div class="filters">
        <el-input
          v-model="searchText"
          placeholder="搜索角色名称或编码..."
          clearable
          style="width: 240px"
          @input="onSearchInput"
        />
      </div>
      <div class="actions">
        <el-button @click="handleRefreshCache">刷新缓存</el-button>
        <el-button type="primary" @click="openCreate">新增角色</el-button>
      </div>
    </div>

    <el-table :data="filteredRoles" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" width="150" show-overflow-tooltip />
      <el-table-column prop="code" label="编码" width="180" show-overflow-tooltip />
      <el-table-column label="内容模板" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          {{ truncate(row.content, 80) }}
        </template>
      </el-table-column>
      <el-table-column prop="userContent" label="用户内容" width="150" show-overflow-tooltip />
      <el-table-column prop="model" label="默认模型" width="150" show-overflow-tooltip />
      <el-table-column prop="contentLength" label="内容长度" width="100" align="center" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="680px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" placeholder="如：翻译助手" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" placeholder="如：def_translator" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="系统 Prompt 模板">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="系统提示词模板，支持 {{key}} 占位符"
          />
        </el-form-item>
        <el-form-item label="用户内容模板">
          <el-input v-model="form.userContent" placeholder="用户内容模板（可选）" />
        </el-form-item>
        <el-form-item label="默认模型">
          <el-input v-model="form.model" placeholder="如：gpt-4o-mini（可选，用于图片分析等场景）" />
        </el-form-item>
        <el-form-item label="内容固定长度">
          <el-input-number v-model="form.contentLength" :min="0" />
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
import { listAiRoles, createAiRole, updateAiRole, deleteAiRole, refreshAiRoleCache } from '@/api/aiRole'

const loading = ref(false)
const roles = ref([])
const searchText = ref('')
let searchTimer = null

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  name: '',
  code: '',
  content: '',
  userContent: '',
  contentLength: 0
})

const filteredRoles = computed(() => {
  if (!searchText.value) return roles.value
  const kw = searchText.value.toLowerCase()
  return roles.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.code?.toLowerCase().includes(kw)
  )
})

function truncate(str, len) {
  if (!str) return '-'
  return str.length > len ? str.substring(0, len) + '...' : str
}

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {}, 300)
}

async function loadData() {
  loading.value = true
  try {
    const res = await listAiRoles()
    roles.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { id: null, name: '', code: '', content: '', userContent: '', model: '', contentLength: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name || !form.value.code) {
    ElMessage.warning('请填写角色名称和编码')
    return
  }
  try {
    if (isEdit.value) {
      await updateAiRole(form.value)
      ElMessage.success('更新成功')
    } else {
      await createAiRole(form.value)
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
    await ElMessageBox.confirm(`确定删除角色「${row.name}」？`, '确认', { type: 'warning' })
    await deleteAiRole(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // cancelled
  }
}

async function handleRefreshCache() {
  try {
    await refreshAiRoleCache()
    ElMessage.success('缓存已刷新')
  } catch (e) {
    // 错误已在拦截器处理
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.ai-role-management {
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
</style>
