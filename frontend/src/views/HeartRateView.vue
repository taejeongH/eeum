<template>
  <div class="bg-gray-50 min-h-screen flex flex-col relative pb-20">
    <!-- Premium Header Area -->
    <div class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0">
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>

      <div class="relative z-30 flex items-center justify-between p-5 pt-6">
        <div class="flex items-center gap-4">
            <button @click="$router.back()" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
              <IconBack />
            </button>
            <h1 class="text-xl font-bold text-white tracking-wide">정밀 심박수 측정</h1>
        </div>
      </div>
    </div>

    <!-- Content Area -->
    <main class="flex-1 px-6 -mt-16 relative z-30 space-y-6">
      <div class="bg-white rounded-[2.5rem] p-8 shadow-lg border border-slate-100 flex flex-col items-center text-center">
        <!-- Heart Animation -->
        <div class="relative mb-6">
          <div class="absolute inset-0 bg-red-100 rounded-full blur-3xl opacity-30 animate-pulse"></div>
          <div class="heart relative text-7xl select-none" :class="{ beating: isMeasuring }">
            ❤️
          </div>
        </div>

        <div class="data-display mb-4">
          <div v-if="isMeasuring" class="flex flex-col items-center">
            <span class="text-3xl font-bold text-slate-400 animate-pulse tracking-tight">수집 중...</span>
            <span class="text-sm text-slate-400 mt-2">안정적인 상태를 유지하세요</span>
          </div>
          <div v-else class="flex flex-col items-center gap-4">
             <!-- Main Avg Display -->
             <div>
                <span class="value text-7xl font-black text-slate-900 tracking-tighter">{{ heartRate }}</span>
                <span class="unit text-xl font-bold text-slate-400 ml-2 tracking-tight">BPM</span>
                <p class="text-sm text-slate-500 font-medium">평균</p>
             </div>

             <!-- Min/Max Display -->
             <div v-if="avgCount > 0" class="flex items-center gap-8 mt-2">
                <div class="flex flex-col items-center">
                    <span class="text-2xl font-bold text-blue-600">{{ minMetric }}</span>
                    <span class="text-xs text-slate-400">최소</span>
                </div>
                <div class="w-px h-8 bg-slate-200"></div>
                <div class="flex flex-col items-center">
                    <span class="text-2xl font-bold text-red-500">{{ maxMetric }}</span>
                    <span class="text-xs text-slate-400">최대</span>
                </div>
             </div>
          </div>
        </div>

        <div class="status-box px-6 py-3 bg-slate-50 rounded-2xl border border-slate-100 mb-8 inline-block">
          <p v-if="isMeasuring" class="text-sm font-bold text-[var(--color-primary)] flex flex-col items-center gap-2">
            <span class="text-lg">남은 시간: {{ timer }}초</span>
            <span class="text-xs text-slate-400">30초간의 데이터를 분석합니다.</span>
          </p>
          <p v-else-if="avgCount > 0" class="text-sm font-bold text-emerald-600">
            분석 완료
          </p>
          <p v-else class="text-sm font-bold text-slate-400">워치를 착용하고 측정을 시작하세요.</p>
        </div>

        <!-- Controls -->
        <!-- ... (unchanged) ... -->

        <!-- Controls -->
        <div class="w-full space-y-3">
          <button 
            @click="startPrecesionMeasuring"
            :disabled="isMeasuring"
            class="w-full py-4 rounded-2xl bg-[var(--color-primary)] text-white font-black text-lg shadow-lg shadow-orange-200 active:scale-[0.98] transition-all flex items-center justify-center gap-2 disabled:opacity-50">
            <span class="material-symbols-outlined">{{ isMeasuring ? 'hourglass_empty' : 'bolt' }}</span>
            30초 정밀 측정 시작
          </button>
          <button 
            @click="stopMonitoring"
            class="w-full py-4 rounded-2xl bg-slate-100 text-slate-500 font-bold text-base active:scale-[0.98] transition-all flex items-center justify-center gap-2">
            측정 중단
          </button>
        </div>
      </div>

      <!-- Info Tip Removed -->
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import IconBack from '@/components/icons/IconBack.vue';
import { useFamilyStore } from '@/stores/family';
import axios from '@/services/api'; 
import healthService from '@/services/healthService';
import { Logger } from '@/services/logger';

const familyStore = useFamilyStore();

const heartRate = ref(0);
const minMetric = ref(0);
const maxMetric = ref(0);
const isMeasuring = ref(false);
const timer = ref(30);
const avgCount = ref(0);
let timerInterval = null;
let realTimeInterval = null;

const currentEventId = ref(null);

const startPrecesionMeasuring = async () => {
    try {
        isMeasuring.value = true;
        timer.value = 30;
        heartRate.value = 0;
        minMetric.value = 0;
        maxMetric.value = 0;
        avgCount.value = 0;
        currentEventId.value = null;
        
        
        if (familyStore.selectedFamily?.id) {
            await healthService.requestMeasurement(familyStore.selectedFamily.id);
        }

        
        timerInterval = setInterval(() => {
            timer.value--;
            if (timer.value <= 0) {
                finishMeasurement();
            }
        }, 1000);

    } catch (e) {
        Logger.error("측정 시작 실패:", e);
        isMeasuring.value = false;
    }
};

const finishMeasurement = async () => {
    stopTimers();
    isMeasuring.value = false;
    
    if (!familyStore.selectedFamily?.id) {
        Logger.error("가족 ID가 없어 결과를 조회할 수 없습니다.");
        return;
    }

    try {
        
        const data = await healthService.getLatestHeartRate(familyStore.selectedFamily.id);
        if (data) {
            heartRate.value = Math.round(data.avgRate || 0);
            minMetric.value = data.minRate || 0;
            maxMetric.value = data.maxRate || 0;
            avgCount.value = data.sampleCount || 0;
        } else {
             Logger.warn("최신 측정 데이터가 없습니다.");
        }
    } catch (e) {
        Logger.error("결과 조회 실패:", e);
    }
};

const stopTimers = () => {
    if (timerInterval) clearInterval(timerInterval);
    if (realTimeInterval) clearInterval(realTimeInterval);
};

const stopMonitoring = () => {
    isMeasuring.value = false;
    stopTimers();
};

onMounted(async () => {
    if (!familyStore.selectedFamily) {
        await familyStore.fetchFamilies();
    }
});

onUnmounted(() => {
    stopTimers();
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
