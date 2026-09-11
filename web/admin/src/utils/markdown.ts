import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

marked.setOptions({
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true
})

/**
 * 预处理 LLM 输出中的 HTML 标签格式问题。
 * LLM 经常输出 <imgsrc= 或 <img src=...data-full-src=（缺少空格），
 * 在此统一修正后再交给 marked 渲染。
 */
function fixLlmHtml(text) {
  // 修正 <imgsrc= → <img src=（LLM 常见错误）
  text = text.replace(/<imgsrc(?=\s*=)/gi, '<img src')
  // 修正引号紧跟属性名的情况：src="..."data-full-src → src="..." data-full-src
  text = text.replace(/(["'])\s*(data-[\w-]+)=/g, '$1 $2=')
  return text
}

/**
 * 将 Markdown 文本渲染为 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(fixLlmHtml(text))
}

/**
 * HTML 转义
 */
export function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

/**
 * 解析 LLM 错误信息，返回友好的中文提示
 */
export function parseLlmError(raw) {
  if (!raw) return { title: '未知错误', detail: '' }
  const msg = String(raw)

  if (msg.includes('429') || msg.toLowerCase().includes('rate limit') || msg.toLowerCase().includes('rate_limit') || msg.toLowerCase().includes('quota')) {
    return { title: '请求过于频繁', detail: 'LLM 服务限速中，请等待几秒后重试' }
  }
  if (msg.includes('401') || msg.includes('Unauthorized') || msg.includes('Authentication')) {
    return { title: '认证失败', detail: '请检查 Token 是否正确' }
  }
  if (msg.includes('403') || msg.includes('Forbidden')) {
    return { title: '访问被拒绝', detail: '当前 Token 无权执行此操作' }
  }
  if (msg.includes('404') || msg.includes('Not Found')) {
    return { title: '资源不存在', detail: '请求的接口或数据不存在' }
  }
  if (msg.includes('timeout') || msg.includes('Timeout') || msg.includes('TIMED_OUT')) {
    return { title: '请求超时', detail: 'LLM 服务响应超时，请稍后重试' }
  }
  if (msg.includes('Connection refused') || msg.includes('connect_error') || msg.includes('ECONNREFUSED')) {
    return { title: '服务连接失败', detail: '无法连接到 LLM 服务，请检查服务是否正常运行' }
  }
  if (msg.includes('500') || msg.includes('Internal Server Error')) {
    return { title: '服务内部错误', detail: 'LLM 服务出现异常，请稍后重试' }
  }
  if (msg.includes('LLM') && (msg.includes('调用失败') || msg.includes('call failed'))) {
    const inner = msg.replace(/.*LLM\s*(流式)?调用失败\s*[:：]\s*/, '').trim()
    const subParsed = parseLlmError(inner)
    if (subParsed.title !== '未知错误') return subParsed
    return { title: 'LLM 调用失败', detail: inner }
  }

  return { title: '请求失败', detail: msg }
}

/**
 * 分页信息正则模式数组（优先级从高到低）
 */
const PAGINATION_PATTERNS = [
  // "共X页，当前第Y页"
  /共\s*(\d+)\s*页[，,]?\s*当前\s*第\s*(\d+)\s*页/,
  // "第Y/X页"
  /第\s*(\d+)\s*\/\s*(\d+)\s*页/,
  // "第Y页，共X页"
  /第\s*(\d+)\s*页[，,]\s*共\s*(\d+)\s*页/,
  // "当前第Y页，共X页"
  /当前\s*第\s*(\d+)\s*页[，,]\s*共\s*(\d+)\s*页/,
  // "page Y of X" (英文兼容)
  /page\s+(\d+)\s+of\s+(\d+)/i
]

/**
 * 从 AI 回复文本中解析分页信息
 * @param {string} text - AI 回复的原始文本
 * @returns {{ currentPage: number, totalPages: number, queryContext: string, cleanedText: string } | null}
 */
export function parsePagination(text) {
  if (!text) return null

  let currentPage = 0
  let totalPages = 0
  let matchedIndex = -1

  for (let i = 0; i < PAGINATION_PATTERNS.length; i++) {
    const match = text.match(PAGINATION_PATTERNS[i])
    if (match) {
      matchedIndex = i
      if (i === 0) {
        // "共X页，当前第Y页" -> [total, current]
        totalPages = parseInt(match[1], 10)
        currentPage = parseInt(match[2], 10)
      } else {
        // 其余模式 -> [current, total]
        currentPage = parseInt(match[1], 10)
        totalPages = parseInt(match[2], 10)
      }
      break
    }
  }

  if (matchedIndex === -1 || totalPages <= 1) return null

  // 提取查询上下文：取分页信息所在行之前的文本
  const lines = text.split('\n').filter(l => l.trim().length > 0)
  let queryContext = ''
  let paginationLineIndex = lines.findIndex(l =>
    PAGINATION_PATTERNS.some(p => p.test(l))
  )
  if (paginationLineIndex > 0) {
    queryContext = lines.slice(0, paginationLineIndex).join(' ').trim()
  } else {
    queryContext = lines[0]?.trim() || ''
  }

  // 清理文本中的分页描述行
  const cleanedText = text.split('\n')
    .filter(line => !PAGINATION_PATTERNS.some(p => p.test(line)))
    .join('\n')
    .trim()

  return { currentPage, totalPages, queryContext, cleanedText }
}

/**
 * 检查文本中是否包含确认请求（needsConfirmation: true）
 * @param {string} text - AI 回复的原始文本
 * @returns {{ confirmationToken: string, summary: string, cleanedText: string } | null}
 */
export function parseConfirmation(text) {
  if (!text) return null
  try {
    const jsonMatch = text.match(/\{[\s\S]*"needsConfirmation"\s*:\s*true[\s\S]*\}/)
    if (!jsonMatch) return null
    const data = JSON.parse(jsonMatch[0])
    if (!data.needsConfirmation || !data.confirmationToken) return null
    // 从显示文本中移除确认JSON块
    const cleanedText = text.replace(jsonMatch[0], '').replace(/\n{3,}/g, '\n\n').trim()
    return {
      confirmationToken: data.confirmationToken,
      summary: data.summary || '即将执行一个需要确认的操作',
      cleanedText
    }
  } catch {
    return null
  }
}
