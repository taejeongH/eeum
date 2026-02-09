<template>
  <div class="fixed inset-0 z-0 bg-black overflow-hidden">
    <!-- Using TransitionGroup or specific key logic to crossfade -->
    <Transition name="fade">
      <div v-if="currentSlide" :key="currentSlide.id" class="absolute inset-0 w-full h-full">
        
        <!-- Background Layer: Blurred & Zoomed to fill -->
        <div class="absolute inset-0 overflow-hidden">
             <img
              :src="currentSlide.url"
              class="w-full h-full object-cover blur-2xl scale-110 opacity-60 brightness-75"
              alt="Background Blur"
            />
        </div>

        <!-- Foreground Layer: Contained Image -->
        <div class="absolute inset-0 flex items-center justify-center p-8"> <!-- p-8 adds margin -->
            <img
              :src="currentSlide.url"
              class="max-w-full max-h-full object-contain drop-shadow-2xl rounded-lg shadow-black/50"
              alt="Slideshow Image"
            />
        </div>

      </div>
      <div v-else class="absolute inset-0 flex items-center justify-center bg-gray-900">
        <span class="text-white/50 text-2xl">대기 중...</span>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useSlideshowStore } from '@/stores/slideshow'

const store = useSlideshowStore()
const currentSlide = computed(() => store.currentSlide)
</script>

<style scoped>

.fade-enter-active,
.fade-leave-active {
  transition: opacity 2000ms ease-in-out;
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
