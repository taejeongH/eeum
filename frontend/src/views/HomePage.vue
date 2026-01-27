<template>
  <div class="bg-[#fcfcfc] min-h-screen pb-20"> <!-- Padding bottom for navigation -->
    <HomeHeader />
    
    <main class="space-y-2">
      <ProfileScroll />
      
      <StatusCard />
      
      <DashboardGrid />
    </main>

    <BottomNav />
  </div>
</template>

<script setup>
import MainHeader from '@/components/MainHeader.vue';
import HomeHeader from '../components/layout/HomeHeader.vue'; // mainheader 통합 필요
import ProfileScroll from '../components/home/ProfileScroll.vue';
import StatusCard from '../components/home/StatusCard.vue';
import DashboardGrid from '../components/home/DashboardGrid.vue';
import BottomNav from '../components/layout/BottomNav.vue';
</script>
