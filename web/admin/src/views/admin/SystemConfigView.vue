<template>
  <div class="system-config-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>系统配置</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        </div>
      </template>

      <el-form label-width="200px" label-position="right">
        <el-form-item label="聊天接口需登录">
          <el-switch
            v-model="chatAuthRequired"
            active-value="1"
            inactive-value="0"
            active-text="需要登录/Token"
            inactive-text="免认证"
          />
          <div class="form-tip">关闭后 /v1/chat/completions 可匿名调用（仅建议内网测试时关闭）。</div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/index'

const saving = ref(false)
const chatAuthRequired = ref('1')

async function loadConfig() {
  try {
    const res = await request.get('/api/system-config')
    const list = res?.data || res || []
    const item = (list as any[]).find((c) => c.configKey === 'chat_completions_auth_required')
    if (item) chatAuthRequired.value = item.configValue === '0' ? '0' : '1'
  } catch (e) {
    /* 默认值 */
  }
}

async function handleSave() {
  saving.value = true
  try {
    await request.put('/api/system-config', {
      configs: [
        {
          configKey: 'chat_completions_auth_required',
          configValue: chatAuthRequired.value,
          description: '聊天接口是否需要登录/Token',
        },
      ],
    })
    ElMessage.success('配置保存成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.system-config-view {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
</style>
