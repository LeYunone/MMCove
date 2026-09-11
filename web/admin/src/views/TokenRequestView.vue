<template>
  <div class="token-request-page">
    <!-- 页面头部 -->
    <el-card class="page-header">
      <div class="header-content">
        <h1>申请 API Token</h1>
        <p>申请您的专属 Token，开始使用 API 服务</p>
      </div>
    </el-card>

    <el-row :gutter="20">
      <!-- 左侧：申请表单 -->
      <el-col :span="12">
        <el-card class="request-card">
          <template #header>
            <span>Token 申请表单</span>
          </template>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="120px"
            size="large"
          >
            <!-- 基本信息 -->
            <h3 class="section-title">基本信息</h3>

            <el-form-item label="Token名称" prop="name">
              <el-input
                v-model="form.name"
                placeholder="例如：我的第一个Token、测试环境Token等"
                maxlength="30"
                show-word-limit
              >
                <template #prefix>
                  <el-icon><EditPen /></el-icon>
                </template>
              </el-input>
              <div class="form-tip">建议使用有意义的名称，方便管理</div>
            </el-form-item>

            <!-- 配额设置 -->
            <h3 class="section-title">配额设置</h3>

            <el-form-item label="配额类型">
              <el-radio-group v-model="quotaType" @change="handleQuotaTypeChange">
                <el-radio label="limited">
                  <span>有限配额</span>
                  <el-tooltip content="设置Token的使用次数上限" placement="top">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </el-radio>
                <el-radio label="unlimited">
                  <span>无限配额</span>
                  <el-tooltip content="无使用次数限制" placement="top">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item
              v-if="quotaType === 'limited'"
              label="使用次数"
              prop="remainQuota"
            >
              <el-input-number
                v-model="form.remainQuota"
                :min="1"
                :max="1000000"
                placeholder="请输入使用次数"
              />
              <div class="form-tip">设置此Token最多可调用的次数</div>
            </el-form-item>

            <!-- 高级设置 -->
            <el-collapse v-model="activeCollapse" style="margin-top: 20px;">
              <el-collapse-item title="高级设置（可选）" name="advanced">
                <h4 style="margin-bottom: 16px;">高级设置</h4>

                <el-form-item label="过期时间" prop="expiredTime">
                  <el-date-picker
                    v-model="form.expiredTime"
                    type="datetime"
                    placeholder="选择过期时间"
                    :disabled-date="disabledDate"
                    value-format="x"
                    style="width: 100%"
                  />
                  <div class="form-tip">不设置将永不过期</div>
                </el-form-item>

                <el-divider text-align="left">访问限制</el-divider>

                <el-form-item label="IP白名单" prop="allowIps">
                  <el-input
                    v-model="form.allowIps"
                    type="textarea"
                    :rows="3"
                    placeholder="请输入允许访问的IP地址，每行一个"
                  />
                  <div class="form-tip">留空表示不限制IP地址</div>
                </el-form-item>

                <el-form-item label="分组名称">
                  <el-input
                    v-model="form.group"
                    placeholder="例如：个人开发、测试项目等"
                  />
                  <div class="form-tip">用于分类管理，可在管理界面查看</div>
                </el-form-item>
              </el-collapse-item>
            </el-collapse>

            <el-form-item style="margin-top: 30px;">
              <el-button
                type="primary"
                size="large"
                :loading="submitting"
                @click="handleSubmit"
                style="width: 100%"
              >
                申请 Token
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：信息面板 -->
      <el-col :span="12">
        <el-card class="info-panel">
          <template #header>
            <span>使用说明</span>
          </template>

          <!-- 申请须知 -->
          <div class="notice-box">
            <h4>📝 申请须知</h4>
            <ul>
              <li>每个用户最多可以创建10个Token</li>
              <li>Token创建后请妥善保管，丢失无法找回</li>
              <li>建议定期更换Token以确保安全</li>
              <li>如有问题，请联系管理员</li>
            </ul>
          </div>

          <!-- 使用流程 -->
          <div class="steps-box">
            <h4>🚀 使用流程</h4>
            <el-steps :active="1" finish-status="success">
              <el-step title="申请Token" description="填写信息提交申请" />
              <el-step title="保存Token" description="复制保存生成的Token" />
              <el-step title="开始使用" description="在API调用中使用Token" />
            </el-steps>
          </div>

          <!-- 快速创建 -->
          <div class="quick-create">
            <h4>⚡ 快速创建</h4>
            <p>如果您只需要一个简单的Token，可以使用快速创建：</p>
            <el-button
              type="primary"
              :loading="submitting"
              @click="handleQuickCreate"
              plain
            >
              快速创建一个Token
            </el-button>
          </div>

          <!-- Token示例 -->
          <div class="example-box">
            <h4>📋 Token使用示例</h4>
            <div class="example-content">
              <div class="example-item">
                <div class="example-title">API调用示例：</div>
                <pre class="example-code">POST /v1/chat/completions
Authorization: Bearer sk-xxx1234567890

{
  "model": "gpt-3.5-turbo",
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ]
}</pre>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Token生成结果对话框 -->
    <el-dialog
      v-model="showResultDialog"
      title="Token 创建成功"
      width="600px"
      :close-on-click-modal="false"
      align-center
    >
      <el-alert
        type="success"
        :closable="false"
        show-icon
      >
        <template #title>申请成功！</template>
        请妥善保管您的Token，创建后将无法再次查看！
      </el-alert>

      <div class="result-content">
        <div class="token-display">
          <div class="token-label">您的Token：</div>
          <div class="token-value">{{ createdTokenKey }}</div>
          <el-button
            type="primary"
            @click="copyToken"
            style="margin-top: 12px;"
          >
            <el-icon><CopyDocument /></el-icon>
            复制Token
          </el-button>
        </div>

        <el-divider />

        <div class="token-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="Token ID">{{ createdToken.id }}</el-descriptions-item>
            <el-descriptions-item label="Token名称">{{ createdToken.name }}</el-descriptions-item>
            <el-descriptions-item label="配额类型">
              <el-tag :type="createdToken.unlimitedQuota ? 'success' : 'info'">
                {{ createdToken.unlimitedQuota ? '无限' : '有限' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="剩余配额">
              {{ createdToken.unlimitedQuota ? '∞' : createdToken.remainQuota }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatTime(createdToken.createdTime * 1000) }}
            </el-descriptions-item>
            <el-descriptions-item label="过期时间">
              {{ formatTime(createdToken.expiredTime * 1000) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <template #footer>
        <el-button type="primary" @click="goToManage">
          管理我的Token
        </el-button>
        <el-button @click="closeResultDialog">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, QuestionFilled, CopyDocument } from '@element-plus/icons-vue'
import request from '@/api'

const formRef = ref(null)
const submitting = ref(false)
const activeCollapse = ref([])
const showResultDialog = ref(false)
const createdToken = ref(null)
const createdTokenKey = ref('')

// 配额类型
const quotaType = ref('limited')

// 表单数据
const form = reactive({
  name: '',
  remainQuota: 1000,
  unlimitedQuota: false,
  expiredTime: null,
  allowIps: '',
  group: ''
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入Token名称', trigger: 'blur' },
    { min: 1, max: 30, message: '长度在 1 到 30 个字符', trigger: 'blur' }
  ],
  remainQuota: [
    { required: true, message: '请输入使用次数', trigger: 'blur' },
    { type: 'number', min: 1, message: '使用次数必须大于0', trigger: 'blur' }
  ]
}

// 处理配额类型变化
function handleQuotaTypeChange(value) {
  if (value === 'unlimited') {
    form.unlimitedQuota = true
    form.remainQuota = null
  } else {
    form.unlimitedQuota = false
    form.remainQuota = 1000 // 默认配额
  }
}

// 禁用过去日期
function disabledDate(time) {
  return time.getTime() < Date.now() - 8.64e7
}

// 格式化时间
function formatTime(timestamp) {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}

// 复制Token
function copyToken() {
  navigator.clipboard.writeText(createdTokenKey.value).then(() => {
    ElMessage.success('Token 已复制到剪贴板')
  })
}

// 跳转到管理页面
function goToManage() {
  window.location.href = '/admin'
}

// 关闭结果对话框
function closeResultDialog() {
  showResultDialog.value = false
  // 重置表单
  formRef.value?.resetFields()
  form.name = ''
  form.remainQuota = 1000
  form.unlimitedQuota = false
  form.expiredTime = null
  form.allowIps = ''
  form.group = ''
  quotaType.value = 'limited'
}

// 快速创建
async function handleQuickCreate() {
  try {
    // 检查是否已登录
    const res = await request.get('/api/user/self')
    if (res.code === 0) {
      // 直接调用快速创建API
      submitQuickCreate()
    } else {
      ElMessage.error('请先登录')
      // 可以在这里跳转到登录页面
    }
  } catch (error) {
    ElMessage.error('请先登录')
  }
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate()

  submitting.value = true
  try {
    // 获取当前用户信息
    const userRes = await request.get('/api/user/self')
    if (userRes.code !== 0) {
      throw new Error('获取用户信息失败')
    }

    // 准备请求数据
    const requestPayload = {
      name: form.name,
      unlimitedQuota: form.unlimitedQuota,
      remainQuota: form.remainQuota,
      expiredTime: form.expiredTime
    }

    const res = await request.post('/api/token', requestPayload)
    if (res.code === 0) {
      createdToken.value = res.data
      createdTokenKey.value = res.data.tokenKey
      showResultDialog.value = true
    } else {
      ElMessage.error(res.message || '创建Token失败')
    }
  } catch (error) {
    console.error('创建Token失败:', error)
    ElMessage.error('创建Token失败')
  } finally {
    submitting.value = false
  }
}

// 快速创建提交
async function submitQuickCreate() {
  submitting.value = true
  try {
    const res = await request.post('/api/token/quick', null, {
      params: {
        name: '未命名Token'
      }
    })
    if (res.code === 0) {
      createdToken.value = res.data
      createdTokenKey.value = res.data.key || res.data.tokenKey // 快速返回可能是key或tokenKey
      showResultDialog.value = true
    } else {
      ElMessage.error(res.message || '创建Token失败')
    }
  } catch (error) {
    console.error('创建Token失败:', error)
    ElMessage.error('创建Token失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.token-request-page {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 30px;
}

.header-content {
  text-align: center;
}

.header-content h1 {
  margin: 0 0 12px;
  font-size: 32px;
  color: #303133;
}

.header-content p {
  margin: 0;
  color: #666;
  font-size: 16px;
}

.request-card, .info-panel {
  height: 100%;
}

.section-title {
  margin: 24px 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.section-title:first-child {
  margin-top: 0;
}

.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
  line-height: 1.4;
}

.notice-box h4,
.steps-box h4,
.quick-create h4,
.example-box h4 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #333;
}

.notice-box ul {
  margin: 0;
  padding-left: 20px;
  color: #666;
  line-height: 1.8;
}

:deep(.el-steps) {
  margin-top: 16px;
}

:deep(.el-step__description) {
  font-size: 12px;
}

.example-box .example-content {
  margin-top: 16px;
}

.example-item {
  margin-bottom: 16px;
}

.example-title {
  font-weight: 500;
  margin-bottom: 8px;
  color: #666;
}

.example-code {
  margin: 0;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow-x: auto;
}

.result-content {
  margin-top: 20px;
}

.token-display {
  margin-bottom: 20px;
}

.token-label {
  font-weight: 500;
  margin-bottom: 8px;
}

.token-value {
  font-family: monospace;
  font-size: 16px;
  word-break: break-all;
  line-height: 1.6;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

:deep(.el-descriptions) {
  margin-top: 16px;
}

:deep(.el-descriptions__label) {
  font-weight: normal;
  color: #666;
}

:deep(.el-descriptions__content) {
  color: #303133;
}

@media (max-width: 768px) {
  .token-request-page {
    padding: 10px;
  }

  .el-col {
    width: 100%;
  }

  .header-content h1 {
    font-size: 24px;
  }
}
</style>