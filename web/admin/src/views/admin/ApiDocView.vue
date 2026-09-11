<template>
  <div class="api-doc">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>API 接口文档</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- Chat Completions -->
        <el-tab-pane label="Chat Completions" name="chat">
          <div class="api-section">
            <h3>POST /v1/chat/completions</h3>
            <p class="desc">OpenAI 兼容的对话补全接口，支持流式和非流式两种模式。</p>

            <h4>请求头</h4>
            <pre class="code-block">Authorization: Bearer sk-xxxxxxxxxxxxxxxx</pre>

            <h4>请求体</h4>
            <pre class="code-block">{{ chatExample }}</pre>

            <h4>响应示例</h4>
            <pre class="code-block">{{ chatResponseExample }}</pre>
          </div>
        </el-tab-pane>

        <!-- Models -->
        <el-tab-pane label="Models" name="models">
          <div class="api-section">
            <h3>GET /v1/models</h3>
            <p class="desc">获取支持的模型列表。</p>

            <h4>请求头</h4>
            <pre class="code-block">Authorization: Bearer sk-xxxxxxxxxxxxxxxx</pre>

            <h4>响应示例</h4>
            <pre class="code-block">{{ modelsExample }}</pre>
          </div>
        </el-tab-pane>

        <!-- cURL 示例 -->
        <el-tab-pane label="cURL 示例" name="curl">
          <div class="api-section">
            <h3>cURL 调用示例</h3>

            <h4>1. 获取模型列表</h4>
            <pre class="code-block">curl -X GET http://localhost:8808/v1/models \
  -H "Authorization: Bearer sk-your-token-here"</pre>

            <h4>2. 同步对话</h4>
            <pre class="code-block">curl -X POST http://localhost:8808/v1/chat/completions \
  -H "Authorization: Bearer sk-your-token-here" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {"role": "system", "content": "你是AI助手"},
      {"role": "user", "content": "你好"}
    ]
  }'</pre>

            <h4>3. 流式对话</h4>
            <pre class="code-block">curl -X POST http://localhost:8808/v1/chat/completions \
  -H "Authorization: Bearer sk-your-token-here" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "写一首诗"}],
    "stream": true
  }'</pre>

            <h4>4. Python 示例</h4>
            <pre class="code-block">import requests

response = requests.post(
    "http://localhost:8808/v1/chat/completions",
    headers={
        "Authorization": "Bearer sk-your-token-here",
        "Content-Type": "application/json"
    },
    json={
        "model": "gpt-3.5-turbo",
        "messages": [
            {"role": "user", "content": "你好"}
        ]
    }
)

print(response.json())</pre>
          </div>
        </el-tab-pane>

        <!-- SDK 使用 -->
        <el-tab-pane label="SDK 使用" name="sdk">
          <div class="api-section">
            <h3>OpenAI SDK 兼容调用</h3>
            <p class="desc">本接口兼容 OpenAI SDK，可直接替换 baseURL 使用。</p>

            <h4>Python</h4>
            <pre class="code-block">from openai import OpenAI

client = OpenAI(
    api_key="sk-your-token-here",
    base_url="http://localhost:8808/v1"  # 指向 MMCove AI
)

response = client.chat.completions.create(
    model="gpt-3.5-turbo",
    messages=[
        {"role": "user", "content": "你好"}
    ]
)

print(response.choices[0].message.content)</pre>

            <h4>JavaScript/Node.js</h4>
            <pre class="code-block">import OpenAI from 'openai';

const client = new OpenAI({
  apiKey: 'sk-your-token-here',
  baseURL: 'http://localhost:8808/v1'
});

const response = await client.chat.completions.create({
  model: 'gpt-3.5-turbo',
  messages: [{ role: 'user', content: '你好' }]
});

console.log(response.choices[0].message.content);</pre>

            <h4>Go</h4>
            <pre class="code-block">package main

import (
    "context"
    "fmt"
    "github.com/sashabaranov/go-openai"
)

func main() {
    client := openai.NewClient("sk-your-token-here")
    client.BaseURL = "http://localhost:8808/v1"

    resp, err := client.CreateChatCompletion(
        context.Background(),
        openai.ChatCompletionRequest{
            Model: "gpt-3.5-turbo",
            Messages: []openai.ChatCompletionMessage{
                {Role: "user", Content: "你好"},
            },
        },
    )
    if err != nil {
        fmt.Printf("Error: %v\n", err)
        return
    }
    fmt.Println(resp.Choices[0].Message.Content)
}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeTab = ref('chat')

const chatExample = `{
  "model": "gpt-3.5-turbo",
  "messages": [
    {"role": "system", "content": "你是AI助手"},
    {"role": "user", "content": "你好"}
  ],
  "stream": false  // true=流式, false=同步
}`

const chatResponseExample = `{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "gpt-3.5-turbo",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "你好！有什么可以帮助你的吗？"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 30,
    "total_tokens": 50
  }
}`

const modelsExample = `{
  "object": "list",
  "data": [
    {
      "id": "gpt-4",
      "object": "model",
      "created": 1677610602,
      "owned_by": "system",
      "mmcove_details": {
        "mode": "chat",
        "type": "text",
        "context_window": 8192,
        "enabled": true
      }
    },
    {
      "id": "gpt-3.5-turbo",
      "object": "model",
      "created": 1677610602,
      "owned_by": "system",
      "mmcove_details": {
        "mode": "chat",
        "type": "text",
        "context_window": 16385,
        "enabled": true
      }
    }
  ]
}`
</script>

<style scoped>
.api-doc {
  max-width: 900px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.api-section {
  padding: 16px 0;
}

.api-section h3 {
  font-size: 16px;
  margin: 0 0 12px;
  color: #303133;
}

.api-section h4 {
  font-size: 14px;
  margin: 16px 0 8px;
  color: #606266;
}

.api-section .desc {
  color: #909399;
  font-size: 13px;
  margin-bottom: 16px;
}

.code-block {
  background: #f5f7fa;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 12px 16px;
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #606266;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
</style>
