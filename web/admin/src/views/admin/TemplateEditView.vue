<template>
  <div class="template-edit" v-loading="loading">
    <div class="edit-card">
      <h3>{{ isEdit ? '编辑模板' : '新增模板' }}</h3>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        style="max-width: 900px; margin-top: 20px;"
      >
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="form.templateCode" :disabled="isEdit" placeholder="如 greeting-template" />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="模板显示名称" />
        </el-form-item>
        <el-form-item label="作用域" prop="scope">
          <el-select v-model="form.scope" placeholder="选择作用域">
            <el-option label="SCENE（通用场景）" value="SCENE" />
            <el-option label="TOOL（工具结果）" value="TOOL" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === 'SCENE'" label="场景类型" prop="sceneType">
          <el-input v-model="form.sceneType" placeholder="如 GREETING, FAQ" />
        </el-form-item>
        <el-form-item v-if="form.scope === 'TOOL'" label="工具方法名" prop="toolName">
          <el-input v-model="form.toolName" placeholder="如 searchUser" />
        </el-form-item>
        <el-form-item label="绑定 Agent">
          <el-select v-model="form.agentId" placeholder="全局" clearable style="width: 300px">
            <el-option label="全局（不绑定）" value="" />
            <el-option
              v-for="agent in agents"
              :key="agent.agentId"
              :label="agent.name"
              :value="agent.agentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="模板内容" prop="templateContent">
          <TemplateEditor v-model="form.templateContent" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
          <el-button @click="router.push('/admin/templates')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminTemplateStore } from '@/stores/admin/template'
import { listAgents } from '@/api/agent'
import TemplateEditor from '@/components/admin/TemplateEditor.vue'

const route = useRoute()
const router = useRouter()
const store = useAdminTemplateStore()

const formRef = ref(null)
const loading = ref(false)
const agents = ref([])

const isEdit = computed(() => route.name === 'TemplateEdit')
const templateId = computed(() => route.params.id)

const form = ref({
  templateCode: '',
  templateName: '',
  scope: 'SCENE',
  sceneType: '',
  toolName: '',
  agentId: '',
  templateContent: '',
  priority: 0,
  status: 'ACTIVE'
})

const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  scope: [{ required: true, message: '请选择作用域', trigger: 'change' }],
  templateContent: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

onMounted(async () => {
  // 加载 Agent 列表供下拉选择
  try {
    const res = await listAgents()
    agents.value = res.data || []
  } catch {
    // handled
  }

  if (isEdit.value && templateId.value) {
    loading.value = true
    const data = await store.fetchTemplate(Number(templateId.value))
    if (data) {
      form.value = { ...data }
    }
    loading.value = false
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value) {
      const result = await store.editTemplate(Number(templateId.value), form.value)
      if (result) router.push('/admin/templates')
    } else {
      const result = await store.addTemplate(form.value)
      if (result) router.push('/admin/templates')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.template-edit {
  max-width: 1100px;
}

.edit-card {
  background: #fff;
  border-radius: 6px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.edit-card h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: #303133;
}
</style>
