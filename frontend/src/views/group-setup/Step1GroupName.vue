<template>
  <div>
    <h2 class="eeum-title">
      가족을 하나로 잇는<br />
      <span class="text-[var(--color-primary)]">그룹 이름</span>을 입력해주세요
    </h2>

    <p class="eeum-sub mt-3">
      이름은 가족 구성원 모두에게 표시되며,<br />
      언제든지 변경할 수 있어요.
    </p>

    <!-- Progress -->
    <div class="mt-6">
      <div class="h-2 w-full rounded-full bg-[var(--color-primary-soft)]">
        <div class="h-2 w-1/4 rounded-full bg-[var(--color-primary)]"></div>
      </div>
      <p class="mt-2 text-xs text-[var(--color-primary)]">
        단계 1 / 4 · 기본 정보 입력
      </p>
    </div>

    <!-- Input -->
    <div class="mt-8">
      <label class="block text-sm font-medium mb-2">
        그룹 이름 <span class="text-[var(--color-primary)]">*</span>
      </label>
      <input
        v-model="groupName"
        class="eeum-input"
        placeholder="예) 우리 가족, 김○○ 가족"
      />
    </div>

    <!-- CTA -->
    <div class="mt-10">
      <button
        class="eeum-btn-primary"
        :disabled="!groupName"
        @click="goNext"
      >
        다음 단계로 →
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
const { groupName } = storeToRefs(setupStore)

const familyId = route.params.familyId

onMounted(() => {
  if (familyId) {
    setupStore.initData(familyId)
  }
})

const goNext = () => {
  // TODO: Maybe save Step 1 changes to server here? Or wait for final step?
  // User workflow suggests immediate save might be expected for Step 1 if it's "Group Name".
  // However, to fix "going back reverts changes", we just need to update the STORE.
  // The store is already updated via v-model binding to `groupName`.
  
  router.push({
    name: 'GroupEditStep2',
    params: { familyId },
  })
}
</script>

