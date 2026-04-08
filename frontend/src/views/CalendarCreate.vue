<template>
  <div
    class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-24 relative overflow-hidden"
  >
    <!-- 배경 콘텐츠 (달력 페이지 스타일) -->
    <header class="sticky top-0 z-10 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex justify-between items-center">
        <button class="p-2 -ml-2 text-slate-600">
          <span class="material-symbols-outlined">menu</span>
        </button>
        <div class="flex gap-4">
          <button class="p-2 text-slate-600">
            <span class="material-symbols-outlined">search</span>
          </button>
          <div class="relative p-2">
            <span class="material-symbols-outlined text-slate-600">notifications</span>
            <span
              class="absolute top-1 right-1 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full border-2 border-[#fcfcfc]"
              >22</span
            >
          </div>
        </div>
      </div>
      <div class="mt-4 text-center relative flex justify-center items-center">
        <span class="absolute left-0 top-1/2 -translate-y-1/2 text-lg font-bold text-slate-900 select-none">
            {{ headerYear }}년
        </span>
        <h1 class="text-2xl font-bold text-slate-900">{{ headerDateText }}</h1>
      </div>
    </header>

    <main class="flex-1 px-4 relative">
      <!-- 배경 시각화를 위한 복제 그리드 -->
      <div class="calendar-grid text-center mb-6 opacity-30">
        <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>
        <!-- 배경용 간소화된 그리드 항목 -->
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50 text-red-500">
          25
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">26</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">27</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">28</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">29</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">30</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50 text-blue-500">
          31
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 font-medium">
          1
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">2</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">3</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>4</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-peach rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">5</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">6</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">7</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500">8</div>
        <!-- 배경용이므로 간결하게 생략됨 -->
      </div>
    </main>

    <BottomNav />

    <!-- 모달 오버레이: 안전성을 위한 래퍼 스크롤 패턴 -->
    <div class="fixed inset-0 z-[60] overflow-y-auto" v-if="true">
      <div class="flex min-h-full items-end justify-center"> <!-- items-end positions it at bottom like a sheet -->
        <div class="fixed inset-0 bg-black/40 backdrop-blur-sm" @click="$router.back()"></div> <!-- Backdrop fixed behind card -->
        
        <!-- Card: Natural height, safe from viewport shrinking -->
        <div 
          ref="sheet"
          class="relative w-full bg-white shadow-2xl rounded-t-[2.5rem] z-10 overflow-hidden transition-transform duration-300 ease-out"
          :class="{ 'animate-slide-up': !isDragging }"
          :style="sheetStyle"
        >
        <div 
          class="flex justify-center pt-4 pb-2 cursor-grab active:cursor-grabbing touch-none"
          @touchstart="onTouchStart"
          @touchmove="onTouchMove"
          @touchend="onTouchEnd"
        >
          <div class="w-12 h-1.5 bg-slate-300 rounded-full"></div>
        </div>
        <div class="px-6 pb-12 pt-4">
          <header class="relative flex items-center justify-center mb-8">
            <button @click="$router.back()" class="absolute left-0 p-2 rounded-full hover:bg-slate-100 transition-colors">
              <svg class="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <h2 class="text-2xl font-bold text-slate-900">{{ pageTitle }}</h2>
          </header>
          <form class="space-y-8" @submit.prevent="submitForm">
            <div class="space-y-2">
              <label class="text-base font-semibold text-slate-700 ml-1">제목</label>
              <input v-model="formData.title" class="w-full bg-white border-none rounded-2xl p-4 text-lg ios-shadow focus:ring-2 focus:ring-primary/50 text-slate-900 placeholder:text-slate-400" placeholder="일정 제목을 입력하세요" type="text"/>
            </div>
            
            <div class="space-y-3">
              <label class="text-base font-semibold text-slate-700 ml-1">일정 구분</label>
              <div class="flex flex-wrap gap-2">
                <button 
                    v-for="cat in categories" 
                    :key="cat.value"
                    @click="selectCategory(cat.value)"
                    :class="[
                      'px-5 py-2.5 rounded-full font-semibold text-sm ios-shadow transition-all',
                      formData.categoryType === cat.value
                        ? 'bg-primary text-white'
                        : 'bg-white text-slate-600 border border-slate-100',
                    ]"
                    type="button"
                  >
                    {{ cat.label }}
                  </button>
                </div>
              </div>

              <div class="grid grid-cols-1 gap-4">
                <div class="bg-white p-4 rounded-2xl ios-shadow space-y-4">
                  <div class="flex items-center gap-3">
                    <span class="text-slate-500 font-bold text-[11px] w-8 shrink-0">시작</span>
                    <div class="flex gap-2 items-center flex-1">
                      <EeumDatePicker
                        v-model="formData.startAtDate"
                        class="flex-[1.4] min-w-[130px]"
                        is-range
                        :start-date="formData.startAtDate"
                        :end-date="formData.endAtDate"
                        @update:start-date="(val) => (formData.startAtDate = val)"
                        @update:end-date="(val) => (formData.endAtDate = val)"
                      />
                      <input
                        v-model="formData.startAtTime"
                        type="time"
                        class="eeum-input !min-h-0 !h-11 !py-0 text-[12px] font-bold !px-2 flex-1 min-w-[80px]"
                      />
                    </div>
                  </div>
                  <div class="h-px bg-slate-100 ms-11"></div>
                  <div class="flex items-center gap-3">
                    <span class="text-slate-500 font-bold text-[11px] w-8 shrink-0">종료</span>
                    <div class="flex gap-2 items-center flex-1">
                      <EeumDatePicker
                        v-model="formData.endAtDate"
                        class="flex-[1.4] min-w-[130px]"
                        is-range
                        :start-date="formData.startAtDate"
                        :end-date="formData.endAtDate"
                        @update:start-date="(val) => (formData.startAtDate = val)"
                        @update:end-date="(val) => (formData.endAtDate = val)"
                      />
                      <input
                        v-model="formData.endAtTime"
                        type="time"
                        class="eeum-input !min-h-0 !h-11 !py-0 text-[12px] font-bold !px-2 flex-1 min-w-[80px]"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <!-- 반복 옵션 -->
              <div class="space-y-2">
                <label class="flex items-center gap-2 text-base font-semibold text-slate-700 ml-1">
                  <input
                    type="checkbox"
                    v-model="formData.repeatType"
                    true-value="YEARLY"
                    false-value="NONE"
                    class="w-5 h-5 rounded text-primary focus:ring-primary"
                  />
                  <span>매년 반복</span>
                </label>
                <div v-if="isRepeatingYearly" class="ml-1 mt-2">
                  <label class="text-xs text-slate-500 block mb-1">반복 종료일</label>
                  <EeumDatePicker v-model="formData.recurrenceEndAt" placeholder="종료일 선택" />
                </div>
              </div>

              <!-- 방문 정보 (카테고리에 따라 조건부 노출) -->
              <div v-if="formData.categoryType === 'VISIT'" class="space-y-4 pt-2">
                <div class="space-y-2">
                  <label class="text-base font-semibold text-slate-700 ml-1">방문자 이름</label>
                  <input
                    v-model="formData.visitorName"
                    type="text"
                    class="w-full bg-white border-none rounded-2xl p-4 text-sm ios-shadow"
                    placeholder="누가 방문하나요?"
                  />
                </div>
                <div class="space-y-2">
                  <label class="text-base font-semibold text-slate-700 ml-1">방문 목적</label>
                  <input
                    v-model="formData.visitPurpose"
                    type="text"
                    class="w-full bg-white border-none rounded-2xl p-4 text-sm ios-shadow"
                    placeholder="방문 목적이 무엇인가요?"
                  />
                </div>
              </div>

              <div class="space-y-2">
                <label class="text-base font-semibold text-slate-700 ml-1">메모</label>
                <textarea
                  v-model="formData.description"
                  class="w-full bg-white border-none rounded-2xl p-4 text-base ios-shadow focus:ring-2 focus:ring-primary/50 text-slate-900 placeholder:text-slate-400 resize-none"
                  placeholder="상세 내용을 입력하세요"
                  rows="4"
                ></textarea>
              </div>
              <div class="flex gap-3 pt-4">
                <button
                  @click="$router.back()"
                  class="flex-1 py-4 rounded-2xl bg-slate-100 text-slate-600 font-bold text-lg"
                  type="button"
                >
                  취소
                </button>
                <button
                  type="submit"
                  class="flex-[2] py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-lg shadow-primary/20"
                >
                  저장하기
                </button>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-4">
              <div class="bg-white p-4 rounded-2xl ios-shadow space-y-4">
                <div class="flex items-center gap-3">
                  <span class="text-slate-500 font-bold text-[11px] w-8 shrink-0">시작</span>
                  <div class="flex gap-2 items-center flex-1">
                    <EeumDatePicker 
                      v-model="formData.startAtDate" 
                      class="flex-[1.4] min-w-[130px]"
                      is-range
                      :start-date="formData.startAtDate"
                      :end-date="formData.endAtDate"
                      @update:start-date="val => formData.startAtDate = val"
                      @update:end-date="val => formData.endAtDate = val"
                    />
                    <input v-model="formData.startAtTime" type="time" class="eeum-input !min-h-0 !h-11 !py-0 text-[12px] font-bold !px-2 flex-1 min-w-[80px]" />
                  </div>
                </div>
                <div class="h-px bg-slate-100 ms-11"></div>
                <div class="flex items-center gap-3">
                  <span class="text-slate-500 font-bold text-[11px] w-8 shrink-0">종료</span>
                  <div class="flex gap-2 items-center flex-1">
                    <EeumDatePicker 
                      v-model="formData.endAtDate" 
                      class="flex-[1.4] min-w-[130px]"
                      is-range
                      :start-date="formData.startAtDate"
                      :end-date="formData.endAtDate"
                      @update:start-date="val => formData.startAtDate = val"
                      @update:end-date="val => formData.endAtDate = val"
                    />
                    <input v-model="formData.endAtTime" type="time" class="eeum-input !min-h-0 !h-11 !py-0 text-[12px] font-bold !px-2 flex-1 min-w-[80px]" />
                  </div>
                </div>
              </div>
            </div>

            <!-- Recurrence Option -->
             <div class="space-y-2">
               <label class="flex items-center gap-2 text-base font-semibold text-slate-700 ml-1">
                 <input type="checkbox" v-model="formData.repeatType" true-value="YEARLY" false-value="NONE" class="w-5 h-5 rounded text-primary focus:ring-primary">
                 <span>매년 반복</span>
               </label>
               <div v-if="isRepeatingYearly" class="ml-1 mt-2">
                  <label class="text-xs text-slate-500 block mb-1">반복 종료일</label>
                  <EeumDatePicker v-model="formData.recurrenceEndAt" placeholder="종료일 선택" />
               </div>
            </div>

            <!-- Visit Info (conditional based on category) -->
            <div v-if="formData.categoryType === 'VISIT'" class="space-y-4 pt-2">
                <div class="space-y-2">
                    <label class="text-base font-semibold text-slate-700 ml-1">방문자 이름</label>
                    <input v-model="formData.visitorName" type="text" class="w-full bg-white border-none rounded-2xl p-4 text-sm ios-shadow" placeholder="누가 방문하나요?">
                </div>
                 <div class="space-y-2">
                    <label class="text-base font-semibold text-slate-700 ml-1">방문 목적</label>
                    <input v-model="formData.visitPurpose" type="text" class="w-full bg-white border-none rounded-2xl p-4 text-sm ios-shadow" placeholder="방문 목적이 무엇인가요?">
                </div>
            </div>

            <div class="space-y-2">
              <label class="text-base font-semibold text-slate-700 ml-1">메모</label>
              <textarea v-model="formData.description" class="w-full bg-white border-none rounded-2xl p-4 text-base ios-shadow focus:ring-2 focus:ring-primary/50 text-slate-900 placeholder:text-slate-400 resize-none" placeholder="상세 내용을 입력하세요" rows="4"></textarea>
            </div>
            <div class="flex gap-3 pt-4">
              <button @click="$router.back()" class="flex-1 py-4 rounded-2xl bg-slate-100 text-slate-600 font-bold text-lg" type="button">취소</button>
              <button type="submit" class="flex-[2] py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-lg shadow-primary/20">저장하기</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import BottomNav from '@/components/layout/BottomNav.vue';
import { scheduleService } from '@/services/scheduleService';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import EeumDatePicker from '@/components/common/EeumDatePicker.vue';
import { Logger } from '@/services/logger';

const router = useRouter();
const route = useRoute();
const familyStore = useFamilyStore();
const modalStore = useModalStore();


const sheet = ref(null);
const touchStartY = ref(0);
const touchCurrentY = ref(0);
const isDragging = ref(false);

const sheetStyle = computed(() => {
  if (!isDragging.value) return {};
  const translateY = Math.max(0, touchCurrentY.value - touchStartY.value);
  return { 
      transform: `translateY(${translateY}px)`, 
      transition: 'none' 
  };
});

const onTouchStart = (e) => {
  touchStartY.value = e.touches[0].clientY;
  touchCurrentY.value = e.touches[0].clientY; 
  isDragging.value = true;
};

const onTouchMove = (e) => {
  if (!isDragging.value) return;
  touchCurrentY.value = e.touches[0].clientY;
  
  
};

const onTouchEnd = () => {
  if (!isDragging.value) return;
  
  const diff = touchCurrentY.value - touchStartY.value;
  isDragging.value = false;
  
  if (diff > 100) { 
    router.back();
  } else {
    
    touchStartY.value = 0;
    touchCurrentY.value = 0;
  }
};

const isEditMode = computed(() => !!route.query.id);
/** @type {import('vue').ComputedRef<string>} 페이지 제목 */
const pageTitle = computed(() => (isEditMode.value ? '일정 수정' : '일정 추가'));

/** @type {import('vue').ComputedRef<string>} 헤더에 표시할 월 텍스트 */
const headerDateText = computed(() => {
  if (!formData.value.startAtDate) return '';
  const [y, m] = formData.value.startAtDate.split('-');
  return `${parseInt(m)}월`;
});

/** @type {import('vue').ComputedRef<string>} 헤더에 표시할 연도 텍스트 */
const headerYear = computed(() => {
  if (!formData.value.startAtDate) return '';
  const [y, m] = formData.value.startAtDate.split('-');
  return y;
});

const headerDateText = computed(() => {
    if (!formData.value.startAtDate) return '';
    const [y, m] = formData.value.startAtDate.split('-');
    return `${parseInt(m)}월`;
});

const headerYear = computed(() => {
    if (!formData.value.startAtDate) return '';
    const [y, m] = formData.value.startAtDate.split('-');
    return y;
});

const formData = ref({
    title: '',
    categoryType: 'VISIT', 
    startAtDate: new Date().toISOString().split('T')[0],
    startAtTime: '12:00',
    endAtDate: new Date().toISOString().split('T')[0],
    endAtTime: '13:00',
    description: '',
    repeatType: 'NONE',
    recurrenceEndAt: '',
    isLunar: false,
    targetPerson: '',
    visitPurpose: '',
    visitorName: ''
});

onMounted(async () => {
    if (!familyStore.selectedFamily) {

        await familyStore.fetchFamilies();
    }


    
    if (isEditMode.value) {
        try {
            const scheduleId = route.query.id;
            const data = await scheduleService.getSchedule(familyStore.selectedFamily.id, scheduleId);
            
            
            formData.value.title = data.title;
            formData.value.categoryType = data.categoryType;
            formData.value.description = data.description;
            formData.value.repeatType = data.repeatType;
            formData.value.recurrenceEndAt = data.recurrenceEndAt;
            formData.value.visitorName = data.visitorName;
            formData.value.visitPurpose = data.visitPurpose;
            formData.value.isLunar = !!data.isLunar;

            if (data.startAt) {
                try {
                    const parts = data.startAt.split('T');
                    if (parts.length > 1) {
                         formData.value.startAtDate = parts[0];
                         formData.value.startAtTime = parts[1].substring(0, 5);
                    }
                } catch (e) {
                    Logger.error("startAt 파싱 오류", e);
                }
            }
            if (data.endAt) {
                try {
                     const parts = data.endAt.split('T');
                     if (parts.length > 1) {
                        formData.value.endAtDate = parts[0];
                        formData.value.endAtTime = parts[1].substring(0, 5);
                     }
                } catch (e) {
                    Logger.error("endAt 파싱 오류", e);
                }
            }
        } catch (error) {
            Logger.error("일정 수정 데이터 로드 실패", error);
            await modalStore.openAlert("일정 정보를 불러오는데 실패했습니다.");
            router.back();
        }
    } else if (route.query.date) {
        
        formData.value.startAtDate = route.query.date;
        formData.value.endAtDate = route.query.date;
    }
  } else if (route.query.date) {
    // 특정 날짜가 지정된 생성 모드
    formData.value.startAtDate = route.query.date;
    formData.value.endAtDate = route.query.date;
  }
});


watch(() => formData.value.startAtDate, (newVal) => {
    if (!isEditMode.value) { 
        formData.value.endAtDate = newVal;
    }
  },
);

const isRepeatingYearly = computed(() => formData.value.repeatType === 'YEARLY');
const categories = [
  { label: '방문', value: 'VISIT', class: 'bg-primary text-white' },
  { label: '행사', value: 'EVENT', class: 'bg-white text-slate-600 border border-slate-100' },
  { label: '병원', value: 'MEDICAL', class: 'bg-white text-slate-600 border border-slate-100' },
  { label: '생일', value: 'BIRTHDAY', class: 'bg-white text-slate-600 border border-slate-100' },
  { label: '기일', value: 'MEMORIAL', class: 'bg-white text-slate-600 border border-slate-100' },
  {
    label: '기념일',
    value: 'ANNIVERSARY',
    class: 'bg-white text-slate-600 border border-slate-100',
  },
];

/**
 * 일성 카테고리를 선택합니다.
 * @param {string} type
 */
const selectCategory = (type) => {
  formData.value.categoryType = type;
};

/**
 * 폼 데이터를 제출하여 일정을 저장하거나 수정합니다.
 */
const submitForm = async () => {

    const targetFamilyId = route.params.familyId || familyStore.selectedFamily?.id;

    if (!targetFamilyId) {
        Logger.error("선택된 가족 없음");
        await modalStore.openAlert("가족 정보가 없습니다. 다시 시도해주세요.");
        return;
    }

    try {
        const payload = {
            ...formData.value,
            
            categoryType: formData.value.categoryType || 'VISIT',
            startAt: `${formData.value.startAtDate}T${formData.value.startAtTime}:00`,
            endAt: `${formData.value.endAtDate}T${formData.value.endAtTime}:00`,
            isLunar: formData.value.isLunar ? 1 : 0, 
            recurrenceEndAt: formData.value.recurrenceEndAt || null, 
            targetPerson: formData.value.targetPerson || null,
            visitPurpose: formData.value.visitPurpose || null,
            visitorName: formData.value.visitorName || null
        };

        
        if (isEditMode.value) {
             await scheduleService.updateSchedule(targetFamilyId, route.query.id, payload);

        } else {
             await scheduleService.createSchedule(targetFamilyId, payload);

        }

        router.back();
    } catch (error) {
        Logger.error("일정 저장 실패", error);
        await modalStore.openAlert('일정 저장에 실패했습니다.');
    }
};
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}
.ios-shadow {
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.05);
}
.modal-overlay {
  background-color: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
}
.modal-content {
  border-top-left-radius: 2.5rem;
  border-top-right-radius: 2.5rem;
}
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}

@keyframes slide-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
.animate-slide-up {
  animation: slide-up 0.3s ease-out;
}
</style>
