<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import SessionSidebar from '@/components/SessionSidebar.vue'
import ChatPanel from '@/components/ChatPanel.vue'

const chat = useChatStore()
const auth = useAuthStore()
const router = useRouter()

onMounted(() => chat.loadSessions())

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="wb">
    <SessionSidebar />
    <div class="main">
      <header class="topbar">
        <span class="title">MMCove AI</span>
        <el-dropdown>
          <span class="user">{{ auth.user?.username }} <small>▾</small></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>
      <ChatPanel />
    </div>
  </div>
</template>

<style scoped>
.wb {
  height: 100%;
  display: grid;
  grid-template-columns: 280px 1fr;
}
.main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: 52px;
  flex: 0 0 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}
.title {
  font-weight: 700;
  letter-spacing: 0.5px;
}
.user {
  cursor: pointer;
  color: var(--muted);
}
@media (max-width: 760px) {
  .wb { grid-template-columns: 1fr; }
}
</style>
