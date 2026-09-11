<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ username: '', password: '' })
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)

async function submit() {
  if (!form.username || form.password.length < 6) {
    ElMessage.warning('请输入用户名和至少 6 位密码')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') await auth.login(form.username, form.password)
    else await auth.register(form.username, form.password)
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand">MMCove AI</div>
      <div class="subtitle">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</div>
      <el-input v-model="form.username" placeholder="用户名" size="large" @keyup.enter="submit" />
      <el-input
        v-model="form.password"
        type="password"
        placeholder="密码"
        size="large"
        show-password
        @keyup.enter="submit"
      />
      <el-button type="primary" size="large" :loading="loading" style="width: 100%" @click="submit">
        {{ mode === 'login' ? '登录' : '注册并登录' }}
      </el-button>
      <div class="switch" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
      </div>
      <div class="hint">默认管理员 admin / admin123</div>
    </div>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(1200px 600px at 50% -10%, #1b2540, var(--bg));
}
.login-card {
  width: 360px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 36px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}
.brand {
  font-size: 26px;
  font-weight: 700;
  text-align: center;
  background: linear-gradient(90deg, #6fb0ff, #b18cff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.subtitle {
  text-align: center;
  color: var(--muted);
  margin-bottom: 6px;
}
.switch {
  text-align: center;
  color: var(--primary);
  cursor: pointer;
  font-size: 13px;
}
.hint {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}
</style>
