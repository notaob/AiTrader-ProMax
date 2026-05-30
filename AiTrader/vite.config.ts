import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 开启本地网络访问，这样同一个局域网的手机也能访问
    host: true, 
    proxy: {
      // 您的后端服务代理
      '/api': {
        // 请在这里填入你的真实后端地址
        target: 'http://localhost:8080', // 例如 http://192.168.1.5:3000
        changeOrigin: true,
        // 如果服务器接口没有 /api 前缀，需要把路径中的 /api 去掉
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
