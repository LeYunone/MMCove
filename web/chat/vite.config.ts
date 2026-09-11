import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// MMCove 聊天端：开发期代理后端 :8808；生产构建产物直出后端 static/chat（base '/'）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 4008,
    host: true,
    proxy: {
      '/api': { target: 'http://localhost:8808', changeOrigin: true },
      '/v1': { target: 'http://localhost:8808', changeOrigin: true },
      '/flux-images': { target: 'http://localhost:8808', changeOrigin: true },
    },
  },
  build: {
    outDir: '../../mmcove-app/src/main/resources/static/chat',
    emptyOutDir: true,
  },
})
