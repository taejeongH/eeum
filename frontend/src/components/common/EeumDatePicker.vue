<template>
  <div class="w-full relative">
    <!-- Trigger Input -->
    <div v-if="!$slots.trigger"
      @click="openPicker"
      class="eeum-input flex items-center justify-between cursor-pointer hover:border-[var(--color-primary)] transition-colors group px-2 !min-h-0 !h-11"
      :class="{ '!border-[var(--color-primary)] !ring-4 !ring-orange-100/50': isOpen }"
    >
      <span :class="[modelValue ? 'text-gray-800' : 'text-gray-400', 'text-[12px] font-bold truncate tracking-tight']">
        {{ formattedDisplayDate || placeholder }}
      </span>
      <span class="material-symbols-outlined text-gray-400 group-hover:text-[var(--color-primary)] transition-colors text-base ml-1 flex-shrink-0">
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
          class="fixed inset-0 bg-black/50 z-[300] backdrop-blur-sm flex items-center justify-center p-4"
          @click.self="closePicker"
        >
          <transition name="pop">
            <div class="bg-white w-full max-w-sm rounded-[2.5rem] shadow-2xl overflow-hidden flex flex-col">
              <!-- Calendar Header -->
              <div class="bg-[var(--color-primary)] p-6 text-white">
                <div class="flex justify-between items-center mb-4">
                  <h3 class="text-lg font-bold">날짜 선택</h3>
                  <button @click="closePicker" class="p-1 hover:bg-white/20 rounded-full transition">
                    <span class="material-symbols-outlined">close</span>
                  </button>
                </div>
                <div class="flex items-center justify-between">
                  <button @click="prevMonth" class="p-2 hover:bg-white/20 rounded-full transition">
                    <span class="material-symbols-outlined">chevron_left</span>
                  </button>
                  <div class="text-center">
                    <p class="text-sm opacity-80 font-medium">{{ currentYear }}년</p>
                    <p class="text-2xl font-bold">{{ currentMonthName }}</p>
                  </div>
                  <button @click="nextMonth" class="p-2 hover:bg-white/20 rounded-full transition">
                    <span class="material-symbols-outlined">chevron_right</span>
                  </button>
                </div>
              </div>

              <!-- Calendar Grid -->
              <div class="p-6">
                <!-- Days of Week -->
                <div class="grid grid-cols-7 gap-1 mb-2">
                  <div v-for="day in weekDays" :key="day" class="text-center text-xs font-bold text-gray-400 py-2">
                    {{ day }}
                  </div>
                </div>

                <!-- Days Grid -->
                <div class="grid grid-cols-7 gap-1">
                  <div 
                    v-for="(day, index) in calendarDays" 
                    :key="index"
                    class="aspect-square flex items-center justify-center"
                  >
                    <button
                      v-if="day"
                      @click="selectDate(day)"
                      class="w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold transition-all duration-200"
                      :class="[
                        isToday(day) ? 'bg-orange-50 text-[var(--color-primary)]' : '',
                        isSelected(day) ? '!bg-[var(--color-primary)] !text-white shadow-lg shadow-orange-200 scale-110' : 'text-gray-700 hover:bg-gray-100',
                        !isCurrentMonth(day) ? 'opacity-30' : ''
                      ]"
                    >
                      {{ day.getDate() }}
                    </button>
                  </div>
                </div>
              </div>

              <!-- Footer -->
              <div class="px-6 pb-8 pt-2 flex gap-3">
                <button 
                  @click="setToday" 
                  class="flex-1 py-3.5 rounded-2xl bg-gray-100 text-gray-600 font-bold hover:bg-gray-200 transition active:scale-95"
                >
                  오늘
                </button>
                <button 
                  @click="closePicker" 
                  class="flex-1 py-3.5 rounded-2xl bg-[var(--color-primary)] text-white font-bold shadow-lg shadow-orange-200 hover:shadow-xl transition active:scale-95"
                >
                  확인
                </button>
              </div>
            </div>
          </transition>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '날짜를 선택하세요'
  }
});

const emit = defineEmits(['update:modelValue']);

const isOpen = ref(false);
const viewDate = ref(new Date());

const currentYear = computed(() => viewDate.value.getFullYear());
const currentMonthName = computed(() => {
  return (viewDate.value.getMonth() + 1) + '월';
});

const formattedDisplayDate = computed(() => {
  if (!props.modelValue) return '';
  const date = new Date(props.modelValue);
  if (isNaN(date.getTime())) return props.modelValue;
  // Compact format: 2026. 01. 30.
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}. ${m}. ${d}.`;
});

const weekDays = ['일', '월', '화', '수', '목', '금', '토'];

const calendarDays = computed(() => {
  const year = viewDate.value.getFullYear();
  const month = viewDate.value.getMonth();
  
  const firstDayOfMonth = new Date(year, month, 1);
  const lastDayOfMonth = new Date(year, month + 1, 0);
  
  const days = [];
  const startPadding = firstDayOfMonth.getDay();
  for (let i = 0; i < startPadding; i++) {
    days.push(null);
  }
  for (let i = 1; i <= lastDayOfMonth.getDate(); i++) {
    days.push(new Date(year, month, i));
  }
  return days;
});

const openPicker = () => {
  if (props.modelValue && !isNaN(new Date(props.modelValue).getTime())) {
    viewDate.value = new Date(props.modelValue);
  } else {
    viewDate.value = new Date();
  }
  isOpen.value = true;
};

const closePicker = () => {
  isOpen.value = false;
};

const prevMonth = () => {
  viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() - 1, 1);
};

const nextMonth = () => {
  viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() + 1, 1);
};

const selectDate = (day) => {
  // Format as YYYY-MM-DD local time
  const y = day.getFullYear();
  const m = String(day.getMonth() + 1).padStart(2, '0');
  const d = String(day.getDate()).padStart(2, '0');
  const dateStr = `${y}-${m}-${d}`;
  emit('update:modelValue', dateStr);
};

const setToday = () => {
  const today = new Date();
  const y = today.getFullYear();
  const m = String(today.getMonth() + 1).padStart(2, '0');
  const d = String(today.getDate()).padStart(2, '0');
  const dateStr = `${y}-${m}-${d}`;
  emit('update:modelValue', dateStr);
  viewDate.value = new Date();
};

const isToday = (day) => {
  if (!day) return false;
  const today = new Date();
  return day.getDate() === today.getDate() &&
         day.getMonth() === today.getMonth() &&
         day.getFullYear() === today.getFullYear();
};

const isSelected = (day) => {
  if (!day || !props.modelValue) return false;
  const selected = new Date(props.modelValue);
  if (isNaN(selected.getTime())) return false;
  return day.getDate() === selected.getDate() &&
         day.getMonth() === selected.getMonth() &&
         day.getFullYear() === selected.getFullYear();
};

const isCurrentMonth = (day) => {
  if (!day) return false;
  return day.getMonth() === viewDate.value.getMonth();
};
</script>

<style scoped>
.eeum-input {
  @apply w-full px-4 py-3.5 rounded-2xl border border-gray-200 bg-gray-50 outline-none text-gray-800 font-medium;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.pop-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.pop-leave-active {
  transition: all 0.2s ease-in;
}
.pop-enter-from {
  opacity: 0;
  transform: scale(0.9) translateY(20px);
}
.pop-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
