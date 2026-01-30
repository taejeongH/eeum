<template>
  <div class="fixed inset-0 bg-black/60 font-display min-h-screen flex flex-col justify-end z-[100]">
    <!-- Modal Container -->
    <div class="bg-[#f8f5f5] w-full max-w-md mx-auto rounded-t-xl overflow-hidden flex flex-col shadow-2xl animate-[slideUp_0.3s_ease-out] h-[92vh]">
      <!-- BottomSheetHandle Style -->
      <div class="flex flex-col items-stretch bg-emergency">
        <button class="flex h-6 w-full items-center justify-center">
          <div class="h-1.5 w-12 rounded-full bg-white/40"></div>
        </button>
      </div>
      
      <!-- High-Urgency Header -->
      <div class="bg-emergency text-white pt-2 pb-6 px-4 text-center">
        <span class="material-symbols-outlined text-5xl mb-2">warning</span>
        <h1 class="tracking-tight text-[32px] font-extrabold leading-tight">SUDDEN FALL DETECTED</h1>
        <p class="text-white/90 text-sm font-medium">Immediate Attention Required</p>
      </div>

      <div class="flex-1 overflow-y-auto">
        <!-- Critical Stats Section -->
        <div class="flex flex-wrap gap-4 p-4">
          <div class="flex min-w-[158px] flex-1 flex-col gap-2 rounded-xl p-5 bg-emergency/10 border border-emergency/20">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-emergency text-xl">timer</span>
              <p class="text-emergency text-base font-semibold leading-normal">Response Time</p>
            </div>
            <p class="text-[#1c0d0d] tracking-light text-3xl font-bold leading-tight">{{ formattedTime }} elapsed</p>
            <div class="flex items-center gap-1">
              <span class="h-2 w-2 rounded-full bg-emergency animate-pulse"></span>
              <p class="text-emergency text-sm font-bold leading-normal uppercase">Critical Window</p>
            </div>
          </div>
        </div>

        <!-- Detail List Section -->
        <div class="px-4 py-2">
          <div class="bg-white rounded-xl p-4 shadow-sm border border-black/5">
            <div class="flex justify-between items-center py-3 border-b border-black/5">
              <div class="flex items-center gap-3">
                <span class="material-symbols-outlined text-gray-500">category</span>
                <p class="text-gray-600 text-base font-normal">Danger Type</p>
              </div>
              <p class="text-[#1c0d0d] text-base font-bold">Sudden Fall</p>
            </div>
            <div class="flex justify-between items-center py-3 border-b border-black/5">
              <div class="flex items-center gap-3">
                <span class="material-symbols-outlined text-gray-500">location_on</span>
                <p class="text-gray-600 text-base font-normal">Location</p>
              </div>
              <p class="text-[#1c0d0d] text-base font-bold">Living Room</p>
            </div>
            <div class="flex justify-between items-center py-3">
              <div class="flex items-center gap-3">
                <span class="material-symbols-outlined text-gray-500">schedule</span>
                <p class="text-gray-600 text-base font-normal">Event Time</p>
              </div>
              <p class="text-[#1c0d0d] text-base font-bold">10:24 AM</p>
            </div>
          </div>
        </div>

        <!-- Action Grid (View Clip / Stream) -->
        <div class="flex flex-col gap-4 p-4">
          <p class="text-[#1c0d0d] text-lg font-bold px-1">Visual Verification</p>
          <div class="flex gap-3">
            <button class="flex-1 flex flex-col items-center justify-center gap-2 h-32 rounded-xl bg-amber-50 border-2 border-amber-200 text-[#1c0d0d] active:scale-95 transition-transform">
              <span class="material-symbols-outlined text-amber-600 text-3xl">play_circle</span>
              <span class="font-bold text-sm">View Clip</span>
              <span class="text-[10px] uppercase opacity-70">Before / After</span>
            </button>
            <button class="flex-1 flex flex-col items-center justify-center gap-2 h-32 rounded-xl bg-amber-50 border-2 border-amber-200 text-[#1c0d0d] active:scale-95 transition-transform">
              <span class="material-symbols-outlined text-amber-600 text-3xl">videocam</span>
              <span class="font-bold text-sm">Live Feed</span>
              <span class="text-[10px] uppercase opacity-70">Real-time Stream</span>
            </button>
          </div>
        </div>

        <!-- Saddle Dismiss -->
        <div class="flex justify-center py-4">
          <button @click="$router.push('/')" class="text-gray-500 text-sm font-medium underline decoration-gray-400 underline-offset-4">
              Dismiss as False Alarm
          </button>
        </div>
      </div>

      <!-- Emergency Footer Button -->
      <div class="p-4 pb-8 bg-white border-t border-black/5">
        <button @click="callEmergency" class="w-full h-16 bg-emergency hover:bg-red-700 active:scale-[0.98] transition-all rounded-xl flex items-center justify-center gap-3 shadow-lg shadow-emergency/30">
          <span class="material-symbols-outlined text-white text-3xl">emergency_share</span>
          <span class="text-white text-xl font-black uppercase tracking-wider">119 Quick Report</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useModalStore } from '@/stores/modal';

const modalStore = useModalStore();

const elapsedSeconds = ref(242); // Start at 04:02 as per design

const formattedTime = computed(() => {
  const m = Math.floor(elapsedSeconds.value / 60).toString().padStart(2, '0');
  const s = (elapsedSeconds.value % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
});

let timerInterval;

onMounted(() => {
  timerInterval = setInterval(() => {
    elapsedSeconds.value++;
  }, 1000);
});

onUnmounted(() => {
  clearInterval(timerInterval);
});

const callEmergency = async () => {
  await modalStore.openAlert("119에 신고를 시작합니다...");
};
</script>

<style scoped>
@keyframes slideUp {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
