<template>
  <div
    class="bg-[#fcfcfc] min-h-screen flex flex-col relative"
    :class="{ 'pb-20': familyStore.families.length > 0 }"
    @touchstart="handleTouchStart"
    @touchmove="handleTouchMove"
    @touchend="handleTouchEnd"
  >
    <!-- 최신 Pull-to-Refresh 표시기 -->
    <div
      class="fixed top-0 left-0 right-0 flex justify-center items-center z-[110] pointer-events-none transition-all duration-300 ease-out"
      :style="{
        transform: `translateY(${refreshPullDistance > 0 ? Math.min(refreshPullDistance - 40, 80) : -60}px)`,
        opacity: Math.min(refreshPullDistance / 60, 1),
      }"
    >
      <div
        class="bg-white rounded-full shadow-2xl border border-gray-100 flex items-center justify-center w-12 h-12 mt-4 relative overflow-hidden"
      >
        <!-- 최신 SVG 스피너 -->
        <svg
          v-if="isRefreshing"
          class="animate-spin h-7 w-7 text-[var(--color-primary)]"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="3"
          ></circle>
          <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          ></path>
        </svg>
        <span
          v-else
          class="material-symbols-outlined text-[var(--color-primary)] text-2xl transition-transform duration-200"
          :style="{ transform: `rotate(${Math.min(refreshPullDistance * 3, 360)}deg)` }"
        >
          refresh
        </span>
      </div>
    </div>
    <!-- 그룹이 있을 때만 정식 헤더 표시 -->
    <MainHeader
      v-if="familyStore.families.length > 0"
      @modal-state-change="handleModalStateChange"
      :show-settings="true"
    />

    <!-- 그룹이 없을 때는 심플한 타이틀 + 로그아웃 표시 -->
    <header
      v-else
      class="bg-white px-6 py-4 shadow-sm flex justify-between items-center relative z-30"
    >
      <div class="w-10"></div>
      <!-- 좌우 균형용 -->
      <h1
        class="text-2xl font-extrabold text-[var(--text-title)] tracking-tighter leading-none"
        style="font-family: 'Pretendard', sans-serif"
      >
        이음
      </h1>
      <button
        @click="handleLogout"
        class="p-2 text-gray-400 hover:text-gray-600 transition-colors"
        title="로그아웃"
      >
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
          />
        </svg>
      </button>
    </header>

    <main
      v-if="familyStore.families.length > 0"
      class="space-y-5 pt-8 pb-12 transition-transform duration-300 ease-out"
      :style="{ transform: `translateY(${isRefreshing ? 60 : refreshPullDistance * 0.4}px)` }"
    >
      <NoticeBar />
      <StatusCard />
      <LatestSchedule />
      <DashboardGrid />
    </main>

    <NoGroupView
      v-else-if="!familyStore.isLoading"
      class="flex-1"
      @create="openAddModal(2)"
      @join="openAddModal(1)"
    />

    <!-- 그룹이 있을 때만 하단 네비게이션 표시 -->
    <BottomNav v-if="familyStore.families.length > 0 && !isModalOpen" />

    <!-- 공용 그룹 추가 모달 (기존 모달 재사용) -->
    <AddGroupModal
      :show="isHomeAddModalOpen"
      :initial-step="addModalStep"
      @close="isHomeAddModalOpen = false"
      @join-group="handleJoinGroup"
      @create-group-request="handleCreateGroup"
    />
  </div>
</template>

<script setup>
/**
 * @component HomePage
 * @description 애플리케이션의 메인 대시보드 화면입니다.
 * 사용자 그룹(가족)의 상태를 요약하여 보여주고, 각 기능(건강, 일정, 알림)으로의 진입점을 제공합니다.
 *
 * [주요 기능]
 * - 가족 그룹 목록 조회 및 선택 (`familyStore`)
 * - 대시보드 위젯 (공지사항, 상태 카드, 일정, 바로가기 그리드)
 * - Pull-to-Refresh를 통한 데이터 갱신 (`usePullToRefresh`)
 * - 그룹 참여 및 생성 모달 연동
 *
 * @dependency familyStore - 가족 그룹 및 멤버 관리
 * @dependency notificationStore - 알림 이력 조회
 * @dependency modalStore - 확인/알림 모달 제어
 */
import { ref, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import MainHeader from '@/components/MainHeader.vue';
import NoticeBar from '@/components/home/NoticeBar.vue';
import StatusCard from '../components/home/StatusCard.vue';
import LatestSchedule from '../components/home/LatestSchedule.vue';
import DashboardGrid from '../components/home/DashboardGrid.vue';
import BottomNav from '../components/layout/BottomNav.vue';
import NoGroupView from '@/components/home/NoGroupView.vue';
import AddGroupModal from '@/components/AddGroupModal.vue';

import { useFamilyStore } from '@/stores/family';
import { useNotificationStore } from '@/stores/notification';
import { useModalStore } from '@/stores/modal';

import { usePullToRefresh } from '@/composables/usePullToRefresh';

const familyStore = useFamilyStore();
const notificationStore = useNotificationStore();
const modalStore = useModalStore();

/**
 * @type {Ref<boolean>} 확인/알림 모달 표시 여부
 */
const isModalOpen = ref(false);
const router = useRouter();

// 공용 모달 상태
/**
 * @type {Ref<boolean>} 그룹 추가/참여 모달 표시 여부
 */
const isHomeAddModalOpen = ref(false);

/**
 * @type {Ref<number>} 모달 단계 (0: 선택, 1: 참여, 2: 생성)
 */
const addModalStep = ref(0);

/**
 * 모달 상태 변경 핸들러
 * @param {boolean} isOpen - 모달 열림 상태
 */
const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

/**
 * 로그아웃 처리
 * 사용자 확인 후 로그아웃 페이지로 이동합니다.
 */
const handleLogout = async () => {
  if (await modalStore.openConfirm('정말 로그아웃 하시겠습니까?')) {
    router.push('/logout');
  }
};

/**
 * 그룹 추가 모달 열기
 * @param {number} step - 초기 단계 (1: 참여하기, 2: 생성하기)
 */
const openAddModal = (step) => {
  addModalStep.value = step;
  isHomeAddModalOpen.value = true;
};

/**
 * 그룹 참여 요청 처리
 * @param {string} code - 참여할 그룹의 초대 코드
 */
const handleJoinGroup = async (code) => {
  try {
    await familyStore.joinFamily(code);
    await modalStore.openAlert('그룹에 성공적으로 참여했습니다!');
    isHomeAddModalOpen.value = false;
  } catch (e) {
    // 에러는 store나 modal에서 처리
  }
};

/**
 * 새 그룹 생성 요청 처리
 * @param {Object} data - 생성할 그룹 정보
 * @param {string} data.groupName - 그룹 이름
 * @param {string} data.relation - 본인의 관계/호칭
 */
const handleCreateGroup = async (data) => {
  try {
    await familyStore.createFamily({
      name: data.groupName,
      relationship: data.relation,
    });
    await modalStore.openAlert(`'${data.groupName}' 그룹이 생성되었습니다!`);
    isHomeAddModalOpen.value = false;
  } catch (e) {
    // 에러 처리
  }
};

/**
 * 대시보드 데이터 새로고침
 * - 가족 목록, 알림, 멤버 목록 갱신
 */
const refreshDashboard = async () => {
  await Promise.all([
    familyStore.fetchFamilies(true), // 강제 새로고침
    familyStore.selectedFamily?.id
      ? notificationStore.fetchHistory(familyStore.selectedFamily.id)
      : Promise.resolve(),
    familyStore.selectedFamily?.id
      ? familyStore.fetchMembers(familyStore.selectedFamily.id, true)
      : Promise.resolve(),
  ]);
};

// Pull-to-Refresh Composable 사용
const { isRefreshing, refreshPullDistance, handleTouchStart, handleTouchMove, handleTouchEnd } =
  usePullToRefresh(refreshDashboard);

// 선택된 가족 그룹이 변경될 때마다 알람 이력을 가져옵니다.
watch(
  () => familyStore.selectedFamily,
  async (newFamily) => {
    if (newFamily?.id) {
      await notificationStore.fetchHistory(newFamily.id);
    }
  },
  { immediate: true },
);

onMounted(async () => {
  await familyStore.fetchFamilies();
  if (familyStore.selectedFamily?.id) {
    await notificationStore.fetchHistory(familyStore.selectedFamily.id);
  }
});
</script>
