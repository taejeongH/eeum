<template>
  <transition name="fade">
    <div
      v-if="show"
      class="fixed inset-0 bg-black/40 z-40"
      @click.self="close"
    />
  </transition>

  <transition name="slide-up">
    <div
      v-if="show"
      ref="sheet"
      class="fixed inset-x-0 bottom-0 z-50 bg-white rounded-t-3xl px-5 pt-3 pb-6 touch-pan-y min-h-[300px] max-h-[90vh] overflow-y-auto"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
    >
      <!-- Drag Handle -->
      <div class="w-10 h-1.5 bg-gray-200 rounded-full mx-auto mb-6" />

      <!-- Title -->
      <h2 class="text-xl font-bold text-center mb-2 text-gray-900">그룹 초대하기</h2>
      <p class="text-sm text-gray-500 text-center mb-8">
        아래 링크를 공유하여 가족을 초대해보세요
      </p>

      <!-- Content (Stable Layout) -->
      <div class="space-y-6">
        <!-- Input & Copy Area -->
        <div class="relative">
          <input
            :value="displayValue"
            readonly
            class="eeum-input w-full pr-12 text-center transition-colors"
            :class="[
              loading ? 'text-gray-400 bg-gray-50' : 'text-gray-600 bg-gray-50 focus:bg-white',
              error ? 'text-red-500' : ''
            ]"
            @click="!loading && !error && copyToClipboard()"
          />
          
          <!-- Loading Spinner (Absolute) -->
          <div v-if="loading" class="absolute right-3 top-1/2 -translate-y-1/2">
             <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-gray-400"></div>
          </div>
          
          <!-- Copy Button (Absolute) -->
          <button 
            v-else
            @click="copyToClipboard"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-primary)] p-2 hover:bg-orange-50 rounded-full transition"
            :class="{ 'opacity-50 cursor-not-allowed': error }"
            :disabled="!!error"
            title="복사하기"
          >
             <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"></path></svg>
          </button>
        </div>

        <!-- Actions -->
         <div class="space-y-3">
            <button 
              class="eeum-btn-primary w-full flex items-center justify-center gap-2 shadow-lg shadow-orange-100"
              @click="copyToClipboard"
              :disabled="loading || !!error"
              :class="{ 'opacity-80': loading || error }"
            >
              <span>링크 복사하기</span>
            </button>
            
            <button 
              @click="regenerateCode"
              class="w-full py-3 text-sm text-gray-400 hover:text-gray-600 transition flex items-center justify-center gap-1.5"
              :disabled="loading"
            >
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              <span>새로운 코드 발급받기</span>
            </button>
         </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue';
import api from '@/services/api';
import { Logger } from '@/services/logger';
import { useModalStore } from '@/stores/modal';

const modalStore = useModalStore();

const props = defineProps({
  show: {
    type: Boolean,
    required: true,
  },
  familyId: {
    type: Number,
    required: true,
  }
});

const emit = defineEmits(['close']);

const inviteCode = ref('');
const loading = ref(false);
const error = ref(null);

const BASE_URL = import.meta.env.VITE_APP_BASE_URL || 'https://i14a105.p.ssafy.io';

const getInviteLink = computed(() => {
    if (!inviteCode.value || inviteCode.value === 'N/A') return '';
    return `${BASE_URL}/#/join?code=${inviteCode.value}`;
});

const displayValue = computed(() => {
  if (loading.value) return '초대 코드를 불러오는 중...';
  if (error.value) return error.value;
  return getInviteLink.value;
});


const sheet = ref(null);
let startY = 0;
let currentY = 0;

const onTouchStart = (e) => {
  startY = e.touches[0].clientY;
};

const onTouchMove = (e) => {
  currentY = e.touches[0].clientY;
  const diff = currentY - startY;
  if (diff > 0 && sheet.value) {
    sheet.value.style.transform = `translateY(${diff}px)`;
  }
};

const onTouchEnd = () => {
  if (currentY - startY > 120) {
    close();
  } else if (sheet.value) {
    sheet.value.style.transform = '';
  }
  startY = 0;
  currentY = 0;
};


const fetchInviteCode = async () => {
  if (!props.familyId) return;
  loading.value = true;
  error.value = null;
  try {
    const response = await api.get(`/families/${props.familyId}/invite`);
    inviteCode.value = response.data;
  } catch (err) {
    Logger.error('초대 코드 조회 실패:', err);
    if (err.response && err.response.status === 403) {
      error.value = '대표자만 초대 코드를 볼 수 있습니다.';
    } else {
      error.value = '코드를 불러오지 못했습니다.';
    }
    inviteCode.value = 'N/A';
  } finally {
    loading.value = false;
  }
};

const regenerateCode = async () => {
  if (!props.familyId) return;
  if (!await modalStore.openConfirm('초대 코드를 재발급하시겠습니까? 기존 코드는 사용할 수 없게 됩니다.')) return;
  
  loading.value = true;
  error.value = null;
  try {
    const response = await api.put(`/families/${props.familyId}/invite`);
    inviteCode.value = response.data;
    await modalStore.openAlert('새로운 초대 코드가 발급되었습니다.');
  } catch (err) {
    Logger.error('초대 코드 재발급 실패:', err);
    if (err.response && err.response.status === 403) {
      error.value = '대표자만 코드를 재발급할 수 있습니다.';
    } else {
      error.value = '재발급에 실패했습니다.';
    }
  } finally {
    loading.value = false;
  }
};

const copyToClipboard = () => {
  if (!getInviteLink.value) {
    modalStore.openAlert('복사할 링크가 없습니다.');
    return;
  }
  navigator.clipboard.writeText(getInviteLink.value).then(() => {
    modalStore.openAlert('초대 링크가 클립보드에 복사되었습니다.');
  }).catch(err => {
    Logger.error('텍스트 복사 실패: ', err);
    modalStore.openAlert('복사에 실패했습니다.');
  });
};

const close = () => {
  emit('close');
};


onMounted(() => {
  if (props.show) {
    fetchInviteCode();
  }
});

watch(() => props.show, (newVal) => {
  if (newVal) {
    fetchInviteCode();
  }
});
</script>

