import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// MMCove 后台端：base '/admin/'；开发期代理后端 :8808；生产构建产物直出后端 static/admin
export default defineConfig({
  plugins: [vue()],
  base: '/admin/',
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: {
      '/api': { target: 'http://localhost:8808', changeOrigin: true },
      '/v1': { target: 'http://localhost:8808', changeOrigin: true },
    },
  },
  build: {
    outDir: '../../mmcove-app/src/main/resources/static/admin',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          vendor: ['vue', 'vue-router', 'pinia', 'axios'],
        },
      },
    },
  },
})
