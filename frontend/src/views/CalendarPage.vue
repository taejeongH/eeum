<template>
  <div class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-20">
    <MainHeader @modal-state-change="handleModalStateChange" :show-profiles="false">
      <template #actions>
        <button
          @click="toggleSearch"
          class="p-2 -mr-2 text-[#1c140d] hover:bg-gray-100 rounded-full transition-colors"
        >
          <IconClose v-if="isSearchOpen" />
          <IconSearch v-else />
        </button>
      </template>
    </MainHeader>
    <div
      class="sticky top-0 z-20 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-6 pb-4 transiton-all duration-300 border-b border-slate-100"
    >
      <!-- 검색 입력창 -->
      <div v-if="isSearchOpen" class="mb-4 animate-fade-in">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="일정 검색..."
          class="w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50"
          autofocus
        />
      </div>

      <!-- 월 이동 내비게이션 -->
      <div
        v-if="!isSearchOpen"
        class="mt-0 text-center flex items-center justify-center gap-6 animate-fade-in relative z-20"
      >
        <!-- 연도 표시 -->
        <span
          class="absolute left-0 top-1/2 -translate-y-1/2 text-lg font-bold text-slate-900 select-none"
          >{{ year }}년</span
        >

        <button
          @click="prevMonth"
          class="p-2 text-slate-400 hover:text-slate-600 active:bg-slate-100 rounded-full transition-colors"
        >
          <span class="material-symbols-outlined text-3xl">chevron_left</span>
        </button>

        <div
          @click="openDatePicker"
          class="flex flex-col items-center cursor-pointer active:scale-95 transition-transform select-none relative group"
        >
          <div class="flex items-center gap-1">
            <h1 class="text-3xl font-bold text-slate-900 leading-none tracking-tight">
              {{ month }}월
            </h1>
          </div>
        </div>

        <button
          @click="nextMonth"
          class="p-2 text-slate-400 hover:text-slate-600 active:bg-slate-100 rounded-full transition-colors"
        >
          <span class="material-symbols-outlined text-3xl">chevron_right</span>
        </button>

        <!-- 오늘 버튼 -->
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
        <button
          @click="
            $router.push({
              name: 'CalendarCreate',
              params: { familyId: familyId },
              query: { date: selectedDate },
            })
          "
          class="bg-primary text-white w-14 h-14 rounded-full flex items-center justify-center shadow-lg shadow-primary/30 active:scale-95 transition-transform"
        >
          <span
            class="material-symbols-outlined text-3xl"
            style="
              font-variation-settings:
                'FILL' 0,
                'wght' 600;
            "
            >add</span
          >
        </button>
      </div>
    </main>

    <BottomNav v-if="!isModalOpen" />

    <!-- 커스텀 휠 날짜 선택기 모달 (바텀 시트 스타일) -->
    <div v-if="isDatePickerModalOpen" class="fixed inset-0 z-[100] flex items-end justify-center">
      <div
        class="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity"
        @click="closeDatePicker"
      ></div>
      <div
        ref="pickerSheet"
        class="bg-white w-full rounded-t-[2rem] shadow-2xl relative z-10 transition-transform duration-300 ease-out pb-10 overflow-hidden"
        :class="{ 'animate-slide-up': !isPickerDragging }"
        :style="pickerSheetStyle"
      >
        <!-- 드래그 핸들 영역 -->
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
            <button @click="closeDatePicker" class="text-slate-500 font-medium px-2 py-1">
              취소
            </button>
            <h3 class="text-lg font-bold text-slate-900">날짜 선택</h3>
            <button @click="confirmDate" class="text-primary font-bold px-2 py-1">확인</button>
          </div>
        </div>

        <div class="relative h-[200px] flex justify-center items-center overflow-hidden">
          <!-- 하이라이트 바 -->
          <div
            class="absolute w-full h-[40px] bg-slate-100/50 border-y border-slate-200 pointer-events-none z-0"
          ></div>

          <div class="flex w-full z-10">
            <!-- 연도 스와이퍼 -->
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

            <!-- 월 스와이퍼 -->
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
            <!-- 일 스와이퍼 제거됨 -->
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
const route = useRoute(); // Instance
const familyStore = useFamilyStore();
const familyId = ref(route.params.familyId); // Reactive familyId
const isModalOpen = ref(false);
const slideDirection = ref('next');

// 휠 선택기 상태
const isDatePickerModalOpen = ref(false);
const pickerSelection = ref({ year: 2025, month: 1 });
const pickerYears = Array.from({ length: 100 }, (_, i) => 1950 + i);

// 선택기 드래그 로직
const pickerSheet = ref(null);
const pickerTouchStartY = ref(0);
const pickerTouchCurrentY = ref(0);
const isPickerDragging = ref(false);

/** @type {import('vue').ComputedRef<Object>} Picker Sheet의 드래그 스타일 */
const pickerSheetStyle = computed(() => {
  if (!isPickerDragging.value) return {};
  const translateY = Math.max(0, pickerTouchCurrentY.value - pickerTouchStartY.value);
  return {
    transform: `translateY(${translateY}px)`,
    transition: 'none',
  };
});

/**
 * Picker 터치 시작 핸들러
 * @param {TouchEvent} e
 */
const onPickerTouchStart = (e) => {
  pickerTouchStartY.value = e.touches[0].clientY;
  pickerTouchCurrentY.value = e.touches[0].clientY;
  isPickerDragging.value = true;
};

/**
 * Picker 터치 이동 핸들러
 * @param {TouchEvent} e
 */
const onPickerTouchMove = (e) => {
  if (!isPickerDragging.value) return;
  pickerTouchCurrentY.value = e.touches[0].clientY;
};

/**
 * Picker 터치 종료 핸들러
 */
const onPickerTouchEnd = () => {
  if (!isPickerDragging.value) return;
  const diff = pickerTouchCurrentY.value - pickerTouchStartY.value;
  isPickerDragging.value = false;

  if (diff > 100) {
    // 100px 이상 드래그 시 닫기
    closeDatePicker();
  } else {
    // 원위치로 복구
    pickerTouchStartY.value = 0;
    pickerTouchCurrentY.value = 0;
  }
};

// Swiper 초기 슬라이드를 위한 계산된 인덱스
/** @type {import('vue').ComputedRef<number>} Picker 초기 연도 인덱스 */
const initialYearIndex = computed(() => pickerYears.indexOf(pickerSelection.value.year));
/** @type {import('vue').ComputedRef<number>} Picker 초기 월 인덱스 */
const initialMonthIndex = computed(() => pickerSelection.value.month - 1);

/**
 * Picker 연도 변경 이벤트 핸들러
 * @param {Object} swiper
 */
const onYearChange = (swiper) => {
  pickerSelection.value.year = pickerYears[swiper.activeIndex];
};
/**
 * Picker 월 변경 이벤트 핸들러
 * @param {Object} swiper
 */
const onMonthChange = (swiper) => {
  pickerSelection.value.month = swiper.activeIndex + 1;
};

/**
 * 날짜 선택기(DatePicker) 모달을 엽니다.
 */
const openDatePicker = () => {
  const d = new Date(selectedDate.value);
  pickerSelection.value = {
    year: d.getFullYear(),
    month: d.getMonth() + 1,
  };
  isDatePickerModalOpen.value = true;
};

/**
 * 날짜 선택기 모달을 닫습니다.
 */
const closeDatePicker = () => {
  isDatePickerModalOpen.value = false;
};

/**
 * 선택한 날짜를 확정하고 달력을 해당 월로 이동시킵니다.
 */
const confirmDate = () => {
  const newDate = new Date(pickerSelection.value.year, pickerSelection.value.month - 1, 1);
  currentDate.value = newDate;

  // 오늘의 연/월과 일치하면 오늘을 선택, 아니면 1일을 선택
  syncSelectedDateWithView(newDate);

  closeDatePicker();
};

/**
 * 모달 상태 변경 시의 핸들러
 * @param {boolean} isOpen
 */
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
    Logger.error('이벤트 목록 조회 실패', error);
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

// 달력 로직
/** @type {import('vue').ComputedRef<Array<Object>>} 달력에 표시할 날짜 객체 배열 */
const calendarDays = computed(() => {
  const currYear = currentDate.value.getFullYear();
  const currMonth = currentDate.value.getMonth();

  const firstDayOfMonth = new Date(currYear, currMonth, 1).getDay();
  const lastDateOfMonth = new Date(currYear, currMonth + 1, 0).getDate();
  const lastDateOfPrevMonth = new Date(currYear, currMonth, 0).getDate();

  const days = [];

  // 이전 달 날짜 채우기
  for (let i = firstDayOfMonth - 1; i >= 0; i--) {
    const date = new Date(currYear, currMonth - 1, lastDateOfPrevMonth - i);
    days.push({
      date: date,
      day: lastDateOfPrevMonth - i,
      type: 'prev',
      dateString: toLocalDateString(date),
    });
  }

  // 이번 달 날짜 채우기
  for (let i = 1; i <= lastDateOfMonth; i++) {
    const date = new Date(currYear, currMonth, i);
    days.push({
      date: date,
      day: i,
      type: 'current',
      dateString: toLocalDateString(date),
    });
  }

  // 다음 달 날짜 채우기 (기본 6주 42칸 기준)
  const remainingCells = 42 - days.length;
  for (let i = 1; i <= remainingCells; i++) {
    const date = new Date(currYear, currMonth + 1, i);
    days.push({
      date: date,
      day: i,
      type: 'next',
      dateString: toLocalDateString(date),
    });
  }

  return days;
});

/**
 * Date 객체를 YYYY-MM-DD 형식의 문자열로 변환합니다.
 * @param {Date} date
 * @returns {string}
 */
const toLocalDateString = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

// 달력 로직 추가
const selectedDate = ref(toLocalDateString(new Date())); // Default to today's date string 'YYYY-MM-DD'
const isSearchOpen = ref(false);
const searchQuery = ref('');

// Helper to check if event matches date (Expanded for Recurrence)
/**
 * 일정이 해당 날짜와 일치하는지 확인합니다. (반복 일정 처리 포함)
 * @param {Object} event
 * @param {string} targetDateString
 * @returns {boolean}
 */
const checkDateMatch = (event, targetDateString) => {
  const targetDate = new Date(targetDateString);
  const startDate = new Date(event.startAt);

  // 1. 단순 날짜 일치 확인
  const eventDateStr = toLocalDateString(startDate);
  if (eventDateStr === targetDateString) return true;

  // 2. 반복 일정(매년) 확인
  if (event.repeatType === 'YEARLY') {
    const startOfTarget = new Date(
      targetDate.getFullYear(),
      targetDate.getMonth(),
      targetDate.getDate(),
    );
    const startOfEvent = new Date(
      startDate.getFullYear(),
      startDate.getMonth(),
      startDate.getDate(),
    );

    // 시작일 이후여야 함
    if (startOfTarget < startOfEvent) return false;

    // 반복 종료일 이전이어야 함 (정보가 있는 경우)
    if (event.recurrenceEndAt) {
      const endDate = new Date(event.recurrenceEndAt);
      if (startOfTarget > endDate) return false;
    }

    // 월과 일이 일치하는지 확인
    return (
      startDate.getMonth() === targetDate.getMonth() && startDate.getDate() === targetDate.getDate()
    );
  }

  return false;
};

// 그리드 인디케이터용 (해당 날짜의 모든 이벤트 표시)
/**
 * 특정 날짜에 해당하는 모든 이벤트를 반환합니다.
 * @param {string} dateString
 * @returns {Array}
 */
const getEventsForDay = (dateString) => {
  return events.value.filter((e) => checkDateMatch(e, dateString));
};

// 간소화된 인디케이터: 카테고리별 점 하나 (최대 2개: 방문 + 기타)
/**
 * 해당 날짜의 상태 인디케이터(점) 목록을 반환합니다.
 * @param {string} dateString
 * @returns {Array}
 */
const getIndicatorsForDay = (dateString) => {
  const dayEvents = getEventsForDay(dateString);
  const hasVisit = dayEvents.some((e) => e.categoryType === 'VISIT');
  const hasOther = dayEvents.some((e) => e.categoryType !== 'VISIT');

  const indicators = [];
  if (hasVisit) indicators.push({ type: 'VISIT', class: 'bg-accent-peach' });
  if (hasOther) indicators.push({ type: 'OTHER', class: 'bg-primary' });

  return indicators;
};

// 화면 표시용 필터링된 목록
/** @type {import('vue').ComputedRef<Array<Object>>} 검색어 또는 선택된 날짜에 따라 필터링된 일정 목록 */
const filteredEvents = computed(() => {
  let result = events.value;

  // 1. 검색어 기준 필터링 (최우선)
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    return result.filter(
      (e) =>
        e.title.toLowerCase().includes(query) ||
        (e.description && e.description.toLowerCase().includes(query)),
    );
  }

  // 2. 선택된 날짜 기준 필터링
  if (selectedDate.value) {
    result = result.filter((e) => checkDateMatch(e, selectedDate.value));
  }

  // 시간순으로 정렬
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

  // 클릭한 날짜가 이전/다음 달인 경우 해당 월로 자동 이동
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

/**
 * 이전 달로 이동합니다.
 */
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

/**
 * 달력 뷰를 오늘 날짜로 초기화합니다.
 */
const resetToToday = () => {
  const today = new Date();
  currentDate.value = today;
  selectedDate.value = toLocalDateString(today);
  sessionStorage.setItem('calendar_last_date', selectedDate.value);
  window.scrollTo({ top: 0, behavior: 'smooth' });
};

// 스와이프 로직
const touchStartX = ref(0);
const touchEndX = ref(0);

/**
 * 달력 스와이프 시작 핸들러
 * @param {TouchEvent} e
 */
const onTouchStart = (e) => {
  touchStartX.value = e.changedTouches[0].screenX;
};

/**
 * 달력 스와이프 종료 핸들러
 * @param {TouchEvent} e
 */
const onTouchEnd = (e) => {
  touchEndX.value = e.changedTouches[0].screenX;
  handleSwipe();
};

/**
 * 스와이프 방향에 따라 월 이동을 처리합니다.
 */
const handleSwipe = () => {
  const diff = touchStartX.value - touchEndX.value;
  if (Math.abs(diff) > 50) {
    if (diff > 0)
      nextMonth(); // 왼쪽 스와이프 -> 다음 달
    else prevMonth(); // 오른쪽 스와이프 -> 이전 달
  }
};

/**
 * 해당 날짜가 오늘인지 확인합니다.
 * @param {Object} day
 * @returns {boolean}
 */
const isToday = (day) => {
  const today = new Date();
  return (
    day.date.getDate() === today.getDate() &&
    day.date.getMonth() === today.getMonth() &&
    day.date.getFullYear() === today.getFullYear()
  );
};

onMounted(async () => {
  // 1. 가능한 경우 라우트 파라미터 우선 사용
  if (route.params.familyId) {
    familyId.value = route.params.familyId;
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

// 라우트 변경에 대응
watch(
  () => route.params.familyId,
  (newId) => {
    if (newId && newId !== familyId.value) {
      familyId.value = newId;
      fetchCalendarEvents();
    }
  },
);

// 스토어 변경에 대응 (헤더 드롭다운)
watch(
  () => familyStore.selectedFamily,
  (newFamily) => {
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
</style>
