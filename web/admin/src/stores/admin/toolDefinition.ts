import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listToolDefinitionsPaged,
  getToolDefinition,
  updateToolDefinition,
  resetToolDefinition as resetToolApi,
  getSourceClasses,
  syncToolDefinitions,
  refreshToolDefinitionCache
} from '@/api/toolDefinition'
import { ElMessage } from 'element-plus'

export const useAdminToolDefinitionStore = defineStore('admin-tool-definition', () => {
  const tools = ref([])
  const sourceClasses = ref([])
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(15)

  async function fetchTools(sourceClass, keyword) {
    loading.value = true
    try {
      const params = { pageNum: pageNum.value, pageSize: pageSize.value }
      if (sourceClass) params.sourceClass = sourceClass
      if (keyword) params.keyword = keyword
      const res = await listToolDefinitionsPaged(params)
      const data = res.data || {}
      tools.value = data.content || []
      total.value = data.totalElements || 0
      pageNum.value = data.pageNumber || 1
    } catch {
      // 拦截器已处理
    } finally {
      loading.value = false
    }
  }

  async function fetchTool(id) {
    loading.value = true
    try {
      const res = await getToolDefinition(id)
      return res.data
    } catch {
      return null
    } finally {
      loading.value = false
    }
  }

  async function editTool(id, data) {
    try {
      const res = await updateToolDefinition(id, data)
      ElMessage.success('工具定义更新成功')
      return res.data
    } catch {
      return null
    }
  }

  async function resetTool(id) {
    try {
      const res = await resetToolApi(id)
      ElMessage.success('已重置为原始描述')
      return res.data
    } catch {
      return null
    }
  }

  async function fetchSourceClasses() {
    try {
      const res = await getSourceClasses()
      sourceClasses.value = res.data || []
    } catch {
      // ignore
    }
  }

  async function syncTools() {
    try {
      const res = await syncToolDefinitions()
      const data = res.data || {}
      ElMessage.success(`同步完成：新增 ${data.inserted}，更新 ${data.updated}，共 ${data.total} 个`)
      return data
    } catch {
      return null
    }
  }

  async function refreshCache() {
    try {
      await refreshToolDefinitionCache()
      ElMessage.success('缓存已刷新')
    } catch {
      // handled
    }
  }

  return {
    tools,
    sourceClasses,
    loading,
    total,
    pageNum,
    pageSize,
    fetchTools,
    fetchTool,
    editTool,
    resetTool,
    fetchSourceClasses,
    syncTools,
    refreshCache
  }
})
