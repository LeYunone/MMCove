<template>
  <div class="ai-role-channel-management" v-loading="loading">
    <div class="list-header">
      <div class="filters">
        <el-input
          v-model="searchText"
          placeholder="搜索分组或角色编码..."
          clearable
          style="width: 240px"
        />
      </div>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新增关联</el-button>
      </div>
    </div>

    <el-table :data="filteredBindings" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="groupName" label="分组" width="150" show-overflow-tooltip />
      <el-table-column prop="roleCode" label="角色编码" width="200" show-overflow-tooltip />
      <el-table-column label="渠道" width="200">
        <template #default="{ row }">
          {{ getChannelName(row.channelId) }}
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="100" align="center" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑关联' : '新增关联'" width="520px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-form-item label="分组">
          <el-input v-model="form.groupName" placeholder="默认: default" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-select v-model="form.roleCode" placeholder="选择角色" filterable style="width: 100%">
            <el-option v-for="role in roles" :key="role.code" :label="`${role.name} (${role.code})`" :value="role.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道" required>
          <el-select v-model="form.channelId" placeholder="选择渠道" style="width: 100%">
            <el-option v-for="ch in channels" :key="ch.id" :label="`${ch.name} (ID: ${ch.id})`" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" />
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
import {
  listAiRoles, listAiChannels,
  listAiRoleChannels, createAiRoleChannel, updateAiRoleChannel, deleteAiRoleChannel
} from '@/api/aiRole'

const loading = ref(false)
const bindings = ref([])
const roles = ref([])
const channels = ref([])
const searchText = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  groupName: 'default',
  roleCode: '',
  channelId: null,
  priority: 0
})

const filteredBindings = computed(() => {
  if (!searchText.value) return bindings.value
  const kw = searchText.value.toLowerCase()
  return bindings.value.filter(b =>
    b.groupName?.toLowerCase().includes(kw) || b.roleCode?.toLowerCase().includes(kw)
  )
})

function getChannelName(channelId) {
  const ch = channels.value.find(c => c.id === channelId)
  return ch ? ch.name : `渠道ID: ${channelId}`
}

async function loadData() {
  loading.value = true
  try {
    const [bindingsRes, rolesRes, channelsRes] = await Promise.all([
      listAiRoleChannels(),
      listAiRoles(),
      listAiChannels()
    ])
    bindings.value = bindingsRes.data || []
    roles.value = rolesRes.data || []
    channels.value = channelsRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { id: null, groupName: 'default', roleCode: '', channelId: null, priority: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.roleCode || !form.value.channelId) {
    ElMessage.warning('请选择角色编码和渠道')
    return
  }
  try {
    const data = {
      ...(isEdit.value ? { id: form.value.id } : {}),
      groupName: form.value.groupName || 'default',
      roleCode: form.value.roleCode,
      channelId: form.value.channelId,
      priority: form.value.priority
    }
    if (isEdit.value) {
      await updateAiRoleChannel(data)
      ElMessage.success('更新成功')
    } else {
      await createAiRoleChannel(data)
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
    await ElMessageBox.confirm(`确定删除该关联（${row.groupName} / ${row.roleCode}）？`, '确认', { type: 'warning' })
    await deleteAiRoleChannel(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // cancelled
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.ai-role-channel-management {
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
