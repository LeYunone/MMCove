<template>
  <el-dialog
    v-model="visible"
    title="编辑 Token"
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
      <el-form-item label="Token信息">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Token ID">{{ token.id }}</el-descriptions-item>
          <el-descriptions-item label="Token名称">{{ token.name }}</el-descriptions-item>
          <el-descriptions-item label="所属用户">
            <span v-if="userInfo">{{ userInfo.username }} (ID: {{ token.userId }})</span>
            <span v-else class="text-muted">加载中...</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(token.createdTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="最后访问">
            {{ formatTime(token.accessedTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="分组名称">
            {{ token.groupName || '无' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-form-item>

      <el-form-item label="Token名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="请输入Token名称"
          maxlength="30"
        />
      </el-form-item>

      <el-form-item label="过期时间" prop="expiredTime">
        <el-date-picker
          v-model="form.expiredTime"
          type="datetime"
          placeholder="选择过期时间"
          :disabled-date="disabledDate"
          value-format="x"
          style="width: 100%"
        />
        <el-checkbox v-model="neverExpire" style="margin-top: 6px" @change="handleNeverExpireChange">
          永不过期
        </el-checkbox>
      </el-form-item>

      <el-divider>配额设置</el-divider>

      <el-form-item label="配额类型">
        <el-radio-group v-model="quotaType" @change="handleQuotaTypeChange">
          <el-radio label="limited">有限配额</el-radio>
          <el-radio label="unlimited">无限配额</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="quotaType === 'limited'" label="剩余配额" prop="remainQuota">
        <el-input-number
          v-model="form.remainQuota"
          :min="0"
          :max="1000000"
        />
      </el-form-item>

      <el-divider>使用限制</el-divider>

      <el-form-item label="状态">
        <div class="status-tags">
          <span
            v-for="item in statusOptions"
            :key="item.value"
            class="status-tag"
            :class="[item.cls, { active: form.status === item.value }]"
            @click="form.status = item.value"
          >
            {{ item.label }}
          </span>
        </div>
      </el-form-item>

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
          style="width: 100%"
        >
          <el-option
            v-for="model in modelOptions"
            :key="model.value"
            :label="model.label"
            :value="model.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="IP限制">
        <el-input
          v-model="form.allowIps"
          type="textarea"
          :rows="3"
          placeholder="请输入允许的IP地址，每行一个"
        />
      </el-form-item>

      <el-form-item label="分组">
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
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        保存修改
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api'
import { listAvailableGroups } from '@/api/aiRole'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  token: { type: Object, required: true }
})

const emit = defineEmits(['update:modelValue', 'updated'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref(null)
const submitting = ref(false)
const userInfo = ref(null)
const quotaType = ref('limited')
const neverExpire = ref(false)
const availableGroups = ref(['default'])

async function loadAvailableGroups() {
  try {
    const res = await listAvailableGroups()
    availableGroups.value = res.data || ['default']
  } catch {
    availableGroups.value = ['default']
  }
}

onMounted(() => {
  loadAvailableGroups()
})

const form = reactive({
  id: '',
  name: '',
  status: 1,
  expiredTime: null,
  remainQuota: 0,
  unlimitedQuota: false,
  modelLimitsEnabled: false,
  modelLimits: [],
  allowIps: '',
  group: ''
})

// 状态选项（带颜色标识）
const statusOptions = [
  { value: 0, label: '待审核', cls: 'tag-pending' },
  { value: 1, label: '启用', cls: 'tag-enabled' },
  { value: 2, label: '禁用', cls: 'tag-disabled' },
  { value: 3, label: '过期', cls: 'tag-expired' },
  { value: 4, label: '额度用尽', cls: 'tag-exhausted' },
  { value: 5, label: '已拒绝', cls: 'tag-rejected' }
]

const modelOptions = [
  { value: 'gpt-4', label: 'GPT-4' },
  { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
  { value: 'gpt-3.5-turbo', label: 'GPT-3.5 Turbo' },
  { value: 'claude-3', label: 'Claude-3' },
  { value: 'claude-3-opus', label: 'Claude-3 Opus' },
  { value: 'claude-3-sonnet', label: 'Claude-3 Sonnet' }
]

const rules = {
  name: [{ required: true, message: '请输入Token名称', trigger: 'blur' }]
}

// 后端存的是秒级时间戳，前端 el-date-picker value-format="x" 要毫秒
function toMs(ts) {
  if (ts == null || ts === -1) return null
  // 如果已经是毫秒级（大于 1e12），直接用；否则乘 1000
  return ts > 1e12 ? ts : ts * 1000
}

function toSec(ms) {
  if (ms == null) return -1
  return Math.floor(ms / 1000)
}

function formatTime(ts) {
  if (!ts || ts === -1) return '-'
  const ms = ts > 1e12 ? ts : ts * 1000
  return new Date(ms).toLocaleString('zh-CN')
}

function disabledDate(time) {
  return time.getTime() < Date.now() - 8.64e7
}

function handleNeverExpireChange(val) {
  if (val) {
    form.expiredTime = null
  }
}

function handleQuotaTypeChange(value) {
  if (value === 'unlimited') {
    form.unlimitedQuota = true
    form.remainQuota = 0
  } else {
    form.unlimitedQuota = false
  }
}

async function loadUserInfo() {
  if (!props.token?.userId) return
  try {
    const res = await request.get('/api/user/admin/search', { params: { keyword: '' } })
    if (res.code === 0 && res.data) {
      const user = res.data.find(u => u.id === props.token.userId)
      if (user) {
        userInfo.value = user
        return
      }
    }
  } catch {
    // 回退：用 userId 显示
  }
  userInfo.value = { username: `用户#${props.token.userId}` }
}

function resetForm() {
  const t = props.token
  if (!t) return
  form.id = t.id
  form.name = t.name || ''
  form.status = t.status ?? 1
  form.remainQuota = t.remainQuota ?? 0
  form.unlimitedQuota = t.unlimitedQuota ?? false
  form.modelLimitsEnabled = t.modelLimitsEnabled ?? false
  form.modelLimits = t.modelLimits ? t.modelLimits.split(',') : []
  form.allowIps = t.allowIps || ''
  form.group = t.groupName || ''

  // 过期时间处理
  if (t.expiredTime == null || t.expiredTime === -1) {
    form.expiredTime = null
    neverExpire.value = true
  } else {
    form.expiredTime = toMs(t.expiredTime)
    neverExpire.value = false
  }

  quotaType.value = form.unlimitedQuota ? 'unlimited' : 'limited'
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    const payload = {
      id: form.id,
      name: form.name,
      status: form.status,
      expiredTime: neverExpire.value ? -1 : toSec(form.expiredTime),
      unlimitedQuota: form.unlimitedQuota,
      modelLimitsEnabled: form.modelLimitsEnabled,
      modelLimits: form.modelLimits.length > 0 ? form.modelLimits.join(',') : null,
      allowIps: form.allowIps || null,
      group: form.group || null
    }

    if (!form.unlimitedQuota) {
      payload.remainQuota = form.remainQuota
    }

    const res = await request.put('/api/token/admin', payload)
    if (res.code === 0) {
      ElMessage.success('Token 更新成功')
      emit('updated')
    } else {
      ElMessage.error(res.message || '更新Token失败')
    }
  } catch (error) {
    ElMessage.error('更新Token失败')
  } finally {
    submitting.value = false
  }
}

watch(() => props.token, (newToken) => {
  if (newToken) {
    resetForm()
    loadUserInfo()
  }
}, { immediate: true })

watch(visible, (val) => {
  if (!val) {
    formRef.value?.resetFields()
    userInfo.value = null
  }
})
</script>

<style scoped>
.text-muted {
  color: #999;
}

.status-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-tag {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
  user-select: none;
}

.status-tag:hover {
  opacity: 0.85;
}

.status-tag.active {
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.1);
  font-weight: 500;
}

/* 待审核 - 蓝色 */
.tag-pending {
  background: #ecf5ff;
  color: #409eff;
  border-color: #d9ecff;
}
.tag-pending.active {
  background: #409eff;
  color: #fff;
}

/* 启用 - 绿色 */
.tag-enabled {
  background: #f0f9eb;
  color: #67c23a;
  border-color: #e1f3d8;
}
.tag-enabled.active {
  background: #67c23a;
  color: #fff;
}

/* 禁用 - 橙色 */
.tag-disabled {
  background: #fdf6ec;
  color: #e6a23c;
  border-color: #faecd8;
}
.tag-disabled.active {
  background: #e6a23c;
  color: #fff;
}

/* 过期 - 灰色 */
.tag-expired {
  background: #f4f4f5;
  color: #909399;
  border-color: #e9e9eb;
}
.tag-expired.active {
  background: #909399;
  color: #fff;
}

/* 额度用尽 - 紫色 */
.tag-exhausted {
  background: #faf0ff;
  color: #9b59b6;
  border-color: #efe0f5;
}
.tag-exhausted.active {
  background: #9b59b6;
  color: #fff;
}

/* 已拒绝 - 红色 */
.tag-rejected {
  background: #fef0f0;
  color: #f56c6c;
  border-color: #fde2e2;
}
.tag-rejected.active {
  background: #f56c6c;
  color: #fff;
}

:deep(.el-descriptions__label) {
  font-weight: normal;
  color: #666;
}

:deep(.el-descriptions__content) {
  color: #303133;
}
</style>
