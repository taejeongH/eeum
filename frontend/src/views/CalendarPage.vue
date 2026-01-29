<template>
  <div class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-24">
    <header class="sticky top-0 z-20 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex justify-between items-center">
        <button @click="$router.push('/home')" class="p-2 -ml-2 text-slate-600">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <div class="flex gap-4">
          <button class="p-2 text-slate-600">
            <span class="material-symbols-outlined">menu</span>
          </button>
          <div class="relative p-2">
            <span class="material-symbols-outlined text-slate-600">notifications</span>
            <span class="absolute top-1 right-1 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full border-2 border-[#fcfcfc]">22</span>
          </div>
        </div>
      </div>
      <div class="mt-4 text-center">
        <h1 class="text-3xl font-bold text-slate-900">2월</h1>
      </div>
    </header>
    <main class="flex-1 px-4 relative">
      <div class="calendar-grid text-center mb-6">
        <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30 text-red-500">25</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">26</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">27</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">28</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">29</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">30</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30 text-blue-500">31</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 font-medium">1</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">2</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">3</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>4</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-peach rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">5</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">6</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">7</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>8</span>
          <div class="absolute bottom-4 flex gap-0.5">
            <div class="w-2.5 h-1 bg-blue-300 rounded-full"></div>
            <div class="w-2.5 h-1 bg-primary/40 rounded-full"></div>
          </div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>9</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-lavender rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">10</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <div class="absolute inset-0 border-2 border-primary/50 rounded-2xl bg-primary/5"></div>
          <span class="relative z-10 font-bold">11</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-lavender rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">12</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>13</span>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">14</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500">15</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>16</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-sage rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>17</span>
          <div class="absolute bottom-4 w-6 h-1 bg-red-200 rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>18</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-sage rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>19</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-peach rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">20</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">21</div>
      </div>
      <div class="h-px bg-slate-200 w-full mb-6"></div>
      <div class="space-y-4 px-2 pb-24">
        <div class="flex justify-between items-center mb-2">
          <span class="text-sm text-slate-500">음력 {{ month }}월 24일</span> <!-- Placeholder for Lunar Date -->
          <button class="text-slate-400"><span class="material-symbols-outlined">sentiment_satisfied</span></button>
        </div>

        <div v-if="events.length === 0" class="text-center py-10 text-slate-400">
            일정이 없습니다.
        </div>

        <div v-for="event in events" :key="event.scheduleId" 
             @click="goToDetail(event.scheduleId)" 
             class="bg-[#FFFBF7] p-5 rounded-3xl ios-shadow border border-slate-100 transition-all active:scale-[0.98] cursor-pointer mb-3 relative overflow-hidden">
          
          <!-- Visited Badge -->
          <div v-if="event.isVisited" class="absolute top-0 right-0 bg-accent-sage text-[#2d5a3f] text-[10px] font-bold px-2 py-1 rounded-bl-xl">
            방문 완료
          </div>

          <div class="flex items-start gap-4">
            <div class="flex flex-col items-center mt-1 min-w-[3rem]">
              <span class="text-base font-bold text-slate-800">{{ event.startAt.split('T')[1]?.substring(0, 5) || '00:00' }}</span>
            </div>
            
            <div class="w-1.5 h-10 rounded-full" :class="{
                'bg-accent-lavender': event.categoryType === 'FAMILY_EVENT',
                'bg-accent-peach': event.categoryType === 'VISIT', 
                'bg-accent-sage': event.categoryType === 'HEALTH',
                'bg-slate-300': !['FAMILY_EVENT', 'VISIT', 'HEALTH'].includes(event.categoryType)
            }"></div>

            <div class="flex-1">
              <div class="flex items-center gap-2">
                  <h3 class="text-xl font-bold text-slate-900 mb-0.5">{{ event.title }}</h3>
                  <!-- Modified Icon: Display if parentId exists (recurrence exception) -->
                  <span v-if="event.parentId" class="material-symbols-outlined text-sm text-slate-400" title="수정된 반복 일정">edit_calendar</span>
                  <!-- Yearly Icon -->
                  <span v-if="event.repeatType === 'YEARLY'" class="material-symbols-outlined text-sm text-slate-400" title="매년 반복">cached</span>
              </div>
              <p class="text-xs text-slate-500">{{ event.startAt.split('T')[1]?.substring(0, 5) }} - {{ event.endAt.split('T')[1]?.substring(0, 5) }}</p>
            </div>
          </div>
        </div>
      </div>
      <div class="fixed bottom-32 right-6 z-30">
        <button @click="$router.push('/calendar/create')" class="bg-primary text-white w-14 h-14 rounded-full flex items-center justify-center shadow-lg shadow-primary/30 active:scale-95 transition-transform">
          <span class="material-symbols-outlined text-3xl" style="font-variation-settings: 'FILL' 0, 'wght' 600">add</span>
        </button>
      </div>
    </main>
    <BottomNav />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import BottomNav from '@/components/layout/BottomNav.vue';
import { scheduleService } from '@/services/scheduleService';
// Assuming we have a way to get the current family ID, e.g., from a store.
// For now, I'll assume we can get it from localStorage or a simple store if it exists.
import { useFamilyStore } from '@/stores/family'; 

const router = useRouter();
const familyStore = useFamilyStore();

const currentDate = ref(new Date());
const events = ref([]);

const year = computed(() => currentDate.value.getFullYear());
const month = computed(() => currentDate.value.getMonth() + 1);

const fetchCalendarEvents = async () => {
    if (!familyStore.selectedFamily?.id) return;
    
    console.log("Fetching calendar events...");
    try {
        const data = await scheduleService.getMonthlySchedules(familyStore.selectedFamily.id, year.value, month.value);
        events.value = data;
    } catch (error) {
        console.error("Failed to fetch events", error);
    }
};

const goToDetail = (scheduleId) => {
    router.push({ name: 'DetailSchedule', query: { id: scheduleId } });
};

// Simple helper to check if a date has events - rudimentary implementation
// In a real grid, you'd map these to the specific days.
const getEventsForDay = (day) => {
    // This logic needs to be robust for comparing YYYY-MM-DD strings
    // Simulating for now based on the visual requirement
    return [];
};

onMounted(async () => {
    // Ensure family is loaded
    if (!familyStore.selectedFamily) {
        await familyStore.fetchFamilies();
    }
    fetchCalendarEvents();
});

watch([year, month], () => {
    fetchCalendarEvents();
});
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}
.ios-shadow {
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.05);
}
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined'; /* Ensure font family is applied */
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  font-size: 28px;
}
</style>
