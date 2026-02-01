<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useAlertStore } from './stores/alert';
import AlertOverlay from './components/AlertOverlay.vue';

const alertStore = useAlertStore();

onMounted(() => {
  alertStore.connect();
});

onUnmounted(() => {
  alertStore.disconnect();
});

const triggerAllTests = () => {
  // 1. Medication Alert
  alertStore.addAlert({ 
    kind: 'medication', 
    title: '복약 알림', 
    content: '점심 식사 후, 고혈압약과 비타민을 드실 시간입니다.',
    sent_at: Date.now() / 1000 
  });

  // 2. Schedule Alert
  alertStore.addAlert({ 
    kind: 'schedule', 
    title: '오늘의 일정', 
    content: '오후 일정이 있습니다.',
    sent_at: Date.now() / 1000,
    data: {
      events_for_today: [
        { time: '14:00', title: '치과 검진' },
        { time: '18:30', title: '가족 저녁 식사' }
      ]
    }
  });

  // 3. Message Alert
  alertStore.addAlert({ 
    kind: 'message', 
    title: '새 메시지', 
    content: '손녀 김지은: "할머니, 오늘 날씨가 추우니까 따뜻하게 입고 나가세요!"',
    sent_at: Date.now() / 1000 
  });

  // 4. Photo Alert
  alertStore.addAlert({ 
    kind: 'photo', 
    title: '사진 도착', 
    content: '새로운 가족 사진 3장이 도착했습니다.',
    sent_at: Date.now() / 1000 
  });
};
</script>

<template>
  <AlertOverlay />
  <div class="h-full bg-bg-page flex flex-col items-center justify-center gap-8 p-4">
    <div class="text-center space-y-4">
      <h1 class="eeum-title text-4xl">IoT Device Setup</h1>
      <p class="eeum-sub text-lg">Checking Fonts & Colors</p>
    </div>
    
    <div class="w-full max-w-md space-y-6">
      <!-- Color Checks -->
      <div class="p-6 rounded-2xl bg-primary shadow-lg text-white">
        <h2 class="text-xl font-bold">Primary Color</h2>
        <p class="opacity-90">#f3532b</p>
        <button 
          @click="triggerAllTests"
          class="mt-4 px-4 py-2 bg-white text-primary rounded-lg text-sm font-bold hover:bg-gray-100 transition"
        >
          모든 알림 테스트
        </button>
      </div>

      <div class="p-6 rounded-2xl bg-emergency shadow-lg text-white">
        <h2 class="text-xl font-bold">Emergency Color</h2>
        <p class="opacity-90">#dc2626</p>
      </div>

      <!-- Surface/Text Checks -->
      <div class="p-6 rounded-2xl bg-white border border-border-default shadow-sm">
        <h2 class="text-xl font-bold text-text-title">Text Title</h2>
        <p class="text-text-body mt-2">Text Body content should be legible and use Pretendard.</p>
        <p class="text-text-sub mt-1 text-sm">Subtext color check.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
