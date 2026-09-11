<template>
  <div class="kb-page">
    <!-- 顶部 -->
    <div class="page-header">
      <div class="header-left">
        <h3>知识库管理</h3>
        <span class="header-hint">业务文档/Wiki 入库供 AI 检索;按产品线隔离,共享库可跨线读取</span>
      </div>
      <div class="header-actions">
        <el-select v-if="!fixedLineId" v-model="lineFilter" placeholder="全部产品线" clearable style="width: 180px; margin-right: 8px">
          <el-option v-for="line in productLines" :key="line.id" :label="line.name" :value="line.id" />
          <el-option label="仅共享库" value="__shared__" />
        </el-select>
        <el-button @click="loadList" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="openCreateDialog">+ 新建知识库</el-button>
      </div>
    </div>

    <!-- 知识库卡片网格 -->
    <div v-loading="loading" class="kb-wrapper">
      <el-empty v-if="!loading && filteredList.length === 0" description="暂无知识库,点击「新建知识库」创建" />
      <el-row v-else :gutter="16">
        <el-col :xs="24" :sm="12" :md="8" v-for="kb in filteredList" :key="kb.id" class="kb-col">
          <el-card shadow="hover" class="kb-card">
            <div class="card-title">
              <span class="kb-name">{{ kb.name }}</span>
              <el-tag size="small" type="info" effect="plain">{{ kb.embeddingChannel }}</el-tag>
            </div>
            <div class="card-tags">
              <el-tag v-if="lineName(kb.productLineId)" size="small" type="primary" effect="plain">
                {{ lineName(kb.productLineId) }}
              </el-tag>
              <el-tag v-if="kb.isShared" size="small" type="warning" effect="plain">共享</el-tag>
            </div>
            <div class="card-desc">{{ kb.description || '无描述' }}</div>
            <div class="card-meta">
              <span>维度:{{ kb.dimensions }}</span>
              <span v-if="kb.keywords">关键词:{{ kb.keywords }}</span>
            </div>
            <div class="card-actions">
              <el-button size="small" type="primary" @click="openDocDialog(kb)">管理文档</el-button>
              <el-button size="small" @click="openEditDialog(kb)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="handleDelete(kb)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑知识库' : '新建知识库'" width="560px">
      <el-form label-position="top" :model="form">
        <el-form-item label="所属产品线(必选)">
          <el-select v-model="form.productLineId" placeholder="选择产品线" style="width: 100%">
            <el-option v-for="line in productLines" :key="line.id" :label="line.name" :value="line.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如 设备激活手册" />
        </el-form-item>
        <el-form-item label="描述(选库依据,重要)">
          <el-input v-model="form.description" type="textarea" :rows="2"
                    placeholder="知识库内容概述,系统据此自动选库" />
        </el-form-item>
        <el-form-item label="关键词(逗号分隔)">
          <el-input v-model="form.keywords" placeholder="激活,license,productKeys" />
        </el-form-item>
        <el-form-item label="场景标签">
          <el-input v-model="form.sceneTags" placeholder="设备,物联" />
        </el-form-item>
        <el-form-item label="Embedding 渠道">
          <el-input v-model="form.embeddingChannel" :disabled="!!editingId"
                    placeholder="zhipu(创建后不可改,维度绑定)" />
        </el-form-item>
        <el-form-item label="优先级(相关度并列时的 tiebreaker)">
          <el-input-number v-model="form.priority" :min="0" />
        </el-form-item>
        <el-form-item label="共享(其他产品线可读,仅本产品线可写)">
          <el-switch v-model="form.isShared" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveForm" :loading="formSaving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文档管理弹窗 -->
    <el-dialog v-model="docVisible" :title="`文档管理:${currentKb ? currentKb.name : ''}`" width="780px" destroy-on-close>
      <div class="doc-upload">
        <el-input v-model="uploadTitle" placeholder="文档标题" style="width: 260px; margin-right: 8px" />
        <el-button type="primary" @click="handleUpload" :loading="uploading">粘贴上传并入库</el-button>
      </div>
      <el-input v-model="uploadContent" type="textarea" :rows="5"
                placeholder="粘贴文档正文(Markdown/纯文本)" style="margin: 8px 0" />

      <el-divider style="margin: 12px 0">或上传文件(md/txt/pdf/docx,单文件 ≤ 20MB)</el-divider>
      <div class="file-upload">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".md,.markdown,.txt,.pdf,.docx"
          :on-change="handleFileChange"
          :on-remove="() => (uploadFile = null)"
        >
          <el-button>选择文件</el-button>
        </el-upload>
        <el-button type="primary" :disabled="!uploadFile" :loading="fileUploading"
                   style="margin-left: 8px" @click="handleFileUpload">
          上传文件并入库
        </el-button>
      </div>

      <el-table :data="documents" stripe v-loading="docLoading" max-height="320" style="margin-top: 12px">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="mime" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.mime || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">
              {{ row.status === 1 ? '已入库' : row.status === 2 ? '失败' : '待入库' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="handleDeleteDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase,
  uploadDocument, uploadDocumentFile, listDocuments, deleteDocument
} from '@/api/knowledgeBase'
import { listProductLines } from '@/api/productLine'

const FILE_MAX_BYTES = 20 * 1024 * 1024
const FILE_ALLOWED_EXTS = ['md', 'markdown', 'txt', 'pdf', 'docx']

const list = ref([])
const loading = ref(false)
const productLines = ref([])
const props = defineProps({ fixedLineId: { type: Number, default: null } })
const lineFilter = ref(props.fixedLineId)

// 新建/编辑
const formVisible = ref(false)
const editingId = ref(null)
const form = ref(emptyForm())
const formSaving = ref(false)

// 文档管理
const docVisible = ref(false)
const currentKb = ref(null)
const documents = ref([])
const docLoading = ref(false)
const uploadTitle = ref('')
const uploadContent = ref('')
const uploading = ref(false)
const uploadRef = ref(null)
const uploadFile = ref(null)
const fileUploading = ref(false)

function emptyForm() {
  return { name: '', description: '', keywords: '', sceneTags: '', embeddingChannel: 'zhipu',
           priority: 0, productLineId: null, isShared: false }
}

// 筛选:全部 / 指定产品线 / 仅共享库
const filteredList = computed(() => {
  if (lineFilter.value === '__shared__') {
    return list.value.filter(kb => kb.isShared)
  }
  if (lineFilter.value != null) {
    return list.value.filter(kb => kb.productLineId === lineFilter.value)
  }
  return list.value
})

function lineName(lineId) {
  const line = productLines.value.find(l => l.id === lineId)
  return line ? line.name : ''
}

async function loadList() {
  loading.value = true
  try {
    const [kbRes, lineRes] = await Promise.all([listKnowledgeBases(), listProductLines()])
    list.value = kbRes.data || []
    productLines.value = lineRes.data || []
  } catch {
    // 拦截器已处理
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingId.value = null
  form.value = emptyForm()
  // 默认选中当前筛选的产品线
  if (typeof lineFilter.value === 'number') {
    form.value.productLineId = lineFilter.value
  }
  formVisible.value = true
}

function openEditDialog(kb) {
  editingId.value = kb.id
  form.value = { ...kb, isShared: !!kb.isShared }
  formVisible.value = true
}

async function saveForm() {
  if (!form.value.name || !form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (!form.value.productLineId) {
    ElMessage.warning('请选择所属产品线')
    return
  }
  formSaving.value = true
  try {
    if (editingId.value) {
      await updateKnowledgeBase(editingId.value, form.value)
    } else {
      await createKnowledgeBase(form.value)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await loadList()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    formSaving.value = false
  }
}

async function handleDelete(kb) {
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${kb.name}」?将级联清理其下全部文档、向量与 Agent 绑定,不可恢复。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await deleteKnowledgeBase(kb.id)
    ElMessage.success('已删除(向量已级联清理)')
    await loadList()
  } catch {
    // cancelled
  }
}

async function openDocDialog(kb) {
  currentKb.value = kb
  docVisible.value = true
  uploadTitle.value = ''
  uploadContent.value = ''
  uploadFile.value = null
  await loadDocuments(kb.id)
}

async function loadDocuments(kbId) {
  docLoading.value = true
  try {
    const res = await listDocuments(kbId)
    documents.value = res.data || []
  } catch {
    documents.value = []
  } finally {
    docLoading.value = false
  }
}

async function handleUpload() {
  if (!uploadTitle.value.trim() || !uploadContent.value.trim()) {
    ElMessage.warning('请输入标题和正文')
    return
  }
  uploading.value = true
  try {
    await uploadDocument(currentKb.value.id, {
      title: uploadTitle.value,
      content: uploadContent.value,
      mime: 'markdown'
    })
    ElMessage.success('上传成功,已入库')
    uploadTitle.value = ''
    uploadContent.value = ''
    await loadDocuments(currentKb.value.id)
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

// 文件选择校验(扩展名白名单 + 大小)
function handleFileChange(file) {
  const name = file.name || ''
  const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : ''
  if (!FILE_ALLOWED_EXTS.includes(ext)) {
    ElMessage.warning(`不支持的文件类型(允许 ${FILE_ALLOWED_EXTS.join('/')}): ${name}`)
    uploadFile.value = null
    uploadRef.value?.clearFiles()
    return
  }
  if (file.size > FILE_MAX_BYTES) {
    ElMessage.warning('文件超过 20MB 上限')
    uploadFile.value = null
    uploadRef.value?.clearFiles()
    return
  }
  uploadFile.value = file.raw
}

async function handleFileUpload() {
  if (!uploadFile.value || !currentKb.value) return
  fileUploading.value = true
  try {
    await uploadDocumentFile(currentKb.value.id, uploadFile.value, uploadTitle.value.trim() || null)
    ElMessage.success('文件上传成功,已入库')
    uploadFile.value = null
    uploadTitle.value = ''
    uploadRef.value?.clearFiles()
    await loadDocuments(currentKb.value.id)
  } catch {
    ElMessage.error('文件上传失败')
  } finally {
    fileUploading.value = false
  }
}

async function handleDeleteDoc(doc) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${doc.title}」?将级联清理其向量与切片。`, '确认', { type: 'warning' })
    await deleteDocument(currentKb.value.id, doc.id)
    ElMessage.success('已删除')
    await loadDocuments(currentKb.value.id)
  } catch {
    // cancelled
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.kb-page {
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
  align-items: center;
}

.kb-wrapper {
  min-height: 200px;
}

.kb-col {
  margin-bottom: 16px;
}

.kb-card {
  display: flex;
  flex-direction: column;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.kb-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}

.card-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.card-desc {
  font-size: 13px;
  color: #606266;
  margin-bottom: 10px;
  min-height: 40px;
  line-height: 1.5;
}

.card-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.doc-upload {
  display: flex;
  align-items: center;
}

.file-upload {
  display: flex;
  align-items: center;
}
</style>
