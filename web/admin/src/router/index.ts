import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue')
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '仪表盘', requiresAdmin: true }
      },
      {
        path: 'templates',
        name: 'TemplateList',
        component: () => import('@/views/admin/TemplateListView.vue'),
        meta: { title: '模板管理', requiresAdmin: true }
      },
      {
        path: 'templates/create',
        name: 'TemplateCreate',
        component: () => import('@/views/admin/TemplateEditView.vue'),
        meta: { title: '新增模板', requiresAdmin: true }
      },
      {
        path: 'templates/:id',
        name: 'TemplateEdit',
        component: () => import('@/views/admin/TemplateEditView.vue'),
        meta: { title: '编辑模板', requiresAdmin: true }
      },
      {
        path: 'sessions',
        name: 'SessionList',
        component: () => import('@/views/admin/SessionListView.vue'),
        meta: { title: '会话管理', requiresAdmin: true }
      },
      {
        path: 'sessions/:id',
        name: 'SessionDetail',
        component: () => import('@/views/admin/SessionDetailView.vue'),
        meta: { title: '会话详情', requiresAdmin: true }
      },
      {
        path: 'tools',
        name: 'ToolDefinitions',
        component: () => import('@/views/admin/ToolDefinitionView.vue'),
        meta: { title: '工具管理', requiresAdmin: true }
      },
      {
        path: 'tool-groups',
        name: 'ToolGroups',
        component: () => import('@/views/admin/ToolGroupView.vue'),
        meta: { title: '工具分组', requiresAdmin: true }
      },
      {
        path: 'product-lines',
        name: 'ProductLines',
        component: () => import('@/views/admin/ProductLineView.vue'),
        meta: { title: '产品线管理', requiresAdmin: true }
      },
      {
        path: 'vibe',
        name: 'VibeHome',
        component: () => import('@/views/admin/VibeHomeView.vue'),
        meta: { title: 'AI 编程流水线', requiresAdmin: true }
      },
      {
        path: 'vibe/:lineId/:tab?',
        name: 'VibeWorkspace',
        component: () => import('@/views/admin/VibeWorkspaceView.vue'),
        meta: { title: '产品工作区', requiresAdmin: true }
      },
      {
                path: 'knowledge-bases',
        name: 'KnowledgeBases',
        component: () => import('@/views/admin/KnowledgeBaseView.vue'),
        meta: { title: '知识库管理', requiresAdmin: true }
      },
      {
        path: 'agents',
        name: 'AgentList',
        component: () => import('@/views/admin/AgentListView.vue'),
        meta: { title: 'Agent 管理', requiresAdmin: true }
      },
      {
        path: 'agents/create',
        name: 'AgentCreate',
        component: () => import('@/views/admin/AgentEditView.vue'),
        meta: { title: '新增 Agent', requiresAdmin: true }
      },
      {
        path: 'agents/:id',
        name: 'AgentEdit',
        component: () => import('@/views/admin/AgentEditView.vue'),
        meta: { title: '编辑 Agent', requiresAdmin: true }
      },
      {
        path: 'tokens',
        name: 'TokenList',
        component: () => import('@/views/admin/token/TokenManagement.vue'),
        meta: { title: 'Token 管理', requiresAdmin: true }
      },
      {
        path: 'api-doc',
        name: 'ApiDoc',
        component: () => import('@/views/admin/ApiDocView.vue'),
        meta: { title: 'API 文档', requiresAdmin: true }
      },
      {
        path: 'ai-roles',
        name: 'AiRoleManagement',
        component: () => import('@/views/admin/role/AiRoleManagement.vue'),
        meta: { title: '角色管理', requiresAdmin: true }
      },
      {
        path: 'ai-channels',
        name: 'AiChannelManagement',
        component: () => import('@/views/admin/role/AiChannelManagement.vue'),
        meta: { title: '渠道管理', requiresAdmin: true }
      },
      {
        path: 'system-config',
        name: 'SystemConfig',
        component: () => import('@/views/admin/SystemConfigView.vue'),
        meta: { title: '系统配置', requiresAdmin: true }
      },
      {
        path: 'usage-details',
        name: 'UsageDetails',
        component: () => import('@/views/admin/UsageDetailsView.vue'),
        meta: { title: '使用明细', requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：检查是否已登录
router.beforeEach((to, from, next) => {
  // 如果路由需要管理权限
  if (to.meta.requiresAdmin) {
    const authToken = localStorage.getItem('mmcove_access_token')
    if (!authToken) {
      // 没有登录，跳转到登录页面
      // 使用事件机制触发登录弹窗
      window.dispatchEvent(new CustomEvent('admin:require-login'))
      // 不阻止导航，让页面显示登录提示
    }
  }
  next()
})

export default router
