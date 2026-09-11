<template>
  <el-dialog
    v-model="visible"
    title="后台管理登录"
    width="380px"
    :close-on-click-modal="false"
    align-center
    class="admin-login-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="login-form"
      @submit.prevent="handleLogin"
    >
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          placeholder="用户名"
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

    <div class="login-tip">
      <p>默认账号：admin</p>
      <p>默认密码：admin123</p>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { loginApi } from '@/api/auth'

const visible = defineModel('visible', { type: Boolean, default: false })
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate()

  loading.value = true
  try {
    // 平台用户体系登录(auth-center),拿 JWT 存 localStorage,后续  请求自动带 Bearer
    const data = await loginApi(form.username, form.password)
    localStorage.setItem('mmcove_access_token', data.accessToken)
    if (data.refreshToken) {
      localStorage.setItem('mmcove_refresh_token', data.refreshToken)
    }
    localStorage.setItem('mmcove_user', JSON.stringify({ username: form.username }))

    ElMessage.success('登录成功')
    visible.value = false
    // 重置表单
    form.username = ''
    form.password = ''

    // 触发登录成功事件
    window.dispatchEvent(new CustomEvent('admin:login-success'))
  } catch (err) {
    ElMessage.error(err.message || '登录失败，请检查账号密码')
  } finally {
    loading.value = false
  }
}

// 重置表单
watch(visible, (val) => {
  if (!val) {
    form.username = ''
    form.password = ''
  }
})
</script>

<style scoped>
.login-form {
  margin-top: 16px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.login-btn {
  width: 100%;
  border-radius: 8px;
  font-size: 15px;
  height: 42px;
  margin-top: 8px;
}

.login-tip {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eee;
  text-align: center;
  font-size: 12px;
  color: #999;
}

.login-tip p {
  margin: 2px 0;
}
</style>

<style>
.admin-login-dialog .el-dialog__body {
  padding: 20px 24px 24px;
}
</style>
