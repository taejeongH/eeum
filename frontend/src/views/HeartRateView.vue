<template>
  <div class="bg-gray-50 min-h-screen flex flex-col relative pb-20">
    <!-- Premium Header Area -->
    <div class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0">
      <!-- Gradient Overlay -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      
      <!-- Decorative Pattern -->
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>

      <!-- Navigation Bar -->
      <div class="relative z-30 flex items-center justify-between p-5 pt-6">
        <div class="flex items-center gap-4">
            <button @click="$router.back()" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
              <IconBack />
            </button>
            <h1 class="text-xl font-bold text-white tracking-wide">실시간 심박수</h1>
        </div>
      </div>
    </div>

    <!-- Content Area (Overlapping Card) -->
    <main class="flex-1 px-6 -mt-16 relative z-30 space-y-6">
      
      <!-- Measurement Card -->
      <div class="bg-white rounded-[2.5rem] p-8 shadow-lg border border-slate-100 flex flex-col items-center text-center">
        <!-- Heart Animation -->
        <div class="relative mb-6">
          <div class="absolute inset-0 bg-red-100 rounded-full blur-3xl opacity-30 animate-pulse"></div>
          <div class="heart relative text-7xl select-none" :class="{ beating: isMeasuring }">
            ❤️
          </div>
        </div>

        <div class="data-display mb-4">
          <span class="value text-7xl font-black text-slate-900 tracking-tighter">{{ heartRate }}</span>
          <span class="unit text-xl font-bold text-slate-400 ml-2 tracking-tight">BPM</span>
        </div>

        <div class="status-box px-6 py-3 bg-slate-50 rounded-2xl border border-slate-100 mb-8 inline-block">
          <p v-if="isMeasuring" class="text-sm font-bold text-slate-700 flex items-center gap-2">
            <span class="relative flex h-2 w-2">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            워치에서 측정 중입니다...
          </p>
          <p v-else class="text-sm font-bold text-slate-400">워치 연결을 확인해주세요.</p>
          <p class="text-[10px] font-medium text-slate-400 mt-1" v-if="lastUpdate">
            마지막 수신: {{ lastUpdate }}
          </p>
        </div>

        <!-- Controls -->
        <div class="w-full space-y-3">
          <button 
            @click="startMonitoring"
            class="w-full py-4 rounded-2xl bg-[var(--color-primary)] text-white font-black text-lg shadow-lg shadow-orange-200 active:scale-[0.98] transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined">play_arrow</span>
            측정 시작
          </button>
          <button 
            @click="stopMonitoring"
            class="w-full py-4 rounded-2xl bg-slate-100 text-slate-500 font-bold text-base active:scale-[0.98] transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined">stop</span>
            측정 종료
          </button>
        </div>
      </div>

      <!-- Info Tip -->
      <div class="p-5 bg-blue-50/50 rounded-2xl border border-blue-100 flex gap-3 items-start">
        <span class="material-symbols-outlined text-blue-500 text-xl mt-0.5">info</span>
        <p class="text-[12px] leading-relaxed text-blue-700 font-medium">
          실시간 심박수 측정은 연결된 갤럭시 워치를 통해 가능합니다. 측정이 시작되지 않으면 워치 스크린이 켜져 있는지 확인해주세요.
        </p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import IconBack from '@/components/icons/IconBack.vue';

const heartRate = ref(0);
const isMeasuring = ref(false);
const lastUpdate = ref('');
let resetTimer = null;

const updateHeartRate = (hr) => {
  heartRate.value = Math.round(Number(hr));
  isMeasuring.value = true;
  const now = new Date();
  lastUpdate.value = now.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  // Reset timer to stop animation if no data received for 5 seconds
  if (resetTimer) clearTimeout(resetTimer);
  resetTimer = setTimeout(() => {
    isMeasuring.value = false;
  }, 5000);
};

const startMonitoring = () => {
  if (window.AndroidBridge && window.AndroidBridge.startHeartRateMonitoring) {
    window.AndroidBridge.startHeartRateMonitoring();
  } else {
    console.warn("Android Bridge not found");
  }
};

const stopMonitoring = () => {
  if (window.AndroidBridge && window.AndroidBridge.stopHeartRateMonitoring) {
    window.AndroidBridge.stopHeartRateMonitoring();
    isMeasuring.value = false;
  } else {
    console.warn("Android Bridge not found");
  }
};

onMounted(async () => {
  // Define global function for Native to call
  window.onNativeNotification = (id, type, familyId, title, message, groupName) => {
    console.log("Received Notification from Native:", id, type, title, message);
    
    if (title === "Heart Rate" || id === "HR_UPDATE") {
      const hrValue = parseFloat(message);
      if (!isNaN(hrValue)) {
        updateHeartRate(hrValue);
      }
    }
  };
});

onUnmounted(() => {
  if (resetTimer) clearTimeout(resetTimer);
});
</script>

<style scoped>
.heart.beating {
  animation: beat 1s infinite cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes beat {
  0% { transform: scale(1); }
  15% { transform: scale(1.15); filter: drop-shadow(0 0 10px rgba(239, 68, 68, 0.4)); }
  30% { transform: scale(1); }
  45% { transform: scale(1.08); filter: drop-shadow(0 0 5px rgba(239, 68, 68, 0.2)); }
  60% { transform: scale(1); }
}

.material-symbols-outlined {
    font-variation-settings: 'FILL' 1, 'wght' 600, 'GRAD' 0, 'opsz' 24;
}
</style>
