<template>
  <div class="agent-edit" v-loading="loading">
    <div class="edit-card">
      <h3>{{ isEdit ? '编辑 Agent' : '新增 Agent' }}</h3>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        style="max-width: 700px; margin-top: 20px;"
      >
        <el-form-item label="Agent ID" prop="agentId">
          <el-input v-model="form.agentId" :disabled="isEdit" placeholder="如 general-assistant" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="Agent 显示名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="Agent 描述" />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="8" placeholder="系统提示词内容" />
        </el-form-item>
        <el-form-item label="绑定工具组">
          <el-select
            v-model="form.toolGroupName"
            filterable
            allow-create
            clearable
            placeholder="选择或输入工具组名"
            style="width: 100%"
          >
            <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
          </el-select>
          <div style="font-size: 12px; color: #999; line-height: 1.4;">路由命中该 Agent 后,只装载此组的工具(留空回退 default/全量兜底)</div>
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select
            v-model="form.knowledgeBaseIds"
            multiple
            filterable
            clearable
            placeholder="选择该 Agent 可用的知识库(可多选)"
            style="width: 100%"
          >
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <div style="font-size: 12px; color: #999; line-height: 1.4;">用户提问时,系统在选中的库内自动选最相关的检索(留空则该 Agent 不启用知识检索)</div>
        </el-form-item>
        <el-form-item label="温度" prop="temperature">
          <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="最大 Token" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="256" :max="128000" :step="256" />
        </el-form-item>
        <el-form-item label="默认 Agent">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
          <el-button @click="router.push('/admin/agents')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminAgentStore } from '@/stores/admin/agent'
import { listGroups } from '@/api/toolDefinition'
import { listKnowledgeBases, getAgentBindings, setAgentBindings } from '@/api/knowledgeBase'

const route = useRoute()
const router = useRouter()
const store = useAdminAgentStore()

const formRef = ref(null)
const loading = ref(false)

const isEdit = computed(() => route.name === 'AgentEdit')
const agentId = computed(() => route.params.id)

const form = ref({
  agentId: '',
  name: '',
  description: '',
  systemPrompt: '',
  toolGroupName: '',
  knowledgeBaseIds: [],
  temperature: 0.7,
  maxTokens: 4096,
  isDefault: false,
  status: 'ACTIVE'
})

/** 工具组选项(供「绑定工具组」下拉,来自后端分组列表) */
const groupOptions = ref([])

/** 知识库选项(供「关联知识库」多选) */
const kbOptions = ref([])

const rules = {
  agentId: [{ required: true, message: '请输入 Agent ID', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

onMounted(async () => {
  // 拉工具组选项(供绑定下拉)
  try {
    const res = await listGroups()
    groupOptions.value = (res.data || []).map(g => g.groupName)
  } catch {
    // ignore
  }
  // 拉知识库选项(供关联多选)
  try {
    const res = await listKnowledgeBases()
    kbOptions.value = res.data || []
  } catch {
    // ignore
  }
  if (isEdit.value && agentId.value) {
    loading.value = true
    const data = await store.fetchAgent(agentId.value)
    if (data) {
      form.value = { ...data, knowledgeBaseIds: [] }
    }
    // 拉该 Agent 已关联的知识库
    try {
      const res = await getAgentBindings(agentId.value)
      form.value.knowledgeBaseIds = res.data || []
    } catch {
      // ignore
    }
    loading.value = false
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    let savedAgentId = agentId.value
    if (isEdit.value) {
      const result = await store.editAgent(agentId.value, form.value)
      if (!result) return
    } else {
      const result = await store.addAgent(form.value)
      if (!result) return
      savedAgentId = form.value.agentId
    }
    // 保存知识库关联(失败不影响 Agent 主流程)
    try {
      await setAgentBindings(savedAgentId, form.value.knowledgeBaseIds || [])
    } catch {
      // ignore
    }
    router.push('/admin/agents')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.agent-edit {
  max-width: 900px;
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
