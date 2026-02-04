<template>
  <div v-if="emergencyStore.isVisible" :class="['fixed inset-0 font-display flex items-center justify-center z-[9999] transition-all duration-300', isFullscreen ? 'bg-black p-0' : 'bg-black/80 p-4']">
    <!-- Modal Container -->
    <div :class="[
      'bg-white overflow-hidden flex flex-col shadow-2xl relative transition-all duration-300 ease-in-out mx-auto',
      isFullscreen ? 'w-full h-full max-w-none max-h-none rounded-none bg-black' : (currentView === 'main' ? 'w-full max-w-sm rounded-[32px] min-h-[570px] max-h-[90vh]' : 'w-full max-w-2xl rounded-[32px] bg-zinc-950')
    ]">
      
      <!-- Close Button (Minimalist) - Only visible in Main View -->
      <button v-if="currentView === 'main'" class="absolute top-4 right-4 z-20 p-2 bg-white/50 backdrop-blur-sm rounded-full hover:bg-white transition-colors animate-[fadeIn_0.3s_ease-out]" @click="handleFalseAlarm">
         <svg class="w-5 h-5 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
      </button>

      <!-- [VIEW: MAIN] Default Emergency View -->
      <div v-if="currentView === 'main'" class="flex flex-col h-full w-full bg-white animate-[fadeIn_0.3s_ease-out] overflow-y-auto custom-scrollbar">
        <!-- ... (Existing Main View Code) ... -->
        <div class="bg-red-600 pt-8 pb-6 px-6 text-center flex flex-col items-center relative overflow-hidden shrink-0">
            <div class="relative z-10 flex flex-col items-center gap-2 mb-4">
                <div class="relative">
                    <div class="relative w-14 h-14 bg-white rounded-full flex items-center justify-center shadow-lg">
                        <component :is="eventIconComponent" class="w-7 h-7" :class="eventIconColor" />
                    </div>
                </div>
                <h1 class="text-2xl font-black text-white leading-none mt-2">
                    {{ headerTitle }}
                    <span class="block text-sm font-bold text-red-100 mt-1 opacity-90">(응급 상황)</span>
                </h1>
            </div>
            <div class="relative z-10 flex flex-col items-center">
            <p class="text-[10px] font-bold text-red-100 uppercase tracking-widest mb-0.5 opacity-80">골든타임 경과</p>
            <div class="flex items-baseline gap-1 text-white font-black tabular-nums leading-none tracking-tighter">
                <span class="text-6xl">{{ formattedMinutes }}</span>
                <span class="text-3xl opacity-50">:</span>
                <span class="text-6xl animate-[pulse_1s_cubic-bezier(0.4,0,0.6,1)_infinite]">{{ formattedSeconds }}</span>
            </div>
            </div>
        </div>
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
            <div v-if="emergencyStore.emergencyData?.location" class="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
                 <svg class="w-3.5 h-3.5 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                 <p class="text-xs font-medium text-gray-600 truncate">{{ emergencyStore.emergencyData.location }}</p>
            </div>
        </div>
        <div class="flex-1 bg-white min-h-[10px]"></div>
        <div class="px-6 pb-5 pt-2 flex flex-col gap-2 shrink-0 bg-white">
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
            <button @click="handleFalseAlarm" class="text-center text-[11px] font-semibold text-gray-400 underline decoration-gray-300 underline-offset-4 hover:text-gray-600 transition-colors py-1.5">
            오알람으로 처리하고 닫기
            </button>
            <button @click="callEmergency" class="w-full py-3.5 bg-gradient-to-r from-red-600 to-red-500 hover:from-red-700 hover:to-red-600 text-white rounded-xl shadow-lg shadow-red-200 active:scale-[0.98] transition-all flex items-center justify-center gap-2 relative overflow-hidden group">
                <div class="absolute inset-0 bg-white/20 translate-x-[-100%] group-hover:translate-x-[100%] transition-transform duration-700 ease-in-out"></div>
                <svg class="w-6 h-6 animate-[wiggle_1s_ease-in-out_infinite]" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"></path></svg>
                <span class="text-xl font-black tracking-tight">지금 119에 연결</span>
            </button>
        </div>
      </div>

      <!-- [VIEW: VIDEO] History or Live -->
      <div v-if="currentView !== 'main'" :class="['w-full bg-black flex flex-col animate-[fadeIn_0.3s_ease-out]', isFullscreen ? 'h-full' : 'h-auto']">
         <!-- Minimized Header -->
         <div 
           :class="[
             'px-4 py-3 bg-zinc-900/80 border-b border-white/5 flex items-center justify-between shrink-0 transition-all duration-300 z-[101]',
             isFullscreen ? 'absolute top-0 left-0 right-0' : '',
             isFullscreen && !showControls ? 'opacity-0 pointer-events-none -translate-y-full' : 'opacity-100 translate-y-0'
           ]"
           @click.stop
         >
             <button @click="closeVideoView" class="p-1.5 text-white/80 hover:text-white bg-white/10 rounded-full transition-colors active:scale-90">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg>
             </button>
             <span class="text-white font-bold text-base tracking-tight">{{ currentView === 'history' ? '녹화 영상' : '실시간 영상' }}</span>
             <div class="w-10"></div>
         </div>

          <!-- Video content area (Wrapped with padding) -->
          <div :class="['w-full bg-black relative flex items-center justify-center py-4 min-h-[220px]', isFullscreen ? 'flex-1 py-0' : '']">
              <!-- [CASE] Video Loaded (History or Live) -->
              <div 
                v-if="videoUrl" 
                ref="fullscreenContainer" 
                class="w-full relative group transition-all duration-300 flex flex-col cursor-pointer"
                :class="isFullscreen ? 'h-full' : ''"
                @click="toggleControls"
              >


                  <!-- Live View (MJPEG Stream uses <img>) -->
                  <div v-if="currentView === 'live'" class="relative" :class="isFullscreen ? 'h-full w-full flex items-center justify-center' : ''">
                      <img 
                        :src="videoUrl"
                        class="block mx-auto rounded-lg shadow-inner"
                        :class="isFullscreen ? 'max-w-full max-h-full object-contain rounded-none' : 'w-full h-auto'"
                        alt="실시간 스트리밍"
                        @error="handleLiveError"
                      />
                      <!-- Live Indicator Badge -->
                      <div 
                        class="absolute top-2.5 right-4 flex items-center gap-1.5 px-2 py-1 bg-red-600/80 backdrop-blur-sm rounded text-[10px] font-bold text-white animate-pulse transition-opacity duration-300"
                        :class="isFullscreen && !showControls ? 'opacity-0' : 'opacity-100'"
                      >
                          <span class="w-1.5 h-1.5 bg-white rounded-full"></span>
                          LIVE
                      </div>
                  </div>

                  <!-- History View (Uploaded Video uses <video>) -->
                  <div v-else class="relative" :class="isFullscreen ? 'h-full w-full flex items-center justify-center' : ''">
                      <video 
                        ref="videoPlayer" 
                        autoplay 
                        muted
                        playsinline
                        webkit-playsinline
                        class="block mx-auto rounded-lg transition-transform duration-300" 
                        :class="isFullscreen ? 'max-w-full max-h-full object-contain rounded-none' : 'w-full h-auto'"
                        :src="videoUrl"
                        @timeupdate="updateVideoProgress"
                        @loadedmetadata="onVideoMetadataLoaded"
                        @ended="isPlaying = false"
                      >
                          브라우저가 비디오 재생을 지원하지 않습니다.
                      </video>
                      
                      <!-- [NEW] Custom Video Overlay Controls -->
                      <div 
                        v-if="showControls || !isPlaying" 
                        class="absolute inset-0 flex items-center justify-center bg-black/20 transition-opacity duration-300"
                        :class="!showControls && isPlaying ? 'opacity-0 pointer-events-none' : 'opacity-100'"
                      >
                          <button @click.stop="togglePlay" class="p-4 bg-white/20 backdrop-blur-md rounded-full text-white hover:bg-white/30 transition-all active:scale-95 shadow-xl border border-white/20">
                              <svg v-if="!isPlaying" class="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"></path></svg>
                              <svg v-else class="w-8 h-8" fill="currentColor" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"></path></svg>
                          </button>
                      </div>


                  </div>


              </div>

              <!-- [CASE] Loading/Processing -->
              <div v-else-if="videoLoading" class="py-12 flex flex-col items-center gap-4">
                  <div class="w-10 h-10 border-4 border-white/20 border-t-white rounded-full animate-spin"></div>
                  <p class="text-white/80 text-sm font-medium animate-pulse">
                      {{ currentView === 'live' ? '실시간 카메라 연결 중...' : '영상을 불러오는 중입니다...' }}
                  </p>
              </div>

              <!-- [CASE] Error or Empty -->
              <div v-else class="py-12 flex flex-col items-center gap-6 px-10 text-center">
                    <component :is="IconHistory" class="w-12 h-12 text-white/20" />
                    <div class="space-y-1">
                      <h3 class="text-white font-bold text-lg leading-tight">
                        {{ videoError ? '영상 로드 실패' : '저장된 영상이 없습니다' }}
                      </h3>
                      <p class="text-white/40 text-xs leading-relaxed break-keep">
                        {{ videoError || '이벤트 발생 시점의 녹화 영상이 존재하지 않습니다.' }}
                      </p>
                    </div>
              </div>
          </div>
         
          <!-- Unified Bottom Controls (Progress + Info + Actions) -->
          <div 
            :class="[
                'shrink-0 transition-all duration-500 z-[101]',
                isFullscreen ? 'fixed bottom-0 left-0 right-0' : 'relative',
                isFullscreen && !showControls ? 'translate-y-full opacity-0 pointer-events-none' : 'translate-y-0 opacity-100'
            ]"
            @click.stop
          >
             <!-- 1. Video Overlay Controls (Progress & Time/Live) - Only when viewing video -->
             <div v-if="currentView !== 'main'" class="bg-gradient-to-t from-zinc-900 to-transparent px-4 pb-1 pt-4 flex flex-col gap-2">
                 <!-- Status Bar & Fullscreen Toggle -->
                 <div class="flex items-center justify-between">
                     <div v-if="currentView === 'history'" class="flex items-center gap-2 text-[11px] text-white/70 font-mono">
                         <span class="text-white font-bold">{{ formatTime(currentTime) }}</span>
                         <span class="opacity-30">/</span>
                         <span>{{ formatTime(duration) }}</span>
                     </div>
                     <div v-else-if="currentView === 'live'" class="flex-1"></div>
                     <!-- Integrated Fullscreen Button -->
                     <button @click.stop="toggleFullscreen" class="p-1.5 bg-white/10 hover:bg-white/20 rounded-lg text-white/80 transition-all active:scale-95 border border-white/5">
                         <svg v-if="!isFullscreen" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15 3h6v6M9 21H3v-6M21 15v6h-6M3 9V3h6" vector-effect="non-scaling-stroke"></path>
                         </svg>
                         <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M8 3v5H3M16 3v5h5M8 21v-5H3M16 21v-5h5" vector-effect="non-scaling-stroke"></path>
                         </svg>
                     </button>
                 </div>
                 
                 <!-- Progress Bar (Only for History) -->
                 <div 
                    v-if="currentView === 'history'" 
                    class="py-3 cursor-pointer group/progress touch-none" 
                    @click.stop="seekVideo"
                    @touchstart.stop="seekVideo"
                 >
                     <div class="h-1.5 w-full bg-white/20 rounded-full overflow-hidden relative">
                         <div class="h-full bg-red-600 transition-all duration-75" :style="{ width: videoProgress + '%' }"></div>
                     </div>
                 </div>
             </div>

             <!-- 2. Main Footer Buttons (Solid Background) -->
             <div class="px-5 py-4 bg-zinc-900 border-t border-white/5">
                 <div class="grid grid-cols-2 gap-3">
                     <button @click="handleFalseAlarm" class="py-3 bg-white/5 hover:bg-white/10 text-white font-bold rounded-xl border border-white/10 active:scale-[0.98] transition-all flex items-center justify-center gap-2">
                         <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                         <span class="text-sm">상황 종료</span>
                     </button>
                     <button @click="callEmergency" class="py-3 bg-red-600 hover:bg-red-700 text-white font-bold rounded-xl flex items-center justify-center gap-2 shadow-lg active:scale-[0.98] transition-all">
                         <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"></path></svg>
                         <span class="text-sm">119 신고</span>
                     </button>
                 </div>
             </div>
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
const videoPlayer = ref(null);
const fullscreenContainer = ref(null);
const isFullscreen = ref(false);
const showControls = ref(true);

// Video Controls State
const isPlaying = ref(true);
const currentTime = ref(0);
const duration = ref(0);
const videoProgress = computed(() => duration.value ? (currentTime.value / duration.value) * 100 : 0);

const togglePlay = () => {
    if (!videoPlayer.value) return;
    if (isPlaying.value) {
        videoPlayer.value.pause();
    } else {
        videoPlayer.value.play();
    }
    isPlaying.value = !isPlaying.value;
};

const updateVideoProgress = () => {
    if (videoPlayer.value) {
        currentTime.value = videoPlayer.value.currentTime;
    }
};

const onVideoMetadataLoaded = () => {
    if (videoPlayer.value) {
        duration.value = videoPlayer.value.duration;
    }
};

const formatTime = (seconds) => {
    const min = Math.floor(seconds / 60);
    const sec = Math.floor(seconds % 60);
    return `${min}:${sec.toString().padStart(2, '0')}`;
};

const seekVideo = (e) => {
    if (!videoPlayer.value || !duration.value) return;
    
    // Support both Mouse and Touch events
    const event = e.touches ? e.touches[0] : (e.changedTouches ? e.changedTouches[0] : e);
    const clientX = event.clientX;
    
    if (clientX === undefined) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const x = Math.max(0, Math.min(clientX - rect.left, rect.width));
    const percentage = x / rect.width;
    
    videoPlayer.value.currentTime = duration.value * percentage;
    // Force immediate update of reactive state for better responsiveness
    currentTime.value = videoPlayer.value.currentTime;
};

const toggleControls = () => {
    if (isFullscreen.value) {
        showControls.value = !showControls.value;
    }
};

const toggleFullscreen = () => {
    isFullscreen.value = !isFullscreen.value;
    showControls.value = true; // Reset controls when toggling fullscreen
    
    // [NEW] Android Native Orientation Control
    if (window.AndroidBridge && window.AndroidBridge.setOrientation) {
        if (isFullscreen.value) {
            window.AndroidBridge.setOrientation('landscape');
        } else {
            window.AndroidBridge.setOrientation('portrait');
        }
    } else if (screen.orientation && screen.orientation.lock) {
        // Standard Web API Fallback
        if (isFullscreen.value) {
            screen.orientation.lock('landscape').catch(() => {});
        } else {
            screen.orientation.unlock();
        }
    }
};

const closeVideoView = () => {
    if (isFullscreen.value) {
        toggleFullscreen(); // This will reset orientation and isFullscreen state
    }
    currentView.value = 'main';
    videoUrl.value = null;
};

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
    window.location.href = 'tel:01076132359';
};

// [NEW] Video Logic
const videoUrl = ref(null);
const videoLoading = ref(false);
const videoError = ref(null);
import { getFallVideo, getFamilyDetails } from '@/services/api';

const handleLiveError = () => {
    videoError.value = '실시간 영상을 불러올 수 없습니다. 카메라 상태를 확인해주세요.';
    videoUrl.value = null;
};

const openVideo = async (view) => {
    currentView.value = view;
    videoUrl.value = null;
    videoError.value = null;
    videoLoading.value = true;
    
    if (view === 'live') {
        const familyId = emergencyStore.emergencyData?.familyId;
        
        if (!familyId) {
            videoError.value = '가족 정보를 확인할 수 없어 실시간 영상을 불러올 수 없습니다.';
            videoLoading.value = false;
            return;
        }

        try {
            const familyData = await getFamilyDetails(familyId);
            if (familyData && familyData.streamingUrl) {
                videoUrl.value = familyData.streamingUrl;
            } else {
                videoError.value = '등록된 실시간 스트리밍 주소가 없습니다.';
            }
        } catch (err) {
            console.error("Failed to fetch streaming URL:", err);
            videoError.value = '실시간 스트리밍 주소를 가져오는데 실패했습니다.';
        }
        
        videoLoading.value = false;
        return;
    }
    
    if (view === 'history') {
        const eventId = emergencyStore.emergencyData?.eventId;
        // const eventId = 40;
        
        if (!eventId) {
            videoError.value = '이벤트 ID가 없어 영상을 조회할 수 없습니다.';
            return;
        }

        try {
            console.log(`Asking video for eventId: ${eventId}`);
            // API 호출
             const response = await getFallVideo(eventId);
             console.log("Video API Response:", response);
             
             if (response.data && response.data.videoUrl) {
                 videoUrl.value = response.data.videoUrl;
                 console.log("Video URL set:", videoUrl.value);
             } else {
                 console.error("Invalid response structure:", response);
                 throw new Error(`영상 URL을 찾을 수 없습니다. (응답: ${JSON.stringify(response)})`);
             }
        } catch (err) {
            console.error('Failed to load video:', err);
            
            // 상세 에러 정보 로깅
            if (err.response) {
                console.error("Error Status:", err.response.status);
                console.error("Error Data:", err.response.data);
                
                if (err.response.data && err.response.data.code === 'IOT001') {
                     videoError.value = '영상이 아직 처리 중입니다. 잠시 후 다시 시도해주세요.';
                     return;
                }
                
                // 그 외 서버 에러 메시지 표시
                if (err.response.data && err.response.data.message) {
                    videoError.value = `오류: ${err.response.data.message}`;
                    return;
                }
            }
            
            videoError.value = `영상 로드 실패: ${err.message}`;
        } finally {
            videoLoading.value = false;
        }
    }
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
