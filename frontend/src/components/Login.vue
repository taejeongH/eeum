<template>
  <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-orange-50 to-orange-100">
    <div class="w-full max-w-md px-6">
      <!-- 로고 및 타이틀 -->
      <div class="text-center mb-8 animate-fade-in-down">
        <div class="inline-block bg-gradient-to-br from-orange-400 to-orange-500 p-8 rounded-3xl shadow-2xl mb-6">
          <svg class="w-20 h-20 text-white" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/>
          </svg>
        </div>
        <h1 class="text-5xl font-bold text-orange-500 mb-2">이음</h1>
        <p class="text-gray-500 text-sm">마음을 잇는 가족 돌봄</p>
      </div>

      <!-- 카카오 로그인 버튼 -->
      <button 
        @click="handleKakaoLogin"
        class="w-full py-4 px-6 bg-yellow-400 hover:bg-yellow-500 rounded-full font-semibold text-lg mb-6 flex items-center justify-center gap-3 shadow-lg transition-all transform hover:-translate-y-1"
      >
        <svg class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
          <path d="M12 3c5.799 0 10.5 3.664 10.5 8.185 0 4.52-4.701 8.184-10.5 8.184a13.5 13.5 0 0 1-1.727-.11l-4.408 2.883c-.501.265-.678.236-.472-.413l.892-3.678c-2.88-1.46-4.785-3.99-4.785-6.866C1.5 6.665 6.201 3 12 3z"/>
        </svg>
        카카오로 시작하기
      </button>

      <!-- 구분선 -->
      <div class="flex items-center mb-6">
        <div class="flex-1 border-t border-gray-300"></div>
        <span class="px-4 text-gray-400 text-sm">또는 이메일 로그인</span>
        <div class="flex-1 border-t border-gray-300"></div>
      </div>

      <!-- 로그인 폼 -->
      <div class="bg-white rounded-3xl shadow-xl p-8 animate-fade-in-up">
        <form @submit.prevent="handleLogin">
          <!-- 아이디 입력 -->
          <div class="mb-4">
            <div class="relative">
              <span class="absolute left-4 top-1/2 transform -translate-y-1/2 text-gray-400">
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                </svg>
              </span>
              <input 
                v-model="loginForm.username"
                type="text" 
                placeholder="아이디"
                class="w-full pl-12 pr-4 py-4 border-2 border-gray-200 rounded-2xl text-gray-700 focus:outline-none focus:border-orange-400 focus:ring-4 focus:ring-orange-100 transition-all"
                required
              >
            </div>
          </div>

          <!-- 비밀번호 입력 -->
          <div class="mb-4">
            <div class="relative">
              <span class="absolute left-4 top-1/2 transform -translate-y-1/2 text-gray-400">
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
                </svg>
              </span>
              <input 
                v-model="loginForm.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="비밀번호"
                class="w-full pl-12 pr-12 py-4 border-2 border-gray-200 rounded-2xl text-gray-700 focus:outline-none focus:border-orange-400 focus:ring-4 focus:ring-orange-100 transition-all"
                required
              >
              <button 
                @click="showPassword = !showPassword"
                type="button"
                class="absolute right-4 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                <svg v-if="!showPassword" class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/>
                </svg>
                <svg v-else class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 로그인 유지 & 비밀번호 찾기 -->
          <div class="flex items-center justify-between mb-6">
            <label class="flex items-center cursor-pointer">
              <input 
                v-model="loginForm.rememberMe"
                type="checkbox" 
                class="w-5 h-5 text-orange-500 border-gray-300 rounded focus:ring-orange-500"
              >
              <span class="ml-2 text-sm text-gray-600">로그인 유지</span>
            </label>
            <router-link 
              to="/forgot-password" 
              class="text-sm text-orange-500 hover:text-orange-600 font-medium"
            >
              비밀번호 찾기
            </router-link>
          </div>

          <!-- 로그인 버튼 -->
          <button 
            type="submit"
            :disabled="loading"
            class="w-full py-4 px-6 bg-orange-400 hover:bg-orange-500 rounded-full text-white font-semibold text-lg shadow-lg disabled:opacity-50 disabled:cursor-not-allowed transition-all transform hover:-translate-y-1"
          >
            <span v-if="!loading">로그인</span>
            <span v-else class="flex items-center justify-center gap-2">
              <svg class="animate-spin h-5 w-5" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              로그인 중...
            </span>
          </button>
        </form>

        <!-- 회원가입 링크 -->
        <div class="text-center mt-6">
          <span class="text-gray-600 text-sm">아직 회원이 아니신가요? </span>
          <router-link 
            to="/signup" 
            class="text-orange-500 hover:text-orange-600 font-semibold text-sm"
          >
            회원가입
          </router-link>
        </div>
      </div>

      <!-- 에러 메시지 -->
      <transition name="fade">
        <div v-if="errorMessage" class="mt-4 bg-red-50 border-l-4 border-red-500 p-4 rounded-lg">
          <div class="flex items-center">
            <svg class="w-5 h-5 text-red-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
            </svg>
            <p class="text-red-700 text-sm">{{ errorMessage }}</p>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/modules/auth'

const router = useRouter()
const authStore = useAuthStore()

const loginForm = ref({
  username: '',
  password: '',
  rememberMe: false
})

const showPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')

// 이메일 로그인
const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''
  
  try {
    await authStore.login(loginForm.value)
    router.push('/main')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '아이디 또는 비밀번호가 올바르지 않습니다.'
  } finally {
    loading.value = false
  }
}

// 카카오 로그인
const handleKakaoLogin = () => {
  const KAKAO_AUTH_URL = `https://kauth.kakao.com/oauth/authorize?client_id=${import.meta.env.VITE_KAKAO_API_KEY}&redirect_uri=${import.meta.env.VITE_KAKAO_REDIRECT_URI}&response_type=code`
  window.location.href = KAKAO_AUTH_URL
}
</script>

<style scoped>
@keyframes fade-in-down {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fade-in-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in-down {
  animation: fade-in-down 0.8s ease;
}

.animate-fade-in-up {
  animation: fade-in-up 0.8s ease;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>