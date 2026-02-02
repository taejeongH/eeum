<template>
  <div class="bg-[#fcfcfc] min-h-screen pb-28"> <!-- Padding bottom for navigation clearance -->
    <MainHeader @modal-state-change="handleModalStateChange" />
    
    <main class="space-y-2 pt-6">
      <NoticeBar />
      <StatusCard />
      <LatestSchedule />
      <DashboardGrid />
    </main>

    <BottomNav v-show="!isModalOpen" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import MainHeader from '@/components/MainHeader.vue';
import NoticeBar from '@/components/home/NoticeBar.vue';
import StatusCard from '../components/home/StatusCard.vue';
import LatestSchedule from '../components/home/LatestSchedule.vue';
import DashboardGrid from '../components/home/DashboardGrid.vue';
import BottomNav from '../components/layout/BottomNav.vue';

import { useFamilyStore } from '@/stores/family';
import { useNotificationStore } from '@/stores/notification';

const familyStore = useFamilyStore();
const notificationStore = useNotificationStore();
const isModalOpen = ref(false);

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

// 선택된 가족 그룹이 변경될 때마다 알람 이력을 가져옵니다.
watch(() => familyStore.selectedFamily, async (newFamily) => {
  if (newFamily?.id) {

    await notificationStore.fetchHistory(newFamily.id);
  }
}, { immediate: true });

onMounted(async () => {
  if (familyStore.selectedFamily?.id) {
    await notificationStore.fetchHistory(familyStore.selectedFamily.id);
  }
});
</script>
