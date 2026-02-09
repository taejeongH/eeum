<template>
  <transition name="fade">
    <div
      v-if="store.isVisible"
      class="fixed inset-0 bg-black/50 z-[10000] backdrop-blur-sm flex items-center justify-center p-6"
      @click.self="handleBackdropClick"
    >
      <transition name="pop">
        <div
          v-if="store.isVisible"
          class="bg-white w-full max-w-sm rounded-[2rem] overflow-hidden shadow-2xl"
        >
          <div class="px-8 pt-10 pb-8 text-center">
            <!-- Icon/Visual -->
            <div class="w-16 h-16 bg-red-50 text-[var(--color-primary)] rounded-full flex items-center justify-center mx-auto mb-6">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>

            <h3 class="text-xl font-bold text-gray-900 mb-2">{{ store.title }}</h3>
            <p class="text-gray-500 font-medium leading-relaxed break-keep">
              {{ store.message }}
            </p>
          </div>

          <div class="flex border-t border-gray-100">
            <!-- Confirm Button (Always visible) -->
            <button
              @click="handleConfirm"
              class="flex-1 py-5 text-[var(--color-primary)] font-black hover:bg-orange-50 transition-all active:scale-95"
            >
              확인
            </button>
            
            <!-- Cancel Button (Only for confirm type) -->
            <template v-if="store.type === 'confirm'">
              <div class="w-[1px] bg-gray-100"></div>
              <button
                @click="handleCancel"
                class="flex-1 py-5 text-gray-400 font-bold hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
            </template>
          </div>
        </div>
      </transition>
    </div>
  </transition>
</template>

<script setup>
import { useModalStore } from '@/stores/modal';

const store = useModalStore();

const handleConfirm = () => {
  store.close(true);
};

const handleCancel = () => {
  store.close(false);
};

const handleBackdropClick = () => {
  
  if (store.type === 'alert') {
    store.close(true);
  } else {
    store.close(false);
  }
};
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.pop-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.pop-leave-active {
  transition: all 0.2s ease-in;
}
.pop-enter-from {
  opacity: 0;
  transform: scale(0.9) translateY(20px);
}
.pop-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>
