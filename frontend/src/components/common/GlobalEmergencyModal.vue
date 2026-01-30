<template>
  <div v-if="emergencyStore.isVisible" class="fixed inset-0 bg-black/70 backdrop-blur-sm font-display flex flex-col justify-end z-[9999]">
    <!-- Modal Container -->
    <div class="bg-white w-full max-w-md mx-auto rounded-t-[2rem] overflow-hidden flex flex-col shadow-2xl animate-[slideUp_0.3s_ease-out] fixed inset-x-0 bottom-0 md:relative md:h-[85vh] max-h-[650px]">
      <!-- Unified Emergency Header with Timer -->
      <div class="relative bg-gradient-to-br from-[var(--color-emergency)] to-[var(--color-emergency-dark)] text-white pt-6 pb-6 px-6 overflow-hidden">
        <!-- Subtle animated background -->
        <div class="absolute inset-0 bg-[var(--color-emergency-dark)] opacity-20 animate-pulse"></div>
        
        <!-- Close Handle -->
        <button class="absolute top-2 left-1/2 -translate-x-1/2 flex items-center justify-center z-10" @click="emergencyStore.close">
          <div class="h-1 w-10 rounded-full bg-white/40"></div>
        </button>

        <div class="relative">
          <!-- Emergency Badge & Time -->
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-1.5 bg-white/20 backdrop-blur-sm px-2.5 py-1 rounded-md">
              <span class="h-1.5 w-1.5 rounded-full bg-white animate-pulse"></span>
              <span class="text-[10px] font-black uppercase tracking-wider">EMERGENCY</span>
            </div>
            <div class="flex items-center gap-1.5 bg-white/20 backdrop-blur-sm px-2.5 py-1 rounded-md">
              <span class="material-symbols-outlined text-sm">schedule</span>
              <span class="text-xs font-bold">{{ currentTime }}</span>
            </div>
          </div>

          <!-- Warning Icon & Title - Compact -->
          <div class="flex items-center gap-3 mb-4">
            <div class="relative flex-shrink-0">
              <div class="absolute inset-0 bg-white/20 rounded-full animate-ping"></div>
              <div class="relative w-14 h-14 bg-white/20 backdrop-blur-sm rounded-full flex items-center justify-center border-2 border-white/40">
                <span class="material-symbols-outlined text-4xl font-bold">warning</span>
              </div>
            </div>
            <div class="flex-1">
              <h1 class="text-2xl font-black leading-tight mb-1">{{ headerTitle }}</h1>
              <p class="text-white/90 text-xs font-bold uppercase tracking-wide">{{ headerSubtitle }}</p>
            </div>
          </div>

          <!-- Timer Display - Integrated -->
          <div class="bg-white/15 backdrop-blur-sm rounded-xl p-4 border border-white/20">
            <div class="flex items-center justify-between">
              <!-- Left: Label -->
              <div class="flex items-center gap-2">
                <div class="w-9 h-9 bg-white/20 rounded-lg flex items-center justify-center">
                  <span class="material-symbols-outlined text-white text-lg">timer</span>
                </div>
                <div>
                  <p class="text-white/90 text-[10px] font-bold uppercase tracking-wide">경과 시간</p>
                  <div class="flex items-center gap-1 mt-0.5">
                    <span class="h-1.5 w-1.5 rounded-full bg-yellow-300 animate-pulse"></span>
                    <span class="text-white text-[10px] font-black uppercase">골든타임</span>
                  </div>
                </div>
              </div>
              
              <!-- Right: Timer -->
              <div class="text-right">
                <p class="text-5xl font-black text-white tracking-tight" style="text-shadow: 0 2px 10px rgba(0,0,0,0.3);">
                  {{ formattedTime }}
                </p>
                <p class="text-white/80 text-[10px] font-bold uppercase tracking-wider mt-0.5">분:초</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="flex-1 overflow-y-auto bg-gray-50">
        <!-- Information Grid - Clean Cards -->
        <div class="px-5 mb-4">
          <div class="bg-white rounded-2xl p-4 shadow-md border border-gray-200">
            <div class="space-y-3">
              <!-- Group & Dependent Info -->
              <div class="flex items-center gap-3 pb-3 border-b border-gray-100">
                <div class="w-10 h-10 bg-purple-50 rounded-lg flex items-center justify-center flex-shrink-0">
                  <span class="material-symbols-outlined text-purple-600 text-lg">group</span>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-500 font-semibold uppercase tracking-wide">그룹 / 피부양자</p>
                  <p class="text-sm font-black text-gray-900">{{ groupName }} / {{ dependentName }}</p>
                </div>
              </div>

              <!-- Event Type -->
              <div class="flex items-center gap-3 pb-3 border-b border-gray-100">
                <div class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0" :class="eventIconBg">
                  <span class="material-symbols-outlined text-lg" :class="eventIconColor">{{ eventIcon }}</span>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-500 font-semibold uppercase tracking-wide">{{ eventTypeLabel }}</p>
                  <p class="text-sm font-black text-gray-900">{{ eventDescription }}</p>
                </div>
              </div>

              <!-- Location (if available) -->
              <div v-if="location" class="flex items-center gap-3">
                <div class="w-10 h-10 bg-blue-50 rounded-lg flex items-center justify-center flex-shrink-0">
                  <span class="material-symbols-outlined text-blue-600 text-lg">location_on</span>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-[10px] text-gray-500 font-semibold uppercase tracking-wide">발생 위치</p>
                  <p class="text-sm font-black text-gray-900">{{ location }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Video Actions - Compact -->
        <div class="px-5 pb-4">
          <p class="text-gray-700 text-xs font-black uppercase tracking-wide mb-2.5 px-1">영상 확인</p>
          <div class="grid grid-cols-2 gap-2.5">
            <button class="group bg-white border-2 border-gray-200 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-soft)] rounded-xl p-3.5 active:scale-95 transition-all shadow-sm">
              <div class="flex flex-col items-center gap-2">
                <div class="w-11 h-11 bg-gray-100 group-hover:bg-[var(--color-primary)] rounded-lg flex items-center justify-center transition-colors">
                  <span class="material-symbols-outlined text-gray-600 group-hover:text-white text-xl transition-colors">play_circle</span>
                </div>
                <span class="font-bold text-xs text-gray-900">녹화영상</span>
              </div>
            </button>
            <button class="group bg-white border-2 border-gray-200 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-soft)] rounded-xl p-3.5 active:scale-95 transition-all shadow-sm">
              <div class="flex flex-col items-center gap-2">
                <div class="w-11 h-11 bg-gray-100 group-hover:bg-[var(--color-primary)] rounded-lg flex items-center justify-center transition-colors">
                  <span class="material-symbols-outlined text-gray-600 group-hover:text-white text-xl transition-colors">videocam</span>
                </div>
                <span class="font-bold text-xs text-gray-900">실시간</span>
              </div>
            </button>
          </div>
        </div>

        <!-- Dismiss -->
        <div class="flex justify-center pb-4">
          <button @click="emergencyStore.close" class="text-gray-400 text-xs font-medium hover:text-gray-600 transition-colors">
            오알람 처리
          </button>
        </div>
      </div>

      <!-- 119 Emergency Button -->
      <div class="p-5 bg-white border-t-2 border-gray-100">
        <button @click="callEmergency" class="w-full h-14 bg-gradient-to-r from-[var(--color-emergency)] to-[var(--color-emergency-light)] hover:from-[var(--color-emergency-dark)] hover:to-[var(--color-emergency)] text-white font-black rounded-xl shadow-xl shadow-[var(--color-emergency)]/30 active:scale-[0.98] transition-all flex items-center justify-center gap-3 group relative overflow-hidden">
          <!-- Shine effect -->
          <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-700"></div>
          
          <div class="relative flex items-center gap-2.5">
            <div class="w-9 h-9 bg-white/20 rounded-full flex items-center justify-center">
              <span class="material-symbols-outlined text-2xl">emergency_share</span>
            </div>
            <div class="text-left">
              <div class="text-lg font-black uppercase tracking-wide leading-tight">119 신고</div>
              <div class="text-[9px] font-semibold opacity-90 uppercase tracking-widest">즉시 연결</div>
            </div>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';
import { useEmergencyStore } from '@/stores/emergency';
import { useModalStore } from '@/stores/modal';

const emergencyStore = useEmergencyStore();
const modalStore = useModalStore();

const elapsedSeconds = ref(0);
const currentTime = ref(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));

const formattedTime = computed(() => {
  const m = Math.floor(elapsedSeconds.value / 60).toString().padStart(2, '0');
  const s = (elapsedSeconds.value % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
});

// Emergency data computed properties
const groupName = computed(() => {
  return emergencyStore.emergencyData?.groupName || '알 수 없음';
});

const dependentName = computed(() => {
  return emergencyStore.emergencyData?.dependentName || '피부양자';
});

const eventType = computed(() => {
  return emergencyStore.emergencyData?.type || 'FALL';
});

const location = computed(() => {
  return emergencyStore.emergencyData?.location || null;
});

// Event type configurations
const eventConfig = computed(() => {
  const type = eventType.value;
  
  if (type === 'FALL') {
    return {
      icon: 'emergency',
      iconBg: 'bg-red-50',
      iconColor: 'text-[var(--color-emergency)]',
      label: '위험 유형',
      description: '갑작스러운 낙상'
    };
  } else if (type === 'OUTING') {
    return {
      icon: 'directions_walk',
      iconBg: 'bg-orange-50',
      iconColor: 'text-orange-600',
      label: '활동 알림',
      description: '외출 감지'
    };
  } else if (type === 'RETURN') {
    return {
      icon: 'home',
      iconBg: 'bg-green-50',
      iconColor: 'text-green-600',
      label: '활동 알림',
      description: '귀가 확인'
    };
  } else {
    return {
      icon: 'notifications',
      iconBg: 'bg-blue-50',
      iconColor: 'text-blue-600',
      label: '알림',
      description: '활동 감지'
    };
  }
});

const eventIcon = computed(() => eventConfig.value.icon);
const eventIconBg = computed(() => eventConfig.value.iconBg);
const eventIconColor = computed(() => eventConfig.value.iconColor);
const eventTypeLabel = computed(() => eventConfig.value.label);
const eventDescription = computed(() => eventConfig.value.description);

// Header text based on event type
const headerTitle = computed(() => {
  const type = eventType.value;
  if (type === 'FALL') return '낙상 감지!';
  if (type === 'OUTING') return '외출 알림';
  if (type === 'RETURN') return '귀가 알림';
  return '활동 알림';
});

const headerSubtitle = computed(() => {
  const type = eventType.value;
  if (type === 'FALL') return '즉시 확인 필요';
  if (type === 'OUTING') return '외출 확인됨';
  if (type === 'RETURN') return '귀가 확인됨';
  return '활동 감지됨';
});

let timerInterval;

watch(() => emergencyStore.isVisible, (visible) => {
  if (visible) {
    elapsedSeconds.value = 0;
    currentTime.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    timerInterval = setInterval(() => {
      elapsedSeconds.value++;
    }, 1000);
  } else {
    if (timerInterval) {
      clearInterval(timerInterval);
      timerInterval = null;
    }
  }
}, { immediate: true });

onUnmounted(() => {
  if (timerInterval) clearInterval(timerInterval);
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
