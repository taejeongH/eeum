<template>
  <header class="flex items-center h-14 px-4 border-b bg-white">
    <!-- 뒤로가기 -->
    <button
      @click="goBack"
      class="p-2 -ml-2 rounded-full hover:bg-gray-100 transition-colors"
    >
      <svg class="w-6 h-6 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
      </svg>
    </button>

    <!-- 타이틀 -->
    <h1 class="absolute left-1/2 -translate-x-1/2 text-lg font-semibold">
      그룹 설정
    </h1>

    <!-- step 표시 -->
    <span class="ml-auto text-sm text-gray-400">
      {{ step }} / 4
    </span>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  step: {
    type: Number,
    required: true,
  },
})

const router = useRouter()

const goBack = () => {
  router.back()
}
</script>
