<template>
  <div class="bg-background-light text-slate-800 min-h-screen flex flex-col" v-if="schedule">
    <header class="sticky top-0 z-20 bg-background-light/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex items-center">
        <button @click="$router.back()" class="p-2 -ml-2 text-slate-600">
          <span class="material-symbols-outlined">arrow_back_ios</span>
        </button>
        <h1 class="flex-1 text-center text-xl font-bold text-slate-900 mr-8">일정 상세</h1>
      </div>
    </header>
    <main class="flex-1 px-6 pt-4 pb-24">
      <div class="space-y-8">
        <div class="space-y-1">
          <label class="text-sm font-semibold text-primary">제목</label>
          <h2 class="text-2xl font-bold text-slate-900">{{ schedule.title }}</h2>
        </div>
        <div class="grid grid-cols-1 gap-6">
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 rounded-full bg-accent-lavender flex items-center justify-center">
              <span class="material-symbols-outlined text-slate-600">person</span>
            </div>
            <div>
              <p class="text-xs text-slate-500">등록자</p>
              <!-- Visitor Name logic if present, else fallback -->
              <p class="text-base font-semibold">{{ schedule.visitorName || '가족 구성원' }}</p>
            </div>
          </div>
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 rounded-full bg-accent-peach flex items-center justify-center">
              <span class="material-symbols-outlined text-slate-600">{{ categoryIcon }}</span>
            </div>
            <div>
              <p class="text-xs text-slate-500">일정 구분</p>
              <p class="text-base font-semibold">{{ categoryLabel }}</p>
            </div>
          </div>
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 rounded-full bg-accent-sage flex items-center justify-center">
              <span class="material-symbols-outlined text-slate-600">calendar_today</span>
            </div>
            <div>
              <p class="text-xs text-slate-500">날짜 및 시간</p>
              <p class="text-base font-semibold">{{ startFormatted }}</p>
              <p class="text-sm text-slate-600">{{ timeRange }}</p>
            </div>
          </div>
          
          <!-- Additional Visit Info Section -->
          <div v-if="schedule.visitPurpose" class="pt-2">
             <p class="text-xs text-slate-500 mb-2">방문 목적</p>
             <div class="bg-white p-4 rounded-2xl border border-slate-100 font-medium">
                {{ schedule.visitPurpose }}
             </div>
          </div>

          <div class="pt-2">
            <p class="text-xs text-slate-500 mb-2">상세 내용</p>
            <div class="bg-surface-light p-4 rounded-2xl border border-slate-100 min-h-[120px]">
              <p class="text-base leading-relaxed text-slate-700 whitespace-pre-wrap">
                {{ schedule.description || '상세 내용이 없습니다.' }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </main>
    <div class="fixed bottom-0 left-0 right-0 px-6 py-4 flex gap-3 bg-gradient-to-t from-background-light via-background-light/90 to-transparent">
      <button @click="confirmDelete" class="flex-1 py-4 bg-slate-200 text-slate-700 font-bold rounded-2xl active:scale-[0.98] transition-all">
        삭제
      </button>
      <button @click="router.push({ path: '/calendar/create', query: { id: scheduleId } })" class="flex-[2] py-4 bg-primary text-white font-bold rounded-2xl shadow-lg shadow-primary/20 active:scale-[0.98] transition-all">
        수정하기
      </button>
    </div>

    <!-- Recurring Delete Modal -->
     <div v-if="showDeleteModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
        <div class="bg-white rounded-3xl p-6 w-full max-w-sm shadow-2xl">
            <h3 class="text-lg font-bold text-slate-900 mb-2">반복 일정 삭제</h3>
            <p class="text-slate-600 mb-6">이 일정은 반복되는 일정입니다.<br>어떻게 삭제하시겠습니까?</p>
            <div class="flex flex-col gap-3">
                <button @click="handleDelete(false)" class="w-full py-3 rounded-xl bg-slate-100 text-slate-700 font-bold">이 일정만 삭제</button>
                <button @click="handleDelete(true)" class="w-full py-3 rounded-xl bg-red-100 text-red-600 font-bold">모든 반복 일정 삭제</button>
                <button @click="showDeleteModal = false" class="w-full py-3 text-slate-400 font-medium mt-2">취소</button>
            </div>
        </div>
     </div>

  </div>
  <div v-else class="flex items-center justify-center min-h-screen">
      <p class="text-slate-400">일정을 불러오는 중...</p>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { scheduleService } from '@/services/scheduleService';
import { useFamilyStore } from '@/stores/family';

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();

const schedule = ref(null);
const showDeleteModal = ref(false);

const scheduleId = route.query.id; 

const fetchSchedule = async () => {
    if (!familyStore.selectedFamily?.id || !scheduleId) return;
    try {
        schedule.value = await scheduleService.getSchedule(familyStore.selectedFamily.id, scheduleId);
    } catch (error) {
        console.error("Failed to load schedule", error);
    }
};

const categoryLabel = computed(() => {
    if (!schedule.value) return '';
    const map = { 
        'VISIT': '방문', 
        'EVENT': '행사', 
        'MEDICAL': '병원', 
        'BIRTHDAY': '생일',
        'MEMORIAL': '기일',
        'ANNIVERSARY': '기념일'
    };
    return map[schedule.value.categoryType] || '기타';
});

const categoryIcon = computed(() => {
    if (!schedule.value) return 'category';
    const map = { 
        'VISIT': 'meeting_room', 
        'EVENT': 'event', 
        'MEDICAL': 'medical_services',
        'BIRTHDAY': 'cake',
        'MEMORIAL': 'filter_vintage',
        'ANNIVERSARY': 'celebration'
    };
    return map[schedule.value.categoryType] || 'category';
});

const startFormatted = computed(() => {
    if(!schedule.value?.startAt) return '';
    const date = new Date(schedule.value.startAt);
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short'});
});

const timeRange = computed(() => {
    if(!schedule.value?.startAt || !schedule.value?.endAt) return '';
    try {
        const startPart = schedule.value.startAt.split('T')[1];
        const endPart = schedule.value.endAt.split('T')[1];

        if (!startPart || !endPart) return '';

        const start = startPart.substring(0, 5);
        const end = endPart.substring(0, 5);
        return `오후 ${start} - 오후 ${end}`; 
    } catch (e) {
        return '';
    }
});

const handleDelete = async (deleteAll = false) => {
    if (!familyStore.selectedFamily?.id) return;
    try {
        await scheduleService.deleteSchedule(familyStore.selectedFamily.id, scheduleId, deleteAll);
        router.back();
    } catch (error) {
        console.error("Delete failed", error);
        alert("삭제 실패");
    }
};

const confirmDelete = () => {
    if (schedule.value?.repeatType === 'YEARLY' || schedule.value?.repeatType === 'MONTHLY' || schedule.value?.repeatType === 'WEEKLY') {
        showDeleteModal.value = true;
    } else {
        if(confirm('정말 삭제하시겠습니까?')) {
            handleDelete(false);
        }
    }
};

onMounted(() => {
    if (!familyStore.selectedFamily) {
         familyStore.fetchFamilies().then(fetchSchedule);
    } else {
        fetchSchedule();
    }
});
</script>

<style scoped>
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
