<script setup lang="ts">
import { useChatStore } from '@/stores/chat'
import { ElMessageBox } from 'element-plus'
import { Plus, Trash2 } from 'lucide-vue-next'

const chat = useChatStore()

async function create() {
  await chat.newSession()
}

async function remove(id: string, title: string) {
  try {
    await ElMessageBox.confirm(`删除会话「${title || '新对话'}」？`, '确认', { type: 'warning' })
  } catch {
    return
  }
  await chat.deleteSession(id)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand-row"><span class="brand">MMCove</span></div>
    <el-button class="new-btn" @click="create"><Plus :size="16" /> 新建对话</el-button>
    <div class="session-list">
      <div
        v-for="s in chat.sessions"
        :key="s.sessionId"
        class="session"
        :class="{ active: s.sessionId === chat.currentSessionId }"
        @click="chat.selectSession(s.sessionId)"
      >
        <span class="t">{{ s.title || '新对话' }}</span>
        <Trash2 class="del" :size="14" @click.stop="remove(s.sessionId, s.title || '新对话')" />
      </div>
      <div v-if="!chat.sessions.length && !chat.loading" class="empty-hint">暂无会话</div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
  min-height: 0;
}
.brand-row {
  padding: 6px 8px 14px;
}
.brand {
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(90deg, #6fb0ff, #b18cff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.new-btn {
  width: 100%;
  justify-content: flex-start;
  gap: 8px;
}
.session-list {
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--muted);
  transition: background 0.15s;
}
.session:hover {
  background: var(--surface-2);
  color: var(--text);
}
.session.active {
  background: var(--primary-weak);
  color: var(--text);
}
.session .t {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.del {
  opacity: 0;
  flex: 0 0 auto;
  color: var(--muted);
}
.session:hover .del {
  opacity: 1;
}
.del:hover {
  color: var(--danger);
}
.empty-hint {
  color: var(--muted);
  padding: 16px 12px;
  font-size: 13px;
}
</style>
