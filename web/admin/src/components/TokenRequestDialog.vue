<template>
  <el-dialog
    v-model="visible"
    title="申请 API Token"
    width="560px"
    :close-on-click-modal="false"
    align-center
    class="token-request-dialog"
  >
    <div class="dialog-body">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        size="default"
      >
        <el-form-item label="Token名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="例如：测试Token"
            maxlength="30"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="配额类型">
          <el-radio-group v-model="quotaType" @change="handleQuotaTypeChange">
            <el-radio label="limited">有限配额</el-radio>
            <el-radio label="unlimited">无限配额</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          v-if="quotaType === 'limited'"
          label="使用次数"
          prop="remainQuota"
        >
          <el-input-number
            v-model="form.remainQuota"
            :min="1"
            :max="1000000"
          />
        </el-form-item>

        <el-collapse v-model="activeCollapse">
          <el-collapse-item title="高级设置（可选）" name="advanced">
            <el-form-item label="过期时间" prop="expiredTime">
              <el-date-picker
                v-model="form.expiredTime"
                type="datetime"
                placeholder="不设置则永不过期"
                :disabled-date="disabledDate"
                value-format="x"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="分组名称">
              <el-input v-model="form.group" placeholder="用于分类管理" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        申请 Token
      </el-button>
    </template>

    <!-- 创建结果弹窗 -->
    <el-dialog
      v-model="showResult"
      title="Token 创建成功"
      width="480px"
      :close-on-click-modal="false"
      append-to-body
      align-center
    >
      <el-alert type="success" :closable="false" show-icon>
        <template #title>申请成功！请妥善保管您的 Token，创建后将无法再次查看。</template>
      </el-alert>

      <div class="result-token-box">
        <div class="result-token-value">{{ createdTokenKey }}</div>
        <el-button type="primary" size="small" @click="copyToken">复制</el-button>
      </div>

      <div v-if="createdToken" class="result-meta">
        <span>名称：{{ createdToken.name }}</span>
        <span>配额：{{ createdToken.unlimitedQuota ? '无限' : createdToken.remainQuota + ' 次' }}</span>
      </div>

      <template #footer>
        <el-button type="primary" @click="handleResultClose">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api'

const visible = defineModel('visible', { type: Boolean, default: false })

const formRef = ref(null)
const submitting = ref(false)
const activeCollapse = ref([])
const showResult = ref(false)
const createdToken = ref(null)
const createdTokenKey = ref('')
const quotaType = ref('limited')

const form = reactive({
  name: '',
  remainQuota: 1000,
  unlimitedQuota: false,
  expiredTime: null,
  allowIps: '',
  group: ''
})

const rules = {
  name: [
    { required: true, message: '请输入Token名称', trigger: 'blur' },
    { min: 1, max: 30, message: '长度在 1 到 30 个字符', trigger: 'blur' }
  ],
  remainQuota: [
    { required: true, message: '请输入使用次数', trigger: 'blur' },
    { type: 'number', min: 1, message: '使用次数必须大于0', trigger: 'blur' }
  ]
}

function handleQuotaTypeChange(value) {
  if (value === 'unlimited') {
    form.unlimitedQuota = true
    form.remainQuota = null
  } else {
    form.unlimitedQuota = false
    form.remainQuota = 1000
  }
}

function disabledDate(time) {
  return time.getTime() < Date.now() - 8.64e7
}

function copyToken() {
  navigator.clipboard.writeText(createdTokenKey.value).then(() => {
    ElMessage.success('Token 已复制到剪贴板')
  })
}

function resetForm() {
  formRef.value?.resetFields()
  form.name = ''
  form.remainQuota = 1000
  form.unlimitedQuota = false
  form.expiredTime = null
  form.allowIps = ''
  form.group = ''
  quotaType.value = 'limited'
  activeCollapse.value = []
}

function handleResultClose() {
  showResult.value = false
  visible.value = false
  resetForm()
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    const res = await request.post('/api/chat/token-apply', {
      name: form.name,
      unlimitedQuota: form.unlimitedQuota,
      remainQuota: form.remainQuota,
      expiredTime: form.expiredTime,
      group: form.group
    })

    if (res.code === 0) {
      createdToken.value = res.data
      createdTokenKey.value = res.data.tokenKey
      showResult.value = true
    } else {
      ElMessage.error(res.message || '创建Token失败')
    }
  } catch (error) {
    ElMessage.error('创建Token失败')
  } finally {
    submitting.value = false
  }
}

// 弹窗关闭时重置
watch(visible, (val) => {
  if (!val && !showResult.value) {
    resetForm()
  }
})
</script>

<style scoped>
.dialog-body {
  padding: 0 4px;
}

.result-token-box {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-token-value {
  flex: 1;
  font-family: monospace;
  font-size: 13px;
  word-break: break-all;
  line-height: 1.5;
}

.result-meta {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #888;
}
</style>
