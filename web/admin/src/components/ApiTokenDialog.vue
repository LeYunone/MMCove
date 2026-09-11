<template>
  <el-dialog
    v-model="visible"
    title="使用 API Token"
    width="440px"
    :close-on-click-modal="false"
    align-center
  >
    <div class="dialog-body">
      <el-form ref="formRef" :model="form" :rules="rules">
        <el-form-item prop="apiToken">
          <el-input
            v-model="form.apiToken"
            placeholder="sk-xxxxxxxxxxxxxxxx"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><Key /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>

      <!-- 校验结果 -->
      <div v-if="verifyResult" class="verify-result success">
        <el-icon color="#67c23a" :size="16"><SuccessFilled /></el-icon>
        <span>{{ verifyResult.name }}</span>
        <span class="verify-quota">
          {{ verifyResult.unlimited ? '无限配额' : '剩余 ' + verifyResult.remainQuota + ' 次' }}
        </span>
      </div>

      <div class="divider">
        <span>或者</span>
      </div>

      <button class="free-mode-btn" @click="handleUseFree">
        使用免费配额（每日 20 次）
      </button>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="verifying"
        :disabled="!form.apiToken"
        @click="handleVerifyAndUse"
      >
        验证并使用
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Key, SuccessFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { verifyApiToken } from '@/api/token'

const emit = defineEmits(['confirm', 'use-free'])

const visible = defineModel('visible', { type: Boolean, default: false })

const formRef = ref(null)
const verifying = ref(false)
const verifyResult = ref(null)

const form = reactive({
  apiToken: ''
})

const rules = {
  apiToken: [
    { required: true, message: '请输入 API Token', trigger: 'blur' },
    { pattern: /^sk-/, message: 'Token 必须以 sk- 开头', trigger: 'blur' }
  ]
}

async function handleVerifyAndUse() {
  if (!formRef.value) return
  await formRef.value.validate()

  verifying.value = true
  verifyResult.value = null

  try {
    const res = await verifyApiToken(form.apiToken)
    if (res.code === 0) {
      verifyResult.value = res.data
      emit('confirm', { apiToken: form.apiToken })
      ElMessage.success('Token 验证通过，已启用')
      visible.value = false
    } else {
      ElMessage.error(res.message || 'Token 验证失败')
    }
  } catch (error) {
    ElMessage.error(error.message || 'Token 验证失败')
  } finally {
    verifying.value = false
  }
}

function handleUseFree() {
  localStorage.removeItem('api_token')
  localStorage.removeItem('api_model')
  emit('use-free')
  ElMessage.success('已切换为免费配额模式')
  visible.value = false
}

// 弹窗打开时重置
watch(visible, (val) => {
  if (val) {
    verifyResult.value = null
    form.apiToken = localStorage.getItem('api_token') || ''
  }
})
</script>

<style scoped>
.dialog-body {
  padding: 4px 0;
}

.verify-result {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 4px;
}

.verify-result.success {
  background: #f0f9eb;
  color: #67c23a;
}

.verify-quota {
  margin-left: auto;
  font-size: 12px;
  color: #888;
}

.divider {
  display: flex;
  align-items: center;
  margin: 16px 0;
  gap: 12px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #eee;
}

.divider span {
  font-size: 12px;
  color: #bbb;
}

.free-mode-btn {
  width: 100%;
  padding: 10px 0;
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  transition: all 0.15s;
}

.free-mode-btn:hover {
  background: #eee;
  border-color: #ddd;
}
</style>
