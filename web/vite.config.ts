import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ command }) => ({
  plugins: [react(), tailwindcss()],
  base: command === 'build' ? '/ui/v2/' : '/',
  server: {
    port: 5173,
    proxy: {
      '/burst': {
        target: process.env.VITE_NODE_URL ?? 'https://brazil.signum.network',
        changeOrigin: true,
      },
      '/events': {
        target: process.env.VITE_NODE_URL ?? 'https://brazil.signum.network',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
}))
