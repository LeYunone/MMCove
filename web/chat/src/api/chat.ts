/**
 * 流式对话（SSE over fetch + ReadableStream）。
 * POST /v1/chat/completions（OpenAI 兼容），后端返回 OpenAI chunk + 自定义事件。
 */

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface StreamHandlers {
  onDelta?: (text: string) => void
  onThinking?: (text: string) => void
  onMeta?: (event: string, data: any) => void
  onError?: (msg: string) => void
}

export interface StreamPayload {
  messages: ChatMessage[]
  sessionId?: string
  model?: string
}

function handleEvent(raw: string, h: StreamHandlers) {
  const lines = raw.split('\n')
  let event = 'message'
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (dataLines.length === 0) return
  const dataStr = dataLines.join('\n')
  if (dataStr === '[DONE]') return

  let data: any = dataStr
  try {
    data = JSON.parse(dataStr)
  } catch {
    h.onMeta?.(event, dataStr)
    return
  }

  // 自定义事件
  if (event === 'error') {
    h.onError?.(data?.error?.message || data?.message || '流式错误')
    return
  }
  if (event === 'thinking') {
    h.onThinking?.(typeof data === 'string' ? data : data?.content || data?.thinking || '')
    return
  }
  if (event !== 'message') {
    h.onMeta?.(event, data)
    // task_progress / ui_render / image / final_answer 等仍尝试提取增量文本
  }

  // OpenAI 兼容 chunk
  const delta = data?.choices?.[0]?.delta
  if (delta) {
    if (typeof delta.content === 'string') h.onDelta?.(delta.content)
    if (typeof delta.reasoning_content === 'string') h.onThinking?.(delta.reasoning_content)
  } else if (typeof data?.content === 'string') {
    h.onDelta?.(data.content)
  }
}

export async function streamChat(payload: StreamPayload, h: StreamHandlers, signal?: AbortSignal) {
  const token = localStorage.getItem('mmcove_access_token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (token) headers.Authorization = `Bearer ${token}`

  let resp: Response
  try {
    resp = await fetch('/v1/chat/completions', {
      method: 'POST',
      headers,
      body: JSON.stringify({ ...payload, stream: true }),
      signal,
    })
  } catch (e: any) {
    if (e?.name !== 'AbortError') h.onError?.(e?.message || '请求失败')
    return
  }

  if (!resp.ok || !resp.body) {
    let msg = `HTTP ${resp.status}`
    try {
      const t = await resp.text()
      const j = JSON.parse(t)
      msg = j?.message || msg
    } catch {
      /* ignore */
    }
    h.onError?.(msg)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      if (raw.trim()) handleEvent(raw, h)
    }
  }
}
