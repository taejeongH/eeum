<template>
  <div class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-24 relative overflow-hidden">
    <!-- Background Content (Calendar Page Look) -->
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
            <span class="absolute top-1 right-1 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full border-2 border-[#fcfcfc]">22</span>
          </div>
        </div>
      </div>
      <div class="mt-4 text-center">
        <h1 class="text-3xl font-bold text-slate-900">2월</h1>
      </div>
    </header>
    
    <main class="flex-1 px-4 relative">
       <!-- Duplicate Grid for background visual -->
       <div class="calendar-grid text-center mb-6 opacity-30">
        <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>
        <!-- Simplified grid items for visual background -->
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50 text-red-500">25</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">26</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">27</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">28</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">29</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">30</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50 text-blue-500">31</div>
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
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500">8</div>
        <!-- Truncated rest for brevity as it is just background -->
      </div>
    </main>
    
    <BottomNav />

    <!-- Modal Overlay: Wrapper Scroll Pattern for Safety -->
    <div class="fixed inset-0 z-[60] overflow-y-auto" v-if="true">
      <div class="flex min-h-full items-end justify-center"> <!-- items-end positions it at bottom like a sheet -->
        <div class="fixed inset-0 bg-black/40 backdrop-blur-sm" @click="$router.back()"></div> <!-- Backdrop fixed behind card -->
        
        <!-- Card: Natural height, safe from viewport shrinking -->
        <div class="relative w-full bg-white shadow-2xl animate-slide-up rounded-t-[2.5rem] z-10 overflow-hidden">
        <div class="flex justify-center pt-4 pb-2">
          <div class="w-12 h-1.5 bg-slate-300 rounded-full"></div>
        </div>
        <div class="px-6 pb-12 pt-4">
          <header class="flex justify-between items-center mb-8">
            <h2 class="text-2xl font-bold text-slate-900">{{ pageTitle }}</h2>
            <button @click="$router.back()" class="p-2 -ml-2 rounded-full hover:bg-slate-100 transition-colors">
              <svg class="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
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
                        formData.categoryType === cat.value ? 'bg-primary text-white' : 'bg-white text-slate-600 border border-slate-100'
                    ]"
                    type="button">
                    {{ cat.label }}
                </button>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-4">
              <div class="bg-white p-4 rounded-2xl ios-shadow space-y-4">
                <div class="flex items-center justify-between">
                  <span class="text-slate-600 font-medium">시작</span>
                  <div class="flex gap-2">
                    <input v-model="formData.startAtDate" type="date" class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold border-none" />
                    <input v-model="formData.startAtTime" type="time" class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold border-none" />
                  </div>
                </div>
                <div class="h-px bg-slate-100"></div>
                <div class="flex items-center justify-between">
                  <span class="text-slate-600 font-medium">종료</span>
                  <div class="flex gap-2">
                    <input v-model="formData.endAtDate" type="date" class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold border-none" />
                    <input v-model="formData.endAtTime" type="time" class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold border-none" />
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
                  <input v-model="formData.recurrenceEndAt" type="date" class="bg-white border-none rounded-xl p-3 text-sm ios-shadow w-full">
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
        <div class="h-8"></div>
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

const router = useRouter();
const route = useRoute();
const familyStore = useFamilyStore();
const modalStore = useModalStore();

const isEditMode = computed(() => !!route.query.id);
const pageTitle = computed(() => isEditMode.value ? '일정 수정' : '일정 추가');

const formData = ref({
    title: '',
    categoryType: 'VISIT', // Default
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
        console.log("Family not selected, fetching...");
        await familyStore.fetchFamilies();
    }
    console.log("Current Family:", familyStore.selectedFamily);

    // Edit Mode: Fetch existing data
    if (isEditMode.value) {
        try {
            const scheduleId = route.query.id;
            const data = await scheduleService.getSchedule(familyStore.selectedFamily.id, scheduleId);
            
            // Populate form
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
                    console.error("Error parsing startAt", e);
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
                    console.error("Error parsing endAt", e);
                }
            }
        } catch (error) {
            console.error("Failed to load schedule for edit", error);
            await modalStore.openAlert("일정 정보를 불러오는데 실패했습니다.");
            router.back();
        }
    }
});

// Sync end date with start date only when start date changes and recurrence is not set
watch(() => formData.value.startAtDate, (newVal) => {
    if (!isEditMode.value) { // Only sync in create mode to avoid overwriting existing end date
        formData.value.endAtDate = newVal;
    }
});

const isRepeatingYearly = computed(() => formData.value.repeatType === 'YEARLY');
const categories = [
    { label: '방문', value: 'VISIT', class: 'bg-primary text-white' },
    { label: '행사', value: 'EVENT', class: 'bg-white text-slate-600 border border-slate-100' },
    { label: '병원', value: 'MEDICAL', class: 'bg-white text-slate-600 border border-slate-100' },
    { label: '생일', value: 'BIRTHDAY', class: 'bg-white text-slate-600 border border-slate-100' },
    { label: '기일', value: 'MEMORIAL', class: 'bg-white text-slate-600 border border-slate-100' },
    { label: '기념일', value: 'ANNIVERSARY', class: 'bg-white text-slate-600 border border-slate-100' }
];

const selectCategory = (type) => {
    formData.value.categoryType = type;
};

const submitForm = async () => {
    console.log("submitForm called");
    const targetFamilyId = route.params.familyId || familyStore.selectedFamily?.id;

    if (!targetFamilyId) {
        console.error("No family selected");
        await modalStore.openAlert("가족 정보가 없습니다. 다시 시도해주세요.");
        return;
    }

    try {
        const payload = {
            ...formData.value,
            // categoryType이 수정된 값을 따르도록 함 (기본값 fallback 주의)
            categoryType: formData.value.categoryType || 'VISIT',
            startAt: `${formData.value.startAtDate}T${formData.value.startAtTime}:00`,
            endAt: `${formData.value.endAtDate}T${formData.value.endAtTime}:00`,
            isLunar: formData.value.isLunar ? 1 : 0, // 0 또는 1로 전송 (DB 호환성 고려)
            recurrenceEndAt: formData.value.recurrenceEndAt || null, 
            targetPerson: formData.value.targetPerson || null,
            visitPurpose: formData.value.visitPurpose || null,
            visitorName: formData.value.visitorName || null
        };
        console.log("Payload:", payload);
        
        if (isEditMode.value) {
             await scheduleService.updateSchedule(targetFamilyId, route.query.id, payload);
             console.log("Schedule updated successfully");
        } else {
             await scheduleService.createSchedule(targetFamilyId, payload);
             console.log("Schedule created successfully");
        }

        router.back();
    } catch (error) {
        console.error("Failed to save schedule", error);
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
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
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
