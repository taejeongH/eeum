<template>
  <div v-if="notificationStore.modalVisible" class="fixed inset-0 bg-black/50 backdrop-blur-sm font-display flex flex-col justify-end items-center z-[999999]" @click.self="notificationStore.closeModal">
    <!-- Modal Container -->
    <!-- [FIX] Removed debug border, ensuring simple standard layout -->
    <div 
      class="bg-white w-full max-w-md rounded-t-[2rem] md:rounded-2xl overflow-hidden flex flex-col shadow-2xl relative animate-[slideUp_0.3s_ease-out] touch-none"
      :style="{ transform: `translateY(${currentY}px)`, transition: isSwiping ? 'none' : 'transform 0.3s ease-out' }"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
    >
      
      <!-- Header -->
      <div class="relative pt-8 pb-6 px-6 overflow-hidden transition-colors duration-300" :class="headerBgClass">
        <!-- Close Handle -->
        <button class="absolute top-2 left-1/2 -translate-x-1/2 flex items-center justify-center z-10 w-12 h-8" @click="notificationStore.closeModal">
          <div class="h-1 w-10 rounded-full bg-black/10"></div>
        </button>

        <div class="relative">
          <!-- Badge & Time -->
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-1.5 bg-white/60 backdrop-blur-sm px-2.5 py-1 rounded-md shadow-sm">
              <span class="text-[10px] font-black uppercase tracking-wider text-gray-800">{{ eventLabel }}</span>
            </div>
            <div class="flex items-center gap-1.5 bg-white/60 backdrop-blur-sm px-2.5 py-1 rounded-md shadow-sm">
              <svg class="w-3.5 h-3.5 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
              <span class="text-xs font-bold text-gray-800">{{ currentTime }}</span>
            </div>
          </div>

          <!-- Icon & Title -->
          <div class="flex items-center gap-4 mb-2">
            <div class="w-16 h-16 bg-white/80 backdrop-blur-sm rounded-2xl flex items-center justify-center shadow-sm shrink-0">
               <!-- Icon Switcher: Standard Heroicons Outline -->
               <!-- EMERGENCY/FALL: ExclamationTriangle -->
               <svg v-if="type === 'EMERGENCY' || type === 'FALL'" class="w-8 h-8 text-red-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"></path></svg>
               <!-- OUTING: ArrowRightStartOnRectangle -->
               <svg v-else-if="type === 'OUTING'" class="w-8 h-8 text-orange-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15M12 9l-3 3m0 0l3 3m-3-3h12.75"></path></svg>
               <!-- RETURN: HomeModern -->
               <svg v-else-if="type === 'RETURN'" class="w-8 h-8 text-green-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25"></path></svg>
               <!-- ACTIVITY: Bolt -->
               <svg v-else-if="type === 'ACTIVITY'" class="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z"></path></svg>
               <!-- DEFAULT: InformationCircle -->
               <svg v-else class="w-8 h-8 text-gray-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z"></path></svg>
            </div>
            <div class="flex-1 min-w-0">
              <h1 class="text-2xl font-black leading-tight text-gray-900 mb-1 truncate">{{ headerTitle }}</h1>
              <p class="text-sm font-semibold text-gray-600 truncate">{{ headerDesc }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Content Area -->
      <div class="flex-1 overflow-y-auto bg-gray-50 pt-4 px-5 pb-10 space-y-4 min-h-[100px]">
        
        <!-- [NEW] Video Player for Fall Events -->
        <div v-if="(type === 'EMERGENCY' || type === 'FALL') && videoUrl" class="bg-black rounded-2xl overflow-hidden shadow-lg border border-red-100 aspect-video relative group">
            <video 
                :src="videoUrl" 
                controls 
                autoplay
                class="w-full h-full object-contain"
            ></video>
            <div class="absolute top-3 right-3 bg-red-600/90 text-white text-[10px] font-bold px-2 py-1 rounded-md backdrop-blur-sm">
                RECORDED
            </div>
        </div>

        <!-- [NEW] Confidence Metric -->
        <div v-if="confidence" class="bg-white rounded-2xl p-4 shadow-sm border border-red-50 flex items-center justify-between">
            <div class="flex items-center gap-3">
                <div class="w-10 h-10 bg-red-50 rounded-xl flex items-center justify-center">
                    <svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                </div>
                <div>
                    <p class="text-[10px] text-gray-400 font-bold uppercase tracking-wide">낙상 분석 신뢰도</p>
                    <p class="text-sm font-black text-gray-900">인공지능 분석 결과</p>
                </div>
            </div>
            <div class="text-right">
                <span class="text-2xl font-black text-red-600 tabular-nums">{{ Math.round(confidence) }}</span>
                <span class="text-xs font-bold text-red-400 ml-0.5">%</span>
            </div>
        </div>
        <!-- [NEW] Return Specific Info: Outing Duration -->
        <div v-if="type === 'RETURN'" class="bg-white rounded-2xl p-5 shadow-sm border border-green-100 relative overflow-hidden">
            <div class="absolute top-0 right-0 w-20 h-20 bg-green-50 rounded-bl-full opacity-50 -mr-4 -mt-4"></div>
            <h3 class="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3">총 외출 시간</h3>
            <div class="flex items-end gap-2">
                <span class="text-3xl font-black text-gray-900 tabular-nums tracking-tight">4</span>
                <span class="text-lg font-bold text-gray-500 mb-1.5">시간</span>
                <span class="text-3xl font-black text-gray-900 tabular-nums tracking-tight ml-2">30</span>
                <span class="text-lg font-bold text-gray-500 mb-1.5">분</span>
            </div>
            <p class="text-xs text-gray-400 mt-2 font-medium bg-gray-50 inline-block px-2 py-1 rounded">
                ※ 이전 외출 기록 기준 (예시 데이터)
            </p>
        </div>

        <!-- Information Card -->
        <div class="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
          <div class="space-y-4">
             <!-- Group & Dependent Info -->
              <div class="flex items-center gap-3 pb-3 border-b border-gray-50">
                <div class="w-10 h-10 bg-gray-50 rounded-lg flex items-center justify-center flex-shrink-0">
                  <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">그룹 / 대상</p>
                  <p class="text-sm font-bold text-gray-900 truncate">{{ groupName }} / {{ dependentName }}</p>
                </div>
              </div>

               <!-- Message Content -->
               <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0" :class="infoIconBgClass">
                   <svg class="w-5 h-5" :class="infoIconColorClass" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">상세 알림 내용</p>
                  <p class="text-sm font-bold text-gray-900 break-keep leading-snug">{{ messageContent }}</p>
                </div>
              </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useNotificationStore } from '@/stores/notification';
import { useRouter } from 'vue-router';

const notificationStore = useNotificationStore();
const router = useRouter();
const currentTime = ref('');



watch(() => notificationStore.modalVisible, (visible) => {
  if (visible) {
    const date = modalData.value.createdAt ? new Date(modalData.value.createdAt) : new Date();
    currentTime.value = date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
  }
});

const modalData = computed(() => notificationStore.modalData || {});

const groupName = computed(() => modalData.value.groupName || '가족 그룹');
const dependentName = computed(() => modalData.value.dependentName || '피부양자');
const messageContent = computed(() => modalData.value.message || '새로운 알림이 도착했습니다.');
const type = computed(() => modalData.value.type || 'INFO');
const videoUrl = computed(() => modalData.value.videoUrl || null);
const confidence = computed(() => modalData.value.confidence || null);

const notificationId = computed(() => modalData.value.notificationId || null);


const headerBgClass = computed(() => {
   if (type.value === 'EMERGENCY' || type.value === 'FALL') return 'bg-red-50';
   if (type.value === 'OUTING') return 'bg-orange-50';
   if (type.value === 'RETURN') return 'bg-green-50';
   if (type.value === 'ACTIVITY') return 'bg-blue-50';
   return 'bg-gray-50';
});

const badgeColorClass = computed(() => {
   if (type.value === 'EMERGENCY' || type.value === 'FALL') return 'bg-red-500';
   if (type.value === 'OUTING') return 'bg-orange-500';
   if (type.value === 'RETURN') return 'bg-green-500';
   if (type.value === 'ACTIVITY') return 'bg-blue-500';
   return 'bg-gray-500';
});

const eventLabel = computed(() => {
    if (type.value === 'EMERGENCY' || type.value === 'FALL') return '응급 상황';
    if (type.value === 'OUTING') return '외출 감지';
    if (type.value === 'RETURN') return '귀가 확인';
    if (type.value === 'ACTIVITY') return '활동 감지';
    return '새 알림';
});

const headerTitle = computed(() => {
   if (type.value === 'EMERGENCY' || type.value === 'FALL') return '낙상이 감지되었습니다';
   if (type.value === 'OUTING') return '외출하셨습니다';
   if (type.value === 'RETURN') return '귀가하셨습니다';
   if (type.value === 'ACTIVITY') return '활동이 감지됨';
   return '알림 도착';
});

const headerDesc = computed(() => {
    if (type.value === 'OUTING') return '대상자가 거주지를 벗어났습니다.';
    if (type.value === 'RETURN') return '대상자가 안전하게 귀가했습니다.';
    if (type.value === 'ACTIVITY') return '새로운 활동 로그가 확인되었습니다.';
    return '새로운 소식이 있습니다.';
});

const infoIconBgClass = computed(() => {
    if (type.value === 'OUTING') return 'bg-orange-100';
    if (type.value === 'RETURN') return 'bg-green-100';
    if (type.value === 'ACTIVITY') return 'bg-blue-100';
    return 'bg-gray-100';
});

const infoIconColorClass = computed(() => {
    if (type.value === 'OUTING') return 'text-orange-600';
    if (type.value === 'RETURN') return 'text-green-600';
    if (type.value === 'ACTIVITY') return 'text-blue-600';
    return 'text-gray-600';
});


const startY = ref(0);
const currentY = ref(0);
const isSwiping = ref(false);

const onTouchStart = (e) => {
    startY.value = e.touches[0].clientY;
    isSwiping.value = true;
};

const onTouchMove = (e) => {
    if (!isSwiping.value) return;
    const deltaY = e.touches[0].clientY - startY.value;
    if (deltaY > 0) {
        currentY.value = deltaY;
    }
};

const onTouchEnd = () => {
    if (currentY.value > 150) {
        closeAndMarkRead();
    }
    currentY.value = 0;
    isSwiping.value = false;
};

const closeAndMarkRead = async () => {
    if (notificationId.value) {
        await notificationStore.markAsRead(notificationId.value);
    }
    notificationStore.closeModal();
};

const handleConfirm = () => {
    closeAndMarkRead();
};
</script>

<style scoped>
@keyframes slideUp {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
