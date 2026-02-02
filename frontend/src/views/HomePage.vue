<template>
  <div class="bg-[#fcfcfc] min-h-screen flex flex-col" :class="{ 'pb-20': familyStore.families.length > 0 }">
    <!-- 그룹이 있을 때만 정식 헤더 표시 -->
    <MainHeader v-if="familyStore.families.length > 0" @modal-state-change="handleModalStateChange" />
    
    <!-- 그룹이 없을 때는 심플한 타이틀 + 로그아웃 표시 -->
    <header v-else class="bg-white px-6 py-4 shadow-sm flex justify-between items-center relative z-30">
      <div class="w-10"></div> <!-- 좌우 균형용 -->
      <h1 class="text-2xl font-extrabold text-[var(--text-title)] tracking-tighter leading-none" style="font-family: 'Pretendard', sans-serif;">
        이음
      </h1>
      <button 
        @click="handleLogout" 
        class="p-2 text-gray-400 hover:text-gray-600 transition-colors"
        title="로그아웃"
      >
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
        </svg>
      </button>
    </header>
    
    <main v-if="familyStore.families.length > 0" class="space-y-2 pt-6">
      <NoticeBar />
      <StatusCard />
      
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
import { ref, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import MainHeader from '@/components/MainHeader.vue';
import NoticeBar from '@/components/home/NoticeBar.vue';
import StatusCard from '../components/home/StatusCard.vue';
import DashboardGrid from '../components/home/DashboardGrid.vue';
import BottomNav from '../components/layout/BottomNav.vue';
import NoGroupView from '@/components/home/NoGroupView.vue';
import AddGroupModal from '@/components/AddGroupModal.vue';

import { useFamilyStore } from '@/stores/family';
import { useNotificationStore } from '@/stores/notification';
import { useModalStore } from '@/stores/modal';

const familyStore = useFamilyStore();
const notificationStore = useNotificationStore();
const modalStore = useModalStore();
const isModalOpen = ref(false);
const router = useRouter();

// 공용 모달 상태
const isHomeAddModalOpen = ref(false);
const addModalStep = ref(0);

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

const handleLogout = async () => {
    if (await modalStore.openConfirm('정말 로그아웃 하시겠습니까?')) {
        router.push('/logout');
    }
};

const openAddModal = (step) => {
    addModalStep.value = step;
    isHomeAddModalOpen.value = true;
};

const handleJoinGroup = async (code) => {
    try {
        await familyStore.joinFamily(code);
        await modalStore.openAlert('그룹에 성공적으로 참여했습니다!');
        isHomeAddModalOpen.value = false;
    } catch (e) {
        // 에러는 store나 modal에서 처리
    }
};

const handleCreateGroup = async (data) => {
    try {
        await familyStore.createFamily({
            name: data.groupName,
            relationship: data.relation
        });
        await modalStore.openAlert(`'${data.groupName}' 그룹이 생성되었습니다!`);
        isHomeAddModalOpen.value = false;
    } catch (e) {
        // 에러 처리
    }
};

// 선택된 가족 그룹이 변경될 때마다 알람 이력을 가져옵니다.
watch(() => familyStore.selectedFamily, async (newFamily) => {
  if (newFamily?.id) {
    console.log('HomePage: Fetching notifications for family:', newFamily.id);
    await notificationStore.fetchHistory(newFamily.id);
  }
}, { immediate: true });

onMounted(async () => {
  await familyStore.fetchFamilies();
  if (familyStore.selectedFamily?.id) {
    await notificationStore.fetchHistory(familyStore.selectedFamily.id);
  }
});
</script>
