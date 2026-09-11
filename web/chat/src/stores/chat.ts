import { defineStore } from 'pinia'
import { sessionApi, type Session, type SessionMessage } from '@/api/sessions'
import { streamChat, type ChatMessage } from '@/api/chat'

interface ChatMsg {
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: string
  pending?: boolean
  error?: boolean
}

interface State {
  sessions: Session[]
  currentSessionId: string | null
  messagesBySession: Record<string, ChatMsg[]>
  loading: boolean
  streaming: boolean
}

export const useChatStore = defineStore('chat', {
  state: (): State => ({
    sessions: [],
    currentSessionId: null,
    messagesBySession: {},
    loading: false,
    streaming: false,
  }),
  getters: {
    currentMessages(s): ChatMsg[] {
      return s.currentSessionId ? s.messagesBySession[s.currentSessionId] ?? [] : []
    },
  },
  actions: {
    async loadSessions() {
      this.loading = true
      try {
        this.sessions = (await sessionApi.list()) || []
        if (this.sessions.length && !this.currentSessionId) this.selectSession(this.sessions[0].sessionId)
      } finally {
        this.loading = false
      }
    },
    async selectSession(id: string) {
      this.currentSessionId = id
      if (!this.messagesBySession[id]) {
        try {
          const msgs = (await sessionApi.messages(id)) || []
          this.messagesBySession[id] = msgs.map((m: SessionMessage) => ({ role: m.role, content: m.content }))
        } catch {
          this.messagesBySession[id] = []
        }
      }
    },
    async newSession(): Promise<string> {
      const s = await sessionApi.create('新对话')
      this.sessions.unshift(s)
      this.messagesBySession[s.sessionId] = []
      this.currentSessionId = s.sessionId
      return s.sessionId
    },
    async deleteSession(id: string) {
      await sessionApi.remove(id)
      this.sessions = this.sessions.filter((s) => s.sessionId !== id)
      delete this.messagesBySession[id]
      if (this.currentSessionId === id) this.currentSessionId = this.sessions[0]?.sessionId ?? null
    },
    pushLocal(id: string, msg: ChatMsg) {
      if (!this.messagesBySession[id]) this.messagesBySession[id] = []
      this.messagesBySession[id].push(msg)
    },
    async send(text: string) {
      const content = text.trim()
      if (!content || this.streaming) return

      let sessionId = this.currentSessionId
      if (!sessionId) sessionId = await this.newSession()

      this.pushLocal(sessionId, { role: 'user', content })
      const assistant: ChatMsg = { role: 'assistant', content: '', thinking: '', pending: true }
      this.pushLocal(sessionId, assistant)
      this.streaming = true

      // 用本地历史构造上下文（无状态友好；后端可再用 sessionId 关联）
      const history: ChatMessage[] = this.messagesBySession[sessionId]
        .filter((m) => !m.pending && m.content)
        .map((m) => ({ role: m.role, content: m.content }))

      await streamChat(
        { messages: history, sessionId },
        {
          onDelta: (t) => {
            assistant.pending = false
            assistant.content += t
          },
          onThinking: (t) => {
            assistant.thinking = (assistant.thinking || '') + t
          },
          onError: (msg) => {
            assistant.pending = false
            assistant.error = true
            assistant.content = assistant.content || `⚠️ ${msg}`
          },
        },
      )

      assistant.pending = false
      this.streaming = false

      // 用首条消息更新会话标题
      if (this.sessions.find((s) => s.sessionId === sessionId)?.title === '新对话') {
        try {
          await sessionApi.rename(sessionId!, content.slice(0, 20))
          const t = this.sessions.find((s) => s.sessionId === sessionId)
          if (t) t.title = content.slice(0, 20)
        } catch {
          /* ignore */
        }
      }
    },
  },
})
