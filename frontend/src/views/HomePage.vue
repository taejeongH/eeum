<template>
  <div class="bg-[#fcfcfc] min-h-screen pb-20"> <!-- Padding bottom for navigation -->
    <MainHeader @modal-state-change="handleModalStateChange" />
    
    <main class="space-y-2 pt-6">
      <NoticeBar />
      <StatusCard />
      
      <DashboardGrid />
    </main>

    <BottomNav v-show="!isModalOpen" />
  </div>
</template>

<script setup>
import { ref } from 'vue';
import MainHeader from '@/components/MainHeader.vue';
import NoticeBar from '@/components/home/NoticeBar.vue';

import StatusCard from '../components/home/StatusCard.vue';
import DashboardGrid from '../components/home/DashboardGrid.vue';
import BottomNav from '../components/layout/BottomNav.vue';

const isModalOpen = ref(false);

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};
</script>
