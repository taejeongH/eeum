<template>
  <div>
    <h2 class="eeum-title">
      매일 잊지 않고 챙길 수 있도록<br />
      <span class="text-[var(--color-primary)]">복약 정보</span>를 입력해주세요
    </h2>

    <p class="eeum-sub mt-3">
      복약 시간에 알림을 보내고,<br />
      필요 시 가족들에게도 공유돼요.
    </p>

    <!-- Progress -->
    <div class="mt-6">
      <div class="h-2 w-full rounded-full bg-[var(--color-primary-soft)]">
        <div class="h-2 w-full rounded-full bg-[var(--color-primary)]"></div>
      </div>
      <p class="mt-2 text-xs text-[var(--color-primary)]">
        단계 4 / 4 · 복약 정보 입력
      </p>
    </div>

    <!-- Medication Card -->
    <div
      v-for="(med, i) in medications"
      :key="i"
      class="mt-6 p-4 rounded-xl border shadow-sm"
    >
      <span class="text-xs px-2 py-1 rounded-full text-white bg-[var(--color-primary)]">
        {{ med.type }}
      </span>
      <h3 class="mt-2 font-semibold">{{ med.name }}</h3>
      <p class="text-sm text-gray-500 mt-1">
        {{ med.time }} · {{ med.repeat }}
      </p>
    </div>

    <!-- Add -->
    <button
      class="mt-6 w-full py-4 rounded-xl border border-dashed text-sm text-gray-500"
      @click="addMedication"
    >
      + 약 추가하기
    </button>

    <!-- CTA -->
    <div class="mt-10">
      <button class="eeum-btn-primary" @click="complete">
        설정 완료하기 ✓
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useGroupSetupStore } from '@/stores/groupSetup'
import { storeToRefs } from 'pinia'

const router = useRouter()
const route = useRoute()
const setupStore = useGroupSetupStore()
const familyId = route.params.familyId

const { medications } = storeToRefs(setupStore)

onMounted(() => {
  if (familyId) {
    setupStore.initData(familyId)
  }
})

const addMedication = () => {
  alert('복약 추가 모달 연결 예정')
}

const complete = () => {
  // TODO: Submit all data in setupStore to the server
  // e.g. await api.put(`/families/${familyId}`, { ...setupStore.state })
  // For now, just finish and clear.
  
  console.log('Group Setup Completed:', {
      name: setupStore.groupName,
      seniorId: setupStore.seniorId,
      bloodType: setupStore.bloodType,
      diseases: setupStore.diseases,
      contacts: setupStore.contactSlots,
      medications: setupStore.medications
  })
  
  // Clear temporary store
  setupStore.reset()
  
  router.push('/home')
}
</script>
