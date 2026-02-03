<template>
  <div class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-20">
    <MainHeader @modal-state-change="handleModalStateChange" :show-profiles="false">
      <template #actions>
        <button @click="toggleSearch" class="p-2 -mr-2 text-[#1c140d] hover:bg-gray-100 rounded-full transition-colors">
          <IconClose v-if="isSearchOpen" />
          <IconSearch v-else />
        </button>
      </template>
    </MainHeader>
    <div class="sticky top-0 z-20 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-2 pb-4 transiton-all duration-300 border-b border-slate-100">
      
      <!-- Search Input -->
      <div v-if="isSearchOpen" class="mb-4 animate-fade-in">
        <input v-model="searchQuery" type="text" placeholder="일정 검색..." class="w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50" autofocus />
      </div>

      <!-- Month Navigation -->
      <div v-if="!isSearchOpen" class="mt-0 text-center flex items-center justify-center gap-4 animate-fade-in">
        <button @click="prevMonth" class="text-slate-400 hover:text-slate-600">
            <span class="material-symbols-outlined">chevron_left</span>
        </button>
        <h1 @click="resetToToday" class="text-3xl font-bold text-slate-900 cursor-pointer active:scale-95 transition-transform">{{ month }}월</h1>
        <button @click="nextMonth" class="text-slate-400 hover:text-slate-600">
            <span class="material-symbols-outlined">chevron_right</span>
        </button>
      </div>
    </div>
    <main class="flex-1 px-4 relative">
      <div v-if="!isSearchOpen" class="calendar-grid text-center mb-6 select-none">
        <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>

        <div v-for="(day, index) in calendarDays" 
             :key="index" 
             @click="selectDate(day)"
             class="h-16 flex flex-col items-center justify-start pt-2 relative cursor-pointer rounded-xl transition-colors hover:bg-slate-50"
             :class="{
                'opacity-30': day.type !== 'current',
                'text-red-500': day.date.getDay() === 0,
                'text-blue-500': day.date.getDay() === 6
             }"
        >
            <!-- Highlight Selection: custom primary-light style -->
            <div v-if="selectedDate === day.dateString" 
                 class="absolute w-8 h-8 top-1 rounded-full z-0 animate-scale-in"
                 style="background-color: #ec856b"></div>
            <!-- Today highlight: Border if not selected -->
            <div v-else-if="isToday(day)" class="absolute w-8 h-8 top-1 border-2 border-primary/50 rounded-full bg-primary/5 z-0"></div>
            
            <span class="relative z-10 text-sm" 
                  :class="{
                      'text-white font-bold': selectedDate === day.dateString,
                      'font-bold': isToday(day) && selectedDate !== day.dateString
                  }">{{ day.day }}</span>

            <!-- Event Indicators (Bars or Dots) -->
            <div class="absolute bottom-3 flex gap-0.5 justify-center w-full px-1 flex-wrap">
                <!-- Limit to 3-4 dots to prevent overflow -->
                <div v-for="(indicator, i) in getIndicatorsForDay(day.dateString)" :key="i"
                     class="w-1.5 h-1.5 rounded-full"
                     :class="indicator.class"
                ></div>
            </div>
        </div>
      </div>
      <div class="h-px bg-slate-200 w-full mb-6"></div>
      <div class="space-y-4 px-2 pb-24">
        <div class="flex justify-between items-center mb-2">
           <!-- Dynamic Subtitle based on Selection/Search -->
           <span v-if="searchQuery" class="text-sm text-slate-500">'{{ searchQuery }}' 검색 결과</span>
           <span v-else-if="selectedDate" class="text-sm text-slate-500">{{ selectedDate.split('-')[1] }}월 {{ selectedDate.split('-')[2] }}일 일정</span>
           <span v-else class="text-sm text-slate-500">전체 일정</span>
        </div>

        <div v-if="filteredEvents.length === 0" class="text-center py-10 text-slate-400">
            일정이 없습니다.
        </div>

        <div v-for="event in filteredEvents" :key="event.scheduleId" 
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
        <button @click="$router.push({ name: 'CalendarCreate', params: { familyId: familyId }, query: { date: selectedDate } })" class="bg-primary text-white w-14 h-14 rounded-full flex items-center justify-center shadow-lg shadow-primary/30 active:scale-95 transition-transform">
          <span class="material-symbols-outlined text-3xl" style="font-variation-settings: 'FILL' 0, 'wght' 600">add</span>
        </button>
      </div>
    </main>
    <BottomNav v-if="!isModalOpen" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router'; // Added useRoute
import MainHeader from '@/components/MainHeader.vue';
import BottomNav from '@/components/layout/BottomNav.vue';
import IconSearch from '@/components/icons/IconSearch.vue';
import IconClose from '@/components/icons/IconClose.vue';
import { scheduleService } from '@/services/scheduleService';
import { useFamilyStore } from '@/stores/family'; 

const router = useRouter();
const route = useRoute(); // Instance
const familyStore = useFamilyStore();
const familyId = ref(route.params.familyId); // Reactive familyId
const isModalOpen = ref(false);

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

const currentDate = ref(new Date());
const events = ref([]);

const year = computed(() => currentDate.value.getFullYear());
const month = computed(() => currentDate.value.getMonth() + 1);

const fetchCalendarEvents = async () => {
    // Priority: route param -> store -> null
    const targetFamilyId = familyId.value;

    if (!targetFamilyId) return;
    

    try {
        const data = await scheduleService.getMonthlySchedules(targetFamilyId, year.value, month.value);
        events.value = data;
    } catch (error) {
        console.error("Failed to fetch events", error);
    }
};

const goToDetail = (scheduleId) => {
    router.push({ name: 'DetailSchedule', params: { familyId: familyId.value }, query: { id: scheduleId } });
};

// Calendar Logic
const calendarDays = computed(() => {
    const currYear = currentDate.value.getFullYear();
    const currMonth = currentDate.value.getMonth();

    const firstDayOfMonth = new Date(currYear, currMonth, 1).getDay(); // 0 (Sun) - 6 (Sat)
    const lastDateOfMonth = new Date(currYear, currMonth + 1, 0).getDate();
    const lastDateOfPrevMonth = new Date(currYear, currMonth, 0).getDate();

    const days = [];

    // Prev Month Days
    for (let i = firstDayOfMonth - 1; i >= 0; i--) {
        const date = new Date(currYear, currMonth - 1, lastDateOfPrevMonth - i);
        days.push({
            date: date,
            day: lastDateOfPrevMonth - i,
            type: 'prev',
            dateString: toLocalDateString(date)
        });
    }

    // Current Month Days
    for (let i = 1; i <= lastDateOfMonth; i++) {
        const date = new Date(currYear, currMonth, i);
        days.push({
            date: date,
            day: i,
            type: 'current',
            dateString: toLocalDateString(date)
        });
    }

    // Next Month Days (Fill remaining grid up to 35 or 42 cells)
    // Basic 6-row calendar requires 42 cells usually, or just fill to end of week
    const remainingCells = 42 - days.length; 
    for (let i = 1; i <= remainingCells; i++) {
        const date = new Date(currYear, currMonth + 1, i);
        days.push({
            date: date,
            day: i,
            type: 'next',
            dateString: toLocalDateString(date)
        });
    }

    return days;
});

// Helper for local YYYY-MM-DD string
const toLocalDateString = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

// Calendar Logic additions
const selectedDate = ref(toLocalDateString(new Date())); // Default to today's date string 'YYYY-MM-DD'
const isSearchOpen = ref(false);
const searchQuery = ref('');

// For Grid Indicators (Show all events for that day)
const getEventsForDay = (dateString) => {
    return events.value.filter(e => {
        const eventDateStr = toLocalDateString(new Date(e.startAt));
        return eventDateStr === dateString;
    });
};

// Simplified Indicators: One dot per category presence (MAX 2 dots: Visit + Other)
const getIndicatorsForDay = (dateString) => {
    const dayEvents = getEventsForDay(dateString);
    const hasVisit = dayEvents.some(e => e.categoryType === 'VISIT');
    const hasOther = dayEvents.some(e => e.categoryType !== 'VISIT');
    
    const indicators = [];
    if (hasVisit) indicators.push({ type: 'VISIT', class: 'bg-accent-peach' });
    if (hasOther) indicators.push({ type: 'OTHER', class: 'bg-primary' });
    
    return indicators;
};

// Filtered List for Display
const filteredEvents = computed(() => {
    let result = events.value;

    // 1. Filter by Search Query (Priority)
    if (searchQuery.value.trim()) {
        const query = searchQuery.value.toLowerCase();
        return result.filter(e => 
            e.title.toLowerCase().includes(query) || 
            (e.description && e.description.toLowerCase().includes(query))
        );
    }

    // 2. Filter by Selected Date (if no search)
    if (selectedDate.value) {
        result = result.filter(e => {
             const eventDateStr = toLocalDateString(new Date(e.startAt));
             return eventDateStr === selectedDate.value;
        });
    }
    
    // Sort by time
    return result.sort((a, b) => new Date(a.startAt) - new Date(b.startAt));
});

const selectDate = (day) => {
    // Ensure we are selecting the date in current view context or update month if needed
    // For simplicity, just update selectedDate. 
    // If day is prev/next month, we could switch month, but for now just select it.
    selectedDate.value = day.dateString;
    
    // Auto-switch month if clicking prev/next days (Optional UX improvement)
    if (day.type === 'prev') prevMonth();
    if (day.type === 'next') nextMonth();
    
    // Save state
    sessionStorage.setItem('calendar_last_date', selectedDate.value);
};

const toggleSearch = () => {
    isSearchOpen.value = !isSearchOpen.value;
    if (!isSearchOpen.value) searchQuery.value = ''; // Clear query on close
};

const getEventDotClass = (matchStr) => {
    // Simplified: VISIT = peach (orange-ish), Others = primary (default/blue-ish)
    return matchStr === 'VISIT' ? 'bg-accent-peach' : 'bg-primary';
};


const prevMonth = () => {
    currentDate.value = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() - 1, 1);
};

const nextMonth = () => {
    currentDate.value = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() + 1, 1);
};

const resetToToday = () => {
    const today = new Date();
    currentDate.value = today;
    selectedDate.value = toLocalDateString(today);
    sessionStorage.setItem('calendar_last_date', selectedDate.value);
    window.scrollTo({ top: 0, behavior: 'smooth' });
};

// Check if a day is today
const isToday = (day) => {
    const today = new Date();
    return day.date.getDate() === today.getDate() &&
           day.date.getMonth() === today.getMonth() &&
           day.date.getFullYear() === today.getFullYear();
};

onMounted(async () => {
    // 1. Prefer route param if available
    if (route.params.familyId) {
        familyId.value = route.params.familyId;
    }
    
    // Restore state if available
    const savedDate = sessionStorage.getItem('calendar_last_date');
    if (savedDate) {
        // Simple check if it's a valid date string
        if (/^\d{4}-\d{2}-\d{2}$/.test(savedDate)) {
            selectedDate.value = savedDate;
            // Also update view month to match the selected date
            currentDate.value = new Date(savedDate); 
        }
    }
    // 2. Fallback: If no route param but store has selection (e.g. direct nav bug?) -> Correct URL
    else if (familyStore.selectedFamily?.id) {
         familyId.value = familyStore.selectedFamily.id;
         router.replace({ name: 'CalendarPage', params: { familyId: familyId.value } });
    }
    // 3. Fallback: Fetch if nothing
    else {
        await familyStore.fetchFamilies();
        if (familyStore.selectedFamily?.id) {
            familyId.value = familyStore.selectedFamily.id;
            router.replace({ name: 'CalendarPage', params: { familyId: familyId.value } });
        }
    }

    if (familyId.value) {
        fetchCalendarEvents();
    }
});

// React to route changes
watch(() => route.params.familyId, (newId) => {
    if (newId && newId !== familyId.value) {
        familyId.value = newId;
        fetchCalendarEvents();
    }
});

// React to store changes (Header dropdown)
watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily && newFamily.id) {
        if (String(newFamily.id) !== String(route.params.familyId)) {
            router.replace({ name: 'CalendarPage', params: { familyId: newFamily.id } });
        }
    }
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
/* Removed local font-family definition to use global one */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  font-size: 28px;
}
@keyframes fade-in {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in {
  animation: fade-in 0.2s ease-out;
}
@keyframes scale-in {
  from { transform: scale(0.8); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
.animate-scale-in {
  animation: scale-in 0.15s ease-out;
}
.bg-primary-light {
    background-color: #ec856b;
}
</style>
