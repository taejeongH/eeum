<template>
  <div class="min-h-[100dvh] flex items-center justify-center" style="background: linear-gradient(135deg, #ffffff 0%, var(--color-primary-soft) 100%);">
    <div class="w-full max-w-md px-6 py-10">
      <div class="text-center mb-8">
        <div class="flex items-center justify-center mb-1">
          <img :src="logoUrl" alt="이음 로고" class="w-32 h-32 object-contain drop-shadow-2xl" />
        </div>
        <h1 class="eeum-title text-5xl font-extrabold mb-3" style="color: var(--color-primary);">이음</h1>
        <p class="eeum-sub">마음을 잇는 가족 돌봄</p>
      </div>

      <button
        type="button"
        @click="handleKakaoLogin"
        class="eeum-btn-primary w-full py-4 px-6 rounded-full font-semibold text-lg mb-6 flex items-center justify-center gap-3 shadow-lg transition hover:-translate-y-0.5"
        style="background-color: #FEE500; color: #000000;"
      >
        <svg class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 3c5.799 0 10.5 3.664 10.5 8.185 0 4.52-4.701 8.184-10.5 8.184a13.5 13.5 0 0 1-1.727-.11l-4.408 2.883c-.501.265-.678.236-.472-.413l.892-3.678c-2.88-1.46-4.785-3.99-4.785-6.866C1.5 6.665 6.201 3 12 3z"/>
        </svg>
        카카오로 시작하기
      </button>

      <div class="flex items-center mb-6">
        <div class="flex-1 border-t border-gray-300"></div>
        <span class="px-4 eeum-sub">또는 이메일 로그인</span>
        <div class="flex-1 border-t border-gray-300"></div>
      </div>

      <form @submit.prevent="handleLogin">
        <div class="mb-4">
          <div class="relative">
            <span class="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400">
              <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
              </svg>
            </span>
            <input
              v-model="loginForm.username"
              type="text"
              placeholder="아이디"
              class="eeum-input pl-12 pr-4"
              style="border-radius: var(--radius-xl);"
              autocomplete="username"
              required
            />
          </div>
        </div>

        <div class="mb-4">
          <div class="relative">
            <span class="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400">
              <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
              </svg>
            </span>
            <input
              v-model="loginForm.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="비밀번호"
              class="eeum-input pl-12 pr-12"
              style="border-radius: var(--radius-xl);"
              autocomplete="current-password"
              required
            />
            <button
              type="button"
              @click="togglePassword"
              class="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              aria-label="비밀번호 표시 토글"
            >
              <svg v-if="!showPassword" class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/>
              </svg>
              <svg v-else class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
              </svg>
            </button>
          </div>
        </div>

        <div class="flex items-center justify-between mb-6">
          <label class="flex items-center cursor-pointer select-none">
            <input
              v-model="loginForm.rememberMe"
              type="checkbox"
              class="w-5 h-5 text-orange-500 border-gray-300 rounded focus:ring-orange-500"
            />
            <span class="ml-2 eeum-sub">로그인 유지</span>
          </label>
          <button type="button" class="text-sm font-medium" style="color: var(--color-primary);" @click="handleForgotPassword">
            계정/비밀번호 찾기
          </button>
        </div>

        <button
          type="submit"
          :disabled="isLoading"
          class="eeum-btn-primary"
        >
          <span v-if="!isLoading">로그인</span>
          <span v-else class="flex items-center justify-center gap-2">
            <svg class="animate-spin h-5 w-5" viewBox="0 0 24 24" aria-hidden="true">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            로그인 중...
          </span>
        </button>
      </form>

      <div class="text-center mt-6">
        <span class="eeum-sub">아직 회원이 아니신가요? </span>
        <button type="button" class="font-semibold text-sm" style="color: var(--color-primary);" @click="handleSignup">
          회원가입
        </button>
      </div>

      <div v-if="errorMessage" class="mt-4 bg-red-50 border-l-4 border-red-500 p-4 rounded-lg">
        <div class="flex items-center">
          <svg class="w-5 h-5 text-red-500 mr-2" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
          </svg>
          <p class="text-red-700 text-sm">{{ errorMessage }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import apiClient from '@/services/api'
import logoUrl from '@/assets/eeum_logo2.png'

const router = useRouter()

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: false,
})

const showPassword = ref(false)
const isLoading = ref(false)
const errorMessage = ref('')

const togglePassword = () => {
  showPassword.value = !showPassword.value
}

const handleKakaoLogin = () => {
  const BACKEND_URL = 'https://i14a105.p.ssafy.io/api/auth/login/social/kakao'
  window.location.href = BACKEND_URL
}

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    errorMessage.value = '아이디와 비밀번호를 입력해주세요.'
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await apiClient.post('/auth/login', {
      email: loginForm.username,
      password: loginForm.password
    }, {
      withCredentials: false
    })

    const { accessToken, refreshToken } = response.data
    
    // 로그인 유지 체크 여부에 따라 스토리지 분기
    if (loginForm.rememberMe) {
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', refreshToken)
      sessionStorage.removeItem('accessToken')
      sessionStorage.removeItem('refreshToken')
    } else {
      sessionStorage.setItem('accessToken', accessToken)
      sessionStorage.setItem('refreshToken', refreshToken)
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
    }
    
    // axios 헤더에 토큰 설정
    apiClient.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`
    
    // 홈 화면으로 이동
    router.push('/home')
  } catch (e) {
    console.error(e)
    const msg = e.response?.data?.message
    if (msg) {
      errorMessage.value = msg
    } else if (e.response?.status === 401) {
      errorMessage.value = '이메일 또는 비밀번호가 일치하지 않습니다.'
    } else {
      errorMessage.value = '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'
    }
  } finally {
    isLoading.value = false
  }
}

const handleForgotPassword = () => {
  router.push('/find-account')
}

const handleSignup = () => {
  location.href = '#/signup'
}
</script>

<style scoped>
/* main.css의 변수/유틸리티를 상속받아 쓰되, 필요한 경우 재정의 */
.login-container {
  background: linear-gradient(135deg, #ffffff 0%, var(--color-primary-light) 100%);
}
</style>
