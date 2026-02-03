<template>
  <div class="px-6 mb-4">
    <div 
      @click="goToNotifications"
      class="rounded-2xl px-5 py-4 flex items-center shadow-sm cursor-pointer active:scale-[0.98] transition-all border"
      :class="notificationStore.latestNotification ? (isEmergency ? 'bg-red-100 border-red-200' : 'bg-amber-100 border-amber-200') : 'bg-gray-50 border-gray-100'"
    >
      <div class="flex-shrink-0 mr-4">
        <!-- Emergency Icon -->
        <svg v-if="isEmergency" xmlns="http://www.w3.org/2000/svg" class="h-7 w-7 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <!-- Standard Icon -->
        <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-7 w-7 text-[#e76f51]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z" />
        </svg>
      </div>

      <span class="text-sm truncate flex-1 font-bold tracking-tight" :class="isEmergency ? 'text-red-700' : 'text-gray-800'">
        {{ displayMessage }}
      </span>

      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-gray-400 ml-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
      </svg>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useNotificationStore } from '@/stores/notification';
import { useFamilyStore } from '@/stores/family';

const router = useRouter();
const notificationStore = useNotificationStore();
const familyStore = useFamilyStore();

const isEmergency = computed(() => {
  const type = notificationStore.latestNotification?.type;
  return type === 'EMERGENCY' || type === 'FALL';
});

const displayMessage = computed(() => {
  // 1. 초기 로딩 중이거나 아직 데이터를 가져오기 전인 경우
  if (!notificationStore.isInitialized || notificationStore.isLoading || familyStore.isLoading) {
    return '알람을 가져오는 중...';
  }
  
  // 2. 가족이 아예 없는 경우
  if (familyStore.families.length === 0) {
    return '참여 중인 그룹이 없습니다.';
  }

  // 3. 알림이 없는 경우
  if (!notificationStore.latestNotification) {
    return '새로운 알림이 없습니다.';
  }

  // 4. 최신 알림 메시지 출력
  return notificationStore.latestNotification.message;
});

const goToNotifications = () => {
  const familyId = familyStore.selectedFamily?.id;
  if (familyId) {
    router.push(`/families/${familyId}/notifications`);
  }
};
</script>
