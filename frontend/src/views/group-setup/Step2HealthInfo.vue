<template>
  <div @keyup.enter="handleEnter">
    <h2 class="eeum-title">
      보살핌이 필요한<br />
      <span class="text-[var(--color-primary)]">가족 정보</span>를 입력해주세요
    </h2>

    <p class="eeum-sub mt-3">입력하신 정보는 위급 상황 시 의료진에게 공유돼요.</p>

    <!-- 진행률 표시 -->
    <div class="mt-6">
      <div class="h-2 w-full rounded-full bg-[var(--color-primary-soft)]">
        <div class="h-2 w-1/2 rounded-full bg-[var(--color-primary)]"></div>
      </div>
      <p class="mt-2 text-xs text-[var(--color-primary)]">단계 2 / 4 · 건강 정보 입력</p>
    </div>

    <!-- 피부양자 선택 영역 -->
    <div class="mt-8">
      <label class="block text-sm font-medium mb-2">
        피부양자 <span class="text-[var(--color-primary)]">*</span>
      </label>

      <CareTargetSelect v-model="seniorId" :members="members" />
    </div>

    <!-- 혈액형 선택 영역 -->
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
          :class="
            bloodType === type
              ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
              : 'border-gray-200 text-gray-600'
          "
        >
          {{ type }}
        </button>
      </div>
    </div>

    <!-- 기저질환 입력 영역 -->
    <div class="mt-8">
      <label class="block text-sm font-medium mb-2"> 기저질환 </label>

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
          <button @click="removeDisease(i)" class="ml-1 text-white/80 hover:text-white text-xs">
            ✕
          </button>
        </span>
      </div>
    </div>

    <!-- 하단 버튼 영역 -->
    <div class="mt-10">
      <button class="eeum-btn-primary" :disabled="!seniorId || !bloodType" @click="goNext">
        다음 단계로 →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import api from '@/services/api';
import { useGroupSetupStore } from '@/stores/groupSetup';
import { storeToRefs } from 'pinia';
import { Logger } from '@/services/logger';
import CareTargetSelect from '../../components/CareTargetSelect.vue';

const router = useRouter();
const route = useRoute();
const setupStore = useGroupSetupStore();

/** 스토어 상태 바인딩 */
const { seniorId, bloodType, diseases } = storeToRefs(setupStore);

const familyId = computed(() => route.params.familyId);
const members = ref([]);
const diseaseInput = ref('');
const bloodTypes = ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-'];

/**
 * 기저질환을 목록에 추가합니다.
 */
const addDisease = () => {
  if (!diseaseInput.value) return;
  diseases.value.push(diseaseInput.value);
  diseaseInput.value = '';
};

/**
 * 인덱스에 해당하는 기저질환을 목록에서 제거합니다.
 * @param {number} index
 */
const removeDisease = (index) => {
  diseases.value.splice(index, 1);
};

onMounted(async () => {
  if (!familyId.value) return;

  setupStore.initData(familyId.value);

  /** 드롭다운용 멤버 목록 조회 (선택 가능한 옵션) */
  /** 이는 UI 상태/옵션이며, 설정 데이터와는 별개임 (스토어에서 캐싱 가능하지만 현재 상태도 무방) */
  try {
    const membersRes = await api.get(`/families/${familyId.value}/members`);
    members.value = membersRes.data;
  } catch (error) {
    Logger.error('멤버 목록 조회 실패:', error);
  }
});

/**
 * 다음 단계(비상 연락망 설정)로 이동합니다.
 */
const goNext = () => {
  router.push({
    name: 'GroupEditStep3',
    params: { familyId: familyId.value },
  });
};

/**
 * 엔터키 입력 시 필수 정보가 입력되었다면 다음 단계로 이동합니다.
 */
const handleEnter = () => {
  if (seniorId.value && bloodType.value) {
    goNext();
  }
};
</script>
