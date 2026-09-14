<template>
  <div class="brain-wrap">
    <div class="brain-toolbar">
      <span class="chip">🧠 {{ docTitle }}</span>
      <span class="chip stat">神经元 <b>{{ chunks.length }}</b> 片</span>
      <span class="chip stat">总字符 <b>{{ totalChars.toLocaleString() }}</b></span>
      <span class="chip stat">平均 <b>{{ avgChars }}</b> 字/片</span>
      <span class="hint">悬停神经元查看这片知识的内容</span>
    </div>

    <div class="brain-canvas" ref="canvasRef">
      <svg :viewBox="`0 0 ${W} ${H}`" class="brain-svg">
        <defs>
          <radialGradient id="bgGlow" cx="50%" cy="46%" r="60%">
            <stop offset="0%" stop-color="#0e2a4a" />
            <stop offset="55%" stop-color="#081527" />
            <stop offset="100%" stop-color="#04080f" />
          </radialGradient>
          <linearGradient id="synapse" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#22d3ee" stop-opacity="0.85" />
            <stop offset="100%" stop-color="#a78bfa" stop-opacity="0.55" />
          </linearGradient>
          <filter id="brainGlow" x="-40%" y="-40%" width="180%" height="180%">
            <feGaussianBlur stdDeviation="7" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <rect width="100%" height="100%" fill="url(#bgGlow)" />
        <g opacity="0.22" stroke="#2dd4bf" stroke-width="0.4">
          <ellipse :cx="cx" :cy="cy" :rx="r1x" :ry="r1y" fill="none" stroke-dasharray="3 6" />
          <ellipse :cx="cx" :cy="cy" :rx="r2x" :ry="r2y" fill="none" stroke-dasharray="3 6" />
        </g>

        <!-- 突触连线 -->
        <path v-for="n in nodes" :key="'s' + n.seq" :d="n.path" fill="none"
          stroke="url(#synapse)" stroke-width="1"
          :opacity="activeSeq === n.seq ? 0.95 : 0.28"
          :style="activeSeq === n.seq ? 'stroke-width:2.2' : ''" />

        <!-- 神经元 -->
        <g v-for="n in nodes" :key="n.seq" class="neuron" :class="{ active: activeSeq === n.seq }"
          :transform="`translate(${n.x},${n.y})`" @mouseenter="activeSeq = n.seq" @mouseleave="activeSeq = null"
          @click="selected = n">
          <circle r="9" fill="none" :stroke="n.color" stroke-width="0.8" class="pulse-ring"
            :style="{ animationDelay: (n.seq * 0.13) % 3 + 's' }" />
          <circle :r="activeSeq === n.seq ? 7 : 4.6" :fill="n.color"
            :filter="activeSeq === n.seq ? 'url(#brainGlow)' : ''" class="core" />
          <text v-if="chunks.length <= 30" y="-11" text-anchor="middle" class="seq-label">{{ n.seq }}</text>
        </g>

        <!-- 中心大脑 -->
        <g :transform="`translate(${cx},${cy})`" class="brain-core">
          <g filter="url(#brainGlow)">
            <path class="brain-path" d="M0,-64 C34,-64 62,-40 64,-6 C65,18 52,34 44,48 C36,62 22,66 8,64
              C-6,62 -18,54 -34,52 C-52,50 -64,36 -64,14 C-64,-10 -50,-30 -34,-46 C-22,-58 -12,-64 0,-64 Z"
              fill="#0b2239" stroke="#22d3ee" stroke-width="2.2" />
            <path d="M-40,-26 C-24,-40 -4,-44 12,-38" fill="none" stroke="#67e8f9" stroke-width="1.4" opacity="0.8" />
            <path d="M-52,2 C-40,-8 -22,-10 -8,-2" fill="none" stroke="#67e8f9" stroke-width="1.2" opacity="0.6" />
            <path d="M-44,30 C-28,22 -8,24 6,34" fill="none" stroke="#67e8f9" stroke-width="1.2" opacity="0.55" />
            <path d="M8,-52 C26,-44 38,-28 40,-10" fill="none" stroke="#a78bfa" stroke-width="1.3" opacity="0.7" />
            <path d="M22,10 C34,16 40,30 36,44" fill="none" stroke="#a78bfa" stroke-width="1.1" opacity="0.6" />
          </g>
          <text y="86" text-anchor="middle" class="brain-title">{{ shortTitle }}</text>
          <text y="104" text-anchor="middle" class="brain-sub">{{ chunks.length }} 个知识神经元 · 已向量化</text>
        </g>
      </svg>

      <div v-if="hoverNode" class="neuron-tip" :style="tipStyle">
        <div class="tip-head">切片 #{{ hoverNode.seq }} <span>{{ hoverNode.chars }} 字</span></div>
        <div class="tip-body">{{ hoverNode.preview }}</div>
      </div>
    </div>

    <div v-if="selected" class="neuron-detail">
      <div class="detail-head">🧠 神经元 #{{ selected.seq }} <el-tag size="small" type="info" effect="plain">{{ selected.chars }} 字符</el-tag>
        <el-button link size="small" @click="selected = null">收起</el-button></div>
      <pre class="detail-body">{{ fullPreview(selected) }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  docTitle: { type: String, default: '' },
  chunks: { type: Array, default: () => [] }
})

const W = 900
const H = 620
const cx = 450
const cy = 288
const r1x = 250
const r1y = 175
const r2x = 360
const r2y = 252

const activeSeq = ref(null)
const selected = ref(null)
const canvasRef = ref(null)

const totalChars = computed(() => props.chunks.reduce((s, c) => s + (c.chars || 0), 0))
const avgChars = computed(() => props.chunks.length ? Math.round(totalChars.value / props.chunks.length) : 0)
const shortTitle = computed(() => props.docTitle.length > 26 ? props.docTitle.slice(0, 26) + '…' : props.docTitle)

function mixColor(t) {
  const a = [34, 211, 238]
  const b = [167, 139, 250]
  const c = a.map((v, i) => Math.round(v + (b[i] - v) * t))
  return `rgb(${c.join(',')})`
}

const nodes = computed(() => {
  const list = props.chunks
  const n = list.length || 1
  return list.map((c, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2
    const outer = i % 2 === 0
    const rx = outer ? r2x : r1x
    const ry = outer ? r2y : r1y
    const x = cx + Math.cos(angle) * rx
    const y = cy + Math.sin(angle) * ry
    const sx = cx + Math.cos(angle) * 66
    const sy = cy + Math.sin(angle) * 66
    const mx = (sx + x) / 2 + Math.sin(angle + Math.PI / 2) * 26
    const my = (sy + y) / 2 + Math.cos(angle + Math.PI / 2) * 26
    return {
      ...c,
      x, y,
      color: mixColor(i / n),
      path: `M ${sx} ${sy} Q ${mx} ${my} ${x} ${y}`
    }
  })
})

const hoverNode = computed(() => nodes.value.find(v => v.seq === activeSeq.value) || null)
const tipStyle = computed(() => {
  if (!hoverNode.value) return {}
  const leftPct = (hoverNode.value.x / W) * 100
  const topPx = hoverNode.value.y
  const flip = leftPct > 55
  return {
    left: flip ? undefined : `calc(${leftPct}% + 14px)`,
    right: flip ? `calc(${100 - leftPct}% + 14px)` : undefined,
    top: Math.min(Math.max(topPx - 30, 8), H - 150) + 'px'
  }
})
function fullPreview(n) {
  return n.preview + (n.chars > 160 ? '\n…(切片全文 ' + n.chars + ' 字,此处为前 160 字预览)' : '')
}
</script>

<style scoped>
.brain-wrap { background: #04080f; border-radius: 10px; overflow: hidden; }
.brain-toolbar { display: flex; align-items: center; gap: 8px; padding: 10px 14px; flex-wrap: wrap;
  background: linear-gradient(180deg, rgba(13,27,42,.9), rgba(4,8,15,.4)); border-bottom: 1px solid #123; }
.chip { font-size: 12px; color: #a5f3fc; background: rgba(34,211,238,.08); border: 1px solid rgba(34,211,238,.25);
  padding: 3px 10px; border-radius: 999px; }
.chip b { color: #fff; }
.hint { margin-left: auto; font-size: 11px; color: #64748b; }
.brain-canvas { position: relative; }
.brain-svg { width: 100%; height: auto; display: block; }
.brain-core { animation: breathe 4s ease-in-out infinite; transform-origin: 0 0; }
@keyframes breathe { 0%,100% { opacity: .92; } 50% { opacity: 1; } }
.brain-path { animation: brainPulse 4s ease-in-out infinite; }
@keyframes brainPulse { 0%,100% { stroke-opacity: .75; } 50% { stroke-opacity: 1; } }
.brain-title { fill: #e0f2fe; font-size: 15px; font-weight: 600; }
.brain-sub { fill: #7dd3fc; font-size: 11px; opacity: .8; }
.seq-label { fill: #94a3b8; font-size: 8.5px; }
.neuron { cursor: pointer; }
.neuron .core { transition: r .15s; }
.neuron .pulse-ring { animation: pulse 3s ease-out infinite; opacity: 0; }
.neuron:hover .pulse-ring { animation: none; opacity: .9; }
@keyframes pulse { 0% { transform: scale(.4); opacity: .8; } 80% { transform: scale(1.6); opacity: 0; } 100% { opacity: 0; } }
.neuron-tip { position: absolute; max-width: 340px; background: rgba(8,20,36,.96); border: 1px solid rgba(34,211,238,.4);
  border-radius: 8px; padding: 8px 10px; pointer-events: none; box-shadow: 0 4px 20px rgba(0,0,0,.5); z-index: 5; }
.tip-head { font-size: 12px; color: #22d3ee; font-weight: 600; margin-bottom: 4px; display: flex; justify-content: space-between; gap: 8px; }
.tip-head span { color: #64748b; font-weight: 400; }
.tip-body { font-size: 11.5px; color: #cbd5e1; line-height: 1.55; max-height: 108px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 5; -webkit-box-orient: vertical; }
.neuron-detail { border-top: 1px solid #123; padding: 10px 14px; background: rgba(8,20,36,.7); }
.detail-head { display: flex; align-items: center; gap: 8px; color: #a5f3fc; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.detail-body { font-size: 12px; color: #cbd5e1; line-height: 1.65; white-space: pre-wrap; word-break: break-all;
  max-height: 180px; overflow-y: auto; margin: 0; background: rgba(4,8,15,.6); padding: 10px; border-radius: 6px; }
</style>
