<template>
  <div v-if="show" @click.self="close" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white rounded-lg shadow-xl w-full max-w-sm m-4">
      <div class="flex justify-between items-center border-b p-4">
        <h2 class="text-lg font-semibold">그룹 초대하기</h2>
        <button @click="close" class="text-gray-500 hover:text-gray-800">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </button>
      </div>
      <div class="p-6 text-center">
        <p class="text-sm text-gray-600 mb-4">아래 초대 링크를 복사하여 멤버를 초대하세요.</p>
        <div v-if="loading" class="h-10 flex items-center justify-center">
          <p class="text-gray-500">로딩 중...</p>
        </div>
        <div v-else-if="error" class="h-10 flex items-center justify-center">
          <p class="text-red-500">{{ error }}</p>
        </div>
        <div v-else class="flex items-center space-x-2">
          <input type="text" :value="getInviteLink" readonly class="bg-gray-100 border border-gray-300 text-gray-700 text-sm rounded-lg block w-full pl-3 pr-10 py-2.5 focus:outline-none">
          <button @click="copyToClipboard" title="복사" class="p-2 text-gray-500 hover:text-gray-800 flex-shrink-0">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"></path></svg>
          </button>
        </div>
      </div>
      <div class="flex justify-center items-center border-t p-4">
        <button @click="regenerateCode" class="px-4 py-2 bg-primary text-white rounded-md hover:bg-orange-600">재생성</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import api from '@/services/api';

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

const BASE_URL = import.meta.env.VITE_APP_BASE_URL || 'http://localhost:5173';

const getInviteLink = computed(() => {
    if (!inviteCode.value || inviteCode.value === 'N/A') return '';
    return `${BASE_URL}/#/join?code=${inviteCode.value}`;
});

const fetchInviteCode = async () => {
  if (!props.familyId) return;
  loading.value = true;
  error.value = null;
  try {
    const response = await api.get(`/families/${props.familyId}/invite`);
    inviteCode.value = response.data;
  } catch (err) {
    console.error('Failed to fetch invite code:', err);
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
  loading.value = true;
  error.value = null;
  try {
    const response = await api.put(`/families/${props.familyId}/invite`);
    inviteCode.value = response.data;
    alert('초대 코드가 재성성되었습니다.');
  } catch (err) {
    console.error('Failed to regenerate invite code:', err);
    if (err.response && err.response.status === 403) {
      error.value = '대표자만 코드를 재성성할 수 있습니다.';
    } else {
      error.value = '재생성에 실패했습니다.';
    }
  } finally {
    loading.value = false;
  }
};

const copyToClipboard = () => {
  if (!getInviteLink.value) {
    alert('복사할 링크가 없습니다.');
    return;
  }
  navigator.clipboard.writeText(getInviteLink.value).then(() => {
    alert('초대 링크가 클립보드에 복사되었습니다.');
  }).catch(err => {
    console.error('Failed to copy text: ', err);
    alert('복사에 실패했습니다.');
  });
};

const close = () => {
  emit('close');
};

watch(() => props.show, (newVal) => {
  if (newVal) {
    fetchInviteCode();
  }
});
</script>