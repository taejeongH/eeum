<template>
  <div class="min-h-screen flex items-center justify-center" style="background: linear-gradient(135deg, #ffffff 0%, var(--color-primary-soft) 100%);">
    <div class="w-full max-w-md px-6 py-10">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-extrabold mb-2" style="color: var(--color-primary);">회원가입</h1>
        <p class="eeum-sub">이음과 함께 가족 돌봄을 시작해보세요</p>
      </div>

      <div class="bg-white p-8 rounded-2xl shadow-xl border border-gray-100">
        <form @submit.prevent="handleSignup">
          <!-- 이메일 입력 -->
          <div class="mb-4">
            <label class="block text-sm font-medium text-gray-700 mb-1">이메일</label>
            <div class="flex gap-2">
              <input
                v-model="form.email"
                type="email"
                placeholder="example@email.com"
                class="eeum-input flex-1"
                :disabled="isCodeSent || isEmailVerified"
                required
              />
              <button
                type="button"
                @click="sendCode"
                :disabled="isCodeSent || isEmailVerified || isLoading"
                class="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
                :class="isCodeSent ? 'bg-gray-100 text-gray-400' : 'bg-orange-100 text-orange-600 hover:bg-orange-200'"
              >
                {{ isCodeSent ? '전송됨' : '인증번호 전송' }}
              </button>
            </div>
          </div>

          <!-- 인증 코드 입력 (이메일 전송 후 표시) -->
          <div v-if="isCodeSent && !isEmailVerified" class="mb-6 animate-fade-in">
            <label class="block text-sm font-medium text-gray-700 mb-1">인증 코드</label>
            <div class="flex gap-2">
              <input
                v-model="form.code"
                type="text"
                placeholder="6자리 코드 입력"
                class="eeum-input flex-1"
                maxlength="6"
              />
              <button
                type="button"
                @click="verifyCode"
                :disabled="isLoading"
                class="px-4 py-2 bg-orange-500 text-white rounded-lg text-sm font-medium hover:bg-orange-600 transition-colors"
              >
                확인
              </button>
            </div>
          </div>

          <!-- 인증 완료 메시지 -->
          <div v-if="isEmailVerified" class="mb-6 p-3 bg-green-50 text-green-700 text-sm rounded-lg flex items-center gap-2">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
            </svg>
            이메일 인증이 완료되었습니다.
          </div>

          <!-- 이름 & 비밀번호 (인증 후 표시) -->
          <div v-if="isEmailVerified" class="space-y-4 animate-fade-in">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">이름</label>
              <input
                v-model="form.name"
                type="text"
                placeholder="홍길동"
                class="eeum-input w-full"
                required
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
              <input
                v-model="form.password"
                type="password"
                placeholder="비밀번호 입력"
                class="eeum-input w-full"
                required
              />
            </div>
            
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호 확인</label>
              <input
                v-model="form.passwordConfirm"
                type="password"
                placeholder="비밀번호 재입력"
                class="eeum-input w-full"
                required
              />
            </div>
          </div>

          <!-- 에러 메시지 -->
          <div v-if="errorMessage" class="mt-4 text-red-600 text-sm bg-red-50 p-3 rounded-lg">
            {{ errorMessage }}
          </div>

          <!-- 회원가입 버튼 -->
          <button
            type="submit"
            :disabled="!isEmailVerified || isLoading"
            class="w-full mt-8 py-3 rounded-xl font-bold text-white transition-all transform active:scale-95"
            :class="isEmailVerified ? 'bg-orange-500 hover:bg-orange-600 shadow-md hover:shadow-lg' : 'bg-gray-300 cursor-not-allowed'"
          >
            <span v-if="isLoading">처리 중...</span>
            <span v-else>회원가입 완료</span>
          </button>
        </form>
      </div>

      <div class="text-center mt-6">
        <span class="text-gray-500 text-sm">이미 계정이 있으신가요? </span>
        <router-link to="/login" class="font-semibold text-sm text-orange-600 hover:text-orange-700">
          로그인하기
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import apiClient from '@/services/api';

const router = useRouter();

const form = reactive({
  email: '',
  code: '',
  name: '',
  password: '',
  passwordConfirm: ''
});

const isCodeSent = ref(false);
const isEmailVerified = ref(false);
const isLoading = ref(false);
const errorMessage = ref('');

// 이메일 인증 코드 전송
const sendCode = async () => {
  if (!form.email) {
    errorMessage.value = '이메일을 입력해주세요.';
    return;
  }
  
  isLoading.value = true;
  errorMessage.value = '';

  try {
    await apiClient.post('/auth/email/code', { email: form.email }, { withCredentials: false });
    isCodeSent.value = true;
    alert('인증 코드가 전송되었습니다. 이메일을 확인해주세요.');
  } catch (e) {
    errorMessage.value = e.response?.data?.message || '인증 코드 전송에 실패했습니다.';
  } finally {
    isLoading.value = false;
  }
};

// 인증 코드 검증
const verifyCode = async () => {
  if (!form.code) {
    errorMessage.value = '인증 코드를 입력해주세요.';
    return;
  }

  isLoading.value = true;
  errorMessage.value = '';

  try {
    await apiClient.post('/auth/email/verify', { email: form.email, code: form.code }, { withCredentials: false });
    isEmailVerified.value = true;
  } catch (e) {
    errorMessage.value = e.response?.data?.message || '인증번호가 올바르지 않습니다.';
  } finally {
    isLoading.value = false;
  }
};

// 비밀번호 유효성 검사 (8자 이상, 영문/숫자/특수문자 포함)
const validatePassword = (password) => {
  const hasLetter = /[a-zA-Z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(password);
  const isValidLength = password.length >= 8;
  return hasLetter && hasNumber && hasSpecial && isValidLength;
};

// 회원가입 요청
const handleSignup = async () => {
  if (!validatePassword(form.password)) {
    errorMessage.value = '비밀번호는 8자 이상이어야 하며, 영문, 숫자, 특수문자를 모두 포함해야 합니다.';
    return;
  }

  if (form.password !== form.passwordConfirm) {
    errorMessage.value = '비밀번호가 일치하지 않습니다.';
    return;
  }

  if (!isEmailVerified.value) {
    errorMessage.value = '이메일 인증을 완료해주세요.';
    return;
  }

  isLoading.value = true;
  errorMessage.value = '';

  try {
    await apiClient.post('/auth/signup', {
      email: form.email,
      password: form.password,
      name: form.name
    }, { withCredentials: false });
    
    alert('회원가입이 완료되었습니다. 로그인해주세요.');
    router.push('/login');
  } catch (e) {
    errorMessage.value = e.response?.data?.message || '회원가입에 실패했습니다.';
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped>
.eeum-input {
  @apply w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-orange-200 focus:border-orange-400 outline-none transition-all duration-200 bg-gray-50 focus:bg-white;
}

.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
