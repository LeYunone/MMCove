<template>
  <div class="message-row" :class="[msg.role]">
    <!-- 用户消息 -->
    <div v-if="msg.role === 'user'" class="user-bubble">
      <span>{{ msg.content }}</span>
    </div>

    <!-- 助手消息 -->
    <div v-else class="assistant-block" :class="{ 'is-error': msg.isError }">
      <!-- 助手头像 -->
      <div class="assistant-avatar">
        <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
          <rect width="22" height="22" rx="6" fill="#e8e8ec"/>
          <circle cx="11" cy="8" r="3" fill="#bbb"/>
          <path d="M5 18c0-3.3 2.7-6 6-6s6 2.7 6 6" fill="#bbb"/>
        </svg>
      </div>

      <div class="assistant-content">
        <!-- 正常内容 -->
        <div v-if="!msg.isError" class="content-text">
          <!-- 思考阶段：显示思考动画 -->
          <div v-if="msg.isStreaming && !msg.content && !hasThinkingChain && !hasTaskPlan" class="thinking-indicator">
            <div class="thinking-text">{{ phaseLabel }}</div>
            <div class="thinking-dots">
              <span></span><span></span><span></span>
            </div>
          </div>

          <!-- 可折叠的思维链区域（单任务模式） -->
          <details v-if="hasThinkingChain" class="chain-collapse" :open="msg.isStreaming">
            <summary class="chain-summary">
              <svg class="summary-arrow" width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M4 2l4 4-4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span class="summary-label">思考过程</span>
              <span v-if="!msg.isStreaming" class="summary-hint">（点击展开）</span>
              <span v-if="msg.isStreaming" class="summary-status running">运行中...</span>
            </summary>
            <ThinkingChain :chain="msg.thinkingChain" />
          </details>

          <!-- 可折叠的子任务区域（多任务模式） -->
          <details v-if="hasTaskPlan" class="chain-collapse" :open="msg.isStreaming">
            <summary class="chain-summary">
              <svg class="summary-arrow" width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M4 2l4 4-4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span class="summary-label">任务规划</span>
              <span class="summary-count">{{ msg.taskPlan.tasks.length }}个子任务</span>
              <span v-if="!msg.isStreaming" class="summary-hint">（点击展开）</span>
              <span v-if="msg.isStreaming" class="summary-status running">执行中...</span>
            </summary>
            <SubTaskList :tasks="msg.taskPlan.tasks" />
          </details>

          <!-- 流式内容：实时渲染 + 闪烁光标 -->
          <div v-if="msg.isStreaming && msg.content" class="streaming-content">
            <div class="markdown-body" v-html="streamingHtml"></div>
            <span class="typing-cursor"></span>
          </div>
          <!-- 完成内容：完整渲染 -->
          <div v-else-if="!msg.isStreaming && msg.content" ref="contentRef" class="markdown-body" v-html="renderedContent"></div>
        </div>

        <!-- 错误内容 -->
        <div v-else class="error-content">
          <div class="error-title">{{ parsedError.title }}</div>
          <div class="error-detail">{{ parsedError.detail }}</div>
        </div>

        <!-- 行内错误 -->
        <div v-if="msg.inlineError" class="inline-error">
          {{ parsedInlineError.title }}{{ parsedInlineError.detail ? ' - ' + parsedInlineError.detail : '' }}
        </div>

        <!-- 确认卡片 -->
        <ConfirmCard
          v-if="confirmationData && !msg.isStreaming && !msg.isError"
          :summary="confirmationData.summary"
          @confirm="$emit('confirm', confirmationData.confirmationToken)"
          @cancel="$emit('cancel')"
        />

        <!-- 分页控件 -->
        <TablePagination
          v-if="paginationData"
          :current-page="paginationData.currentPage"
          :total-pages="paginationData.totalPages"
          :query-context="paginationData.queryContext"
          @page-change="$emit('page-change', $event)"
        />
      </div>
    </div>

    <!-- 图片放大遮罩层 -->
    <Teleport to="body">
      <div v-if="lightboxUrl" class="img-lightbox" @click="closeLightbox">
        <div class="lightbox-toolbar" @click.stop>
          <span class="toolbar-label">{{ Math.round(lbScale * 100) }}%</span>
          <button class="toolbar-btn" @click="lbScale = Math.max(0.1, lbScale - 0.2)">-</button>
          <button class="toolbar-btn" @click="lbScale = 1; lbX = 0; lbY = 0">1:1</button>
          <button class="toolbar-btn" @click="lbScale = Math.min(10, lbScale + 0.2)">+</button>
        </div>
        <div
          class="lightbox-viewport"
          @click.stop
          @wheel.prevent="onWheel"
          @mousedown.prevent="onDragStart"
          @mousemove.prevent="onDragMove"
          @mouseup="onDragEnd"
          @mouseleave="onDragEnd"
        >
          <img
            :src="lightboxUrl"
            class="lightbox-img"
            :style="{ transform: `translate(${lbX}px, ${lbY}px) scale(${lbScale})` }"
            @load="onImgLoad"
            draggable="false"
          />
        </div>
        <div class="lightbox-hint">滚轮缩放 · 拖拽平移 · ESC 关闭</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { renderMarkdown, parseLlmError, parseConfirmation, parsePagination } from '@/utils/markdown'
import ConfirmCard from './ConfirmCard.vue'
import TablePagination from './TablePagination.vue'
import ThinkingChain from './ThinkingChain.vue'
import SubTaskList from './SubTaskList.vue'

const props = defineProps({
  msg: {
    type: Object,
    required: true
  }
})

defineEmits(['confirm', 'cancel', 'page-change'])

const contentRef = ref(null)
const lightboxUrl = ref('')

// lightbox 缩放 & 平移状态
const lbScale = ref(1)
const lbX = ref(0)
const lbY = ref(0)
const lbDragging = ref(false)
const lbStartX = ref(0)
const lbStartY = ref(0)
const lbStartOffsetX = ref(0)
const lbStartOffsetY = ref(0)

/** 事件委托：点击 markdown-body 内的 img 打开大图 */
function onContentClick(e) {
  const img = e.target
  if (img.tagName === 'IMG' && img.src) {
    lbScale.value = 1
    lbX.value = 0
    lbY.value = 0
    lightboxUrl.value = img.getAttribute('data-full-src') || img.src
  }
}

function closeLightbox() {
  lightboxUrl.value = ''
  lbScale.value = 1
  lbX.value = 0
  lbY.value = 0
}

/** 图片加载完成，自适应初始缩放 */
function onImgLoad(e) {
  const img = e.target
  const vw = window.innerWidth * 0.9
  const vh = window.innerHeight * 0.85
  const fitScale = Math.min(vw / img.naturalWidth, vh / img.naturalHeight, 1)
  lbScale.value = Math.round(fitScale * 100) / 100
}

/** 滚轮缩放 */
function onWheel(e) {
  const delta = e.deltaY > 0 ? -0.15 : 0.15
  const next = Math.round(Math.min(10, Math.max(0.1, lbScale.value + delta)) * 100) / 100
  // 以鼠标位置为中心缩放
  const rect = e.currentTarget.getBoundingClientRect()
  const cx = e.clientX - rect.left - rect.width / 2 - lbX.value
  const cy = e.clientY - rect.top - rect.height / 2 - lbY.value
  const ratio = next / lbScale.value
  lbX.value -= cx * (ratio - 1)
  lbY.value -= cy * (ratio - 1)
  lbScale.value = next
}

/** 拖拽平移 */
function onDragStart(e) {
  lbDragging.value = true
  lbStartX.value = e.clientX
  lbStartY.value = e.clientY
  lbStartOffsetX.value = lbX.value
  lbStartOffsetY.value = lbY.value
}

function onDragMove(e) {
  if (!lbDragging.value) return
  lbX.value = lbStartOffsetX.value + (e.clientX - lbStartX.value)
  lbY.value = lbStartOffsetY.value + (e.clientY - lbStartY.value)
}

function onDragEnd() {
  lbDragging.value = false
}

function onKeydown(e) {
  if (e.key === 'Escape' && lightboxUrl.value) {
    closeLightbox()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})

/** 消息内容变化或流式结束后绑定点击事件 */
watch([() => props.msg.content, () => props.msg.isStreaming], () => {
  nextTick(() => {
    if (contentRef.value) {
      contentRef.value.removeEventListener('click', onContentClick)
      contentRef.value.addEventListener('click', onContentClick)
    }
  })
}, { immediate: true })

const hasThinkingChain = computed(() => {
  return (!props.msg.taskPlan || !props.msg.taskPlan.tasks || props.msg.taskPlan.tasks.length === 0)
      && props.msg.thinkingChain && props.msg.thinkingChain.length > 0
})

const hasTaskPlan = computed(() => {
  return props.msg.taskPlan && props.msg.taskPlan.tasks && props.msg.taskPlan.tasks.length > 0
})

/** 流式输出时实时渲染 markdown */
const streamingHtml = computed(() => {
  if (!props.msg.content) return ''
  return renderMarkdown(props.msg.content)
})

/** 完成后渲染最终 markdown（去除确认JSON和分页描述） */
const renderedContent = computed(() => {
  let text = props.msg.content
  // 第一步：去除确认JSON块
  const parsed = parseConfirmation(text)
  if (parsed) {
    text = parsed.cleanedText
  }
  // 第二步：从已清理的文本中去除分页描述
  text = parsePagination(text)?.cleanedText || text
  return renderMarkdown(text)
})

const parsedError = computed(() => {
  return parseLlmError(props.msg.errorMsg)
})

const parsedInlineError = computed(() => {
  return parseLlmError(props.msg.inlineError)
})

const confirmationData = computed(() => {
  if (props.msg.role !== 'assistant' || props.msg.isError) return null
  // 优先使用 SSE 推送的确认数据（后端 confirmation_required 事件）
  if (props.msg.confirmationData && props.msg.confirmationData.confirmationToken) {
    return props.msg.confirmationData
  }
  // 回退：从 AI 文本中解析 JSON 格式的确认信息
  return parseConfirmation(props.msg.content)
})

const paginationData = computed(() => {
  if (props.msg.role !== 'assistant' || props.msg.isStreaming || props.msg.isError) return null
  return parsePagination(props.msg.content)
})

/** 阶段标签 */
const phaseLabel = computed(() => {
  switch (props.msg.phase) {
    case 'planning': return '正在规划任务'
    case 'executing': return '正在执行任务'
    case 'answering': return '正在生成回答'
    default: return '思考中'
  }
})
</script>

<style scoped>
.message-row {
  margin-bottom: 24px;
  display: flex;
}

/* 用户消息 — 靠右 */
.message-row.user {
  justify-content: flex-end;
}

.user-bubble {
  max-width: 65%;
  padding: 10px 16px;
  border-radius: 18px 18px 4px 18px;
  background: #f0f0f3;
  color: #1a1a1a;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

/* 助手消息 — 靠左 */
.assistant-block {
  display: flex;
  gap: 10px;
  max-width: 85%;
}

.assistant-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.assistant-content {
  flex: 1;
  min-width: 0;
}

.content-text {
  font-size: 14px;
  line-height: 1.7;
  color: #1a1a1a;
  word-break: break-word;
}

/* 思考动画 */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
}

.thinking-text {
  font-size: 14px;
  color: #999;
}

.thinking-dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
}

.thinking-dots span {
  display: inline-block;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #bbb;
  animation: thinkDot 1.4s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes thinkDot {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1.2);
  }
}

/* 可折叠思维链/任务区域 */
.chain-collapse {
  margin-bottom: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.chain-collapse[open] {
  padding-bottom: 4px;
}

.chain-summary {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: #555;
  list-style: none;
}

.chain-summary::-webkit-details-marker {
  display: none;
}

.summary-arrow {
  transition: transform 0.2s ease;
  flex-shrink: 0;
  color: #999;
}

details[open] > .chain-summary .summary-arrow {
  transform: rotate(90deg);
}

.summary-label {
  font-weight: 500;
  color: #333;
}

.summary-count {
  font-size: 12px;
  color: #888;
  background: #eef;
  padding: 1px 6px;
  border-radius: 10px;
}

.summary-hint {
  font-size: 12px;
  color: #aaa;
}

.summary-status.running {
  font-size: 12px;
  color: #6366f1;
}

/* 流式内容区 */
.streaming-content {
  display: inline;
}

/* 打字光标 */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: #1a1a1a;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* Markdown 渲染样式 */
.markdown-body :deep(p) {
  margin: 0 0 10px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  background: #f3f3f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  color: #d63384;
}

.markdown-body :deep(pre) {
  background: #f7f7f9;
  border-radius: 10px;
  padding: 14px 16px;
  margin: 10px 0;
  overflow-x: auto;
  border: 1px solid #f0f0f2;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 13px;
  color: #333;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 6px 0 10px 20px;
}

.markdown-body :deep(li) {
  margin: 3px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #eee;
  padding: 8px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f7f7f9;
  font-weight: 500;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid #ddd;
  padding-left: 14px;
  color: #888;
  margin: 10px 0;
}

.markdown-body :deep(a) {
  color: #666;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #1a1a1a;
}

/* 素材缩略图样式 */
.markdown-body :deep(img) {
  max-width: 160px;
  max-height: 120px;
  border-radius: 6px;
  object-fit: cover;
  margin: 4px;
  vertical-align: middle;
  border: 1px solid #eee;
  cursor: zoom-in;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.markdown-body :deep(img:hover) {
  transform: scale(1.05);
  border-color: #bbb;
}

/* 图片放大遮罩层 */
.img-lightbox {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  animation: lightbox-fade-in 0.2s ease;
}

@keyframes lightbox-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 工具栏 */
.lightbox-toolbar {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 8px;
  padding: 6px 10px;
  z-index: 10;
}

.toolbar-label {
  color: #fff;
  font-size: 13px;
  min-width: 44px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.toolbar-btn {
  width: 28px;
  height: 28px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.toolbar-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

/* 可视区域（缩放/拖拽容器） */
.lightbox-viewport {
  flex: 1;
  width: 100%;
  overflow: hidden;
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lightbox-viewport:active {
  cursor: grabbing;
}

/* 图片本身 */
.lightbox-img {
  transform-origin: center center;
  transition: transform 0.08s ease-out;
  max-width: none;
  max-height: none;
  user-select: none;
  -webkit-user-drag: none;
}

/* 底部提示 */
.lightbox-hint {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
  pointer-events: none;
}

/* 错误样式 */
.error-content {
  padding: 10px 14px;
  background: #fef7f7;
  border-radius: 10px;
  border: 1px solid #fde8e8;
}

.error-title {
  font-weight: 500;
  color: #c53030;
  margin-bottom: 4px;
  font-size: 14px;
}

.error-detail {
  font-size: 13px;
  color: #999;
}

.inline-error {
  margin-top: 10px;
  padding: 8px 12px;
  background: #fef7f7;
  border-radius: 8px;
  color: #c53030;
  font-size: 13px;
  border: 1px solid #fde8e8;
}
</style>
