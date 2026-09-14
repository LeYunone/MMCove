<template>
  <div class="pl-page">
    <!-- 顶部 -->
    <div class="page-header">
      <div class="header-left">
        <h3>产品线管理</h3>
        <span class="header-hint">知识库体系的顶层维度:各产品线下知识库默认隔离,共享库可跨线读取</span>
      </div>
      <div class="header-actions">
        <el-button @click="loadAll" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="openCreateDialog">+ 新建产品线</el-button>
      </div>
    </div>

    <!-- 产品线表格 -->
    <el-table :data="lines" stripe v-loading="loading" empty-text="暂无产品线,点击「新建产品线」创建">
      <el-table-column prop="code" label="编码" width="160">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.code }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="知识库数" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="kbCount(row.id) > 0 ? 'primary' : 'info'">{{ kbCount(row.id) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑产品线' : '新建产品线'" width="520px">
      <el-form label-position="top" :model="form">
        <el-form-item label="编码(唯一;用于集合命名与外部引用)">
          <el-input v-model="form.code" :disabled="!!editingId"
                    placeholder="如 lmm / iot / charge(字母数字下划线中划线,创建后不可改)" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如 示例产品线" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="产品线职责/范围说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveForm" :loading="formSaving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProductLines, createProductLine, updateProductLine, deleteProductLine
} from '@/api/productLine'
import { listKnowledgeBases } from '@/api/knowledgeBase'

const loading = ref(false)
const lines = ref([])
const kbs = ref([])

const formVisible = ref(false)
const editingId = ref(null)
const form = ref(emptyForm())
const formSaving = ref(false)

function emptyForm() {
  return { code: '', name: '', description: '', statusEnabled: true }
}

// 按产品线分组统计知识库数(含共享库归属统计)
const kbCountMap = computed(() => {
  const map = {}
  for (const kb of kbs.value) {
    if (kb.productLineId != null) {
      map[kb.productLineId] = (map[kb.productLineId] || 0) + 1
    }
  }
  return map
})

function kbCount(lineId) {
  return kbCountMap.value[lineId] || 0
}

async function loadAll() {
  loading.value = true
  try {
    const [lineRes, kbRes] = await Promise.all([listProductLines(), listKnowledgeBases()])
    lines.value = lineRes.data || []
    kbs.value = kbRes.data || []
  } catch {
    // 拦截器已处理
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEditDialog(row) {
  editingId.value = row.id
  form.value = {
    code: row.code,
    name: row.name,
    description: row.description || '',
    statusEnabled: row.status === 1
  }
  formVisible.value = true
}

async function saveForm() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (!editingId.value && !form.value.code.trim()) {
    ElMessage.warning('请输入编码')
    return
  }
  formSaving.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      description: form.value.description,
      status: form.value.statusEnabled ? 1 : 0
    }
    if (editingId.value) {
      await updateProductLine(editingId.value, payload)
    } else {
      await createProductLine({ ...payload, code: form.value.code.trim() })
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await loadAll()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    formSaving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除产品线「${row.name}」?仅当其下没有知识库时允许删除。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteProductLine(row.id)
    ElMessage.success('已删除')
    await loadAll()
  } catch (e) {
    // 取消或后端 400(有挂载知识库)时拦截器已提示
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.pl-page {
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
</style>
