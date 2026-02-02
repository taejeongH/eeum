<template>
  <div 
    class="relative w-full h-full overflow-hidden bg-black font-sans select-none flex justify-center items-center"
    @click="toggleDetailView"
  >
    
    <!-- 1. Background Layer (Global, Blurred) -->
    <div class="absolute inset-0 z-0">
        <Transition name="fade-bg">
            <img 
                v-if="currentPhoto"
                :key="currentPhoto.id"
                :src="currentPhoto.url" 
                class="w-full h-full object-cover blur-[80px] opacity-40 brightness-50 transition-all duration-1000 transform scale-105"
            >
        </Transition>
    </div>

    <!-- 2. Main Photo Layer (Centered) -->
    <!-- object-contain ensures it fits Height/Width requirements automatically -->
    <div class="relative z-10 w-full h-full flex items-center justify-center p-4 transition-all duration-700">
        <Transition name="fade-photo" mode="out-in">
             <img 
                v-if="currentPhoto"
                :key="currentPhoto.id"
                :src="currentPhoto.url" 
                class="max-w-full max-h-full rounded-2xl shadow-[0_10px_40px_rgba(0,0,0,0.6)] object-contain transition-all duration-700"
                @load="checkOrientation"
             >
        </Transition>
    </div>

    <!-- UI LAYER: Floating Panels -->
    <!-- Visible only when not in full detail view -->
    <Transition name="fade-ui">
      <div v-if="!isDetailView" class="absolute inset-0 z-20 pointer-events-none">
          
          <!-- 3. Left Panel: Corkboard (Board) -->
          <!-- Floats on the Left "Wing" -->
          <div class="absolute top-0 bottom-0 left-0 w-[420px] flex flex-col justify-center items-center pointer-events-auto pl-8">
              
              <!-- Floating Corkboard CSS -->
              <div 
                  class="w-full py-12 px-6 rounded-r-3xl shadow-2xl relative flex flex-col gap-10 items-center transform transition-transform duration-500 hover:scale-[1.02] origin-left border-y border-r border-white/10"
                  style="background-image: url('https://www.transparenttextures.com/patterns/cork-board.png'); background-color: #dcbfa3;"
                  @click.stop
              >
                  <!-- Texture overlay -->
                  <div class="absolute inset-0 bg-black/5 pointer-events-none rounded-r-3xl"></div>

                  <!-- Msg Note -->
                  <div v-if="currentPhoto" class="relative group transform rotate-1 transition-transform hover:-rotate-1 duration-300">
                      <div class="absolute -top-3 left-1/2 -translate-x-1/2 z-20 w-4 h-4 rounded-full bg-red-600 shadow-md border border-white/30"></div>
                      <div class="post-it-yellow w-[320px] p-6 pb-8 shadow-lg flex items-center justify-center text-center min-h-[200px]">
                          <h2 class="text-3xl font-serif font-bold text-gray-900 leading-snug break-keep">
                              "{{ currentPhoto.message || '즐거운 하루 보내세요' }}"
                          </h2>
                      </div>
                  </div>

                  <!-- Info Note -->
                  <div v-if="currentPhoto" class="relative group transform -rotate-2 transition-transform hover:rotate-2 duration-300">
                      <div class="post-it-blue w-[280px] p-5 shadow-md flex flex-col items-center gap-2 text-center">
                          <div class="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center mb-1">
                              <span class="text-blue-500 text-xs font-bold">From</span>
                          </div>
                          <div class="text-xl font-bold text-gray-800">{{ currentPhoto.uploader }}</div>
                          <div class="text-gray-500 text-sm font-mono border-t border-blue-200 pt-2 w-full">
                              {{ formatDate(currentPhoto.takenAt) }}
                          </div>
                      </div>
                  </div>

              </div>
          </div>


          <!-- 4. Right Panel: Control (Menu) -->
          <!-- Floats on the Right "Wing" -->
          <div class="absolute top-0 bottom-0 right-0 w-[140px] flex flex-col pointer-events-auto py-6 pr-6">
              
              <!-- Control Bar Container -->
              <!-- Glassmorphism pill -->
              <div class="flex-1 bg-stone-900/80 backdrop-blur-xl rounded-full border border-white/10 shadow-2xl flex flex-col items-center py-8 gap-6 text-white overflow-hidden relative" @click.stop>
                  
                  <!-- 1. Clock (Top) -->
                  <div class="flex flex-col items-center mb-2">
                       <div class="text-3xl font-black tracking-tighter font-mono text-white/90">
                           {{ currentTimeFormatted }}
                       </div>
                       <div class="text-xs text-white/50 font-bold mt-1 uppercase">
                           {{ currentDayStr }}
                       </div>
                  </div>

                  <!-- Divider -->
                  <div class="w-12 h-[1px] bg-white/10"></div>

                  <!-- 2. Wifi -->
                   <div class="flex flex-col items-center gap-1">
                      <div class="p-3 bg-black/30 rounded-full border border-white/5">
                        <svg v-if="slideshowStore.wifiStatus" xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                        </svg>
                         <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3l18 18M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                        </svg>
                      </div>
                      <span class="text-[10px] text-white/40">WiFi</span>
                   </div>

                  <!-- Divider -->
                  <div class="w-12 h-[1px] bg-white/10"></div>

                  <!-- 3. Menu Items List -->
                  <!-- Scrollable if needed, but flex-1 usually fits -->
                  <nav class="flex-1 flex flex-col items-center gap-5 w-full overflow-y-auto no-scrollbar pt-2">
                       
                       <!-- Alert -->
                       <button class="nav-btn group" :class="{ active: activeTab === 'alert' }" @click="activeTab = 'alert'">
                           <div class="icon-box">
                               <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                                </svg>
                           </div>
                           <span class="label">알림</span>
                       </button>

                       <!-- Message -->
                       <button class="nav-btn group" :class="{ active: activeTab === 'chat' }" @click="activeTab = 'chat'">
                           <div class="icon-box">
                               <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                                </svg>
                           </div>
                           <span class="label">메시지</span>
                       </button>

                       <!-- Schedule -->
                       <button class="nav-btn group" :class="{ active: activeTab === 'schedule' }" @click="activeTab = 'schedule'">
                           <div class="icon-box">
                               <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                </svg>
                           </div>
                           <span class="label">일정</span>
                       </button>

                       <!-- Health -->
                       <button class="nav-btn group" :class="{ active: activeTab === 'health' }" @click="activeTab = 'health'">
                           <div class="icon-box">
                               <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                                </svg>
                           </div>
                           <span class="label">건강</span>
                       </button>

                        <!-- Settings -->
                       <button class="nav-btn group mt-auto" :class="{ active: activeTab === 'settings' }" @click="activeTab = 'settings'">
                           <div class="icon-box">
                               <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                           </div>
                           <span class="label">설정</span>
                       </button>

                  </nav>

              </div>
          </div>

      </div>
    </Transition>

  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useSlideshowStore } from '@/stores/slideshow'

const slideshowStore = useSlideshowStore()
const activeTab = ref('health')

const currentPhoto = computed(() => slideshowStore.currentSlide)
const isDetailView = ref(false)
const toggleDetailView = () => isDetailView.value = !isDetailView.value
const checkOrientation = (event) => {} 

watch(currentPhoto, () => isDetailView.value = false)

const now = ref(new Date())
let timer = null
const updateTime = () => now.value = new Date()
const currentTimeFormatted = computed(() => {
    const h = String(now.value.getHours()).padStart(2, '0')
    const m = String(now.value.getMinutes()).padStart(2, '0')
    return `${h}:${m}`
})
const currentDayStr = computed(() => {
    const days = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
    return days[now.value.getDay()]
})
const currentDateFormatted = computed(() => {
    // Not used in simplified clock, but available
    return now.value.toLocaleDateString()
})

onMounted(() => {
  slideshowStore.startStream()
  slideshowStore.updateWifiStatus()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => { if(timer) clearInterval(timer) })
const formatDate = (dateStr) => dateStr || ''
</script>

<style scoped>
.post-it-yellow { background: linear-gradient(135deg, #fefce8 0%, #fef08a 100%); color: #1f2937; border-radius: 4px; }
.post-it-blue { background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); color: #1e3a8a; border-radius: 4px; }

/* Control Panel Buttons */
.nav-btn {
    @apply flex flex-col items-center gap-1 w-full p-2 rounded-xl transition-all duration-200 opacity-60 hover:opacity-100 hover:scale-105;
}
.nav-btn.active {
    @apply opacity-100;
}
.nav-btn.active .icon-box {
    @apply bg-orange-500 text-white shadow-lg border-orange-400;
}
.nav-btn .icon-box {
    @apply w-12 h-12 rounded-full bg-black/30 border border-white/5 flex items-center justify-center text-white/90 shadow-md transition-colors;
}
.nav-btn .label {
    @apply text-[11px] font-bold text-white/80;
}

/* Scrollbar Hide */
.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }

/* Transitions */
.fade-bg-enter-active, .fade-bg-leave-active { transition: opacity 2000ms ease; }
.fade-bg-enter-from, .fade-bg-leave-to { opacity: 0; }

.fade-photo-enter-active, .fade-photo-leave-active { transition: opacity 800ms ease; }
.fade-photo-enter-from, .fade-photo-leave-to { opacity: 0; transform: scale(0.98); }

.fade-ui-enter-active, .fade-ui-leave-active { transition: opacity 500ms ease; }
.fade-ui-enter-from, .fade-ui-leave-to { opacity: 0; }
</style>
