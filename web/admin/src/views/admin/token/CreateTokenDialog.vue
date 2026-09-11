<template>
  <el-dialog
    v-model="visible"
    title="创建 Token"
    width="600px"
    :close-on-click-modal="false"
    align-center
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
    >
      <el-form-item label="选择用户" prop="userId">
        <el-select
          v-model="form.userId"
          placeholder="请选择用户"
          filterable
          remote
          :remote-method="searchUsers"
          :loading="userLoading"
        >
          <el-option
            v-for="user in userList"
            :key="user.id"
            :label="`${user.username} (ID: ${user.id})`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Token名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="请输入Token名称，方便识别"
          maxlength="30"
        />
        <div class="form-tip">用于标识Token的用途，如"测试环境"、"生产API"等</div>
      </el-form-item>

      <el-form-item label="过期时间" prop="expiredTime">
        <el-date-picker
          v-model="form.expiredTime"
          type="datetime"
          placeholder="选择过期时间"
          :disabled-date="disabledDate"
          value-format="x"
        />
        <div class="form-tip">不选择表示永不过期</div>
      </el-form-item>

      <el-divider>配额设置</el-divider>

      <el-form-item label="配额类型">
        <el-radio-group v-model="quotaType" @change="handleQuotaTypeChange">
          <el-radio label="limited">有限配额</el-radio>
          <el-radio label="unlimited">无限配额</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="quotaType === 'limited'" label="初始配额" prop="remainQuota">
        <el-input-number
          v-model="form.remainQuota"
          :min="0"
          :max="1000000"
          placeholder="请输入初始配额"
        />
        <div class="form-tip">Token可使用的总次数，0表示无限制</div>
      </el-form-item>

      <el-divider>使用限制</el-divider>

      <el-form-item label="模型限制">
        <el-switch
          v-model="form.modelLimitsEnabled"
          active-text="启用模型限制"
          inactive-text="禁用模型限制"
        />
      </el-form-item>

      <el-form-item
        v-if="form.modelLimitsEnabled"
        label="允许的模型"
        prop="modelLimits"
      >
        <el-select
          v-model="form.modelLimits"
          multiple
          placeholder="选择允许使用的模型"
          filterable
        >
          <el-option
            v-for="model in modelOptions"
            :key="model.value"
            :label="model.label"
            :value="model.value"
          />
        </el-select>
        <div class="form-tip">限制Token只能使用指定的模型</div>
      </el-form-item>

      <el-form-item label="IP限制" prop="allowIps">
        <el-input
          v-model="form.allowIps"
          type="textarea"
          :rows="3"
          placeholder="请输入允许的IP地址，每行一个"
        />
        <div class="form-tip">限制Token只能在指定IP地址下使用，留空表示不限制</div>
      </el-form-item>

      <el-form-item label="分组" prop="group">
        <el-select
          v-model="form.group"
          placeholder="选择分组"
          filterable
          allow-create
          default-first-option
          style="width: 100%"
        >
          <el-option
            v-for="g in availableGroups"
            :key="g"
            :label="g"
            :value="g"
          />
        </el-select>
        <div class="form-tip">与渠道管理、角色管理的分组保持一致</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        创建 Token
      </el-button>
    </template>

    <!-- 生成结果 -->
    <el-card
      v-if="createdToken"
      class="result-card"
      shadow="never"
    >
      <template #header>
        <div class="header">
          <span>Token 创建成功</span>
          <el-button
            type="primary"
            link
            @click="copyToken"
          >
            <el-icon><CopyDocument /></el-icon>
            复制 Token
          </el-button>
        </div>
      </template>

      <div class="token-result">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
        >
          请妥善保管此 Token，创建后将无法再次查看！
        </el-alert>

        <div class="token-display">
          <div class="token-label">Token：</div>
          <div class="token-value">{{ createdToken.tokenKey }}</div>
        </div>

        <div class="token-info">
          <div class="info-item">
            <span class="info-label">Token ID：</span>
            <span class="info-value">{{ createdToken.id }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">创建时间：</span>
            <span class="info-value">{{ formatTime(createdToken.createdTime) }}</span>
          </div>
        </div>
      </div>
    </el-card>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import request from '@/api'
import { listAvailableGroups } from '@/api/aiRole'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'created'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref(null)
const submitting = ref(false)
const userLoading = ref(false)
const userList = ref([])
const createdToken = ref(null)
const availableGroups = ref(['default'])

async function loadAvailableGroups() {
  try {
    const res = await listAvailableGroups()
    availableGroups.value = res.data || ['default']
  } catch {
    availableGroups.value = ['default']
  }
}

// 配额类型
const quotaType = ref('limited')

// 表单数据
const form = reactive({
  userId: '',
  name: '',
  expiredTime: null,
  remainQuota: 0,
  unlimitedQuota: false,
  modelLimitsEnabled: false,
  modelLimits: [],
  allowIps: '',
  group: ''
})

// 模型选项
const modelOptions = [
  { value: 'gpt-4', label: 'GPT-4' },
  { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
  { value: 'gpt-3.5-turbo', label: 'GPT-3.5 Turbo' },
  { value: 'claude-3', label: 'Claude-3' },
  { value: 'claude-3-opus', label: 'Claude-3 Opus' },
  { value: 'claude-3-sonnet', label: 'Claude-3 Sonnet' }
]

// 表单验证规则
const rules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'blur' }],
  name: [{ required: true, message: '请输入Token名称', trigger: 'blur' }],
  remainQuota: [{ required: true, message: '请输入初始配额', trigger: 'blur' }]
}

// 搜索用户
async function searchUsers(query) {
  if (!query) {
    userList.value = []
    return
  }

  userLoading.value = true
  try {
    const res = await request.get('/api/user/admin/search', { params: { keyword: query } })
    if (res.code === 0) {
      userList.value = res.data
    }
  } catch (error) {
    console.error('搜索用户失败:', error)
  } finally {
    userLoading.value = false
  }
}

// 处理配额类型变化
function handleQuotaTypeChange(value) {
  if (value === 'unlimited') {
    form.unlimitedQuota = true
    form.remainQuota = 0
  } else {
    form.unlimitedQuota = false
    form.remainQuota = 1000 // 默认配额
  }
}

// 禁用过去日期
function disabledDate(time) {
  return time.getTime() < Date.now() - 8.64e7
}

// 格式化时间
function formatTime(timestamp) {
  if (!timestamp) return '-'
  const date = new Date(timestamp * 1000)
  return date.toLocaleString('zh-CN')
}

// 复制Token
function copyToken() {
  const token = createdToken.value.tokenKey
  navigator.clipboard.writeText(token).then(() => {
    ElMessage.success('Token 已复制到剪贴板')
  })
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate()

  submitting.value = true
  try {
    // 准备请求数据
    const requestPayload = {
      userId: form.userId,
      name: form.name,
      expiredTime: form.expiredTime,
      unlimitedQuota: form.unlimitedQuota,
      modelLimitsEnabled: form.modelLimitsEnabled,
      modelLimits: form.modelLimits.length > 0 ? form.modelLimits.join(',') : null,
      allowIps: form.allowIps,
      group: form.group
    }

    // 如果不是无限配额，添加剩余配额
    if (!form.unlimitedQuota && form.remainQuota > 0) {
      requestPayload.remainQuota = form.remainQuota
    }

    const res = await request.post('/api/token/admin', requestPayload)
    if (res.code === 0) {
      createdToken.value = {
        ...res.data,
        maskedKey: formatMaskedKey(res.data.tokenKey)
      }
      ElMessage.success('Token 创建成功')
      emit('created')
    } else {
      ElMessage.error(res.message || '创建Token失败')
    }
  } catch (error) {
    console.error('创建Token失败:', error)
    ElMessage.error('创建Token失败')
  } finally {
    submitting.value = false
  }
}

// Token脱敏
function formatMaskedKey(key) {
  if (!key || key.length < 10) return '***'
  return key.substring(0, 6) + '***' + key.substring(key.length - 4)
}

// 重置表单
function resetForm() {
  formRef.value?.resetFields()
  createdToken.value = null
  Object.assign(form, {
    userId: '',
    name: '',
    expiredTime: null,
    remainQuota: 0,
    unlimitedQuota: false,
    modelLimitsEnabled: false,
    modelLimits: [],
    allowIps: '',
    group: ''
  })
  quotaType.value = 'limited'
}

onMounted(() => {
  loadAvailableGroups()
})

// 监听弹窗关闭
watch(visible, (val) => {
  if (!val) {
    resetForm()
  }
})
</script>

<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
  line-height: 1.4;
}

.result-card {
  margin-top: 20px;
}

.result-card :deep(.el-card__header) {
  padding: 12px 20px;
  border-bottom: 1px solid #eee;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.token-result {
  margin-top: 16px;
}

.token-display {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.token-label {
  font-weight: 500;
  margin-bottom: 8px;
  display: block;
}

.token-value {
  font-family: monospace;
  font-size: 14px;
  word-break: break-all;
  line-height: 1.6;
}

.token-info {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-weight: 500;
  color: #666;
}

.info-value {
  color: #303133;
}
</style>