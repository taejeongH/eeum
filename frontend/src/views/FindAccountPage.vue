<template>
  <div
    class="min-h-screen flex items-center justify-center"
    style="background: linear-gradient(135deg, #ffffff 0%, var(--color-primary-soft) 100%)"
  >
    <div class="w-full max-w-md px-6 py-10">
      <!-- 헤더 영역 -->
      <div class="text-center mb-8">
        <h1 class="text-3xl font-extrabold mb-2" style="color: var(--color-primary)">
          계정/비밀번호 찾기
        </h1>
        <p class="eeum-sub">잃어버린 계정 정보를 확인해보세요</p>
      </div>

      <div class="bg-white p-8 rounded-2xl shadow-xl border border-gray-100">
        <!-- 상단 탭 메뉴 -->
        <div class="flex border-b border-gray-200 mb-6">
          <button
            @click="activeTab = 'findEmail'"
            class="flex-1 pb-3 text-sm font-medium transition-colors relative"
            :class="activeTab === 'findEmail' ? 'font-bold' : 'text-gray-400 hover:text-gray-600'"
            :style="activeTab === 'findEmail' ? { color: 'var(--color-primary)' } : {}"
          >
            이메일 찾기
            <div
              v-if="activeTab === 'findEmail'"
              class="absolute bottom-0 left-0 w-full h-0.5"
              style="background-color: var(--color-primary)"
            ></div>
          </button>
          <button
            @click="activeTab = 'resetPassword'"
            class="flex-1 pb-3 text-sm font-medium transition-colors relative"
            :class="
              activeTab === 'resetPassword' ? 'font-bold' : 'text-gray-400 hover:text-gray-600'
            "
            :style="activeTab === 'resetPassword' ? { color: 'var(--color-primary)' } : {}"
          >
            비밀번호 재설정
            <div
              v-if="activeTab === 'resetPassword'"
              class="absolute bottom-0 left-0 w-full h-0.5"
              style="background-color: var(--color-primary)"
            ></div>
          </button>
        </div>

        <!-- 탭 1: 이메일 찾기 -->
        <div v-if="activeTab === 'findEmail'" class="animate-fade-in">
          <form @submit.prevent="handleFindEmail" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">이름</label>
              <input
                v-model="emailForm.name"
                type="text"
                class="eeum-input"
                placeholder="홍길동"
                required
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">휴대폰 번호</label>
              <input
                v-model="emailForm.phone"
                type="tel"
                class="eeum-input"
                placeholder="010-0000-0000"
                @input="formatPhoneNumber"
                maxlength="13"
                required
              />
            </div>

            <button
              type="submit"
              :disabled="loading"
              class="w-full mt-4 py-3 rounded-xl font-bold text-white transition-all transform active:scale-95 shadow-md hover:shadow-lg disabled:opacity-50 disabled:cursor-not-allowed"
              style="background-color: var(--color-primary)"
              @mouseover="$event.target.style.filter = 'brightness(0.9)'"
              @mouseleave="$event.target.style.filter = 'brightness(1)'"
            >
              <span v-if="!loading">이메일 찾기</span>
              <span v-else>확인 중...</span>
            </button>
          </form>

          <!-- 결과 표시 영역 -->
          <div
            v-if="foundEmail"
            class="mt-6 p-4 bg-orange-50 rounded-xl border border-orange-100 text-center animate-fade-in"
          >
            <p class="text-sm text-gray-600 mb-1">회원님의 이메일은</p>
            <p class="text-lg font-bold" style="color: var(--color-primary)">{{ foundEmail }}</p>
            <p class="text-sm text-gray-600 mt-1">입니다.</p>
          </div>
        </div>

        <!-- 탭 2: 비밀번호 재설정 -->
        <div v-if="activeTab === 'resetPassword'" class="animate-fade-in">
          <!-- 성공 상태 표시 -->
          <div v-if="resetSuccess" class="text-center py-6">
            <div
              class="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4 text-3xl"
            >
              ✅
            </div>
            <h3 class="text-lg font-bold text-gray-900 mb-2">비밀번호 변경 완료</h3>
            <p class="text-gray-500 text-sm mb-6">새로운 비밀번호로 로그인해주세요.</p>

            <button
              @click="$router.replace('/login')"
              class="w-full py-3 rounded-xl font-bold text-white transition-colors"
              style="background-color: var(--color-primary)"
              @mouseover="$event.target.style.filter = 'brightness(0.9)'"
              @mouseleave="$event.target.style.filter = 'brightness(1)'"
            >
              로그인하러 가기
            </button>
          </div>

          <div v-else>
            <!-- 1단계: 본인 인증 -->
            <div v-if="resetStep === 1" class="space-y-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">이메일</label>
                <div class="flex gap-2">
                  <input
                    v-model="resetForm.email"
                    type="email"
                    class="eeum-input flex-1"
                    placeholder="example@email.com"
                    :disabled="codeSent"
                    @keyup.enter="handleSendCode"
                    required
                  />
                  <button
                    type="button"
                    @click="handleSendCode"
                    :disabled="loading || codeSent"
                    class="px-4 py-2 rounded-lg text-sm font-medium transition-colors"
                    :class="codeSent ? 'bg-gray-100 text-gray-400' : ''"
                    :style="
                      !codeSent
                        ? {
                            backgroundColor: 'var(--color-primary-soft)',
                            color: 'var(--color-primary)',
                          }
                        : {}
                    "
                  >
                    {{ codeSent ? '전송 완료' : '전송' }}
                  </button>
                </div>
              </div>

              <div v-if="codeSent" class="space-y-4 animate-fade-in text-left">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">인증번호</label>
                  <div class="flex gap-2">
                    <input
                      v-model="resetForm.code"
                      type="text"
                      class="eeum-input flex-1"
                      placeholder="6자리 코드"
                      @keyup.enter="handleVerifyCode"
                      required
                    />
                    <button
                      type="button"
                      @click="handleVerifyCode"
                      :disabled="loading"
                      class="px-4 py-2 text-white rounded-lg text-sm font-medium transition-colors"
                      style="background-color: var(--color-primary)"
                      @mouseover="$event.target.style.filter = 'brightness(0.9)'"
                      @mouseleave="$event.target.style.filter = 'brightness(1)'"
                    >
                      확인
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 2단계: 새 비밀번호 입력 -->
            <form
              v-else-if="resetStep === 2"
              @submit.prevent="handleResetPassword"
              class="space-y-4"
            >
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">새 비밀번호</label>
                <input
                  v-model="resetForm.newPassword"
                  type="password"
                  class="eeum-input"
                  placeholder="새 비밀번호 입력"
                  required
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">비밀번호 확인</label>
                <input
                  v-model="resetForm.confirmPassword"
                  type="password"
                  class="eeum-input"
                  placeholder="새 비밀번호 확인"
                  required
                />
              </div>

              <button
                type="submit"
                :disabled="loading"
                class="w-full mt-6 py-3 rounded-xl font-bold text-white transition-all transform active:scale-95 shadow-md hover:shadow-lg disabled:opacity-50"
                style="background-color: var(--color-primary)"
                @mouseover="$event.target.style.filter = 'brightness(0.9)'"
                @mouseleave="$event.target.style.filter = 'brightness(1)'"
              >
                비밀번호 변경하기
              </button>
            </form>
          </div>
        </div>

        <div
          v-if="message && !resetSuccess"
          class="mt-4 text-center text-sm font-medium bg-red-50 p-3 rounded-lg text-red-600 animate-pulse"
        >
          {{ message }}
        </div>
      </div>

      <div class="text-center mt-6">
        <button
          @click="$router.push('/login')"
          class="text-gray-500 text-sm hover:text-gray-700 font-medium"
        >
          로그인 페이지로 돌아가기
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import {
  findEmail,
  sendPasswordResetCode,
  verifyPasswordResetCode,
  resetPassword,
} from '@/services/api';

const router = useRouter();

const activeTab = ref('findEmail');
const loading = ref(false);
const message = ref('');
const isError = ref(false);

const emailForm = reactive({ name: '', phone: '' });
const foundEmail = ref('');

const resetStep = ref(1);
const codeSent = ref(false);
const resetSuccess = ref(false);
const resetForm = reactive({ email: '', code: '', newPassword: '', confirmPassword: '' });

/**
 * 휴대폰 번호 입력 시 자동으로 하이픈(-)을 추가합니다.
 * @param {Event} e
 */
const formatPhoneNumber = (e) => {
  let value = e.target.value.replace(/[^0-9]/g, '');
  if (value.length > 11) value = value.slice(0, 11);

  if (value.length > 3 && value.length <= 7) {
    value = value.replace(/(\d{3})(\d{1,4})/, '$1-$2');
  } else if (value.length >= 8) {
    value = value.replace(/(\d{3})(\d{3,4})(\d{1,4})/, '$1-$2-$3');
  }

  emailForm.phone = value;
};

/**
 * 이름과 전화번호를 사용하여 가입된 이메일을 찾습니다.
 */
const handleFindEmail = async () => {
  loading.value = true;
  message.value = '';
  foundEmail.value = '';

  try {
    const res = await findEmail({ ...emailForm });
    foundEmail.value = res.data;
  } catch (err) {
    message.value = err.response?.data?.message || '사용자를 찾을 수 없습니다.';
  } finally {
    loading.value = false;
  }
};

/**
 * 비밀번호 재설정을 위한 인증 코드를 이메일로 전송합니다.
 */
const handleSendCode = async () => {
  if (!resetForm.email) return;
  loading.value = true;
  message.value = '';

  try {
    await sendPasswordResetCode(resetForm.email);
    codeSent.value = true;
  } catch (err) {
    message.value = err.response?.data?.message || '이메일 발송에 실패했습니다.';
  } finally {
    loading.value = false;
  }
};

/**
 * 입력된 비밀번호 재설정 인증 코드를 검증합니다.
 */
const handleVerifyCode = async () => {
  if (!resetForm.code) return;
  loading.value = true;
  message.value = '';

  try {
    await verifyPasswordResetCode(resetForm.email, resetForm.code);
    resetStep.value = 2;
  } catch (err) {
    message.value = '인증번호가 올바르지 않습니다.';
  } finally {
    loading.value = false;
  }
};

/**
 * 새로운 비밀번호로 계정 비밀번호를 업데이트합니다.
 */
const handleResetPassword = async () => {
  if (!validateResetPasswords()) return;

  loading.value = true;
  message.value = '';

  try {
    await resetPassword(resetForm.email, resetForm.newPassword);
    resetSuccess.value = true;
  } catch (err) {
    message.value = '비밀번호 변경에 실패했습니다.';
  } finally {
    loading.value = false;
  }
};

/**
 * 비밀번호 재설정 폼 유효성을 검사합니다. (Internal)
 * @returns {boolean}
 */
const validateResetPasswords = () => {
  if (resetForm.newPassword === resetForm.confirmPassword) return true;
  message.value = '비밀번호가 일치하지 않습니다.';
  return false;
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
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
