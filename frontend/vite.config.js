import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'


export default defineConfig(({ mode }) => {
  
  const env = loadEnv(mode, process.cwd(), '')

  return {
    base: './',
    plugins: [vue()],
    resolve: {
      alias: {
        
        '@': path.resolve(__dirname, './src'),
        'vue': 'vue/dist/vue.esm-bundler.js'
      }
    },
    server: {
      host: true, 
      proxy: {
        '/api': {
          
          target: env.VITE_API_BASE_URL || 'https://i14a105.p.ssafy.io',
          changeOrigin: true,
          secure: false,
          timeout: 300000,
          proxyTimeout: 300000,
        },
        '/gmsapi': {
          target: 'https://gms.ssafy.io',
          changeOrigin: true,
          secure: false,
        },
      },
    }
  }
})
