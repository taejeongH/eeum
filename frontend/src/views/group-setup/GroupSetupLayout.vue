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
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFamilyStore } from '@/stores/family'
import GroupSetupHeader from './GroupSetupHeader.vue'

const route = useRoute()
const router = useRouter()
const familyStore = useFamilyStore()

onMounted(async () => {
  const familyId = route.params.familyId
  
  if (!familyId) return

  // Ensure families are loaded
  if (familyStore.families.length === 0) {
      await familyStore.fetchFamilies()
  }

  const targetFamily = familyStore.families.find(f => String(f.id) === String(familyId))

  if (!targetFamily) {
      alert('존재하지 않는 그룹입니다.')
      router.replace('/home')
      return;
  }

  if (!targetFamily.owner) {
      alert('그룹 설정은 대표자만 가능합니다.')
      router.replace('/home')
  }
})

const currentStep = computed(() => {
  if (route.name === 'GroupEditStep1') return 1
  if (route.name === 'GroupEditStep2') return 2
  if (route.name === 'GroupEditStep3') return 3
  if (route.name === 'GroupEditStep4') return 4
  return 1 // fallback
})
</script>
