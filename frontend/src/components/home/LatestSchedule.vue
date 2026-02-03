<template>
  <div class="px-6 mb-8">
    <div class="bg-white rounded-3xl p-6 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-bold text-gray-900">오늘의 최신 일정</h3>
        <button 
          @click="goToCalendar" 
          class="flex items-center text-xs font-bold text-[#f3532b] bg-[#fdf2f0] px-3 py-1.5 rounded-full hover:bg-[#fde8e3] transition-colors"
        >
          전체보기
          <span class="material-symbols-outlined text-[14px] ml-1">chevron_right</span>
        </button>
      </div>

      <div v-if="latestSchedule" class="flex items-center gap-4 p-4 bg-gray-50 rounded-2xl border border-gray-100 cursor-pointer hover:bg-gray-100 transition-colors" @click="goToDetail">
        <div class="flex-shrink-0 w-12 h-12 rounded-2xl bg-white border border-gray-100 flex flex-col items-center justify-center">
            <span class="text-[10px] font-bold text-gray-400 uppercase tracking-tighter">{{ monthStr }}</span>
            <span class="text-lg font-black text-gray-900 -mt-1">{{ dayStr }}</span>
        </div>
        <div class="flex-grow">
          <p class="text-xs font-bold text-[#f3532b] mb-0.5">{{ latestSchedule.time }}</p>
          <h4 class="text-sm font-bold text-gray-900 line-clamp-1">{{ latestSchedule.title }}</h4>
          <p class="text-[11px] text-gray-500 line-clamp-1">{{ latestSchedule.location || '장소 정보 없음' }}</p>
        </div>
        <span class="material-symbols-outlined text-gray-300">chevron_right</span>
      </div>

      <div v-else class="py-10 text-center">
        <div class="w-12 h-12 bg-gray-50 rounded-full flex items-center justify-center mx-auto mb-3">
          <span class="material-symbols-outlined text-gray-300">calendar_today</span>
        </div>
        <p class="text-sm font-medium text-gray-400">오늘 예정된 일정이 없습니다</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import api from '@/services/api';

const router = useRouter();
const familyStore = useFamilyStore();
const latestSchedule = ref(null);

const now = new Date();
const monthStr = computed(() => (now.getMonth() + 1) + '월');
const dayStr = computed(() => now.getDate());

const fetchLatestSchedule = async () => {
  try {
    const familyId = familyStore.selectedFamily?.id;
    if (!familyId) return;

    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1; // Backend expects 1-indexed

    // Fetch schedules for this month
    const response = await api.get(`/families/${familyId}/schedules`, {
      params: { year, month }
    });
    
    // Response wrapper: { statusCode, message, data: [...] }
    const schedules = response.data.data || [];
    
    const todayStr = now.toISOString().split('T')[0];
    
    // Filter for today
    const todaySchedules = schedules.filter(s => s.startAt && s.startAt.startsWith(todayStr));
    
    if (todaySchedules.length > 0) {
      // Sort by time
      todaySchedules.sort((a, b) => a.startAt.localeCompare(b.startAt));
      
      const target = todaySchedules[0];
      
      // Format time (e.g., "오전 09:00" or "09:00 PM")
      const dateObj = new Date(target.startAt);
      const timeStr = new Intl.DateTimeFormat('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      }).format(dateObj);

      latestSchedule.value = {
        ...target,
        time: timeStr,
        location: target.description // Fallback to description if location not separate
      };
    } else {
      latestSchedule.value = null;
    }
  } catch (e) {
    console.error("Failed to fetch schedules:", e);
  }
};

// Re-fetch when family changes
import { watch } from 'vue';
watch(() => familyStore.selectedFamily, () => {
  fetchLatestSchedule();
});

onMounted(() => {
  fetchLatestSchedule();
});

const goToCalendar = () => {
  const familyId = familyStore.selectedFamily?.id;
  if (familyId) {
    router.push({ name: 'CalendarPage', params: { familyId } });
  }
};

const goToDetail = () => {
  const familyId = familyStore.selectedFamily?.id;
  const scheduleId = latestSchedule.value?.id;
  if (familyId && scheduleId) {
    router.push({ 
      name: 'DetailSchedule', 
      params: { familyId }, 
      query: { id: scheduleId } 
    });
  } else {
    goToCalendar();
  }
};
</script>
