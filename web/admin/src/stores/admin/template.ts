import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listTemplatesPaged,
  getTemplate,
  createTemplate,
  updateTemplate,
  deleteTemplate
} from '@/api/responseTemplate'
import { ElMessage } from 'element-plus'

export const useAdminTemplateStore = defineStore('admin-template', () => {
  const templates = ref([])
  const currentTemplate = ref(null)
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)

  async function fetchTemplates(scope, keyword) {
    loading.value = true
    try {
      const params = { pageNum: pageNum.value, pageSize: pageSize.value }
      if (scope) params.scope = scope
      if (keyword) params.keyword = keyword
      const res = await listTemplatesPaged(params)
      const data = res.data || {}
      templates.value = data.content || []
      total.value = data.totalElements || 0
      pageNum.value = data.pageNumber || 1
    } catch {
      // 拦截器已处理
    } finally {
      loading.value = false
    }
  }

  async function fetchTemplate(id) {
    loading.value = true
    try {
      const res = await getTemplate(id)
      currentTemplate.value = res.data
      return res.data
    } catch {
      return null
    } finally {
      loading.value = false
    }
  }

  async function addTemplate(data) {
    try {
      const res = await createTemplate(data)
      ElMessage.success('模板创建成功')
      return res.data
    } catch {
      return null
    }
  }

  async function editTemplate(id, data) {
    try {
      const res = await updateTemplate(id, data)
      ElMessage.success('模板更新成功')
      return res.data
    } catch {
      return null
    }
  }

  async function removeTemplate(id) {
    try {
      await deleteTemplate(id)
      ElMessage.success('模板已删除')
      await fetchTemplates()
    } catch {
      // 拦截器已处理
    }
  }

  return {
    templates,
    currentTemplate,
    loading,
    total,
    pageNum,
    pageSize,
    fetchTemplates,
    fetchTemplate,
    addTemplate,
    editTemplate,
    removeTemplate
  }
})
