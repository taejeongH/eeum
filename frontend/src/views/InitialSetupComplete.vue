<template>
  <div class="min-h-screen bg-white flex flex-col">
    <!-- Main Content Area (Truly Centered) -->
    <main class="flex-1 flex flex-col items-center justify-center px-8 text-center pt-10 pb-32">
      <!-- Success Animation/Icon -->
      <div class="mb-12 relative animate-fade-in">
        <div class="w-32 h-32 bg-orange-50 rounded-full flex items-center justify-center animate-pulse">
          <div class="w-24 h-24 bg-[var(--color-primary-soft)] rounded-full flex items-center justify-center animate-bounce-gentle">
            <svg class="w-12 h-12 text-[var(--color-primary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
            </svg>
          </div>
        </div>
        <!-- Decorative Sparkles -->
        <div class="absolute -top-2 -right-2 w-4 h-4 bg-yellow-400 rounded-full animate-ping"></div>
        <div class="absolute -bottom-4 -left-4 w-3 h-3 bg-orange-300 rounded-full animate-ping delay-300"></div>
      </div>

      <!-- Celebration Text -->
      <div class="space-y-4 animate-slide-up">
        <h1 class="text-3xl font-black text-gray-900 leading-tight">
          반가워요!<br/>
          가입을 축하드려요.
        </h1>
        <p class="text-gray-500 font-medium leading-relaxed break-keep px-4">
          회원님의 소중한 정보를 모두 등록했습니다.<br/>
          이제 <span class="text-[var(--color-primary)] font-bold">'이음'</span>으로 가족의 마음을 이어보세요.
        </p>
      </div>
    </main>

    <!-- Fixed Navigation Footer (Consistent with Wizard) -->
    <footer class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 px-6 pt-5 pb-8 z-40 shadow-[0_-4px_24px_rgba(0,0,0,0.04)]">
      <div class="max-w-md mx-auto">
        <button 
          @click="finishSetup"
          class="eeum-btn-primary !h-16 !text-xl !font-black"
        >
          시작하기 →
        </button>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';

const router = useRouter();

const finishSetup = () => {
  // Clear any setup-related state if needed
  router.replace('/home');
};
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.8s ease-out forwards;
}

.animate-slide-up {
  animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.animate-bounce-gentle {
  animation: bounceGentle 2s infinite ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes bounceGentle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}
</style>
