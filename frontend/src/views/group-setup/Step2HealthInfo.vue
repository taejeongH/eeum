<template>
  <div @keyup.enter="handleEnter">
    <h2 class="eeum-title">
      보살핌이 필요한<br />
      <span class="text-[var(--color-primary)]">가족 정보</span>를 입력해주세요
    </h2>

    <p class="eeum-sub mt-3">
      입력하신 정보는 위급 상황 시 의료진에게 공유돼요.
    </p>

    <!-- Progress -->
    <div class="mt-6">
      <div class="h-2 w-full rounded-full bg-[var(--color-primary-soft)]">
        <div class="h-2 w-1/2 rounded-full bg-[var(--color-primary)]"></div>
      </div>
      <p class="mt-2 text-xs text-[var(--color-primary)]">
        단계 2 / 4 · 건강 정보 입력
      </p>
    </div>

    <!-- Care Target -->
    <div class="mt-8">
      <label class="block text-sm font-medium mb-2">
        피부양자 <span class="text-[var(--color-primary)]">*</span>
      </label>

      <CareTargetSelect
        v-model="seniorId"
        :members="members"
      />
    </div>

    <!-- Blood Type -->
    <div class="mt-8">
      <label class="block text-sm font-medium mb-2">
        혈액형 <span class="text-[var(--color-primary)]">*</span>
      </label>

      <div class="grid grid-cols-4 gap-3">
        <button
          v-for="type in bloodTypes"
          :key="type"
          @click="bloodType = type"
          class="py-2 rounded-lg border text-sm transition"
          :class="bloodType === type
            ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
            : 'border-gray-200 text-gray-600'"
        >
          {{ type }}
        </button>
      </div>
    </div>

    <!-- Disease -->
    <div class="mt-8">
      <label class="block text-sm font-medium mb-2">
        기저질환
      </label>

      <input
        v-model="diseaseInput"
        @keyup.enter="addDisease"
        class="eeum-input"
        placeholder="입력 후 엔터를 눌러 추가"
      />

    <div class="flex flex-wrap gap-2 mt-3">
      <span
        v-for="(d, i) in diseases"
        :key="i"
        class="flex items-center gap-1 px-3 py-1 rounded-full text-xs text-white bg-[var(--color-primary)]"
      >
        {{ d }}
        <button
          @click="removeDisease(i)"
          class="ml-1 text-white/80 hover:text-white text-xs"
        >
          ✕
        </button>
      </span>
    </div>

    </div>

    <!-- CTA -->
    <div class="mt-10">
      <button
        class="eeum-btn-primary"
        :disabled="!seniorId || !bloodType"
        @click="goNext"
      >
        다음 단계로 →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '@/services/api' 
import { useGroupSetupStore } from '@/stores/groupSetup'
import { storeToRefs } from 'pinia'
import { Logger } from '@/services/logger'
import CareTargetSelect from '../../components/CareTargetSelect.vue'

const router = useRouter()
const route = useRoute()
const setupStore = useGroupSetupStore()


const { seniorId, bloodType, diseases } = storeToRefs(setupStore)

const familyId = computed(() => route.params.familyId)
const members = ref([])
const diseaseInput = ref('')
const bloodTypes = ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-']

const addDisease = () => {
  if (!diseaseInput.value) return
  diseases.value.push(diseaseInput.value)
  diseaseInput.value = ''
}

const removeDisease = (index) => {
  diseases.value.splice(index, 1)
}

onMounted(async () => {
  if (!familyId.value) return
  
  
  setupStore.initData(familyId.value)

  
  
  
  try {
    const membersRes = await api.get(`/families/${familyId.value}/members`)
    members.value = membersRes.data
  } catch (error) {
    Logger.error('멤버 목록 조회 실패:', error)
  }
})

const goNext = () => {
  router.push({
    name: 'GroupEditStep3',
    params: { familyId: familyId.value },
  })
}

const handleEnter = () => {
  if (seniorId.value && bloodType.value) {
    goNext()
  }
}
</script>
