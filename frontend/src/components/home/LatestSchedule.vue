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

    // Fetch schedules for today
    const response = await api.get(`/families/${familyId}/schedules`);
    const schedules = response.data;
    
    // Simple filter for today and sorting (mock logic for now if backend doesn't support today only)
    const today = new Date().toISOString().split('T')[0];
    const todaySchedules = schedules.filter(s => s.date === today);
    
    if (todaySchedules.length > 0) {
      // Sort by time and pick the next/latest one
      todaySchedules.sort((a, b) => a.time.localeCompare(b.time));
      latestSchedule.value = todaySchedules[0];
    }
  } catch (e) {
    console.error("Failed to fetch schedules:", e);
  }
};

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
