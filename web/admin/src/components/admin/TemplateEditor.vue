<template>
  <div class="template-editor">
    <div class="editor-area">
      <el-input
        v-model="content"
        type="textarea"
        :rows="14"
        placeholder="输入模板提示词内容..."
        @input="handleInput"
      />
    </div>
    <div class="preview-area">
      <div class="preview-header">预览</div>
      <div class="preview-content" v-html="previewHtml"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const content = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const previewHtml = computed(() => renderMarkdown(props.modelValue || ''))

function handleInput(val) {
  emit('update:modelValue', val)
}
</script>

<style scoped>
.template-editor {
  display: flex;
  gap: 12px;
}

.editor-area {
  flex: 1;
}

.preview-area {
  flex: 1;
  background: #fafbfc;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-header {
  padding: 8px 12px;
  background: #f0f2f5;
  border-bottom: 1px solid #e8e8e8;
  font-size: 12px;
  color: #909399;
  font-weight: 600;
}

.preview-content {
  padding: 12px;
  overflow-y: auto;
  max-height: 350px;
  font-size: 13px;
  line-height: 1.6;
}

.preview-content :deep(pre) {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}
</style>
