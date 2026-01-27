import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  // 환경 변수(.env)를 로드합니다.
  const env = loadEnv(mode, process.cwd(), '')

  return {
    base: './',
    plugins: [vue()],
    resolve: {
      alias: {
        // @를 src 폴더 주소로 매핑 (아까 발생한 경로 에러 방지용)
        '@': path.resolve(__dirname, './src')
      }
    },
    server: {
      proxy: {
        '/api': {
          // 환경 변수에 설정된 주소를 타겟으로 함 (배포 시 유연함)
          target: env.VITE_API_BASE_URL || 'https://i14a105.p.ssafy.io',
          changeOrigin: true,
          secure: false,
        },
      },
    }
  }
})
