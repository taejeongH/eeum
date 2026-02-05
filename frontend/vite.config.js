import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
// import basicSsl from '@vitejs/plugin-basic-ssl'

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
      host: true, // 로컬-원격 하이브리드 설정(창민)
      proxy: {
        '/api': {
          // 환경 변수에 설정된 주소를 타겟으로 함 (배포 시 유연함)
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
