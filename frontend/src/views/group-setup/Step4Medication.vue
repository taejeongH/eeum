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

    <!-- Medication List -->
    <div v-if="medications.length > 0" class="space-y-3 mt-6">
      <div
        v-for="(med, i) in medications"
        :key="i"
        class="p-5 rounded-2xl border border-gray-100 bg-white shadow-sm flex justify-between items-center group hover:shadow-md transition-all"
      >
        <div>
          <div class="flex items-center gap-2 mb-1">
             <span class="text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider"
              :class="{
                'bg-blue-100 text-blue-600': med.cycleType === 'DAILY',
                'bg-green-100 text-green-600': med.cycleType === 'WEEKLY'
              }"
             >
              {{ getCycleLabel(med.cycleType) }}
             </span>
             <h3 class="font-bold text-[var(--text-title)]">{{ med.medicineName }}</h3>
          </div>
          
          <div class="text-sm text-gray-500 flex items-center gap-2">
            <span>{{ med.notificationTimes.length }}회 복용</span>
            <span class="w-1 h-1 rounded-full bg-gray-300"></span>
            <span class="truncate max-w-[150px]">{{ formatTimes(med.notificationTimes) }}</span>
          </div>
        </div>
        
        <button @click="removeMedication(i)" class="p-2 text-gray-300 hover:text-red-500 transition">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
        </button>
      </div>
    </div>
    
    <!-- Empty State -->
    <div v-else class="mt-8 py-10 text-center bg-gray-50 rounded-2xl border border-dashed border-gray-200">
        <p class="text-gray-400 text-sm">등록된 복약 정보가 없습니다.</p>
    </div>

    <!-- Add Button -->
    <button
      class="mt-4 w-full py-4 rounded-xl border border-dashed border-gray-300 text-sm font-medium text-gray-500 hover:text-[var(--color-primary)] hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-soft)] transition-all flex items-center justify-center gap-2"
      @click="openModal"
    >
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
      약 추가하기
    </button>

    <!-- CTA -->
    <div class="mt-10">
      <button class="eeum-btn-primary" @click="complete">
        설정 완료하기 ✓
      </button>
    </div>

    <MedicationAddModal 
      :show="isModalOpen" 
      @close="closeModal"
      @add-medication="handleAddMedication"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useGroupSetupStore } from '@/stores/groupSetup'
import { useModalStore } from '@/stores/modal'
import { storeToRefs } from 'pinia'
import MedicationAddModal from './MedicationAddModal.vue'

const router = useRouter()
const route = useRoute()
const setupStore = useGroupSetupStore()
const modalStore = useModalStore()
const familyId = route.params.familyId

const { medications } = storeToRefs(setupStore)
const isModalOpen = ref(false)

onMounted(() => {
  if (familyId) {
    setupStore.initData(familyId)
  }
})

const openModal = () => isModalOpen.value = true
const closeModal = () => isModalOpen.value = false

const handleAddMedication = (medData) => {
  setupStore.addMedication(medData)
  closeModal()
}

const removeMedication = (index) => {
  setupStore.removeMedication(index)
}

const getCycleLabel = (type) => {
    const map = { 'DAILY': '매일', 'WEEKLY': '매주' };
    return map[type] || type;
}

const formatTimes = (times) => {
    if(!times || times.length === 0) return '';
    return times.join(', ');
}

const complete = async () => {
  try {
    await setupStore.saveData(familyId)
    await modalStore.openAlert('그룹 설정이 저장되었습니다.')
    
    // Clear temporary store
    setupStore.reset()
    router.push('/home')
    
  } catch (error) {
    await modalStore.openAlert('저장에 실패했습니다. 메인으로 이동합니다.')
    setupStore.reset()
    router.push('/home')
  }
}
</script>
