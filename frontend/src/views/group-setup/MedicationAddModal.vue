<template>
  <Teleport to="body">
    <transition name="fade">
      <div
        v-if="show"
        class="fixed inset-0 bg-black/50 z-[200] backdrop-blur-sm flex items-center justify-center p-4"
        @click.self="$emit('close')"
      >
        <transition name="pop">
          <!-- 카드 컨테이너 -->
          <div
            v-if="show"
            class="bg-white w-full max-w-lg rounded-2xl shadow-xl overflow-hidden block relative"
          >
            <!-- 헤더 영역 -->
            <div class="bg-white px-6 py-4 border-b flex items-center justify-between">
              <h3 class="text-lg font-bold text-[var(--text-title)]">
                {{ initialData ? '복약 정보 수정' : '복약 정보 추가' }}
              </h3>
              <button
                @click="$emit('close')"
                class="p-2 -mr-2 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            </div>

            <!-- 본문 영역 (스크롤 가능) -->
            <div class="p-6 space-y-6 h-[60vh] min-h-[400px] overflow-y-auto custom-scrollbar">
              <!-- 약 이름 -->
              <div>
                <label class="block text-sm font-semibold text-[var(--text-body)] mb-1.5"
                  >약 이름 <span class="text-red-500">*</span></label
                >
                <input
                  v-model="form.medicineName"
                  type="text"
                  placeholder="예: 혈압약, 비타민"
                  class="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--color-primary-soft)] outline-none transition text-sm"
                />
              </div>

              <!-- 복용 주기 -->
              <div>
                <label class="block text-sm font-semibold text-[var(--text-body)] mb-2"
                  >복용 주기 <span class="text-red-500">*</span></label
                >
                <div class="grid grid-cols-2 gap-3">
                  <button
                    v-for="type in cycleTypes"
                    :key="type.value"
                    @click="form.cycleType = type.value"
                    class="py-2.5 px-3 rounded-lg text-sm font-medium border transition-all duration-200"
                    :class="
                      form.cycleType === type.value
                        ? 'bg-[var(--color-primary-soft)] border-[var(--color-primary)] text-[var(--color-primary)]'
                        : 'border-gray-200 text-gray-500 hover:bg-gray-50'
                    "
                  >
                    {{ type.label }}
                  </button>
                </div>
              </div>

              <!-- 주간 선택 (WEEKLY) -->
              <div
                v-if="form.cycleType === 'WEEKLY'"
                class="bg-gray-50 p-4 rounded-xl border border-gray-100 animate-fade-in-down"
              >
                <label class="block text-xs font-semibold text-gray-500 mb-2">요일 선택</label>
                <div class="flex justify-between">
                  <button
                    v-for="(day, index) in days"
                    :key="day"
                    @click="toggleDay(index)"
                    class="w-10 h-10 rounded-full text-sm font-medium transition-all duration-200"
                    :class="
                      isDaySelected(index)
                        ? 'bg-[var(--color-primary)] text-white shadow-md transform scale-105'
                        : 'bg-white border border-gray-200 text-gray-400 hover:border-gray-300'
                    "
                  >
                    {{ day }}
                  </button>
                </div>
              </div>

              <!-- 복용 기간 -->
              <div>
                <label class="block text-sm font-semibold text-[var(--text-body)] mb-1.5"
                  >복용 기간 <span class="text-red-500">*</span></label
                >
                <div class="flex items-center gap-3">
                  <div class="flex-1">
                    <label class="text-xs text-gray-400 mb-1 block">시작일</label>
                    <EeumDatePicker v-model="form.startDate" placeholder="시작일 선택" />
                  </div>
                  <span class="text-gray-400">~</span>
                  <div class="flex-1 relative">
                    <label class="text-xs text-gray-400 mb-1 block">종료일</label>
                    <EeumDatePicker 
                      v-model="form.endDate" 
                      placeholder="종료일 선택" 
                      :disabled="form.isLifetime"
                      :class="{ 'opacity-50 pointer-events-none': form.isLifetime }"
                    />
                  </div>
                </div>
                <!-- 평생 복용 여부 체크박스 -->
                <div class="mt-2 flex items-center">
                  <input
                    type="checkbox"
                    id="lifetime"
                    v-model="form.isLifetime"
                    class="w-4 h-4 text-[var(--color-primary)] border-gray-300 rounded focus:ring-[var(--color-primary)]"
                  />
                  <label
                    for="lifetime"
                    class="ml-2 text-sm text-gray-600 font-medium cursor-pointer select-none"
                    >평생 복용 (종료일 없음)</label
                  >
                </div>
              </div>

              <!-- 알림 시간 -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="block text-sm font-semibold text-[var(--text-body)]"
                    >알림 시간</label
                  >
                  <button
                    @click="addTime"
                    class="text-xs font-semibold text-[var(--color-primary)] hover:underline flex items-center gap-1"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M12 4v16m8-8H4"
                      ></path>
                    </svg>
                    시간 추가
                  </button>
                </div>

                <div class="space-y-2">
                  <div
                    v-for="(time, index) in form.notificationTimes"
                    :key="index"
                    class="flex items-center gap-2 group"
                  >
                    <div class="relative flex-1">
                      <input 
                        v-model="form.notificationTimes[index]" 
                        type="time" 
                        class="eeum-input"
                      />
                    </div>
                    <button
                      v-if="form.notificationTimes.length > 1"
                      @click="removeTime(index)"
                      class="p-2 text-gray-300 hover:text-red-500 transition rounded-lg hover:bg-red-50"
                    >
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          stroke-width="2"
                          d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                        ></path>
                      </svg>
                    </button>
                  </div>
                  <p
                    v-if="form.notificationTimes.length === 0"
                    class="text-sm text-gray-400 py-2 text-center bg-gray-50 rounded-lg border border-dashed border-gray-200"
                  >
                    알림 시간을 추가해주세요
                  </p>
                </div>
              </div>
            </div>

            <!-- 하단 버튼 영역 -->
            <div class="bg-white p-6 border-t flex gap-3">
              <button
                @click="$emit('close')"
                class="flex-1 py-3.5 rounded-xl border border-gray-200 text-gray-600 font-semibold hover:bg-gray-50 transition"
              >
                취소
              </button>
              <button
                @click="submit"
                class="flex-1 py-3.5 rounded-xl bg-[var(--color-primary)] text-white font-semibold shadow-lg shadow-orange-200 hover:shadow-xl hover:-translate-y-0.5 transition-all"
              >
                {{ initialData ? '수정하기' : '추가하기' }}
              </button>
            </div>
          </div>
        </transition>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { reactive, watch } from 'vue';
import { useModalStore } from '@/stores/modal';
import EeumDatePicker from '@/components/common/EeumDatePicker.vue';

const modalStore = useModalStore();

const props = defineProps({
  show: Boolean,
  initialData: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['close', 'add-medication']);

const cycleTypes = [
  { label: '매일', value: 'DAILY' },
  { label: '매주', value: 'WEEKLY' },
];

const days = ['월', '화', '수', '목', '금', '토', '일'];

const dayBitmasks = [1, 2, 4, 8, 16, 32, 64]; 

const form = reactive({
  medicineName: '',
  cycleType: 'DAILY',
  cycleValue: '',
  daysOfWeek: 0, 
  startDate: new Date().toISOString().split('T')[0],
  endDate: '',
  isLifetime: false,
  notificationTimes: ['09:00'],
});

watch(
  () => form.isLifetime,
  (newVal) => {
    if (newVal) {
      form.endDate = '';
    }
  },
);

const resetForm = () => {
  form.medicineName = '';
  form.cycleType = 'DAILY';
  form.cycleValue = '';
  form.daysOfWeek = 0;
  form.startDate = new Date().toISOString().split('T')[0];
  form.endDate = '';
  form.isLifetime = false;
  form.notificationTimes = ['09:00'];
};


watch(() => props.initialData, (newData) => {
  if (newData) {
    form.medicineName = newData.medicineName;
    form.cycleType = newData.cycleType;
    form.cycleValue = newData.cycleValue;
    form.daysOfWeek = newData.daysOfWeek;
    form.startDate = newData.startDate;
    form.endDate = newData.endDate || '';
    form.isLifetime = !newData.endDate; 
    
    form.notificationTimes = newData.notificationTimes ? [...newData.notificationTimes] : ['09:00'];
  } else {
    resetForm();
  }
}, { immediate: true });


watch(() => props.show, (isShow) => {
  if (isShow && !props.initialData) {
    resetForm();
  }
});

const isDaySelected = (index) => {
  return (form.daysOfWeek & dayBitmasks[index]) !== 0;
};

const toggleDay = (index) => {
  const mask = dayBitmasks[index];
  if (isDaySelected(index)) {
    form.daysOfWeek &= ~mask;
  } else {
    form.daysOfWeek |= mask;
  }
};

const addTime = () => {
  form.notificationTimes.push('12:00');
};

const removeTime = (index) => {
  form.notificationTimes.splice(index, 1);
};

const submit = async () => {
  if (!form.medicineName.trim()) {
    await modalStore.openAlert('약 이름을 입력해주세요.');
    return;
  }
  if (!form.startDate) {
    await modalStore.openAlert('복용 시작일을 입력해주세요.');
    return;
  }
  if (!form.isLifetime && !form.endDate) {
    await modalStore.openAlert('복용 종료일을 입력해주세요.');
    return;
  }
  if (form.cycleType === 'WEEKLY' && form.daysOfWeek === 0) {
    await modalStore.openAlert('요일을 하나 이상 선택해주세요.');
    return;
  }
  
  
  const payload = JSON.parse(JSON.stringify(form));
  emit('add-medication', payload);
  resetForm();
};
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: #f1f1f1;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 10px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #a1a1aa;
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

@keyframes fade-in-down {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.animate-fade-in-down {
  animation: fade-in-down 0.2s ease-out forwards;
}
</style>
