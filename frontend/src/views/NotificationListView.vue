<template>
  <div class="bg-[#fcfcfc] min-h-screen pb-10">
    <!-- 상단 헤더 영역 -->
    <header
      class="bg-white/90 backdrop-blur-md sticky top-0 z-[100] border-b border-gray-100 shadow-sm transition-all duration-300"
    >
      <div class="px-6 pt-6 pb-2 flex items-center">
        <!-- 뒤로 가기 버튼 -->
        <button
          @click="router.back()"
          class="p-2 -ml-2 rounded-full hover:bg-gray-100 active:scale-90 transition-all"
          title="이전 페이지로 이동"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            class="h-6 w-6 text-gray-800"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2.5"
              d="M15 19l-7-7 7-7"
            />
          </svg>
        </button>
        <h1 class="text-2xl font-black text-gray-900 ml-2 tracking-tight">알람 로그</h1>
      </div>

      <!-- 알림 필터 탭 바 영역 -->
      <div class="px-6 py-4 flex gap-3 overflow-x-auto no-scrollbar scroll-smooth">
        <button
          v-for="filter in filters"
          :key="filter.value"
          @click="activeFilter = filter.value"
          class="px-5 py-2 rounded-2xl text-[13px] font-extrabold transition-all duration-300 whitespace-nowrap active:scale-95 shadow-sm"
          :class="
            activeFilter === filter.value
              ? 'bg-gray-900 text-white shadow-gray-200 shadow-md ring-2 ring-gray-900 ring-offset-2'
              : 'bg-white text-gray-400 border border-gray-100 hover:bg-gray-50 hover:text-gray-600'
          "
        >
          {{ filter.label }}
        </button>
      </div>
    </header>

    <main class="px-5 py-6">
      <!-- 로딩 중 상태 표시 영역 -->
      <div
        v-if="notificationStore.isLoading"
        class="flex flex-col items-center justify-center py-20"
      >
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-[#e76f51]"></div>
        <p class="mt-4 text-gray-500 text-sm">알람을 불러오고 있습니다...</p>
      </div>

      <!-- 알람 데이터가 없는 경우의 상태 표시 영역 -->
      <div
        v-else-if="groupedNotifications.length === 0"
        class="flex flex-col items-center justify-center py-20 px-10 text-center"
      >
        <!-- 비어있음 아이콘 -->
        <div class="w-20 h-20 bg-gray-50 rounded-full flex items-center justify-center mb-6">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            class="h-10 w-10 text-gray-300"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
            />
          </svg>
        </div>
        <p class="text-gray-800 font-semibold text-lg">알람이 없습니다</p>
        <p class="mt-2 text-gray-500 text-sm">선택한 필터에 해당하는 알람이 없습니다.</p>
      </div>

      <!-- 날짜별로 그룹화된 알림 리스트 영역 -->
      <div v-else class="space-y-8">
        <div v-for="group in groupedNotifications" :key="group.dateLabel" class="space-y-4">
          <!-- 날짜 구분 헤더 (예: 오늘, 어제, 2월 10일) -->
          <div class="flex items-center gap-3">
            <div class="h-[1px] flex-1 bg-gray-100"></div>
            <span class="text-[11px] font-bold text-gray-400 uppercase tracking-widest">{{
              group.dateLabel
            }}</span>
            <div class="h-[1px] flex-1 bg-gray-100"></div>
          </div>

          <!-- 개별 알림 항목 카드 -->
          <div
            v-for="noti in group.items"
            :key="noti.id"
            @click="handleNotiClick(noti)"
            class="bg-white rounded-2xl p-5 shadow-sm border-l-4 flex items-start group active:scale-[0.98] transition-all relative overflow-hidden cursor-pointer"
            :class="[
              getBorderClass(noti.type),
              getBgClass(noti.type),
              !noti.isRead ? 'ring-2 ring-gray-100 shadow-md' : 'opacity-80',
            ]"
          >
            <!-- 알림 타입별 아이콘 영역 -->
            <div
              :class="getIconContainerClass(noti.type)"
              class="flex-shrink-0 p-3 rounded-xl mr-4 shadow-sm z-10"
            >
              <component :is="getIconComponent(noti.type)" class="h-6 w-6" />
            </div>

            <!-- 알림 내용 텍스트 영역 -->
            <div class="flex-1 min-w-0 z-10">
              <div class="flex justify-between items-center mb-1.5">
                <div class="flex items-center gap-2">
                  <!-- 카테고리 태그 (응급, 활동 등) -->
                  <span
                    :class="getCategoryTagClass(noti.type)"
                    class="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider"
                  >
                    {{ getCategoryLabel(noti.type) }}
                  </span>
                  <!-- 알림 제목 -->
                  <h2 class="text-base font-bold text-gray-900 truncate">{{ noti.title }}</h2>
                </div>
                <!-- 발생 시각 -->
                <span class="text-[11px] text-gray-400 whitespace-nowrap">{{
                  formatDetailTime(noti.createdAt)
                }}</span>
              </div>
              <!-- 알림 본문 (최대 2줄 노출) -->
              <p class="text-sm text-gray-600 line-clamp-2 leading-relaxed">{{ noti.message }}</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useNotificationStore } from '@/stores/notification';
import { useFamilyStore } from '@/stores/family';

const router = useRouter();
const route = useRoute();
const notificationStore = useNotificationStore();
const familyStore = useFamilyStore();

const activeFilter = ref('ALL');

/** 알림 필터 구성 */
const filters = [
  { label: '전체', value: 'ALL' },
  { label: '🚨 응급/낙상', value: 'EMERGENCY' },
  { label: '🚶 활동 (외출/귀가)', value: 'ACTIVITY' },
  { label: '💬 기타 알림', value: 'OTHERS' },
];

onMounted(async () => {
  if (familyStore.families.length === 0) {
    await familyStore.fetchFamilies();
  }
});

/**
 * 알림 항목 클릭 시 상세 정보를 모달로 표시하고 읽음 처리를 수행합니다.
 * @param {Object} noti
 */
const handleNotiClick = async (noti) => {
  const modalData = prepareModalData(noti);
  notificationStore.openModal(modalData);

  if (!noti.isRead) {
    await notificationStore.markAsRead(noti.id);
  }
};

/**
 * 모달에 표시할 알림 데이터를 준비합니다. (내부 사용)
 * @param {Object} noti
 * @returns {Object}
 */
const prepareModalData = (noti) => {
  return {
    ...noti,
    groupName: familyStore.selectedFamily?.name || '우리 가족',
    dependentName: familyStore.selectedFamily?.dependentName || '피부양자',
  };
};

/**
 * 경로 파라미터(familyId)가 변경될 때마다 데이터를 새로 가져옵니다.
 * (컴포넌트가 재사용되는 경우 onMounted가 다시 호출되지 않기 때문)
 */
watch(
  () => route.params.familyId,
  async (newFamilyId) => {
    if (newFamilyId) {
      await notificationStore.fetchHistory(newFamilyId);
    }
  },
  { immediate: true },
);

/** 그룹화 및 필터링 로직 */
/**
 * 필터와 날짜별로 그룹화된 알림 목록을 반환합니다.
 */
const groupedNotifications = computed(() => {
  const filtered = filterNotifications();
  const groups = groupNotificationsByDate(filtered);
  return sortDateGroups(groups);
});

/**
 * 활성 필터에 따라 알림을 필터링합니다. (내부 사용)
 * @returns {Array}
 */
const filterNotifications = () => {
  return (notificationStore.notifications || []).filter((n) => {
    if (activeFilter.value === 'ALL') return true;
    if (activeFilter.value === 'EMERGENCY') return n.type === 'EMERGENCY' || n.type === 'FALL';
    if (activeFilter.value === 'ACTIVITY')
      return n.type === 'ACTIVITY' || n.type === 'OUTING' || n.type === 'RETURN';
    if (activeFilter.value === 'OTHERS')
      return !['EMERGENCY', 'FALL', 'ACTIVITY', 'OUTING', 'RETURN'].includes(n.type);
    return true;
  });
};

/**
 * 알림 목록을 날짜 라벨별로 그룹화합니다. (내부 사용)
 * @param {Array} list
 * @returns {Object}
 */
const groupNotificationsByDate = (list) => {
  const groups = {};
  list.forEach((item) => {
    const label = getDateLabel(item.createdAt);
    if (!groups[label]) groups[label] = [];
    groups[label].push(item);
  });
  return groups;
};

/**
 * 그룹화된 데이터를 오늘/어제 순서로 정렬된 배열로 변환합니다. (내부 사용)
 * @param {Object} groups
 * @returns {Array}
 */
const sortDateGroups = (groups) => {
  return Object.keys(groups)
    .map((dateLabel) => ({
      dateLabel,
      items: groups[dateLabel],
    }))
    .sort((a, b) => {
      if (a.dateLabel === '오늘') return -1;
      if (b.dateLabel === '오늘') return 1;
      if (a.dateLabel === '어제') return -1;
      if (b.dateLabel === '어제') return 1;
      return 0;
    });
};

/**
 * 날짜 문자열로부터 '오늘', '어제' 또는 'MM월 DD일' 라벨을 생성합니다.
 * @param {string} dateStr
 * @returns {string}
 */
const getDateLabel = (dateStr) => {
  const date = new Date(dateStr);
  const now = new Date();

  const dMidnight = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const nowMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  const diffDays = Math.ceil((nowMidnight - dMidnight) / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return '오늘';
  if (diffDays === 1) return '어제';

  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
};

/**
 * 날짜 문자열을 상세 시간(오전/오후 H:MM)으로 포맷팅합니다.
 * @param {string} dateStr
 * @returns {string}
 */
const formatDetailTime = (dateStr) => {
  const date = new Date(dateStr);
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const ampm = hours >= 12 ? '오후' : '오전';
  const displayHours = hours % 12 || 12;
  const displayMinutes = minutes < 10 ? '0' + minutes : minutes;
  return `${ampm} ${displayHours}:${displayMinutes}`;
};

/**
 * 알림 타입별 스타일 및 라벨 구성을 정의합니다.
 */
const NOTI_STYLE_MAP = {
  EMERGENCY: {
    label: '응급',
    tag: 'bg-red-600',
    border: 'border-red-600',
    bg: 'bg-red-50',
    icon: 'bg-red-600',
  },
  FALL: {
    label: '응급',
    tag: 'bg-red-600',
    border: 'border-red-600',
    bg: 'bg-red-50',
    icon: 'bg-red-600',
  },
  ACTIVITY: {
    label: '활동',
    tag: 'bg-blue-600',
    border: 'border-blue-600',
    bg: 'bg-blue-50/50',
    icon: 'bg-blue-600',
  },
  OUTING: {
    label: '활동',
    tag: 'bg-blue-600',
    border: 'border-blue-600',
    bg: 'bg-blue-50/50',
    icon: 'bg-blue-600',
  },
  RETURN: {
    label: '활동',
    tag: 'bg-blue-600',
    border: 'border-blue-600',
    bg: 'bg-blue-50/50',
    icon: 'bg-blue-600',
  },
  DEFAULT: {
    label: '알림',
    tag: 'bg-gray-600',
    border: 'border-amber-600',
    bg: 'bg-white',
    icon: 'bg-amber-600',
  },
};

/**
 * 알림 타입에 따른 한글 라벨을 반환합니다.
 * @param {string} type
 * @returns {string}
 */
const getCategoryLabel = (type) => (NOTI_STYLE_MAP[type] || NOTI_STYLE_MAP.DEFAULT).label;

/**
 * 알림 타입에 따른 카테고리 태그 클래스를 반환합니다.
 * @param {string} type
 * @returns {string}
 */
const getCategoryTagClass = (type) =>
  `${(NOTI_STYLE_MAP[type] || NOTI_STYLE_MAP.DEFAULT).tag} text-white shadow-sm`;

/**
 * 알림 타입에 따른 테두리 클래스를 반환합니다.
 * @param {string} type
 * @returns {string}
 */
const getBorderClass = (type) => (NOTI_STYLE_MAP[type] || NOTI_STYLE_MAP.DEFAULT).border;

/**
 * 알림 타입에 따른 배경색 클래스를 반환합니다.
 * @param {string} type
 * @returns {string}
 */
const getBgClass = (type) => (NOTI_STYLE_MAP[type] || NOTI_STYLE_MAP.DEFAULT).bg;

/**
 * 알림 타입에 따른 아이콘 컨테이너 클래스를 반환합니다.
 * @param {string} type
 * @returns {string}
 */
const getIconContainerClass = (type) =>
  `${(NOTI_STYLE_MAP[type] || NOTI_STYLE_MAP.DEFAULT).icon} text-white shadow-md`;

/**
 * 알림 타입에 따른 아이콘 컴포넌트(SVG)를 반환합니다.
 * @param {string} type
 * @returns {Object}
 */
const getIconComponent = (type) => {
  const isEmergency = ['EMERGENCY', 'FALL'].includes(type);
  const isActivity = ['ACTIVITY', 'OUTING', 'RETURN'].includes(type);

  return {
    template: isEmergency
      ? '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77-1.333.192 3 1.732 3z" /></svg>'
      : isActivity
        ? '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-6 0v-1m6 0H9" /></svg>'
        : '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-6 0v-1m6 0H9" /></svg>',
  };
};
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800;900&display=swap');
h1,
h2,
h3,
span {
  font-family: 'Inter', sans-serif;
}
</style>
