<template>
  <transition name="fade">
    <div
      v-if="show"
      class="fixed inset-0 bg-black/50 z-[100] backdrop-blur-sm flex items-center justify-center p-6"
      @click.self="$emit('cancel')"
    >
      <transition name="pop">
        <div
          v-if="show"
          class="bg-white w-full max-w-sm rounded-[2rem] overflow-hidden shadow-2xl"
        >
          <div class="px-8 pt-10 pb-8 text-center">
            <!-- Icon/Visual -->
            <div class="w-16 h-16 bg-red-50 text-[var(--color-primary)] rounded-full flex items-center justify-center mx-auto mb-6">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
            </div>

            <h3 class="text-xl font-bold text-gray-900 mb-2">{{ title }}</h3>
            <p class="text-gray-500 font-medium leading-relaxed break-keep">
              {{ message }}
            </p>
          </div>

          <div class="flex border-t border-gray-100">
            <button
              @click="$emit('confirm')"
              class="flex-1 py-5 text-[var(--color-primary)] font-black hover:bg-orange-50 transition-all active:scale-95"
            >
              확인
            </button>
            <div class="w-[1px] bg-gray-100"></div>
            <button
              @click="$emit('cancel')"
              class="flex-1 py-5 text-gray-400 font-bold hover:bg-gray-50 transition-colors"
            >
              취소
            </button>
          </div>
        </div>
      </transition>
    </div>
  </transition>
</template>

<script setup>
defineProps({
  show: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
    default: '알림'
  },
  message: {
    type: String,
    default: '정말로 진행하시겠습니까?'
  }
});

defineEmits(['confirm', 'cancel']);
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
