<template>
  <div class="min-h-screen flex flex-col">
    <!-- ✅ step → currentStep 로 수정 -->
    <GroupSetupHeader :step="currentStep" />

    <main class="flex-1 px-5 pt-6">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GroupSetupHeader from './GroupSetupHeader.vue'

const route = useRoute()

const currentStep = computed(() => {
  if (route.name === 'GroupEditStep1') return 1
  if (route.name === 'GroupEditStep2') return 2
  if (route.name === 'GroupEditStep3') return 3
  return 1 // fallback
})
</script>
