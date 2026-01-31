<template>
  <div v-if="emergencyStore.isVisible" class="fixed inset-0 bg-black/70 font-display flex items-center justify-center z-[9999] p-4">
    <!-- Modal Container -->
    <!-- [FIX]: Added min-h-[600px] and max-h-[85vh] to prevent collapse/squashing -->
    <div class="bg-white w-full max-w-sm mx-auto rounded-3xl overflow-hidden flex flex-col shadow-2xl relative min-h-[600px] max-h-[85vh]">
      
      <!-- Close Button (Minimalist) - Only visible in Main View -->
      <button v-if="currentView === 'main'" class="absolute top-4 right-4 z-20 p-2 bg-white/50 backdrop-blur-sm rounded-full hover:bg-white transition-colors animate-[fadeIn_0.3s_ease-out]" @click="handleFalseAlarm">
         <svg class="w-5 h-5 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
      </button>

      <!-- [VIEW: MAIN] Default Emergency View -->
      <div v-if="currentView === 'main'" class="flex flex-col h-full w-full bg-white animate-[fadeIn_0.3s_ease-out] overflow-y-auto custom-scrollbar">
        <!-- Emergency Header Block (Red & Ping) -->
        <!-- [FIX]: Reduced padding (pt-10 -> pt-8, pb-8 -> pb-6) to fit more content -->
        <div class="bg-red-600 pt-8 pb-6 px-6 text-center flex flex-col items-center relative overflow-hidden shrink-0">
            <!-- Main Icon & Title -->
            <div class="relative z-10 flex flex-col items-center gap-2 mb-4">
                <!-- Icon with Ping Animation -->
                <div class="relative">
                    <div class="absolute inset-0 bg-white rounded-full animate-ping opacity-30"></div>
                    <div class="relative w-14 h-14 bg-white rounded-full flex items-center justify-center shadow-lg">
                        <component :is="eventIconComponent" class="w-7 h-7" :class="eventIconColor" />
                    </div>
                </div>

                <h1 class="text-2xl font-black text-white leading-none mt-2">
                    {{ headerTitle }}
                    <span class="block text-sm font-bold text-red-100 mt-1 opacity-90">(응급 상황)</span>
                </h1>
            </div>
            
            <!-- Large Timer -->
            <div class="relative z-10 flex flex-col items-center">
            <p class="text-[10px] font-bold text-red-100 uppercase tracking-widest mb-0.5 opacity-80">골든타임 경과</p>
            <div class="flex items-baseline gap-1 text-white font-black tabular-nums leading-none tracking-tighter">
                <span class="text-6xl">{{ formattedMinutes }}</span>
                <span class="text-3xl opacity-50">:</span>
                <span class="text-6xl animate-[pulse_1s_cubic-bezier(0.4,0,0.6,1)_infinite]">{{ formattedSeconds }}</span>
            </div>
            </div>
        </div>

        <!-- Info Area -->
        <!-- [FIX]: Reduced padding (py-4 -> py-3) -->
        <div class="px-6 py-3 bg-white space-y-2 shrink-0">
            <div class="grid grid-cols-2 gap-4">
                <div class="flex flex-col gap-0.5">
                    <span class="text-[10px] font-bold text-gray-400 uppercase tracking-wider">발생 시각</span>
                    <span class="text-lg font-bold text-gray-900">{{ occurrenceTime }}</span>
                </div>
                <div class="flex flex-col gap-0.5 text-right">
                    <span class="text-[10px] font-bold text-gray-400 uppercase tracking-wider">{{ groupName }}</span>
                    <span class="text-lg font-bold text-gray-900">{{ dependentName }}</span>
                </div>
            </div>
             <!-- Location (Optional - Compact) -->
            <div v-if="emergencyStore.emergencyData?.location" class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
                 <svg class="w-3.5 h-3.5 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                 <p class="text-xs font-medium text-gray-600 truncate">
                     {{ emergencyStore.emergencyData.location }}
                 </p>
            </div>
        </div>

        <!-- scrollable spacer -->
        <div class="flex-1 bg-white min-h-[10px]"></div>

        <!-- Actions Footer -->
        <!-- [FIX]: Reduced padding (pb-6 -> pb-5) -->
        <div class="px-6 pb-5 pt-2 flex flex-col gap-2 shrink-0 bg-white">
            
            <!-- 1. Secondary Video Actions -->
            <div class="grid grid-cols-2 gap-2">
                <button @click="openVideo('history')" class="flex flex-col items-center justify-center gap-1 py-2.5 bg-gray-50 hover:bg-gray-100 text-gray-600 font-bold rounded-xl transition-colors h-16 active:scale-95 shadow-sm border border-gray-100">
                    <component :is="IconHistory" class="w-5 h-5 mb-0.5" />
                    <span class="text-sm">녹화 영상</span>
                </button>
                <button @click="openVideo('live')" class="flex flex-col items-center justify-center gap-1 py-2.5 bg-gray-50 hover:bg-gray-100 text-gray-900 font-bold rounded-xl border-2 border-red-100 hover:border-red-200 transition-colors h-16 active:scale-95 shadow-sm">
                    <component :is="IconLive" class="w-5 h-5 text-red-500 mb-0.5" />
                    <span class="text-sm">실시간 영상</span>
                </button>
            </div>

            <!-- 2. False Alarm Check (Moved Up) -->
            <button @click="handleFalseAlarm" class="text-center text-[11px] font-semibold text-gray-400 underline decoration-gray-300 underline-offset-4 hover:text-gray-600 transition-colors py-1.5">
            오알람으로 처리하고 닫기
            </button>

            <!-- 3. Primary 119 Call (Bottom) -->
            <button @click="callEmergency" class="w-full py-3.5 bg-gradient-to-r from-red-600 to-red-500 hover:from-red-700 hover:to-red-600 text-white rounded-xl shadow-lg shadow-red-200 active:scale-[0.98] transition-all flex items-center justify-center gap-2 relative overflow-hidden group">
                <div class="absolute inset-0 bg-white/20 translate-x-[-100%] group-hover:translate-x-[100%] transition-transform duration-700 ease-in-out"></div>
                <svg class="w-6 h-6 animate-[wiggle_1s_ease-in-out_infinite]" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"></path></svg>
                <span class="text-xl font-black tracking-tight">지금 119에 연결</span>
            </button>
        </div>
      </div>

      <!-- [VIEW: VIDEO] History or Live -->
      <div v-if="currentView !== 'main'" class="absolute inset-0 w-full h-full bg-black z-30 flex flex-col animate-[fadeIn_0.3s_ease-out]">
         <!-- Header -->
         <div class="flex items-center justify-between p-4 bg-black/50 backdrop-blur-md absolute top-0 left-0 right-0 z-40">
             <button @click="currentView = 'main'" class="p-2 text-white/80 hover:text-white bg-white/10 rounded-full transition-colors active:scale-90">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg>
             </button>
             <span class="text-white font-bold text-lg tracking-tight">{{ currentView === 'history' ? '녹화 영상' : '실시간 영상' }}</span>
             <div class="w-10"></div> <!-- Spacer for center alignment -->
         </div>

         <!-- Placeholder Content -->
         <div class="flex-1 flex flex-col items-center justify-center text-center p-6 space-y-6">
             <div class="relative">
                <div class="absolute inset-0 bg-white/20 rounded-full blur-xl animate-pulse"></div>
                <div class="w-24 h-24 rounded-full bg-white/10 flex items-center justify-center relative backdrop-blur-sm border border-white/10">
                    <component :is="currentView === 'history' ? IconHistory : IconLive" class="w-10 h-10 text-white/70" />
                </div>
             </div>
             
             <div class="space-y-2 max-w-[80%]">
                 <h3 class="text-white font-bold text-xl leading-tight">
                    {{ currentView === 'history' ? '저장된 영상이 없습니다' : '영상 연결 중...' }}
                 </h3>
                 <p class="text-white/40 text-sm leading-relaxed break-keep">
                    {{ currentView === 'history' ? '이벤트 발생 시점의 녹화 영상이 존재하지 않습니다.' : '카메라 장치와 보안 연결을 시도하고 있습니다. 잠시만 기다려주세요.' }}
                 </p>
             </div>
         </div>
         
         <!-- Call 119 Mini Bar in Video View -->
         <div class="p-4 bg-gradient-to-t from-black/90 via-black/50 to-transparent pt-10 pb-6 shrink-0 relative z-40">
            <button @click="callEmergency" class="w-full py-3.5 bg-red-600 hover:bg-red-700 text-white font-bold rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-red-900/50 active:scale-[0.98] transition-all">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"></path></svg>
                <span>119 긴급 신고</span>
            </button>
         </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted, h } from 'vue';
import { useEmergencyStore } from '@/stores/emergency';
import { useModalStore } from '@/stores/modal';

// Icons using render functions
const IconFall = { render: () => h('svg', { class: 'w-full h-full', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [ h('path', { d: 'M13 13h3a3 3 0 0 0 0-6h-.025A5.56 5.56 0 0 0 16 6.5 5.5 5.5 0 0 0 5.207 5.021C5.137 5.017 5.071 5 5 5a4 4 0 0 0 0 8h2.167M10 15V6m0 0L8 8m2-2l2 2' }) ]) };
const IconEmergency = { render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [ h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z' }) ]) };
const IconWalk = { render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [ h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M13.5 4.5L11 2m0 0l-2.5 2.5M11 2v4m-1 4l-2 3m2-3l1 2 2-3m-3 0V6' }) ]) };
const IconHome = { render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [ h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' }) ]) };
const IconBell = { render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [ h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9' }) ]) };
const IconHistory = { render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [ h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z' }), h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z' }) ]) };
const IconLive = { render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [ h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', d: 'M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z' }) ]) };

const emergencyStore = useEmergencyStore();
const modalStore = useModalStore();

// View State: 'main', 'history', 'live'
const currentView = ref('main');

const elapsedSeconds = ref(0);
const occurrenceTime = ref('');

// Generate Occurrence Time from timestamp
const updateOccurrenceTime = () => {
    const timestamp = emergencyStore.emergencyData?.timestamp || Date.now();
    occurrenceTime.value = new Date(timestamp).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
};

const formattedMinutes = computed(() => Math.floor(elapsedSeconds.value / 60).toString().padStart(2, '0'));
const formattedSeconds = computed(() => (elapsedSeconds.value % 60).toString().padStart(2, '0'));

// Data Binding Fallbacks
const groupName = computed(() => {
    const gn = emergencyStore.emergencyData?.groupName;
    if (!gn || gn === '가족 그룹') return '나의 가족'; // Better fallback
    return gn;
});

const dependentName = computed(() => {
    const dn = emergencyStore.emergencyData?.dependentName;
    if (!dn || dn === '피부양자 확인 불가') return '대상자 정보 없음'; // Clearer fallback
    return dn;
});

const eventType = computed(() => emergencyStore.emergencyData?.type || 'FALL');

const eventConfig = computed(() => {
  const type = eventType.value;
  if (type === 'FALL') return { component: IconEmergency, iconColor: 'text-red-500', label: '낙상 감지' };
  if (type === 'OUTING') return { component: IconWalk, iconColor: 'text-orange-500', label: '외출 감지' };
  if (type === 'RETURN') return { component: IconHome, iconColor: 'text-green-500', label: '귀가 확인' };
  return { component: IconBell, iconColor: 'text-blue-500', label: '단순 알림' };
});

const eventIconComponent = computed(() => eventConfig.value.component);
const eventIconColor = computed(() => eventConfig.value.iconColor);
const eventTypeLabel = computed(() => eventConfig.value.label);

const headerTitle = computed(() => {
  const type = eventType.value;
  if (type === 'FALL') return '낙상 감지!';
  if (type === 'OUTING') return '외출 알림';
  if (type === 'RETURN') return '귀가 알림';
  return '활동 알림';
});

let timerInterval;

watch(() => emergencyStore.isVisible, (visible) => {
  if (visible) {
    currentView.value = 'main'; // Reset view
    if (emergencyStore.emergencyData?.timestamp) {
        const diff = Math.floor((Date.now() - emergencyStore.emergencyData.timestamp) / 1000);
        elapsedSeconds.value = diff > 0 ? diff : 0;
    } else {
        elapsedSeconds.value = 0;
    }
    
    updateOccurrenceTime();
    
    timerInterval = setInterval(() => {
      elapsedSeconds.value++;
    }, 1000);
  } else {
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = null;
  }
}, { immediate: true });

onUnmounted(() => {
  if (timerInterval) clearInterval(timerInterval);
});

const callEmergency = async () => {
    window.location.href = 'tel:119';
};

const openVideo = (view) => {
    currentView.value = view;
};

// [NEW] 오알람 처리 로직 (Confirm Modal 연동)
const handleFalseAlarm = async () => {
  const isConfirmed = await modalStore.openConfirm(
    '오알람으로 처리하시겠습니까?', 
    '상황이 확실히 종료되었는지 확인해주세요.'
  );

  if (isConfirmed) {
    emergencyStore.close();
  }
};
</script>

<style scoped>
@keyframes scaleUp {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
@keyframes wiggle {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-5deg); }
  75% { transform: rotate(5deg); }
}
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}
</style>
