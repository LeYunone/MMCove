<template>
  <el-dialog
    v-model="visible"
    width="380px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    align-center
    class="login-dialog"
  >
    <div class="login-container">
      <div class="login-header">
        <div class="login-logo">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="8" fill="#1a1a1a" />
            <path d="M8 16L14 22L24 10" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <h2 class="login-title">登录 MMCove</h2>
        <p class="login-desc">请输入账号和密码</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="account">
          <el-input
            v-model="form.account"
            placeholder="账号"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : '登录' }}
        </el-button>
      </el-form>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()

const visible = defineModel('visible', { type: Boolean, default: false })
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  account: '',
  password: ''
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate()

  loading.value = true
  try {
    await userStore.loginAction(form.account, form.password)
    ElMessage.success('登录成功')
    visible.value = false
    // 重置表单
    form.account = ''
    form.password = ''
  } catch (err) {
    ElMessage.error(err.message || '登录失败，请检查账号密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  padding: 8px 0 4px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  margin-bottom: 16px;
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 6px;
}

.login-desc {
  font-size: 13px;
  color: #999;
  margin: 0;
}

.login-form {
  margin-top: 4px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.login-btn {
  width: 100%;
  border-radius: 10px;
  font-size: 15px;
  height: 42px;
  margin-top: 4px;
}
</style>

<style>
.login-dialog .el-dialog__body {
  padding: 20px 32px 28px;
}

.login-dialog .el-dialog__header {
  display: none;
}
</style>
