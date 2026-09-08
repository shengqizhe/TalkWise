import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000, // 指定前端开发端口为3000
    proxy: {
      '/api': { // 关键修正：路径前缀添加`/`
        target: 'http://localhost:8080', // 后端服务地址（8080端口）
        changeOrigin: true,
        secure: false, // 允许http请求（非https）
      }
    }
  }
})