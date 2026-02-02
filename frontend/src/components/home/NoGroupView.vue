<template>
  <div class="flex-1 flex flex-col items-center justify-center px-6 pb-20 animate-fade-in -mt-16">
    <!-- Illustration / Icon Area -->
    <div class="w-24 h-24 bg-orange-50 rounded-full flex items-center justify-center mb-8 shadow-inner">
      <svg class="w-12 h-12 text-[var(--color-primary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
      </svg>
    </div>

    <!-- Message Area -->
    <div class="text-center mb-12">
      <h2 class="text-2xl font-extrabold text-gray-900 mb-4 tracking-tight">함께하는 돌봄의 시작</h2>
      <p class="text-gray-500 leading-relaxed word-break-keep">
        아직 소속된 가족 그룹이 없습니다.<br>
        새로운 그룹을 만들거나 초대 코드로 참여해보세요.
      </p>
    </div>

    <!-- Call to Action Buttons -->
    <div class="w-full space-y-4 max-w-sm">
      <button 
        @click="emit('create')"
        class="eeum-btn-primary flex items-center justify-center gap-2 shadow-lg shadow-orange-100 active:scale-[0.98] transition-transform"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
        </svg>
        <span>우리 가족 그룹 만들기</span>
      </button>

      <button 
        @click="emit('join')"
        class="w-full h-14 bg-white border-2 border-gray-100 rounded-2xl font-bold text-gray-700 hover:border-[var(--color-primary-soft)] hover:bg-orange-50/30 transition-all active:scale-[0.98]"
      >
        초대 코드로 참여하기
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const emit = defineEmits(['create', 'join']);
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.8s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.word-break-keep {
  word-break: keep-all;
}
</style>
