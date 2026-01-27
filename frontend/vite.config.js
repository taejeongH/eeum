import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path' // Import path

// https://vite.dev/config/
export default defineConfig({
  base:'./',
  plugins: [vue()],
  resolve: { // Add resolve configuration
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // Your backend server address
        changeOrigin: true,
        secure: false, // For local development with HTTP
        // rewrite: (path) => path.replace(/^\/api/, '') // Only if your backend doesn't expect /api prefix
      },
    },
  },
})
