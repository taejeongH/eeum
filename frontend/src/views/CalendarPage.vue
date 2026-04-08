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
    <div class="sticky top-0 z-20 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-6 pb-4 transiton-all duration-300 border-b border-slate-100">
      
      <!-- Search Input -->
      <div v-if="isSearchOpen" class="mb-4 animate-fade-in">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="일정 검색..."
          class="w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
          autofocus
        />
      </div>

      <!-- Month Navigation -->
      <div v-if="!isSearchOpen" class="mt-0 text-center flex items-center justify-center gap-6 animate-fade-in relative z-20">
        
        <!-- Year Display -->
        <span class="absolute left-0 top-1/2 -translate-y-1/2 text-lg font-bold text-slate-900 select-none">{{ year }}년</span>

        <button @click="prevMonth" class="p-2 text-slate-400 hover:text-slate-600 active:bg-slate-100 rounded-full transition-colors">
            <span class="material-symbols-outlined text-3xl">chevron_left</span>
        </button>
        
        <div @click="openDatePicker" class="flex flex-col items-center cursor-pointer active:scale-95 transition-transform select-none relative group">
             <div class="flex items-center gap-1">
                <h1 class="text-3xl font-bold text-slate-900 leading-none tracking-tight">
                    {{ month }}월
                </h1>
             </div>
        </div>

        <button @click="nextMonth" class="p-2 text-slate-400 hover:text-slate-600 active:bg-slate-100 rounded-full transition-colors">
            <span class="material-symbols-outlined text-3xl">chevron_right</span>
        </button>

        <!-- Today Button -->
        <button 
            @click="resetToToday" 
            class="absolute right-0 top-1/2 -translate-y-1/2 bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-bold px-3 py-1.5 rounded-full transition-colors"
        >
            오늘
        </button>
      </div>
    </div>
    <main class="flex-1 px-4 relative overflow-x-hidden">
      <div class="relative min-h-[440px] overflow-hidden">
        <Transition :name="'slide-' + slideDirection">
        <div 
            :key="year + '-' + month"
            v-if="!isSearchOpen" 
            class="calendar-grid text-center mb-6 select-none touch-pan-y w-full"
            @touchstart="onTouchStart"
            @touchend="onTouchEnd"
        >
            <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>

            <div
              v-for="(day, index) in calendarDays"
              :key="index"
              @click="selectDate(day)"
              class="h-16 flex flex-col items-center justify-start pt-2 relative cursor-pointer rounded-xl transition-colors hover:bg-slate-50"
              :class="{
                'opacity-30': day.type !== 'current',
                'text-red-500': day.date.getDay() === 0,
                'text-blue-500': day.date.getDay() === 6,
              }"
            >
              <!-- 선택된 날짜 하이라이트: 커스텀 프라이머리 스타일 -->
              <div
                v-if="selectedDate === day.dateString"
                class="absolute w-8 h-8 top-1 rounded-full z-0 animate-scale-in"
                style="background-color: #ec856b"
              ></div>
              <!-- 오늘 날짜 하이라이트: 선택되지 않았을 때 테두리 -->
              <div
                v-else-if="isToday(day)"
                class="absolute w-8 h-8 top-1 border-2 border-primary/50 rounded-full bg-primary/5 z-0"
              ></div>

              <span
                class="relative z-10 text-sm"
                :class="{
                  'text-white font-bold': selectedDate === day.dateString,
                  'font-bold': isToday(day) && selectedDate !== day.dateString,
                }"
                >{{ day.day }}</span
              >

              <!-- 이벤트 인디케이터 (바 또는 점) -->
              <div class="absolute bottom-3 flex gap-0.5 justify-center w-full px-1 flex-wrap">
                <!-- 오버플로우 방지를 위해 3-4개로 제한 -->
                <div
                  v-for="(indicator, i) in getIndicatorsForDay(day.dateString)"
                  :key="i"
                  class="w-1.5 h-1.5 rounded-full"
                  :class="indicator.class"
                ></div>
              </div>
            </div>
          </div>
        </Transition>
      </div>
      </Transition>
      </div>
      
      <div class="h-px bg-slate-200 w-full mb-6"></div>
      <div class="space-y-4 px-2 pb-24">
        <div class="flex justify-between items-center mb-2">
          <!-- 선택/검색에 따른 동적 부제목 -->
          <span v-if="searchQuery" class="text-sm text-slate-500"
            >'{{ searchQuery }}' 검색 결과</span
          >
          <span v-else-if="selectedDate" class="text-sm text-slate-500"
            >{{ selectedDate.split('-')[1] }}월 {{ selectedDate.split('-')[2] }}일 일정</span
          >
          <span v-else class="text-sm text-slate-500">전체 일정</span>
        </div>

        <div v-if="filteredEvents.length === 0" class="text-center py-10 text-slate-400">
          일정이 없습니다.
        </div>

        <div
          v-for="event in filteredEvents"
          :key="event.scheduleId"
          @click="goToDetail(event.scheduleId)"
          class="bg-[#FFFBF7] p-5 rounded-3xl ios-shadow border border-slate-100 transition-all active:scale-[0.98] cursor-pointer mb-3 relative overflow-hidden"
        >
          <!-- 방문 완료 뱃지 -->
          <div
            v-if="event.isVisited"
            class="absolute top-0 right-0 bg-accent-sage text-[#2d5a3f] text-[10px] font-bold px-2 py-1 rounded-bl-xl"
          >
            방문 완료
          </div>

          <div class="flex items-start gap-4">
            <div class="flex flex-col items-center mt-1 min-w-[3rem]">
              <span class="text-base font-bold text-slate-800">{{
                event.startAt.split('T')[1]?.substring(0, 5) || '00:00'
              }}</span>
            </div>

            <div
              class="w-1.5 h-10 rounded-full"
              :class="{
                'bg-accent-lavender': event.categoryType === 'FAMILY_EVENT',
                'bg-accent-peach': event.categoryType === 'VISIT',
                'bg-accent-sage': event.categoryType === 'HEALTH',
                'bg-slate-300': !['FAMILY_EVENT', 'VISIT', 'HEALTH'].includes(event.categoryType),
              }"
            ></div>

            <div class="flex-1">
              <div class="flex items-center gap-2">
                <h3 class="text-xl font-bold text-slate-900 mb-0.5">{{ event.title }}</h3>
                <!-- 수정된 아이콘: parentId가 있으면 표시 (반복 일정 예외) -->
                <span
                  v-if="event.parentId"
                  class="material-symbols-outlined text-sm text-slate-400"
                  title="수정된 반복 일정"
                  >edit_calendar</span
                >
                <!-- 매년 반복 아이콘 -->
                <span
                  v-if="event.repeatType === 'YEARLY'"
                  class="material-symbols-outlined text-sm text-slate-400"
                  title="매년 반복"
                  >cached</span
                >
              </div>
              <p class="text-xs text-slate-500">
                {{ event.startAt.split('T')[1]?.substring(0, 5) }} -
                {{ event.endAt.split('T')[1]?.substring(0, 5) }}
              </p>
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
    
    <!-- Custom Wheel Date Picker Modal (Bottom Sheet Style) -->
    <div v-if="isDatePickerModalOpen" class="fixed inset-0 z-[100] flex items-end justify-center">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity" @click="closeDatePicker"></div>
        <div 
            ref="pickerSheet"
            class="bg-white w-full rounded-t-[2rem] shadow-2xl relative z-10 transition-transform duration-300 ease-out pb-10 overflow-hidden"
            :class="{ 'animate-slide-up': !isPickerDragging }"
            :style="pickerSheetStyle"
        >
            <!-- Drag Handle Area -->
            <div 
                class="flex justify-center pt-4 pb-2 cursor-grab active:cursor-grabbing touch-none"
                @touchstart="onPickerTouchStart"
                @touchmove="onPickerTouchMove"
                @touchend="onPickerTouchEnd"
            >
                <div class="w-12 h-1.5 bg-slate-300 rounded-full"></div>
            </div>

            <div class="px-6 pt-2">
                <div class="flex justify-between items-center mb-6">
                    <button @click="closeDatePicker" class="text-slate-500 font-medium px-2 py-1">취소</button>
                    <h3 class="text-lg font-bold text-slate-900">날짜 선택</h3>
                    <button @click="confirmDate" class="text-primary font-bold px-2 py-1">확인</button>
                </div>
            </div>
            
            <div class="relative h-[200px] flex justify-center items-center overflow-hidden">
                <!-- Highlight Bar -->
                <div class="absolute w-full h-[40px] bg-slate-100/50 border-y border-slate-200 pointer-events-none z-0"></div>
                
                <div class="flex w-full z-10">
                    <!-- Year Swiper -->
                    <swiper 
                        :direction="'vertical'"
                        :slides-per-view="5"
                        :centered-slides="true"
                        :initial-slide="initialYearIndex"
                        @slideChange="onYearChange"
                        class="picker-swiper flex-1"
                    >
                        <swiper-slide v-for="y in pickerYears" :key="y">
                            <div class="picker-item">{{ y }}년</div>
                        </swiper-slide>
                    </swiper>

                    <!-- Month Swiper -->
                    <swiper 
                        :direction="'vertical'"
                        :slides-per-view="5"
                        :centered-slides="true"
                        :initial-slide="initialMonthIndex"
                        @slideChange="onMonthChange"
                        class="picker-swiper flex-1"
                    >
                        <swiper-slide v-for="m in 12" :key="m">
                             <div class="picker-item">{{ m }}월</div>
                        </swiper-slide>
                    </swiper>
                    <!-- Day Swiper removed -->
                </div>
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue';
import { useRouter, useRoute } from 'vue-router'; 
import MainHeader from '@/components/MainHeader.vue';
import BottomNav from '@/components/layout/BottomNav.vue';
import IconSearch from '@/components/icons/IconSearch.vue';
import IconClose from '@/components/icons/IconClose.vue';
import { scheduleService } from '@/services/scheduleService';
import { useFamilyStore } from '@/stores/family'; 
import { Swiper, SwiperSlide } from 'swiper/vue';
import 'swiper/css';
import { Logger } from '@/services/logger';

const router = useRouter();
const route = useRoute(); 
const familyStore = useFamilyStore();
const familyId = ref(route.params.familyId); 
const isModalOpen = ref(false);
const slideDirection = ref('next');


const isDatePickerModalOpen = ref(false);
const pickerSelection = ref({ year: 2025, month: 1 });
const pickerYears = Array.from({ length: 100 }, (_, i) => 1950 + i);


const pickerSheet = ref(null);
const pickerTouchStartY = ref(0);
const pickerTouchCurrentY = ref(0);
const isPickerDragging = ref(false);

const pickerSheetStyle = computed(() => {
  if (!isPickerDragging.value) return {};
  const translateY = Math.max(0, pickerTouchCurrentY.value - pickerTouchStartY.value);
  return { 
      transform: `translateY(${translateY}px)`, 
      transition: 'none' 
  };
});

const onPickerTouchStart = (e) => {
  pickerTouchStartY.value = e.touches[0].clientY;
  pickerTouchCurrentY.value = e.touches[0].clientY;
  isPickerDragging.value = true;
};

const onPickerTouchMove = (e) => {
  if (!isPickerDragging.value) return;
  pickerTouchCurrentY.value = e.touches[0].clientY;
};

const onPickerTouchEnd = () => {
  if (!isPickerDragging.value) return;
  const diff = pickerTouchCurrentY.value - pickerTouchStartY.value;
  isPickerDragging.value = false;
  
  if (diff > 100) { 
    closeDatePicker();
  } else {
    
    pickerTouchStartY.value = 0;
    pickerTouchCurrentY.value = 0;
  }
};


const initialYearIndex = computed(() => pickerYears.indexOf(pickerSelection.value.year));
const initialMonthIndex = computed(() => pickerSelection.value.month - 1);

const onYearChange = (swiper) => {
    pickerSelection.value.year = pickerYears[swiper.activeIndex];
};
const onMonthChange = (swiper) => {
    pickerSelection.value.month = swiper.activeIndex + 1;
};

const openDatePicker = () => {
    
    const d = new Date(selectedDate.value);
    pickerSelection.value = {
        year: d.getFullYear(),
        month: d.getMonth() + 1
    };
    isDatePickerModalOpen.value = true;
};

const closeDatePicker = () => {
    isDatePickerModalOpen.value = false;
};

const confirmDate = () => {
    
    const newDate = new Date(pickerSelection.value.year, pickerSelection.value.month - 1, 1);
    currentDate.value = newDate;
    
    
    syncSelectedDateWithView(newDate);
    
    closeDatePicker();
};

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

const currentDate = ref(new Date());
const events = ref([]);

const year = computed(() => currentDate.value.getFullYear());
const month = computed(() => currentDate.value.getMonth() + 1);

/**
 * 서버로부터 해당 월의 일정 목록을 가져옵니다.
 */
const fetchCalendarEvents = async () => {
    
    const targetFamilyId = familyId.value;

    if (!targetFamilyId) return;
    

    try {
        const data = await scheduleService.getMonthlySchedules(targetFamilyId, year.value, month.value);
        events.value = data;
    } catch (error) {
        Logger.error("이벤트 목록 조회 실패", error);
    }
};

/**
 * 일정 상세 페이지로 이동합니다.
 * @param {string|number} scheduleId
 */
const goToDetail = (scheduleId) => {
  router.push({
    name: 'DetailSchedule',
    params: { familyId: familyId.value },
    query: { id: scheduleId },
  });
};


const calendarDays = computed(() => {
  const currYear = currentDate.value.getFullYear();
  const currMonth = currentDate.value.getMonth();

    const firstDayOfMonth = new Date(currYear, currMonth, 1).getDay(); 
    const lastDateOfMonth = new Date(currYear, currMonth + 1, 0).getDate();
    const lastDateOfPrevMonth = new Date(currYear, currMonth, 0).getDate();

  const days = [];

    
    for (let i = firstDayOfMonth - 1; i >= 0; i--) {
        const date = new Date(currYear, currMonth - 1, lastDateOfPrevMonth - i);
        days.push({
            date: date,
            day: lastDateOfPrevMonth - i,
            type: 'prev',
            dateString: toLocalDateString(date)
        });
    }

    
    for (let i = 1; i <= lastDateOfMonth; i++) {
        const date = new Date(currYear, currMonth, i);
        days.push({
            date: date,
            day: i,
            type: 'current',
            dateString: toLocalDateString(date)
        });
    }

    
    
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


const toLocalDateString = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};


const selectedDate = ref(toLocalDateString(new Date())); 
const isSearchOpen = ref(false);
const searchQuery = ref('');


const checkDateMatch = (event, targetDateString) => {
    const targetDate = new Date(targetDateString);
    const startDate = new Date(event.startAt);
    
    
    const eventDateStr = toLocalDateString(startDate);
    if (eventDateStr === targetDateString) return true;

    
    if (event.repeatType === 'YEARLY') {
        const startOfTarget = new Date(targetDate.getFullYear(), targetDate.getMonth(), targetDate.getDate());
        const startOfEvent = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate());

        
        if (startOfTarget < startOfEvent) return false;

        
        if (event.recurrenceEndAt) {
            const endDate = new Date(event.recurrenceEndAt);
            if (startOfTarget > endDate) return false;
        }

        
        return startDate.getMonth() === targetDate.getMonth() && 
               startDate.getDate() === targetDate.getDate();
    }
    
    return false;
};


const getEventsForDay = (dateString) => {
    return events.value.filter(e => checkDateMatch(e, dateString));
};


const getIndicatorsForDay = (dateString) => {
  const dayEvents = getEventsForDay(dateString);
  const hasVisit = dayEvents.some((e) => e.categoryType === 'VISIT');
  const hasOther = dayEvents.some((e) => e.categoryType !== 'VISIT');

  const indicators = [];
  if (hasVisit) indicators.push({ type: 'VISIT', class: 'bg-accent-peach' });
  if (hasOther) indicators.push({ type: 'OTHER', class: 'bg-primary' });

  return indicators;
};


const filteredEvents = computed(() => {
  let result = events.value;

    
    if (searchQuery.value.trim()) {
        const query = searchQuery.value.toLowerCase();
        return result.filter(e => 
            e.title.toLowerCase().includes(query) || 
            (e.description && e.description.toLowerCase().includes(query))
        );
    }

    
    if (selectedDate.value) {
        result = result.filter(e => checkDateMatch(e, selectedDate.value));
    }
    
    
    return result.sort((a, b) => {
        const dateA = new Date(a.startAt);
        const dateB = new Date(b.startAt);
        const timeA = dateA.getHours() * 60 + dateA.getMinutes();
        const timeB = dateB.getHours() * 60 + dateB.getMinutes();
        return timeA - timeB;
    });
});

/**
 * 달력의 특정 날짜를 선택합니다.
 * @param {Object} day
 */
const selectDate = (day) => {
    
    
    
    selectedDate.value = day.dateString;
    
    
    if (day.type === 'prev') prevMonth();
    if (day.type === 'next') nextMonth();
    
    
    sessionStorage.setItem('calendar_last_date', selectedDate.value);
};

/**
 * 검색 바 노출 여부를 토글합니다.
 */
const toggleSearch = () => {
    isSearchOpen.value = !isSearchOpen.value;
    if (!isSearchOpen.value) searchQuery.value = ''; 
};

/**
 * 이벤트 타입에 따른 원형 인디케이터 클래스명을 반환합니다.
 * @param {string} matchStr
 * @returns {string}
 */
const getEventDotClass = (matchStr) => {
    
    return matchStr === 'VISIT' ? 'bg-accent-peach' : 'bg-primary';
};

/**
 * 화면에 표시되는 월과 선택된 날짜를 동기화합니다.
 * (오늘인 경우 오늘 선택, 아니면 1일 선택)
 * @param {Date} newDate
 */
const syncSelectedDateWithView = (newDate) => {
  const today = new Date();
  if (newDate.getFullYear() === today.getFullYear() && newDate.getMonth() === today.getMonth()) {
    selectedDate.value = toLocalDateString(today);
  } else {
    const firstDay = new Date(newDate.getFullYear(), newDate.getMonth(), 1);
    selectedDate.value = toLocalDateString(firstDay);
  }
  currentDate.value = newDate;
};

const syncSelectedDateWithView = (newDate) => {
    
    
    const today = new Date();
    if (newDate.getFullYear() === today.getFullYear() && newDate.getMonth() === today.getMonth()) {
        selectedDate.value = toLocalDateString(today);
    } else {
        const firstDay = new Date(newDate.getFullYear(), newDate.getMonth(), 1);
        selectedDate.value = toLocalDateString(firstDay);
    }
    
    currentDate.value = newDate;
};

const prevMonth = () => {
    slideDirection.value = 'prev';
    const newDate = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() - 1, 1);
    syncSelectedDateWithView(newDate);
};

/**
 * 다음 달로 이동합니다.
 */
const nextMonth = () => {
    slideDirection.value = 'next';
    const newDate = new Date(currentDate.value.getFullYear(), currentDate.value.getMonth() + 1, 1);
    syncSelectedDateWithView(newDate);
};

const resetToToday = () => {
    const today = new Date();
    currentDate.value = today;
    selectedDate.value = toLocalDateString(today);
    sessionStorage.setItem('calendar_last_date', selectedDate.value);
    window.scrollTo({ top: 0, behavior: 'smooth' });
};


const touchStartX = ref(0);
const touchEndX = ref(0);

const onTouchStart = (e) => {
    touchStartX.value = e.changedTouches[0].screenX;
};

const onTouchEnd = (e) => {
    touchEndX.value = e.changedTouches[0].screenX;
    handleSwipe();
};

const handleSwipe = () => {
    const diff = touchStartX.value - touchEndX.value;
    if (Math.abs(diff) > 50) { 
        if (diff > 0) nextMonth(); 
        else prevMonth(); 
    }
};


const isToday = (day) => {
  const today = new Date();
  return (
    day.date.getDate() === today.getDate() &&
    day.date.getMonth() === today.getMonth() &&
    day.date.getFullYear() === today.getFullYear()
  );
};

onMounted(async () => {
    
    if (route.params.familyId) {
        familyId.value = route.params.familyId;
    }
    
    
    const savedDate = sessionStorage.getItem('calendar_last_date');
    if (savedDate) {
        
        if (/^\d{4}-\d{2}-\d{2}$/.test(savedDate)) {
            selectedDate.value = savedDate;
            
            currentDate.value = new Date(savedDate); 
        }
    }
    
    else if (familyStore.selectedFamily?.id) {
         familyId.value = familyStore.selectedFamily.id;
         router.replace({ name: 'CalendarPage', params: { familyId: familyId.value } });
    }
    
    else {
        await familyStore.fetchFamilies();
        if (familyStore.selectedFamily?.id) {
            familyId.value = familyStore.selectedFamily.id;
            router.replace({ name: 'CalendarPage', params: { familyId: familyId.value } });
        }
    }

  // 가능한 경우 상태 복구
  const savedDate = sessionStorage.getItem('calendar_last_date');
  if (savedDate) {
    // 유효한 날짜 형식인지 확인
    if (/^\d{4}-\d{2}-\d{2}$/.test(savedDate)) {
      selectedDate.value = savedDate;
      // 선택된 날짜에 맞춰 표시 월 업데이트
      currentDate.value = new Date(savedDate);
    }
  }
  // 2. Fallback: If no route param but store has selection (e.g. direct nav bug?) -> Correct URL
  else if (familyStore.selectedFamily?.id) {
    familyId.value = familyStore.selectedFamily.id;
    router.replace({ name: 'CalendarPage', params: { familyId: familyId.value } });
  }
  // 3. 폴백: 아무것도 없으면 가져오기
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


watch(() => route.params.familyId, (newId) => {
    if (newId && newId !== familyId.value) {
      familyId.value = newId;
      fetchCalendarEvents();
    }
  },
);


watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily && newFamily.id) {
      if (String(newFamily.id) !== String(route.params.familyId)) {
        router.replace({ name: 'CalendarPage', params: { familyId: newFamily.id } });
      }
    }
  },
);

watch([year, month], () => {
  fetchCalendarEvents();
});

watch(selectedDate, (newVal) => {
  if (newVal) {
    sessionStorage.setItem('calendar_last_date', newVal);
  }
});

watch(selectedDate, (newVal) => {
    if (newVal) {
        sessionStorage.setItem('calendar_last_date', newVal);
    }
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
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
  font-size: 28px;
}
@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.animate-fade-in {
  animation: fade-in 0.2s ease-out;
}
.animate-scale-in {
  animation: scale-in 0.15s ease-out;
}
.bg-primary-light {
  background-color: #ec856b;
}

/* 슬라이드 애니메이션 - 연속 (겹침 처리) */
.slide-next-enter-active,
.slide-next-leave-active,
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition: all 0.3s ease-out;
}

/* 부드러운 슬라이드를 위해 나가는 요소 겹침 처리 */
.slide-next-leave-active,
.slide-prev-leave-active {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  pointer-events: none; /* 나가는 요소에 대한 클릭 방지 */
}

.slide-next-enter-from {
  transform: translateX(100%);
}
.slide-next-leave-to {
  transform: translateX(-100%);
}

.slide-prev-enter-from {
  transform: translateX(-100%);
}
.slide-prev-leave-to {
  transform: translateX(100%);
}

/* Swiper 선택기 스타일 */
.picker-swiper {
  height: 200px;
}
.picker-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%; /* 40px */
  font-size: 16px;
  color: #94a3b8;
  transition: all 0.3s;
}
.swiper-slide-active .picker-item {
  color: #1e293b;
  font-weight: 700;
  font-size: 18px;
  transform: scale(1.1);
}


.slide-next-enter-active, .slide-next-leave-active,
.slide-prev-enter-active, .slide-prev-leave-active {
  transition: all 0.3s ease-out;
}


.slide-next-leave-active, .slide-prev-leave-active {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  pointer-events: none; 
}

.slide-next-enter-from {
  transform: translateX(100%);
}
.slide-next-leave-to {
  transform: translateX(-100%);
}

.slide-prev-enter-from {
  transform: translateX(-100%);
}
.slide-prev-leave-to {
  transform: translateX(100%);
}


.picker-swiper {
  height: 200px;
}
.picker-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%; 
  font-size: 16px;
  color: #94a3b8;
  transition: all 0.3s;
}
.swiper-slide-active .picker-item {
  color: #1e293b;
  font-weight: 700;
  font-size: 18px;
  transform: scale(1.1);
}
</style>
