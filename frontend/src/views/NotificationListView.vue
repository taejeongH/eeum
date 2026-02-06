<template>
  <div class="bg-[#fcfcfc] min-h-screen pb-10">
    <!-- Header -->
    <header class="bg-white/90 backdrop-blur-md sticky top-0 z-[100] border-b border-gray-100 shadow-sm transition-all duration-300">
      <div class="px-6 pt-6 pb-2 flex items-center">
        <button @click="router.back()" class="p-2 -ml-2 rounded-full hover:bg-gray-100 active:scale-90 transition-all">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-gray-800" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 class="text-2xl font-black text-gray-900 ml-2 tracking-tight">알람 로그</h1>
      </div>

      <!-- Filter Tabs: Premium Redesign -->
      <div class="px-6 py-4 flex gap-3 overflow-x-auto no-scrollbar scroll-smooth">
        <button 
          v-for="filter in filters" 
          :key="filter.value"
          @click="activeFilter = filter.value"
          class="px-5 py-2 rounded-2xl text-[13px] font-extrabold transition-all duration-300 whitespace-nowrap active:scale-95 shadow-sm"
          :class="activeFilter === filter.value 
            ? 'bg-gray-900 text-white shadow-gray-200 shadow-md ring-2 ring-gray-900 ring-offset-2' 
            : 'bg-white text-gray-400 border border-gray-100 hover:bg-gray-50 hover:text-gray-600'"
        >
          {{ filter.label }}
        </button>
      </div>
    </header>

    <main class="px-5 py-6">
      <!-- Loading State -->
      <div v-if="notificationStore.isLoading" class="flex flex-col items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-[#e76f51]"></div>
        <p class="mt-4 text-gray-500 text-sm">알람을 불러오고 있습니다...</p>
      </div>

      <!-- Empty State -->
      <div v-else-if="groupedNotifications.length === 0" class="flex flex-col items-center justify-center py-20 px-10 text-center">
        <div class="w-20 h-20 bg-gray-50 rounded-full flex items-center justify-center mb-6">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
        </div>
        <p class="text-gray-800 font-semibold text-lg">알람이 없습니다</p>
        <p class="mt-2 text-gray-500 text-sm">선택한 필터에 해당하는 알람이 없습니다.</p>
      </div>

      <!-- Notification List with Grouping -->
      <div v-else class="space-y-8">
        <div v-for="group in groupedNotifications" :key="group.dateLabel" class="space-y-4">
          <!-- Date Header -->
          <div class="flex items-center gap-3">
            <div class="h-[1px] flex-1 bg-gray-100"></div>
            <span class="text-[11px] font-bold text-gray-400 uppercase tracking-widest">{{ group.dateLabel }}</span>
            <div class="h-[1px] flex-1 bg-gray-100"></div>
          </div>

          <div 
            v-for="noti in group.items" 
            :key="noti.id"
            @click="handleNotiClick(noti)"
            class="bg-white rounded-2xl p-5 shadow-sm border-l-4 flex items-start group active:scale-[0.98] transition-all relative overflow-hidden cursor-pointer"
            :class="[getBorderClass(noti.type), getBgClass(noti.type), !noti.isRead ? 'ring-2 ring-gray-100 shadow-md' : 'opacity-80']"
          >
            <!-- Icon Based on Type -->
            <div :class="getIconContainerClass(noti.type)" class="flex-shrink-0 p-3 rounded-xl mr-4 shadow-sm z-10">
              <component :is="getIconComponent(noti.type)" class="h-6 w-6" />
            </div>

            <div class="flex-1 min-w-0 z-10">
              <div class="flex justify-between items-center mb-1.5">
                <div class="flex items-center gap-2">
                  <span :class="getCategoryTagClass(noti.type)" class="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider">
                    {{ getCategoryLabel(noti.type) }}
                  </span>
                  <h2 class="text-base font-bold text-gray-900 truncate">{{ noti.title }}</h2>
                </div>
                <span class="text-[11px] text-gray-400 whitespace-nowrap">{{ formatDetailTime(noti.createdAt) }}</span>
              </div>
              <p class="text-sm text-gray-600 line-clamp-2 leading-relaxed">{{ noti.message }}</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useNotificationStore } from '@/stores/notification';
import { useFamilyStore } from '@/stores/family';

const router = useRouter();
const route = useRoute();
const notificationStore = useNotificationStore();
const familyStore = useFamilyStore();

const activeFilter = ref('ALL');

const filters = [
  { label: '전체', value: 'ALL' },
  { label: '🚨 응급/낙상', value: 'EMERGENCY' },
  { label: '🚶 활동 (외출/귀가)', value: 'ACTIVITY' },
  { label: '💬 기타 알림', value: 'OTHERS' }
];

onMounted(async () => {
  if (familyStore.families.length === 0) {
    await familyStore.fetchFamilies();
  }
});

const handleNotiClick = async (noti) => {
  // 모달 데이터 준비 (기존 데이터 보존하며 추가 정보 입력)
  const modalData = {
    ...noti,
    groupName: familyStore.selectedFamily?.name || '우리 가족',
    dependentName: familyStore.selectedFamily?.dependentName || '피부양자'
  };
  
  // 모달 열기
  notificationStore.openModal(modalData);
  
  // 읽지 않은 알림이면 읽음 처리
  if (!noti.isRead) {
    await notificationStore.markAsRead(noti.id);
  }
};

// 경로 파라미터(familyId)가 변경될 때마다 데이터를 새로 가져옵니다.
// (컴포넌트가 재사용되는 경우 onMounted가 다시 호출되지 않기 때문)
watch(() => route.params.familyId, async (newFamilyId) => {
  if (newFamilyId) {

    await notificationStore.fetchHistory(newFamilyId);
  }
}, { immediate: true });

// Grouping and Filtering Logic
const groupedNotifications = computed(() => {
  // 1. Filter
  const filtered = (notificationStore.notifications || []).filter(n => {
    if (activeFilter.value === 'ALL') return true;
    if (activeFilter.value === 'EMERGENCY') return n.type === 'EMERGENCY' || n.type === 'FALL';
    if (activeFilter.value === 'ACTIVITY') return n.type === 'ACTIVITY' || n.type === 'OUTING' || n.type === 'RETURN';
    if (activeFilter.value === 'OTHERS') return !['EMERGENCY', 'FALL', 'ACTIVITY', 'OUTING', 'RETURN'].includes(n.type);
    return true;
  });

  // 2. Group by Date
  const groups = {};
  filtered.forEach(item => {
    const label = getDateLabel(item.createdAt);
    if (!groups[label]) groups[label] = [];
    groups[label].push(item);
  });

  return Object.keys(groups).map(dateLabel => ({
    dateLabel,
    items: groups[dateLabel]
  })).sort((a, b) => {
    // Basic heuristic to keep Today at top, then Yesterday
    if (a.dateLabel === '오늘') return -1;
    if (b.dateLabel === '오늘') return 1;
    if (a.dateLabel === '어제') return -1;
    if (b.dateLabel === '어제') return 1;
    return 0;
  });
});

const getDateLabel = (dateStr) => {
  const date = new Date(dateStr);
  const now = new Date();
  
  // Normalize dates to midnight for comparison
  const dMidnight = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const nowMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  
  const diffTime = nowMidnight - dMidnight;
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return '오늘';
  if (diffDays === 1) return '어제';
  
  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
};

const formatDetailTime = (dateStr) => {
  const date = new Date(dateStr);
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const ampm = hours >= 12 ? '오후' : '오전';
  const displayHours = hours % 12 || 12;
  const displayMinutes = minutes < 10 ? '0' + minutes : minutes;
  return `${ampm} ${displayHours}:${displayMinutes}`;
};

const getCategoryLabel = (type) => {
  switch (type) {
    case 'EMERGENCY':
    case 'FALL':
      return '응급';
    case 'ACTIVITY':
    case 'OUTING':
    case 'RETURN':
      return '활동';
    default:
      return '알림';
  }
};

const getCategoryTagClass = (type) => {
  switch (type) {
    case 'EMERGENCY':
    case 'FALL':
      return 'bg-red-600 text-white shadow-sm';
    case 'ACTIVITY':
    case 'OUTING':
    case 'RETURN':
      return 'bg-blue-600 text-white shadow-sm';
    default:
      return 'bg-gray-600 text-white shadow-sm';
  }
};

const getBorderClass = (type) => {
  switch (type) {
    case 'EMERGENCY':
    case 'FALL': return 'border-red-600';
    case 'ACTIVITY':
    case 'OUTING':
    case 'RETURN': return 'border-blue-600';
    default: return 'border-amber-600';
  }
};

const getBgClass = (type) => {
  if (type === 'EMERGENCY' || type === 'FALL') return 'bg-red-50';
  if (type === 'ACTIVITY' || type === 'OUTING' || type === 'RETURN') return 'bg-blue-50/50';
  return 'bg-white';
};

const getIconContainerClass = (type) => {
  switch (type) {
    case 'EMERGENCY':
    case 'FALL':
      return 'bg-red-600 text-white shadow-md';
    case 'ACTIVITY':
    case 'OUTING':
    case 'RETURN':
      return 'bg-blue-600 text-white shadow-md';
    default:
      return 'bg-amber-600 text-white shadow-md';
  }
};

const getIconComponent = (type) => {
  return {
    template: type === 'EMERGENCY' || type === 'FALL' 
      ? '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77-1.333.192 3 1.732 3z" /></svg>'
      : type === 'ACTIVITY' || type === 'OUTING' || type === 'RETURN'
      ? '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-6 0v-1m6 0H9" /></svg>'
      : '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-6 0v-1m6 0H9" /></svg>'
  };
};
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800;900&display=swap');
h1, h2, h3, span {
  font-family: 'Inter', sans-serif;
}
</style>
