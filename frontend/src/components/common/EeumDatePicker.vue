<template>
  <div class="w-full relative">
    <!-- Trigger Input -->
    <div v-if="!$slots.trigger"
      @click="openPicker"
      class="eeum-input flex items-center justify-between cursor-pointer hover:border-[#ec856b] transition-colors group px-4 py-3 bg-white rounded-2xl border border-slate-200"
      :class="{ '!border-[#ec856b] !ring-2 !ring-orange-100': isOpen }"
    >
      <span :class="[modelValue ? 'text-slate-800' : 'text-slate-400', 'text-sm font-bold truncate tracking-tight']">
        {{ formattedDisplayDate || placeholder }}
      </span>
      <span class="material-symbols-outlined text-slate-400 group-hover:text-[#ec856b] transition-colors text-xl flex-shrink-0">
        calendar_today
      </span>
    </div>
    
    <!-- Custom Trigger Slot -->
    <div v-else @click="openPicker">
      <slot name="trigger"></slot>
    </div>

    <!-- Modal Overlay -->
    <Teleport to="body">
      <transition name="fade">
        <div 
          v-if="isOpen" 
          class="fixed inset-0 z-[300] flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
          @click.self="closePicker"
        >
          <!-- Central Modal Card -->
          <div 
            class="bg-white w-full max-w-[350px] rounded-[2rem] shadow-2xl relative z-10 overflow-hidden animate-pop-in"
          >
            <!-- Modal Header (Actions) -->
            <div class="px-6 py-4 flex justify-between items-center border-b border-slate-50">
                <button @click="closePicker" class="text-slate-400 hover:text-slate-600 font-medium px-2 py-1 text-sm transition-colors">취소</button>
                <h3 class="text-base font-bold text-slate-800 select-none">{{ isRange ? '기간 선택' : '날짜 선택' }}</h3>
                <button @click="confirmSelection" class="text-[#ec856b] hover:text-[#d46a51] font-bold px-2 py-1 text-sm transition-colors">확인</button>
            </div>

            <!-- Calendar Navigation Header (CalendarPage Style) -->
            <div class="px-6 relative flex items-center justify-center py-4 mb-2 z-20">
                <!-- Left: Year -->
                <span class="absolute left-6 top-1/2 -translate-y-1/2 text-lg font-bold text-slate-900 select-none">
                    {{ currentYear }}년
                </span>

                <!-- Center: Month Nav -->
                <div class="flex items-center gap-2">
                    <button @click="prevMonth" class="text-slate-400 hover:text-slate-600 p-1 active:scale-90 transition-transform">
                        <span class="material-symbols-outlined text-3xl">chevron_left</span>
                    </button>  
                    <div @click="toggleWheelPicker" class="flex items-center justify-center cursor-pointer active:scale-95 transition-transform select-none h-10 min-w-[60px]">
                        <h1 class="text-2xl font-bold text-slate-900 leading-none tracking-tight">{{ currentMonth }}월</h1>
                    </div>
                    <button @click="nextMonth" class="text-slate-400 hover:text-slate-600 p-1 active:scale-90 transition-transform">
                        <span class="material-symbols-outlined text-3xl">chevron_right</span>
                    </button>
                </div>

                <!-- Right: Today -->
                <button 
                    @click="resetToToday"
                    class="absolute right-6 top-1/2 -translate-y-1/2 bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-bold px-3 py-1.5 rounded-full transition-colors"
                >
                    오늘
                </button>
            </div>

             <!-- Content Area -->
             <div class="relative h-[350px]">
                 
                 <!-- Wheel Picker Overlay -->
                 <transition name="fade">
                     <div v-if="isWheelOpen" class="absolute inset-0 bg-white z-30 flex flex-col items-center justify-center pt-4">
                         <div class="w-full px-8 pb-4">
                            <h4 class="text-center font-bold text-slate-400 mb-4 text-sm">연도와 월을 선택하세요</h4>
                            <div class="relative h-[200px] flex justify-center items-center overflow-hidden">
                                <div class="absolute w-full h-[40px] bg-slate-100/50 border-y border-slate-200 pointer-events-none z-0"></div>
                                <div class="flex w-full z-10 px-4 gap-4">
                                    <swiper :direction="'vertical'" :slides-per-view="5" :centered-slides="true" :initial-slide="initialYearIndex" @slideChange="onYearChange" class="h-[200px] flex-1">
                                        <swiper-slide v-for="y in pickerYears" :key="y"><div class="picker-item">{{ y }}년</div></swiper-slide>
                                    </swiper>
                                    <swiper :direction="'vertical'" :slides-per-view="5" :centered-slides="true" :initial-slide="initialMonthIndex" @slideChange="onMonthChange" class="h-[200px] flex-1">
                                        <swiper-slide v-for="m in 12" :key="m"><div class="picker-item">{{ m }}월</div></swiper-slide>
                                    </swiper>
                                </div>
                            </div>
                            <button @click="confirmWheel" class="w-full mt-4 py-3 bg-[#ec856b] text-white rounded-xl font-bold shadow-lg shadow-orange-200 active:scale-95 transition">
                                이동하기
                            </button>
                         </div>
                     </div>
                 </transition>

                 <!-- Calendar Grid -->
                 <div class="absolute inset-0 px-5 pb-8 select-none overflow-hidden"
                      @touchstart="onGridTouchStart"
                      @touchend="onGridTouchEnd"
                 >
                    <div class="grid grid-cols-7 mb-2">
                        <div v-for="(day, i) in weekDays" :key="day" 
                            class="text-center text-[13px] font-bold py-2"
                            :class="[i === 0 ? 'text-red-400' : (i === 6 ? 'text-blue-400' : 'text-slate-400')]"
                        >
                            {{ day }}
                        </div>
                    </div>

                    <transition :name="slideDirection">
                        <div :key="currentMonthKey" class="absolute w-full left-0 px-5 top-[40px]"> 
                            <div class="grid grid-cols-7 gap-y-1 relative w-full">
                                <div v-for="(item, index) in calendarDays" :key="index" class="h-10 flex items-center justify-center relative">
                                    <div v-if="item.type !== 'empty' && isInRange(item.date)" 
                                        class="absolute inset-y-0 w-full bg-orange-100/70"
                                        :class="{ 'rounded-l-full': isRangeStart(item.date), 'rounded-r-full': isRangeEnd(item.date) }"
                                    ></div>
                                    <button v-if="item.type !== 'empty'" @click="selectDate(item.date)" 
                                            class="w-9 h-9 rounded-full flex items-center justify-center text-sm font-semibold transition-all duration-200 relative z-10"
                                            :class="[getDayStatusClass(item.date), getDayColorClass(item.date)]">
                                        {{ item.date.getDate() }}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </transition>
                 </div>
             </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Swiper, SwiperSlide } from 'swiper/vue';
import 'swiper/css';

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '날짜를 선택하세요' },
  isRange: { type: Boolean, default: false },
  startDate: { type: String, default: '' },
  endDate: { type: String, default: '' }
});

const emit = defineEmits(['update:modelValue', 'update:startDate', 'update:endDate']);

const isOpen = ref(false);
const viewDate = ref(new Date());

const slideDirection = ref('slide-next');
const currentMonthKey = computed(() => {
    return `${viewDate.value.getFullYear()}-${viewDate.value.getMonth()}`;
});

const localStart = ref(null);
const localEnd = ref(null);

const currentYear = computed(() => viewDate.value.getFullYear());
const currentMonth = computed(() => viewDate.value.getMonth() + 1);

const formattedDisplayDate = computed(() => {
  if (!props.modelValue) return '';
  const date = new Date(props.modelValue);
  if (isNaN(date.getTime())) return props.modelValue;
  
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const weekDay = ['일', '월', '화', '수', '목', '금', '토'][date.getDay()];
  return `${y}. ${m}. ${d}. (${weekDay})`;
});

const weekDays = ['일', '월', '화', '수', '목', '금', '토'];

const calendarDays = computed(() => {
  const year = viewDate.value.getFullYear();
  const month = viewDate.value.getMonth();
  const days = [];
  
  const firstDayObj = new Date(year, month, 1);
  const startDay = firstDayObj.getDay(); 

  for (let i = 0; i < startDay; i++) days.push({ type: 'empty', date: null });
  
  const lastDate = new Date(year, month + 1, 0).getDate();
  for (let i = 1; i <= lastDate; i++) days.push({ type: 'current', date: new Date(year, month, i) });
  
  const remaining = 42 - days.length;
  for (let i = 0; i < remaining; i++) days.push({ type: 'empty', date: null });
  
  return days;
});

const openPicker = () => {
  if (props.isRange) {
      if(props.startDate) localStart.value = new Date(props.startDate);
      else localStart.value = null;
      if(props.endDate) localEnd.value = new Date(props.endDate);
      else localEnd.value = null;

      if(localStart.value) viewDate.value = new Date(localStart.value);
      else viewDate.value = new Date();
  } else {
      if (props.modelValue && !isNaN(new Date(props.modelValue).getTime())) {
        viewDate.value = new Date(props.modelValue);
      } else {
        viewDate.value = new Date();
      }
  }
  
  isWheelOpen.value = false;
  isOpen.value = true;
};

const closePicker = () => {
  isOpen.value = false;
};

/* Wheel Picker */
const isWheelOpen = ref(false);
const pickerYears = Array.from({ length: 100 }, (_, i) => 1950 + i);
const wheelYear = ref(2025);
const wheelMonth = ref(1);
const initialYearIndex = computed(() => Math.max(0, pickerYears.indexOf(wheelYear.value)));
const initialMonthIndex = computed(() => Math.max(0, wheelMonth.value - 1));

const toggleWheelPicker = () => {
    if(!isWheelOpen.value) {
        wheelYear.value = viewDate.value.getFullYear();
        wheelMonth.value = viewDate.value.getMonth() + 1;
        isWheelOpen.value = true;
    } else {
        isWheelOpen.value = false;
    }
};
const onYearChange = (swiper) => wheelYear.value = pickerYears[swiper.activeIndex];
const onMonthChange = (swiper) => wheelMonth.value = swiper.activeIndex + 1;
const confirmWheel = () => {
    viewDate.value = new Date(wheelYear.value, wheelMonth.value - 1, 1);
    isWheelOpen.value = false;
};

/* Navigation */
const prevMonth = () => {
    slideDirection.value = 'slide-prev';
    viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() - 1, 1);
};
const nextMonth = () => {
    slideDirection.value = 'slide-next';
    viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() + 1, 1);
};
const resetToToday = () => {
    viewDate.value = new Date();
    if(!props.isRange) {
        // Option: Select today? or just view?
        // User behavior: just view 'today' context
    }
};

/* Gestures (Swipe Only) */
const gridTouchStartX = ref(0);
const gridTouchEndX = ref(0);
const onGridTouchStart = (e) => { gridTouchStartX.value = e.changedTouches[0].screenX; };
const onGridTouchEnd = (e) => {
    gridTouchEndX.value = e.changedTouches[0].screenX;
    const diff = gridTouchStartX.value - gridTouchEndX.value;
    if (Math.abs(diff) > 50) {
        if (diff > 0) nextMonth(); 
        else prevMonth();
    }
};

const selectDate = (day) => {
  if (props.isRange) {
      if (!localStart.value || (localStart.value && localEnd.value)) {
          localStart.value = day;
          localEnd.value = null;
      } else {
          if (day < localStart.value) {
              localEnd.value = localStart.value;
              localStart.value = day;
          } else {
              localEnd.value = day;
          }
      }
  } else {
      const dateStr = formatDate(day);
      emit('update:modelValue', dateStr);
  }
};

const confirmSelection = () => {
  if (props.isRange) {
      if (localStart.value) emit('update:startDate', formatDate(localStart.value));
      if (localEnd.value) emit('update:endDate', formatDate(localEnd.value));
      else if (localStart.value) emit('update:endDate', formatDate(localStart.value)); 
      
      // IMPORTANT: Do NOT emit update:modelValue here to prevent overwriting the triggering input's specific model
      // if (localStart.value) emit('update:modelValue', formatDate(localStart.value));
  }
  closePicker();
};

const formatDate = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
};

const isSameDay = (d1, d2) => d1 && d2 && d1.getDate() === d2.getDate() && d1.getMonth() === d2.getMonth() && d1.getFullYear() === d2.getFullYear();
const isToday = (day) => isSameDay(day, new Date());
const isSelected = (day) => {
    if (props.isRange) return isSameDay(day, localStart.value) || isSameDay(day, localEnd.value);
    return props.modelValue && isSameDay(day, new Date(props.modelValue));
};
const isRangeStart = (day) => props.isRange && isSameDay(day, localStart.value) && localEnd.value;
const isRangeEnd = (day) => props.isRange && isSameDay(day, localEnd.value) && localStart.value;
const isInRange = (day) => {
    if (!props.isRange || !localStart.value || !localEnd.value) return false;
    return day > localStart.value && day < localEnd.value;
};
const getDayStatusClass = (day) => {
    const selected = isSelected(day);
    if (selected) return 'bg-[#ec856b] text-white shadow-md shadow-primary/30 scale-105';
    if (isToday(day)) return 'bg-orange-50 text-[#ec856b] ring-1 ring-[#ec856b]/30';
    if (isInRange(day)) return 'text-[#ec856b] font-bold'; 
    return 'text-slate-700 hover:bg-slate-100 active:scale-95';
};
const getDayColorClass = (day) => {
    if (isSelected(day) || isInRange(day)) return '';
    const d = day.getDay();
    if (d === 0) return 'text-red-500 font-medium';
    if (d === 6) return 'text-blue-500 font-medium';
    return '';
};
</script>

<style scoped>
.eeum-input { /* Tailwind */ }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.animate-pop-in { animation: popIn 0.3s cubic-bezier(0.16, 1, 0.3, 1); }

@keyframes popIn {
    from { opacity: 0; transform: scale(0.95); }
    to { opacity: 1; transform: scale(1); }
}

.picker-item {
  display: flex; align-items: center; justify-content: center; height: 100%;
  font-size: 18px; color: #94a3b8; transition: all 0.3s;
}
.swiper-slide-active .picker-item {
  color: #ec856b; font-weight: 700; transform: scale(1.1);
}
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-variation-settings: 'FILL' 0, 'wght' 500, 'GRAD' 0, 'opsz' 24;
}
/* Slide Animations */
.slide-next-enter-active, .slide-next-leave-active,
.slide-prev-enter-active, .slide-prev-leave-active {
  transition: all 0.3s ease-out;
  position: absolute;
  width: 100%;
}
.slide-next-enter-from { transform: translateX(100%); }
.slide-next-leave-to { transform: translateX(-100%); }
.slide-prev-enter-from { transform: translateX(-100%); }
.slide-prev-leave-to { transform: translateX(100%); }
</style>
