<template>
  <header class="flex items-center h-14 px-4 border-b bg-white">
    <!-- 뒤로가기 -->
    <button
      @click="goBack"
      class="mr-3 text-gray-600 hover:text-gray-900"
    >
      ←
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
