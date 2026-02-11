<script setup>
import { computed } from 'vue';
import { useSlideshowStore } from '@/stores/slideshow';

/**
 * 슬라이드쇼 스토어를 호출합니다.
 * @type {import('pinia').Store}
 */
const slideshowStore = useSlideshowStore();

/**
 * 현재 활성화된 슬라이드 정보를 가져옵니다.
 * @type {import('vue').ComputedRef<object|null>}
 */
const currentSlide = computed(() => slideshowStore.currentSlide);
</script>

<template>
  <div class="fixed inset-0 z-0 bg-black overflow-hidden select-none">
    <!-- 크로스페이드 전환 래퍼 (Transition Wrapper) -->
    <Transition name="fade" mode="in-out">
      <!-- 현재 슬라이드 컨테이너 -->
      <div v-if="currentSlide" :key="currentSlide.id" class="absolute inset-0 w-full h-full">
        <!-- 1. 배경 레이어: 화면을 가득 채우기 위해 흐리게(blur) 확대(scale) 처리 -->
        <div class="absolute inset-0 overflow-hidden">
          <img
            :src="currentSlide.url"
            class="w-full h-full object-cover blur-2xl scale-110 opacity-60 brightness-75 transform transition-transform duration-[10000ms] ease-linear hover:scale-125"
            alt="배경 블러 이미지"
          />
        </div>

        <!-- 2. 전경 레이어: 원본 비율을 유지하며 중앙에 배치 -->
        <div class="absolute inset-0 flex items-center justify-center p-8">
          <img
            :src="currentSlide.url"
            class="max-w-full max-h-full object-contain drop-shadow-2xl rounded-lg shadow-black/50"
            alt="슬라이드쇼 이미지"
          />
          <!-- 선택적: 메시지 오버레이 (하단 자막) -->
          <div
            v-if="currentSlide.message"
            class="absolute bottom-16 bg-black/50 backdrop-blur-md px-8 py-4 rounded-full text-white text-2xl font-medium tracking-wide border border-white/20"
          >
            {{ currentSlide.message }}
          </div>
        </div>
      </div>

      <!-- 대기 상태 (사진 없음) -->
      <div
        v-else
        class="absolute inset-0 flex flex-col items-center justify-center bg-gray-900 gap-4"
      >
        <div
          class="w-12 h-12 border-4 border-white/20 border-t-white/80 rounded-full animate-spin"
        ></div>
        <span class="text-white/50 text-2xl font-light tracking-widest">사진을 기다리는 중...</span>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* 부드러운 크로스페이드 (Crossfade) 전환 효과 */
/* 지속 시간을 길게 설정하여 자연스럽게 연출 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 1500ms ease-in-out;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-enter-to,
.fade-leave-from {
  opacity: 1;
}
</style>
