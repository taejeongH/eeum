<template>
  <div class="bg-background-light text-slate-800 min-h-screen flex flex-col" v-if="schedule">
    <header class="sticky top-0 z-20 bg-background-light/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex items-center">
        <button
          @click="$router.back()"
          class="p-2 -ml-2 rounded-full hover:bg-gray-100 transition-colors"
        >
          <svg class="w-6 h-6 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M15 19l-7-7 7-7"
            />
          </svg>
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
              <!-- 등록자 이름 로직 (있는 경우 사용, 없으면 폴백) -->
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

          <!-- 추가 방문 정보 섹션 -->
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
    <div
      v-if="isCreator"
      class="fixed bottom-0 left-0 right-0 px-6 py-4 flex gap-3 bg-gradient-to-t from-background-light via-background-light/90 to-transparent"
    >
      <button
        @click="confirmDelete"
        class="flex-1 py-4 bg-slate-200 text-slate-700 font-bold rounded-2xl active:scale-[0.98] transition-all"
      >
        삭제
      </button>
      <button
        @click="
          router.push({
            name: 'CalendarCreate',
            params: { familyId: route.params.familyId },
            query: { id: scheduleId },
          })
        "
        class="flex-[2] py-4 bg-primary text-white font-bold rounded-2xl shadow-lg shadow-primary/20 active:scale-[0.98] transition-all"
      >
        수정하기
      </button>
    </div>

    <!-- 반복 일정 삭제 모달 -->
    <div
      v-if="showDeleteModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
    >
      <div class="bg-white rounded-3xl p-6 w-full max-w-sm shadow-2xl">
        <h3 class="text-lg font-bold text-slate-900 mb-2">반복 일정 삭제</h3>
        <p class="text-slate-600 mb-6">
          이 일정은 반복되는 일정입니다.<br />어떻게 삭제하시겠습니까?
        </p>
        <div class="flex flex-col gap-3">
          <button
            @click="handleDelete(false)"
            class="w-full py-3 rounded-xl bg-slate-100 text-slate-700 font-bold"
          >
            이 일정만 삭제
          </button>
          <button
            @click="handleDelete(true)"
            class="w-full py-3 rounded-xl bg-red-100 text-red-600 font-bold"
          >
            모든 반복 일정 삭제
          </button>
          <button
            @click="showDeleteModal = false"
            class="w-full py-3 text-slate-400 font-medium mt-2"
          >
            취소
          </button>
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
import { useUserStore } from '@/stores/user';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { Logger } from '@/services/logger';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const familyStore = useFamilyStore();
const modalStore = useModalStore();

/** @type {import('vue').Ref<Object|null>} 일정 상세 정보 */
const schedule = ref(null);
/** @type {import('vue').Ref<boolean>} 삭제 확인 모달 노출 여부 */
const showDeleteModal = ref(false);

const scheduleId = route.query.id;

/** @type {import('vue').ComputedRef<boolean>} 현재 사용자가 일정 생성자인지 여부 */
const isCreator = computed(() => {
  return schedule.value?.creatorId === userStore.profile?.id;
});

/**
 * 서버로부터 일정 상세 정보를 가져옵니다.
 */
const fetchSchedule = async () => {
  const familyId = route.params.familyId || familyStore.selectedFamily?.id;
  if (!userStore.profile) await userStore.fetchUser();

  if (!familyId || !scheduleId) return;
  try {
    schedule.value = await scheduleService.getSchedule(familyId, scheduleId);
  } catch (error) {
    Logger.error('일정 상세 로드 실패', error);
  }
};

/** @type {import('vue').ComputedRef<string>} 카테고리 표시 텍스트 */
const categoryLabel = computed(() => {
  if (!schedule.value) return '';
  const map = {
    VISIT: '방문',
    EVENT: '행사',
    MEDICAL: '병원',
    BIRTHDAY: '생일',
    MEMORIAL: '기일',
    ANNIVERSARY: '기념일',
  };
  return map[schedule.value.categoryType] || '기타';
});

/** @type {import('vue').ComputedRef<string>} 카테고리 아이콘 이름 */
const categoryIcon = computed(() => {
  if (!schedule.value) return 'category';
  const map = {
    VISIT: 'meeting_room',
    EVENT: 'event',
    MEDICAL: 'medical_services',
    BIRTHDAY: 'cake',
    MEMORIAL: 'filter_vintage',
    ANNIVERSARY: 'celebration',
  };
  return map[schedule.value.categoryType] || 'category';
});

/** @type {import('vue').ComputedRef<string>} 시작 날짜 포맷 (YYYY년 M월 D일 (요일)) */
const startFormatted = computed(() => {
  if (!schedule.value?.startAt) return '';
  const date = new Date(schedule.value.startAt);
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  });
});

/** @type {import('vue').ComputedRef<string>} 시간 범위 포맷 (오전/오후 H:mm - H:mm) */
const timeRange = computed(() => {
  if (!schedule.value?.startAt || !schedule.value?.endAt) return '';
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

/**
 * 일정을 삭제합니다.
 * @param {boolean} [deleteAll=false] - 반복 일정의 경우 전체 삭제할지 여부
 */
const handleDelete = async (deleteAll = false) => {
  const familyId = route.params.familyId || familyStore.selectedFamily?.id;
  if (!familyId) return;
  try {
    await scheduleService.deleteSchedule(familyId, scheduleId, deleteAll);
    router.back();
  } catch (error) {
    Logger.error('삭제 실패', error);
    await modalStore.openAlert('삭제 실패');
  }
};

/**
 * 삭제 버튼 클릭 시 확인 절차를 수행합니다.
 * (반복 일정 여부에 따른 분기 처리)
 */
const confirmDelete = async () => {
  if (
    schedule.value?.repeatType === 'YEARLY' ||
    schedule.value?.repeatType === 'MONTHLY' ||
    schedule.value?.repeatType === 'WEEKLY'
  ) {
    showDeleteModal.value = true;
  } else {
    if (await modalStore.openConfirm('정말 삭제하시겠습니까?')) {
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
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}
</style>
