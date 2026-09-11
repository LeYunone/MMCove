<template>
  <div class="token-edit" v-loading="loading">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑 Token' : '新建 Token' }}</span>
          <el-button @click="goBack">返回</el-button>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        style="max-width: 600px"
      >
        <el-form-item label="Token名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入Token名称" maxlength="30" show-word-limit />
        </el-form-item>

        <el-form-item label="过期时间">
          <el-radio-group v-model="expireType">
            <el-radio :label="0">永不过期</el-radio>
            <el-radio :label="1">指定时间</el-radio>
          </el-radio-group>
          <el-date-picker
            v-if="expireType === 1"
            v-model="form.expiredTime"
            type="datetime"
            placeholder="选择过期时间"
            style="margin-left: 12px"
            :disabled-date="disabledDate"
          />
        </el-form-item>

        <el-form-item label="配额设置">
          <el-switch v-model="form.unlimitedQuota" active-text="无限配额" inactive-text="限制配额" />
        </el-form-item>

        <el-form-item v-if="!form.unlimitedQuota" label="剩余配额" prop="remainQuota">
          <el-input-number v-model="form.remainQuota" :min="0" :step="1000" />
        </el-form-item>

        <el-form-item label="模型限制">
          <el-switch v-model="form.modelLimitsEnabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>

        <el-form-item v-if="form.modelLimitsEnabled" label="允许的模型">
          <el-input
            v-model="form.modelLimits"
            type="textarea"
            :rows="3"
            placeholder="输入允许的模型，多个用逗号分隔，如: gpt-3.5-turbo,gpt-4"
          />
          <div class="form-tip">留空表示允许所有模型</div>
        </el-form-item>

        <el-form-item label="允许的IP">
          <el-input
            v-model="form.allowIps"
            type="textarea"
            :rows="3"
            placeholder="输入允许的IP地址，每行一个，如: 192.168.1.1"
          />
          <div class="form-tip">留空表示允许所有IP</div>
        </el-form-item>

        <el-form-item label="分组">
          <el-input v-model="form.group" placeholder="请输入分组名称" />
        </el-form-item>

        <el-form-item v-if="isEdit" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="2">禁用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="isEdit && generatedKey" label="Token Key">
          <div class="key-display">
            <code>{{ generatedKey }}</code>
            <el-button type="primary" size="small" @click="copyKey">复制</el-button>
          </div>
          <div class="form-tip warning">请妥善保管此Key，关闭后将无法再次查看</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTokenStore } from '@/stores/admin/token'
import { ElMessage } from 'element-plus'
import { useClipboard } from '@vueuse/core'

const route = useRoute()
const router = useRouter()
const store = useTokenStore()
const { copy } = useClipboard()

const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const generatedKey = ref('')
const expireType = ref(0)

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  name: '',
  expiredTime: -1,
  remainQuota: 0,
  unlimitedQuota: false,
  modelLimitsEnabled: false,
  modelLimits: '',
  allowIps: '',
  group: '',
  status: 1
})

const rules = {
  name: [
    { required: true, message: '请输入Token名称', trigger: 'blur' },
    { max: 30, message: '名称不能超过30个字符', trigger: 'blur' }
  ]
}

// 加载数据
onMounted(async () => {
  if (isEdit.value) {
    loading.value = true
    try {
      const token = await store.fetchToken(route.params.id)
      if (token) {
        form.name = token.name || ''
        form.remainQuota = token.remainQuota || 0
        form.unlimitedQuota = token.unlimitedQuota || false
        form.modelLimitsEnabled = token.modelLimitsEnabled || false
        form.modelLimits = token.modelLimits || ''
        form.allowIps = token.allowIps || ''
        form.group = token.groupName || ''
        form.status = token.status || 1

        if (token.expiredTime === -1) {
          expireType.value = 0
          form.expiredTime = -1
        } else {
          expireType.value = 1
          form.expiredTime = token.expiredTime * 1000
        }

        generatedKey.value = token.key
      }
    } finally {
      loading.value = false
    }
  }
})

// 禁用未来日期
function disabledDate(date) {
  return date < new Date()
}

// 复制Key
async function copyKey() {
  try {
    await copy(generatedKey.value)
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

// 提交
async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  submitting.value = true
  try {
    const data = {
      name: form.name,
      unlimitedQuota: form.unlimitedQuota,
      modelLimitsEnabled: form.modelLimitsEnabled,
      group: form.group
    }

    // 处理过期时间
    if (expireType.value === 0) {
      data.expiredTime = -1
    } else {
      data.expiredTime = Math.floor(new Date(form.expiredTime).getTime() / 1000)
    }

    // 处理配额
    if (!form.unlimitedQuota) {
      data.remainQuota = form.remainQuota
    }

    // 处理模型限制
    if (form.modelLimitsEnabled) {
      data.modelLimits = form.modelLimits
    }

    // 处理IP白名单
    if (form.allowIps) {
      data.allowIps = form.allowIps
    }

    if (isEdit.value) {
      data.id = parseInt(route.params.id)
      data.status = form.status
      await store.editToken(data)
    } else {
      await store.addToken(data)
    }

    router.push('/admin/tokens')
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitting.value = false
  }
}

// 返回
function goBack() {
  router.back()
}
</script>

<style scoped>
.token-edit {
  max-width: 800px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.form-tip.warning {
  color: #e6a23c;
}

.key-display {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
}

.key-display code {
  font-family: monospace;
  color: #409eff;
  word-break: break-all;
}
</style>
