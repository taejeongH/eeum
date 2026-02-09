<template>
  <div class="px-6 mb-8">
    <div class="bg-white rounded-3xl p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-gray-100/50">
      <div class="flex items-center justify-between mb-5">
        <h3 class="text-[17px] font-extrabold text-gray-900 tracking-tight">오늘의 주요 일정</h3>
        <button 
          @click="goToCalendar" 
          class="flex items-center text-[12px] font-bold text-[#f3532b] hover:opacity-60 transition-all duration-300 active:scale-95 leading-none -mr-1"
        >
          <span class="mt-[1px]">전체보기</span>
          <span class="material-symbols-outlined text-[16px] ml-1 -mr-1 opacity-70" style="font-variation-settings: 'wght' 500">chevron_right</span>
        </button>
      </div>

      <div v-if="latestSchedule" 
           class="group relative overflow-hidden p-5 bg-[#fcfcfc] rounded-2xl border border-gray-100 cursor-pointer hover:border-[#f3532b]/20 hover:bg-white hover:shadow-md transition-all duration-300" 
           @click="goToDetail">
        <div class="flex items-center gap-4 relative z-10">
            <div class="flex-shrink-0 w-12 h-12 rounded-2xl bg-white shadow-sm border border-gray-100 flex flex-col items-center justify-center">
                <span class="text-[9px] font-black text-[#f3532b] uppercase tracking-tighter">{{ monthStr }}</span>
                <span class="text-lg font-black text-gray-900 -mt-1">{{ dayStr }}</span>
            </div>
            <div class="flex-grow">
              <p class="text-[10px] font-black text-[#f3532b] mb-0.5 opacity-80">{{ latestSchedule.time }}</p>
              <h4 class="text-[15px] font-bold text-gray-900 line-clamp-1 tracking-tight">{{ latestSchedule.title }}</h4>
              <p class="text-[11px] text-gray-500 line-clamp-1 font-medium">{{ latestSchedule.location || '장소 정보 없음' }}</p>
            </div>
            <span class="material-symbols-outlined text-gray-300 group-hover:text-[#f3532b] transition-colors">chevron_right</span>
        </div>
      </div>

      <div v-else class="py-12 text-center bg-gray-50/50 rounded-2xl border border-dashed border-gray-200">
        <div class="w-12 h-12 bg-white rounded-full flex items-center justify-center mx-auto mb-3 shadow-sm">
          <span class="material-symbols-outlined text-gray-300 text-xl">calendar_today</span>
        </div>
        <p class="text-[13px] font-bold text-gray-400">오늘 등록된 일정이 없습니다</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import api from '@/services/api';
import { Logger } from '@/services/logger';

const router = useRouter();
const familyStore = useFamilyStore();
const latestSchedule = ref(null);


const today = new Date();
const monthStr = computed(() => (today.getMonth() + 1) + '월');
const dayStr = computed(() => today.getDate());

const fetchLatestSchedule = async () => {
  try {
    const familyId = familyStore.selectedFamily?.id;
    if (!familyId) return;

    const year = today.getFullYear();
    const month = today.getMonth() + 1;

    
    const response = await api.get(`/families/${familyId}/schedules`, {
      params: { year, month }
    });
    
    const schedules = response.data.data || [];
    
    
    const dateStr = today.getFullYear() + '-' + 
                    String(today.getMonth() + 1).padStart(2, '0') + '-' + 
                    String(today.getDate()).padStart(2, '0');
    
    
    const daySchedules = schedules.filter(s => s.startAt && s.startAt.startsWith(dateStr));
    
    if (daySchedules.length > 0) {
      daySchedules.sort((a, b) => a.startAt.localeCompare(b.startAt));
      const target = daySchedules[0];
      
      const dateObj = new Date(target.startAt);
      const timeStr = new Intl.DateTimeFormat('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      }).format(dateObj);

      latestSchedule.value = {
        ...target,
        time: timeStr,
        location: target.description
      };
    } else {
      latestSchedule.value = null;
    }
  } catch (e) {
    Logger.error("일정 조회 실패:", e);
  }
};

watch(() => familyStore.selectedFamily, () => {
  fetchLatestSchedule();
});

onMounted(() => {
  fetchLatestSchedule();
});

const goToCalendar = () => {
  const familyId = familyStore.selectedFamily?.id;
  if (familyId) {
    
    const todayStr = new Date().getFullYear() + '-' + 
                    String(new Date().getMonth() + 1).padStart(2, '0') + '-' + 
                    String(new Date().getDate()).padStart(2, '0');
    sessionStorage.setItem('calendar_last_date', todayStr);
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
