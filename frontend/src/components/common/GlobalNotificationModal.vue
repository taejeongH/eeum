<template>
  <div v-if="notificationStore.modalVisible" class="fixed inset-0 bg-black/50 backdrop-blur-sm font-display flex flex-col justify-end z-[9990]">
    <!-- Modal Container -->
    <div class="bg-white w-full max-w-md mx-auto rounded-t-[2rem] overflow-hidden flex flex-col shadow-2xl animate-[slideUp_0.3s_ease-out] fixed inset-x-0 bottom-0 md:relative md:h-auto max-h-[650px]">
      <!-- Header -->
      <div class="relative pt-6 pb-6 px-6 overflow-hidden transition-colors duration-300" :class="headerBgClass">
        <!-- Close Handle -->
        <button class="absolute top-2 left-1/2 -translate-x-1/2 flex items-center justify-center z-10" @click="notificationStore.closeModal">
          <div class="h-1 w-10 rounded-full bg-black/10"></div>
        </button>

        <div class="relative">
          <!-- Badge & Time -->
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-1.5 bg-white/60 backdrop-blur-sm px-2.5 py-1 rounded-md shadow-sm">
              <span class="h-1.5 w-1.5 rounded-full animate-pulse" :class="badgeColorClass"></span>
              <span class="text-[10px] font-black uppercase tracking-wider text-gray-800">{{ eventConfig.label }}</span>
            </div>
            <div class="flex items-center gap-1.5 bg-white/60 backdrop-blur-sm px-2.5 py-1 rounded-md shadow-sm">
              <span class="material-symbols-outlined text-sm text-gray-600">schedule</span>
              <span class="text-xs font-bold text-gray-800">{{ currentTime }}</span>
            </div>
          </div>

          <!-- Icon & Title -->
          <div class="flex items-center gap-4 mb-2">
            <div class="w-14 h-14 bg-white/80 backdrop-blur-sm rounded-2xl flex items-center justify-center shadow-sm">
              <span class="material-symbols-outlined text-4xl" :class="iconColorClass">{{ eventConfig.icon }}</span>
            </div>
            <div class="flex-1">
              <h1 class="text-2xl font-black leading-tight text-gray-900 mb-1">{{ headerTitle }}</h1>
              <p class="text-sm font-semibold text-gray-600">{{ headerSubtitle }}</p>
            </div>
          </div>
        </div>
      </div>

      <div class="flex-1 overflow-y-auto bg-gray-50 pt-4 pb-6 px-5 space-y-4">
        <!-- Information Card -->
        <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
          <div class="space-y-4">
             <!-- Group & Dependent Info -->
              <div class="flex items-center gap-3 pb-3 border-b border-gray-50">
                <div class="w-10 h-10 bg-gray-50 rounded-lg flex items-center justify-center flex-shrink-0">
                  <span class="material-symbols-outlined text-gray-500 text-lg">group</span>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">그룹 / 대상</p>
                  <p class="text-sm font-bold text-gray-900">{{ groupName }} / {{ dependentName }}</p>
                </div>
              </div>

               <!-- Message Content -->
               <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0" :class="eventConfig.iconBg">
                  <span class="material-symbols-outlined text-lg" :class="eventConfig.iconColor">info</span>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">알림 내용</p>
                  <p class="text-sm font-bold text-gray-900 break-keep">{{ messageContent }}</p>
                </div>
              </div>
          </div>
        </div>

        <!-- Action Button -->
        <button @click="handleConfirm" class="w-full h-14 bg-gray-900 text-white font-bold rounded-xl shadow-lg active:scale-[0.98] transition-all flex items-center justify-center gap-2">
            <span>확인했습니다</span>
            <span class="material-symbols-outlined text-sm">check</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useNotificationStore } from '@/stores/notification';
import { useRouter } from 'vue-router';

const notificationStore = useNotificationStore();
const router = useRouter();
const currentTime = ref('');

watch(() => notificationStore.modalVisible, (visible) => {
  if (visible) {
    currentTime.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
});

const modalData = computed(() => notificationStore.modalData || {});

const groupName = computed(() => modalData.value.groupName || '알 수 없음');
const dependentName = computed(() => modalData.value.dependentName || '피부양자');
const messageContent = computed(() => modalData.value.message || '새로운 알림이 도착했습니다.');
const type = computed(() => modalData.value.type || 'INFO');

const eventConfig = computed(() => {
  const t = type.value;
  if (t === 'OUTING') {
    return {
      icon: 'directions_walk',
      label: '외출 감지',
      iconBg: 'bg-orange-50',
      iconColor: 'text-orange-500'
    };
  } else if (t === 'RETURN') {
     return {
      icon: 'home',
      label: '귀가 확인',
      iconBg: 'bg-green-50',
      iconColor: 'text-green-600'
    };
  } else if (t === 'ACTIVITY') {
      return {
      icon: 'run_circle',
      label: '활동 감지',
      iconBg: 'bg-blue-50',
      iconColor: 'text-blue-500'
    };
  } else {
    return {
      icon: 'notifications',
      label: '새 알림',
      iconBg: 'bg-gray-100',
      iconColor: 'text-gray-600'
    };
  }
});

const headerBgClass = computed(() => {
   if (type.value === 'OUTING') return 'bg-orange-50';
   if (type.value === 'RETURN') return 'bg-green-50';
   if (type.value === 'ACTIVITY') return 'bg-blue-50';
   return 'bg-gray-50';
});

const badgeColorClass = computed(() => {
   if (type.value === 'OUTING') return 'bg-orange-500';
   if (type.value === 'RETURN') return 'bg-green-500';
   if (type.value === 'ACTIVITY') return 'bg-blue-500';
   return 'bg-gray-500';
});

const iconColorClass = computed(() => {
   if (type.value === 'OUTING') return 'text-orange-500';
   if (type.value === 'RETURN') return 'text-green-600';
   if (type.value === 'ACTIVITY') return 'text-blue-500';
   return 'text-gray-600';
});

const headerTitle = computed(() => {
   if (type.value === 'OUTING') return '외출하셨습니다';
   if (type.value === 'RETURN') return '귀가하셨습니다';
   if (type.value === 'ACTIVITY') return '활동이 감지됨';
   return '새로운 알림';
});

const headerSubtitle = computed(() => {
    return '실시간 상태 모니터링 중입니다.';
});

const handleConfirm = () => {
    notificationStore.closeModal();
};

</script>

<style scoped>
@keyframes slideUp {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
