<template>
  <div class="thinking-chain">
    <div
      v-for="turn in chain"
      :key="turn.turnIndex"
      class="thinking-turn"
    >
      <!-- Thought -->
      <div v-if="turn.thought" class="turn-step thought">
        <span class="step-icon">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6" stroke="#999" stroke-width="1.5"/>
            <path d="M5 5.5a2 2 0 1 1 2 2v.5" stroke="#999" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </span>
        <span class="step-label">思考</span>
        <span class="step-content">{{ turn.thought }}</span>
      </div>

      <!-- Action -->
      <div v-if="turn.action" class="turn-step action">
        <span class="step-icon">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <rect x="1" y="1" width="12" height="12" rx="2" stroke="#6366f1" stroke-width="1.5"/>
            <path d="M4 7h6M7 4v6" stroke="#6366f1" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </span>
        <span class="step-label">调用工具</span>
        <span class="step-content tool-name">{{ turn.action.toolName }}</span>
      </div>

      <!-- Observation -->
      <div v-if="turn.observation" class="turn-step observation">
        <span class="step-icon">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M2 7l3.5 3.5L12 3" stroke="#22c55e" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <span class="step-label">结果</span>
        <span class="step-content obs-text">{{ truncateText(turn.observation, 150) }}</span>
      </div>

      <!-- 进行中指示 -->
      <div v-if="turn.status === 'RUNNING' && !turn.observation" class="turn-step running">
        <span class="step-icon spinning">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="5.5" stroke="#999" stroke-width="1.5" stroke-dasharray="3 3"/>
          </svg>
        </span>
        <span class="step-content running-text">执行中...</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  chain: {
    type: Array,
    default: () => []
  }
})

function truncateText(text, maxLen) {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}
</script>

<style scoped>
.thinking-chain {
  margin: 6px 0;
  padding-left: 4px;
}

.thinking-turn {
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 2px solid #e5e7eb;
}

.thinking-turn:last-child {
  margin-bottom: 0;
}

.turn-step {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 2px 0;
  font-size: 13px;
  line-height: 1.5;
}

.step-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.step-label {
  flex-shrink: 0;
  color: #888;
  font-size: 12px;
  min-width: 48px;
}

.step-content {
  color: #555;
  word-break: break-word;
}

.tool-name {
  color: #6366f1;
  font-weight: 500;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
}

.obs-text {
  color: #666;
  font-size: 12px;
}

.running-text {
  color: #999;
  font-size: 12px;
}

.spinning svg {
  animation: spin 1.5s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
