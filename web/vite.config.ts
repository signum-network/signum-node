import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

const nodeUrl = process.env.VITE_NODE_URL ?? 'http://localhost:8125'

export default defineConfig(({ command }) => ({
  plugins: [react(), tailwindcss()],
  base: command === 'build' ? '/app/' : '/',
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: nodeUrl,
        changeOrigin: true,
      },
      '/events': {
        target: nodeUrl,
        changeOrigin: true,
        ws: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: 'dist',
  },
}))
