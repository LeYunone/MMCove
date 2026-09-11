import { request } from './http'

export interface Session {
  sessionId: string
  title: string
  createdAt?: string
  updatedAt?: string
}

export interface SessionMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt?: string
}

export const sessionApi = {
  list: () => request.get<Session[]>('/sessions'),
  create: (title?: string) => request.post<Session>('/sessions', { title: title || '新对话' }),
  messages: (id: string) => request.get<SessionMessage[]>(`/sessions/${id}/messages`),
  remove: (id: string) => request.delete<void>(`/sessions/${id}`),
  rename: (id: string, title: string) => request.put<Session>(`/sessions/${id}/title`, { title }),
}
