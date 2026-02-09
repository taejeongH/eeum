<template>
  <div 
    class="relative w-full h-full overflow-hidden bg-black font-sans select-none flex justify-center items-center"
    @click="toggleDetailView"
    @touchstart="handleTouchStart"
    @touchend="handleTouchEnd"
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
            <div 
                v-else
                class="w-full h-full bg-gradient-to-br from-gray-900 via-stone-900 to-black transition-all duration-1000"
            ></div>
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
             <!-- Default Screen (No Photo) -->
             <div 
                v-else 
                class="flex flex-col items-center justify-center gap-12 animate-fade-in-up"
             >
                <div class="flex flex-col items-center">
                    <h1 class="text-[12rem] font-black text-white/90 tracking-tighter leading-none font-mono">
                        {{ currentTimeFormatted }}
                    </h1>
                    <p class="text-5xl text-orange-400 font-bold uppercase tracking-[0.5em] mt-4">
                        {{ currentDayStr }}
                    </p>
                </div>
                
                <div class="h-px w-48 bg-white/20"></div>

                <div class="text-center">
                    <p class="text-5xl font-medium text-white/90 leading-relaxed">
                        행복한 순간을<br>공유해주세요
                    </p>
                    <p class="text-2xl text-white/40 mt-6 tracking-wide">
                        가족들의 사진을 기다리고 있습니다
                    </p>
                </div>
             </div>
        </Transition>
    </div>

    <!-- UI LAYER: Floating Panels -->
    <!-- Visible only when not in full detail view -->
    <Transition name="fade-ui">
      <div v-if="!isDetailView" class="absolute inset-0 z-20 pointer-events-none">
          
           <!-- 3. Left Panel: Photo Info Panel -->
          <!-- Floats on the Left "Wing" -->
          <div v-if="currentPhoto" class="absolute top-0 bottom-0 left-0 w-[600px] flex flex-col justify-center items-center pointer-events-auto pl-12">
              
              <!-- Modern Sticky Notes Board -->
              <div 
                  class="w-full py-16 px-8 rounded-r-[50px] shadow-[0_20px_60px_rgba(0,0,0,0.5)] relative flex flex-col gap-10 items-center transform transition-transform duration-500 hover:scale-[1.02] origin-left border-y border-r border-white/10 overflow-hidden"
                  style="background: linear-gradient(135deg, #fdfbf7 0%, #f5eee6 100%);"
                  @click.stop
              >
                  <!-- Subtle Texture overlay -->
                  <div class="absolute inset-0 opacity-10 pointer-events-none" style="background-image: url('data:image/svg+xml,%3Csvg width=\'100\' height=\'100\' xmlns=\'http://www.w3.org/2000/svg\'%3E%3Cfilter id=\'noise\'%3E%3CfeTurbulence baseFrequency=\'0.9\' numOctaves=\'4\'/%3E%3C/filter%3E%3Crect width=\'100\' height=\'100\' filter=\'url(%23noise)\' opacity=\'0.4\'/%3E%3C/svg%3E');"></div>

                  <!-- Message Note -->
                  <div v-if="currentPhoto" class="relative group w-[520px] transform rotate-1 hover:-rotate-1 transition-transform duration-300">
                      <!-- Pin (Enlarged) -->
                      <div class="absolute -top-6 left-1/2 -translate-x-1/2 z-20 w-10 h-10 rounded-full bg-gradient-to-br from-red-500 to-red-600 shadow-xl border-4 border-white"></div>
                      
                      <!-- Note Content -->
                      <div class="bg-gradient-to-br from-yellow-50 to-amber-50 rounded-[40px] p-12 shadow-2xl border border-yellow-200/50 relative overflow-hidden">
                          <div class="relative z-10">
                              <div class="flex flex-col gap-6">
                                  <div class="flex items-center gap-4">
                                      <div class="w-16 h-16 rounded-2xl bg-orange-500 flex items-center justify-center shadow-lg">
                                          <svg class="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                                          </svg>
                                      </div>
                                      <h3 class="text-2xl font-black text-orange-600 uppercase tracking-[0.2em]">Message</h3>
                                  </div>
                                  <p class="text-[3.5rem] font-black text-gray-900 leading-[1.2] break-keep font-serif">
                                      {{ currentPhoto.message || '오늘도 건강하고 행복한 하루 보내세요.' }}
                                  </p>
                              </div>
                          </div>
                      </div>
                  </div>

                  <!-- Info Note -->
                  <div v-if="currentPhoto" class="relative group w-[500px] transform -rotate-2 hover:rotate-2 transition-transform duration-300">
                      <div class="bg-white rounded-[40px] p-10 shadow-2xl border border-blue-100 relative overflow-hidden">
                          <div class="relative z-10 flex items-center gap-10">
                              <!-- Avatar (Much Larger) -->
                              <div class="flex-shrink-0 w-32 h-32 rounded-[32px] bg-gradient-to-br from-blue-500 via-indigo-500 to-purple-600 flex items-center justify-center text-white text-5xl font-black shadow-xl ring-8 ring-blue-50 overflow-hidden">
                                  <img 
                                      v-if="currentPhoto.sender?.profile_image_url" 
                                      :src="currentPhoto.sender.profile_image_url" 
                                      class="w-full h-full object-cover"
                                  >
                                  <span v-else>{{ (currentPhoto.sender?.name || currentPhoto.uploader || '가')[0] }}</span>
                              </div>
                              
                              <!-- Info -->
                              <div class="text-left flex-1">
                                  <div class="text-xl font-black text-blue-600 uppercase tracking-widest mb-2">From</div>
                                  <div class="text-[3.2rem] font-black text-gray-900 leading-tight">
                                      {{ currentPhoto.sender?.name || currentPhoto.uploader || '가족' }}
                                  </div>
                                  <div class="flex items-center gap-3 text-gray-500 mt-4">
                                      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 14 14">
                                          <rect x="2" y="3" width="10" height="9" rx="1" stroke-width="1.5"/>
                                          <path d="M4 3V2a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v1" stroke-width="1.5"/>
                                      </svg>
                                      <span class="text-2xl font-bold">{{ formatDate(currentPhoto.takenAt) }}</span>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>

              </div>
          </div>


          <!-- Central Playback Controls (Premium Glow) -->
          <div class="absolute bottom-16 left-1/2 -translate-x-1/2 flex items-center gap-14 pointer-events-auto z-30">
              <button 
                  @click.stop="slideshowStore.controlPrev()"
                  class="p-10 bg-stone-900/60 backdrop-blur-3xl rounded-full border border-white/10 hover:bg-stone-800/80 transition-all text-white/50 hover:text-white hover:scale-110 shadow-2xl active:scale-95"
              >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-16 h-16" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M15 19l-7-7 7-7" />
                  </svg>
              </button>
              
              <button 
                  @click.stop="toggleSlideshow"
                  class="w-[180px] h-[180px] bg-gradient-to-br from-orange-400 to-orange-600 rounded-full flex items-center justify-center shadow-[0_0_80px_rgba(249,115,22,0.6)] transition-all hover:scale-110 active:scale-90 text-white relative overflow-hidden group"
              >
                <!-- Pulsing Glow effect -->
                <div class="absolute inset-0 bg-white/20 animate-pulse rounded-full opacity-50"></div>
                
                <svg v-if="slideshowStore.isPlaying" xmlns="http://www.w3.org/2000/svg" class="w-28 h-28 relative z-10" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-28 h-28 relative z-10 ml-3" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M8 5v14l11-7z" />
                </svg>
              </button>

              <button 
                  @click.stop="slideshowStore.controlNext()"
                  class="p-10 bg-stone-900/60 backdrop-blur-3xl rounded-full border border-white/10 hover:bg-stone-800/80 transition-all text-white/50 hover:text-white hover:scale-110 shadow-2xl active:scale-95"
              >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-16 h-16" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M9 5l7 7-7 7" />
                  </svg>
              </button>
          </div>



                   <!-- 4. Right Panel: Control (Menu) -->
          <!-- Floats on the Right "Wing" -->
          <div class="absolute top-0 bottom-0 right-0 w-[420px] flex flex-col pointer-events-auto py-10 pr-10">
              
              <!-- Control Bar Container -->
              <!-- Ultra-Glassmorphism Card -->
              <div class="flex-1 bg-stone-900/85 backdrop-blur-3xl rounded-[60px] border border-white/10 shadow-[0_30px_100px_rgba(0,0,0,0.8)] flex flex-col items-center py-16 gap-12 text-white overflow-hidden relative" @click.stop>
                  
                  <!-- 1. Clock (Top) - Dominate Scale -->
                   <div class="flex flex-col items-center mb-6">
                        <div class="text-[7.5rem] font-black tracking-tighter font-mono text-white leading-none">
                            {{ currentTimeFormatted }}
                        </div>
                        <div class="text-3xl text-orange-400 font-bold mt-4 uppercase tracking-[0.3em]">
                            {{ currentDayStr }}
                        </div>
                   </div>

                  <!-- Divider (Premium Gradient) -->
                  <div class="w-48 h-[2px] bg-gradient-to-r from-transparent via-white/20 to-transparent"></div>

                  <!-- 2. Wifi Chip -->
                   <div class="flex items-center gap-6 px-10 py-5 bg-white/5 rounded-full border border-white/10">
                        <div class="flex items-center justify-center">
                            <svg v-if="slideshowStore.wifiStatus" xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                            </svg>
                            <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M3 3l18 18M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                            </svg>
                        </div>
                    </div>

                  <!-- 3. Menu Items List (Tactile Tiles) -->
                   <nav class="flex-1 flex flex-col items-center gap-8 w-full px-6 overflow-y-auto no-scrollbar py-4">
                        
                        <!-- Alert Card -->
                        <button class="menu-card group" :class="{ active: activeTab === 'alert' }" @click="toggleTab('alert')">
                            <div class="flex items-center gap-6 w-full">
                                <div class="icon-box-premium">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                       <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                                     </svg>
                                </div>
                                <div class="flex flex-col items-start gap-1">
                                    <div class="flex items-center gap-5">
                                        <span class="text-4xl font-black">알림</span>
                                        <div v-if="alertStore.history.length > 0" class="w-14 h-14 bg-red-500 rounded-full flex items-center justify-center text-2xl font-black shadow-lg shadow-red-900/40 border-2 border-white/20">
                                            {{ alertStore.history.length }}
                                        </div>
                                    </div>
                                    <span class="text-lg font-bold text-white/30 uppercase tracking-widest">Notifications</span>
                                </div>
                            </div>
                        </button>

                        <!-- Message Card -->
                        <button class="menu-card group" :class="{ active: activeTab === 'chat' }" @click="toggleTab('chat')">
                            <div class="flex items-center gap-6 w-full">
                                <div class="icon-box-premium">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                       <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                                     </svg>
                                </div>
                                <div class="flex flex-col items-start gap-1">
                                    <div class="flex items-center gap-5">
                                        <span class="text-4xl font-black whitespace-nowrap">메시지</span>
                                        <div v-if="(voiceStore.unreadCount + alertStore.chatHistory.length) > 0" 
                                             class="w-14 h-14 bg-red-500 rounded-full flex items-center justify-center text-2xl font-black shadow-lg shadow-red-900/40 border-2 border-white/20">
                                            {{ voiceStore.unreadCount + alertStore.chatHistory.length }}
                                        </div>
                                    </div>
                                    <span class="text-lg font-bold text-white/30 uppercase tracking-widest">Messages</span>
                                </div>
                            </div>
                        </button>

                        <!-- Schedule Card -->
                        <button class="menu-card group" :class="{ active: activeTab === 'schedule' }" @click="toggleTab('schedule')">
                            <div class="flex items-center gap-6 w-full">
                                <div class="icon-box-premium">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                       <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                     </svg>
                                </div>
                                <div class="flex flex-col items-start gap-1">
                                    <div class="flex items-center gap-5">
                                        <span class="text-4xl font-black whitespace-nowrap">일정</span>
                                        <div v-if="displayData.length > 0 && activeTab === 'schedule'" 
                                             class="w-14 h-14 bg-red-500 rounded-full flex items-center justify-center text-2xl font-black shadow-lg shadow-red-900/40 border-2 border-white/20">
                                            {{ displayData.length }}
                                        </div>
                                    </div>
                                    <span class="text-lg font-bold text-white/30 uppercase tracking-widest">Calendar</span>
                                </div>
                            </div>
                        </button>

                         <!-- Settings Card (Bottom) -->
                        <button class="menu-card group mt-auto mb-4" :class="{ active: activeTab === 'settings' }" @click="toggleTab('settings')">
                            <div class="flex items-center gap-6 w-full">
                                <div class="icon-box-premium">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                     </svg>
                                </div>
                                <div class="flex flex-col items-start gap-1">
                                    <span class="text-4xl font-black">설정</span>
                                    <span class="text-lg font-bold text-white/30 uppercase tracking-widest">Settings</span>
                                </div>
                            </div>
                        </button>

                   </nav>

              </div>
          </div>
      </div>
    </Transition>

          <!-- 5. Sidebar Detailed Panel -->
          <Transition name="slide-panel">
            <div v-if="activeTab && activeTab !== 'none'" class="absolute inset-y-0 right-[420px] w-[700px] z-10 pointer-events-auto p-10 flex flex-col" @click.stop>
                <div class="flex-1 bg-stone-900/98 backdrop-blur-3xl rounded-[60px] border border-white/10 shadow-3xl text-white flex flex-col overflow-hidden">
                    <!-- Header -->
                    <div class="p-6 flex items-center justify-between border-b border-white/10">
                        <h3 class="text-3xl font-black tracking-tight">{{ tabTitle }}</h3>
                        <div class="flex items-center gap-4">
                            <button 
                                v-if="activeTab === 'alert' && displayData.length > 0"
                                @click="confirmClearAlerts"
                                class="px-5 py-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-full text-sm font-bold transition-colors"
                            >
                                전체 삭제
                            </button>
                            <button @click="activeTab = null" class="p-2.5 hover:bg-white/10 rounded-full transition-colors">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </button>
                        </div>
                    </div>

                    <!-- Content -->
                    <div class="flex-1 overflow-y-auto p-8 space-y-3 no-scrollbar">
                        <div v-if="isLoading" class="flex items-center justify-center h-full">
                            <div class="animate-spin rounded-full h-12 w-12 border-b-4 border-orange-500"></div>
                        </div>
                        <template v-else>
                            <!-- Empty State -->
                            <div v-if="displayData.length === 0 && activeTab !== 'settings'" class="flex flex-col items-center justify-center h-full text-white/30 gap-6">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-24 h-24 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
                                </svg>
                                <span class="text-2xl font-medium">데이터가 없습니다</span>
                            </div>

                            <!-- List -->
                            <!-- Unified Chat/Voice Message Style -->
                            <template v-if="activeTab === 'chat'">
                                <div 
                                    v-for="(item, idx) in displayData" 
                                    :key="item.id || idx" 
                                    class="group relative bg-white/5 backdrop-blur-2xl rounded-[50px] border-2 border-white/10 hover:bg-white/10 transition-all duration-300 overflow-hidden mb-10 shadow-3xl"
                                >
                                    <!-- Sidebar Color Indicator (Unified Theme) -->
                                    <div class="absolute left-0 top-0 bottom-0 w-5 bg-gradient-to-b shadow-[0_0_30px_rgba(59,130,246,0.4)]"
                                         :class="item.type === 'VOICE' ? 'from-orange-500 to-red-500' : 'from-blue-500 to-indigo-500'">
                                    </div>

                                    <div class="flex items-stretch">
                                        <!-- Content Area -->
                                        <div class="flex-1 p-10 pr-6 flex flex-col gap-6 min-w-0" 
                                             :class="{'cursor-pointer': item.type === 'VOICE'}"
                                             @click.stop="item.type === 'VOICE' ? voiceStore.playMessage(item.id) : (selectedItem = item)">
                                            
                                            <div class="flex flex-wrap items-center gap-6 mb-2">
                                                <!-- Sender Avatar -->
                                                <div 
                                                    class="flex-shrink-0 w-20 h-20 rounded-[24px] flex items-center justify-center text-white text-3xl font-black shadow-xl ring-6 ring-white/10"
                                                    :class="getSenderColor(item.sender || '가족')"
                                                >
                                                    <img v-if="item.profile_image" :src="item.profile_image" class="w-full h-full object-cover rounded-[24px]" />
                                                    <span v-else>{{ (item.sender || '가족')[0] }}</span>
                                                </div>
                                                <div class="flex flex-col">
                                                    <span class="text-3xl font-black text-white">{{ item.sender || '가족' }}</span>
                                                    <div class="flex items-center gap-3 mt-1">
                                                        <span v-if="item.type === 'VOICE'" class="text-[1.6rem] font-bold text-orange-400 opacity-80 uppercase tracking-widest leading-none">
                                                            Voice Message
                                                        </span>
                                                        <span v-else class="text-[1.6rem] font-bold text-blue-400 opacity-80 uppercase tracking-widest leading-none">
                                                            Family Message
                                                        </span>
                                                    </div>
                                                </div>
                                                <span class="ml-auto text-xl font-mono text-white/40 font-bold self-start mt-2">
                                                    {{ formatTime(item.created_at ? item.created_at * 1000 : item.timestamp) }}
                                                </span>
                                            </div>

                                            <!-- Content Display -->
                                            <div class="relative group/bubble">
                                                <p class="text-[3.4rem] text-white font-black leading-tight break-keep">
                                                    {{ item.content || item.description }}
                                                </p>
                                                
                                                <!-- Voice Playback Controls (Embedded) -->
                                                <div v-if="item.type === 'VOICE'" class="flex items-center gap-6 mt-6 px-6 py-4 bg-white/5 rounded-[30px] w-fit border border-white/5">
                                                    <div v-if="item.status === 'playing'" class="flex gap-2 items-end h-8">
                                                        <div class="w-2 bg-orange-500 animate-[bounce_1s_infinite_0ms]" style="height: 100%"></div>
                                                        <div class="w-2 bg-orange-500 animate-[bounce_1s_infinite_200ms]" style="height: 70%"></div>
                                                        <div class="w-2 bg-orange-500 animate-[bounce_1s_infinite_400ms]" style="height: 90%"></div>
                                                    </div>
                                                    <span class="text-3xl font-black" :class="item.status === 'playing' ? 'text-orange-400' : 'text-white/60'">
                                                        {{ item.status === 'playing' ? '지금 듣는 중...' : '터치하여 재생' }}
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        <!-- Action Button (Delete for ALL) -->
                                        <button 
                                            @click.stop="item.type === 'VOICE' ? voiceStore.removeMessage(item.id) : alertStore.removeChatHistory(item.id)"
                                            class="w-32 bg-red-500/5 hover:bg-red-500/20 text-red-500/40 hover:text-red-500 flex flex-col items-center justify-center gap-4 transition-all border-l border-white/10 group/btn"
                                        >
                                            <div class="p-3 rounded-full bg-red-500/10 group-hover/btn:bg-red-500 group-hover/btn:text-white transition-all duration-300">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                </svg>
                                            </div>
                                            <span class="text-sm font-black uppercase tracking-widest opacity-60 group-hover/btn:opacity-100">삭제</span>
                                        </button>
                                    </div>
                                </div>
                            </template>

                            <!-- Alert/Schedule Style (for other tabs) -->
                            <template v-else-if="activeTab !== 'settings'">
                                <div 
                                    v-for="(item, idx) in displayData" 
                                    :key="item.id || idx" 
                                    class="group relative bg-white/5 backdrop-blur-2xl rounded-[50px] border-2 border-white/10 hover:bg-white/10 transition-all duration-300 overflow-hidden mb-10 shadow-3xl"
                                >
                                    <!-- Sidebar Color Indicator (Thicker & Glowing) -->
                                    <div class="absolute left-0 top-0 bottom-0 w-6 shadow-[0_0_40px_rgba(0,0,0,0.6)]" :class="getTypeColor(item.type || item.kind)"></div>
 
                                    <div class="flex items-stretch">
                                        <!-- Content Area (Mega Content) -->
                                        <div class="flex-1 p-10 pr-6 flex flex-col gap-6 min-w-0 cursor-pointer" @click.stop="selectedItem = item">
                                            <div class="flex flex-wrap items-baseline gap-8 mb-2">
                                                <span class="text-4xl font-black text-white">
                                                    {{ item.title || item.sender || (activeTab === 'alert' ? '알림' : '일정') }}
                                                </span>
                                                <span class="text-xl font-mono text-orange-400 font-black uppercase tracking-widest px-4 py-1 bg-white/5 rounded-full border border-white/10">
                                                    {{ formatTime(item.createdAt || item.startAt || item.timestamp) }}
                                                </span>
                                            </div>
                                            
                                            <div class="flex flex-col gap-2">
                                                <p class="text-[3.2rem] text-white leading-tight font-black break-keep">
                                                    {{ item.content || item.message || item.description || '' }}
                                                </p>
                                                <p v-if="item.data?.text_message && item.data.text_message !== item.content" 
                                                   class="text-2xl font-bold text-rose-400/60 italic">
                                                    {{ item.data.text_message }}
                                                </p>
                                            </div>
  
                                            <div v-if="item.type" class="text-2xl font-black text-white/40 tracking-[0.4em] uppercase mt-4 px-6 py-2 bg-white/5 rounded-2xl w-fit border border-white/5">
                                                {{ item.type }}
                                            </div>
                                        </div>
 
                                        <!-- Massive Delete Strip (Standardized) -->
                                        <button 
                                            v-if="activeTab === 'alert'"
                                            @click.stop="alertStore.removeHistory(item.id)"
                                            class="w-32 bg-red-500/5 hover:bg-red-500/20 text-red-500/40 hover:text-red-500 flex flex-col items-center justify-center gap-4 transition-all border-l border-white/10"
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" class="w-14 h-14" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                            </svg>
                                            <span class="text-lg font-black uppercase">삭제</span>
                                        </button>
                                    </div>
                                </div>
                            </template>

                            <!-- Settings UI -->
                            <template v-else-if="activeTab === 'settings'">
                                <div class="space-y-10">
                                    <!-- Current WiFi Status -->
                                    <div class="bg-white/5 backdrop-blur-2xl rounded-[40px] border-2 border-white/10 p-10 shadow-2xl">
                                        <div class="flex items-start flex-wrap gap-8">
                                            <div class="w-20 h-20 rounded-2xl bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center shadow-lg shrink-0">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                                                </svg>
                                            </div>
                                            <div class="flex-1 min-w-[300px]">
                                                <h4 class="text-3xl font-black text-white">현재 연결</h4>
                                                <p class="text-[2rem] text-blue-400 font-bold mt-1 break-all">{{ activeSSID || '연결되지 않음' }}</p>
                                            </div>
                                            <button 
                                                @click="scanWiFi(true)"
                                                :disabled="wifiScanning || wifiConnecting"
                                                class="px-10 py-5 bg-blue-500 hover:bg-blue-600 text-white rounded-[24px] text-2xl font-black transition-all disabled:opacity-50 shadow-lg shadow-blue-500/20 ml-auto"
                                            >
                                                {{ wifiScanning ? '찾는 중...' : '새로고침' }}
                                            </button>
                                        </div>
                                    </div>

                                    <!-- Available Networks -->
                                    <div class="bg-white/5 backdrop-blur-2xl rounded-[40px] border-2 border-white/10 p-10 shadow-2xl">
                                        <h4 class="text-3xl font-black text-white mb-8">연결 가능한 와이파이</h4>
                                        <div class="space-y-6 max-h-[500px] overflow-y-auto no-scrollbar pr-2">
                                            <div 
                                                v-for="ap in wifiAPs" 
                                                :key="ap.ssid"
                                                @click="selectAP(ap)"
                                                class="flex flex-wrap items-center justify-between p-6 bg-white/5 hover:bg-white/10 rounded-[30px] cursor-pointer transition-all border-2 border-white/5 gap-6"
                                                :class="{ 'bg-blue-500/20 border-blue-500/30': ap.in_use }"
                                            >
                                                <div class="flex items-center gap-6 min-w-[200px]">
                                                    <div class="w-16 h-16 rounded-xl bg-white/10 flex items-center justify-center shrink-0">
                                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0" />
                                                        </svg>
                                                    </div>
                                                    <div class="flex-1">
                                                        <div class="text-[2rem] font-black text-white leading-none break-all">{{ ap.ssid }}</div>
                                                        <div class="text-lg text-white/50 mt-1 uppercase tracking-widest font-bold">{{ ap.security }}</div>
                                                    </div>
                                                </div>
                                                <div class="flex items-center gap-6 ml-auto">
                                                    <div v-if="wifiConnecting && connectingSSID === ap.ssid" class="px-6 py-3 bg-blue-500/20 text-blue-400 rounded-2xl text-lg font-black animate-pulse">
                                                        연결 중...
                                                    </div>
                                                    <div v-else-if="ap.in_use" class="px-6 py-3 bg-blue-500 text-white rounded-2xl text-lg font-black shadow-lg shadow-blue-500/30">
                                                        사용 중
                                                    </div>
                                                    <div class="text-white/40 text-xl font-black">{{ ap.signal }}%</div>
                                                </div>
                                            </div>
                                            <div v-if="wifiAPs.length === 0" class="text-center py-16 text-white/20 text-2xl font-bold">
                                                주변에 와이파이가 없습니다
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Saved Profiles -->
                                    <div class="bg-white/5 backdrop-blur-2xl rounded-[40px] border-2 border-white/10 p-10 shadow-2xl">
                                        <div class="flex flex-wrap items-center justify-between mb-8 gap-6">
                                            <h4 class="text-3xl font-black text-white">저장된 네트워크</h4>
                                            <button 
                                                @click="getWiFiProfiles(true)"
                                                class="px-6 py-3 bg-white/10 hover:bg-white/20 text-white rounded-2xl text-xl font-black transition-all"
                                            >
                                                새로고침
                                            </button>
                                        </div>
                                        <div class="space-y-6">
                                            <div 
                                                v-for="profile in wifiProfiles" 
                                                :key="profile.name"
                                                class="flex flex-wrap items-center justify-between p-6 bg-white/5 rounded-[30px] border-2 border-white/5 gap-6"
                                            >
                                                <div class="flex items-center gap-6 min-w-[200px] flex-1" :class="{ 'opacity-40 grayscale': !availableSSIDs.has(profile.ssid) }">
                                                    <div class="w-14 h-14 rounded-xl bg-purple-500/20 flex items-center justify-center shrink-0">
                                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-purple-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                                                        </svg>
                                                    </div>
                                                    <div class="flex-1">
                                                        <div class="text-[2rem] font-black text-white flex flex-wrap items-center gap-3 break-all">
                                                            {{ profile.ssid }}
                                                            <span v-if="!availableSSIDs.has(profile.ssid)" class="px-3 py-1 bg-red-500/10 text-red-400 rounded-lg text-sm font-bold border border-red-500/20 whitespace-nowrap">
                                                                검색되지 않음
                                                            </span>
                                                        </div>
                                                        <div class="text-lg text-white/40 mt-1 font-bold">
                                                            {{ profile.autoconnect ? '자동 연결됨' : '수동 연결' }}
                                                        </div>
                                                    </div>
                                                </div>
                                                <div class="flex items-center gap-4 ml-auto">
                                                    <button 
                                                        @click="connectProfile(profile.name)"
                                                        :disabled="wifiConnecting || !availableSSIDs.has(profile.ssid)"
                                                        class="px-8 py-4 bg-blue-500/20 hover:bg-blue-500/30 text-blue-400 rounded-xl text-xl font-black transition-all disabled:opacity-30 disabled:grayscale"
                                                    >
                                                        {{ wifiConnecting && connectingSSID === profile.ssid ? '연결 중...' : '연결' }}
                                                    </button>
                                                    <button 
                                                        @click="deleteProfile(profile.name)"
                                                        class="px-8 py-4 bg-red-500/20 hover:bg-red-500/30 text-red-400 rounded-xl text-xl font-black transition-all"
                                                    >
                                                        삭제
                                                    </button>
                                                </div>
                                            </div>
                                            <div v-if="wifiProfiles.length === 0" class="text-center py-16 text-white/20 text-2xl font-bold">
                                                저장된 정보가 없습니다
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Brightness Control -->
                                    <div class="bg-white/5 backdrop-blur-2xl rounded-[40px] border-2 border-white/10 p-10 shadow-2xl">
                                        <div class="flex items-center gap-6 mb-8">
                                            <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-amber-500 to-yellow-500 flex items-center justify-center shadow-lg shrink-0">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                                                </svg>
                                            </div>
                                            <div class="flex-1">
                                                <h4 class="text-3xl font-black text-white">화면 밝기</h4>
                                                <p class="text-[2rem] text-amber-400 font-extrabold mt-1">{{ brightness }}%</p>
                                            </div>
                                        </div>
                                        <div class="px-2">
                                            <input 
                                                type="range" 
                                                v-model="brightness" 
                                                @input="adjustBrightness(brightness)"
                                                min="0" 
                                                max="100" 
                                                class="w-full h-12 bg-white/10 rounded-full appearance-none cursor-pointer slider"
                                            />
                                        </div>
                                    </div>

                                    <!-- Volume Control -->
                                    <div class="bg-white/5 backdrop-blur-2xl rounded-[40px] border-2 border-white/10 p-10 shadow-2xl">
                                        <div class="flex items-center gap-6 mb-8">
                                            <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center shadow-lg shrink-0">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                                                </svg>
                                            </div>
                                            <div class="flex-1">
                                                <h4 class="text-3xl font-black text-white">소리 크기</h4>
                                                <p class="text-[2rem] text-purple-400 font-extrabold mt-1">{{ volume }}%</p>
                                            </div>
                                        </div>
                                        <div class="px-2">
                                            <input 
                                                type="range" 
                                                v-model="volume" 
                                                @input="adjustVolume(volume)"
                                                min="0" 
                                                max="100" 
                                                class="w-full h-12 bg-white/10 rounded-full appearance-none cursor-pointer slider"
                                            />
                                        </div>
                                    </div>

                                    <!-- Restart Button -->
                                    <div class="bg-white/5 backdrop-blur-2xl rounded-[40px] border-2 border-white/10 p-10 shadow-2xl">
                                        <div class="flex flex-wrap items-center justify-between gap-6">
                                            <div class="flex items-center gap-6">
                                                <div class="w-16 h-16 rounded-2xl bg-white/10 flex items-center justify-center shrink-0">
                                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                                    </svg>
                                                </div>
                                                <div>
                                                    <h4 class="text-3xl font-black text-white">기기 재시작</h4>
                                                    <p class="text-xl text-white/40 mt-1 font-bold">시스템을 다시 시작합니다</p>
                                                </div>
                                            </div>
                                            <button 
                                                @click="restartDevice"
                                                class="px-10 py-5 bg-red-500 hover:bg-red-600 text-white rounded-[24px] text-2xl font-black transition-all shadow-lg shadow-red-500/20 ml-auto"
                                            >
                                                재시작
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </template>
                        </template>
                    </div>
                </div>
            </div>
          </Transition>

          <!-- 6. Detail View Modal (Premium Overlay) -->
          <Transition name="fade-modal">
              <div v-if="selectedItem" class="absolute inset-0 z-[100] flex items-center justify-center p-20 pointer-events-auto" @click="selectedItem = null">
                  <!-- Background blurring overlay -->
                  <div class="absolute inset-0 bg-black/40 modal-backdrop"></div>
                  
                  <!-- Modal Content -->
                  <div class="relative w-[800px] max-h-[90vh] bg-stone-900/90 border border-white/10 rounded-[4rem] shadow-3xl overflow-hidden flex flex-col transform transition-all animate-scale-up backdrop-blur-md" @click.stop>
                      <!-- Top Color Header -->
                      <div class="h-4 w-full" :class="selectedItem.type === 'EMERGENCY' ? 'bg-red-500' : 'bg-orange-500'"></div>
                      
                      <div class="p-10 flex flex-col gap-10 overflow-y-auto no-scrollbar">
                          <!-- Header -->
                          <div class="flex justify-between items-start">
                              <div class="flex flex-col gap-2">
                                  <div class="text-orange-400 font-bold text-xl uppercase tracking-widest">
                                      {{ selectedItem.type || 'MESSAGE' }}
                                  </div>
                                  <h2 class="text-6xl font-black text-white">
                                      {{ selectedItem.title || selectedItem.sender || (activeTab === 'alert' ? '알림' : '') }}
                                  </h2>
                              </div>
                              <span class="text-2xl font-mono text-white/30 bg-white/5 py-2 px-6 rounded-full self-start">
                                  {{ formatTime(selectedItem.createdAt || selectedItem.startAt || selectedItem.timestamp) }}
                              </span>
                          </div>

                           <!-- Body Text -->
                           <p class="text-5xl text-white leading-tight font-black break-keep">
                               {{ selectedItem.content || selectedItem.message || selectedItem.description }}
                           </p>

                           <div class="h-px w-full bg-white/10 my-4"></div>

                           <!-- Medication List Detail -->
                           <div v-if="selectedItem.kind === 'medication' && selectedItem.data?.medication_list?.length > 0" class="flex flex-col gap-8">
                               <div class="flex items-center gap-4">
                                   <div class="w-12 h-12 rounded-2xl bg-orange-500 flex items-center justify-center shadow-lg shadow-orange-900/40">
                                       <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                           <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
                                       </svg>
                                   </div>
                                   <div class="text-3xl font-black text-white/90 uppercase tracking-widest">복약 대상</div>
                               </div>
                               
                               <div class="grid grid-cols-1 gap-4">
                                   <div v-for="med in selectedItem.data.medication_list" :key="med" 
                                         class="px-8 py-6 bg-white/5 border border-white/10 rounded-[30px] flex items-center justify-between shadow-2xl group hover:bg-white/10 transition-all">
                                       <div class="flex items-center gap-6">
                                           <div class="w-10 h-10 rounded-full bg-blue-500 animate-pulse shadow-[0_0_15px_rgba(59,130,246,0.5)]"></div>
                                           <span class="text-4xl font-black text-white leading-tight break-all">{{ med }}</span>
                                       </div>
                                   </div>
                               </div>
                           </div>

                          <!-- Footer Buttons -->
                          <div class="flex justify-end gap-4 mt-4">
                              <button 
                                  @click="selectedItem = null"
                                  class="px-10 py-5 bg-white/10 hover:bg-white/20 text-white rounded-3xl text-2xl font-black transition-all"
                              >
                                  닫기
                              </button>
                          </div>
                      </div>
                  </div>
              </div>
          </Transition>

          <!-- WiFi Password Modal -->
          <Transition name="fade-modal">
              <div v-if="showPasswordModal" class="absolute inset-0 z-[100] flex items-center justify-center p-20 pointer-events-auto" @click="showPasswordModal = false">
                  <!-- Background blurring overlay -->
                  <div class="absolute inset-0 bg-black/40 modal-backdrop"></div>
                  
                  <!-- Modal Content -->
                  <div class="relative w-[900px] bg-stone-900/95 border border-white/10 rounded-[4rem] shadow-3xl overflow-hidden flex flex-col transform transition-all animate-scale-up backdrop-blur-md" @click.stop>
                      <!-- Top Color Header -->
                      <div class="h-6 w-full bg-blue-500"></div>
                      
                      <div class="p-16 flex flex-col gap-12">
                          <!-- Header -->
                          <div class="flex flex-col gap-4">
                              <div class="text-blue-400 font-bold text-3xl uppercase tracking-widest">
                                  Wi-Fi 연결
                              </div>
                              <h2 class="text-6xl font-black text-white leading-tight">
                                  {{ selectedAP?.ssid }}
                              </h2>
                          </div>

                          <!-- Password Input -->
                          <div class="flex flex-col gap-6">
                              <label class="text-3xl font-bold text-white/80">비밀번호</label>
                              <input 
                                  type="password"
                                  v-model="wifiPassword"
                                  @keyup.enter="submitPassword"
                                  placeholder="비밀번호를 입력하세요"
                                  class="w-full px-10 py-8 bg-white/10 border border-white/20 rounded-3xl text-4xl text-white placeholder-white/30 focus:outline-none focus:border-blue-500/50 focus:bg-white/15 transition-all text-center tracking-widest"
                                  autofocus
                              />
                          </div>

                          <!-- Footer Buttons -->
                          <div class="flex justify-end gap-6 mt-8">
                              <button 
                                  @click="showPasswordModal = false"
                                  class="px-12 py-6 bg-white/10 hover:bg-white/20 text-white rounded-3xl text-3xl font-black transition-all"
                              >
                                  취소
                              </button>
                              <button 
                                  @click="submitPassword"
                                  :disabled="!wifiPassword"
                                  class="px-12 py-6 bg-blue-500 hover:bg-blue-600 text-white rounded-3xl text-3xl font-black transition-all disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-blue-500/30"
                              >
                                  연결하기
                              </button>
                          </div>
                      </div>
                  </div>
              </div>
          </Transition>

          <!-- Custom Alert/Confirm Modal -->
          <Transition name="fade-modal">
             <div v-if="generalModal.show" class="absolute inset-0 z-[110] flex items-center justify-center p-20 pointer-events-auto">
                 <div class="absolute inset-0 bg-black/60 modal-backdrop" @click="handleModalCancel"></div>
                 
                 <div class="relative w-[800px] bg-stone-900/95 border border-white/10 rounded-[4rem] shadow-3xl overflow-hidden flex flex-col transform transition-all animate-scale-up backdrop-blur-md" @click.stop>
                     <div class="h-6 w-full" :class="generalModal.type === 'confirm' ? 'bg-orange-500' : 'bg-blue-500'"></div>
                     
                     <div class="p-16 flex flex-col gap-10 text-center items-center">
                         <div class="flex flex-col gap-6">
                             <div class="text-3xl font-black uppercase tracking-widest" :class="generalModal.type === 'confirm' ? 'text-orange-500' : 'text-blue-500'">
                                 {{ generalModal.title }}
                             </div>
                             <p class="text-4xl text-white font-bold leading-relaxed break-keep whitespace-pre-wrap">{{ generalModal.message }}</p>
                         </div>
                         
                         <div class="flex justify-center gap-6 w-full mt-4">
                             <button 
                                 v-if="generalModal.type === 'confirm'"
                                 @click="handleModalCancel"
                                 class="flex-1 py-6 bg-white/10 hover:bg-white/20 text-white rounded-3xl text-3xl font-black transition-all"
                             >
                                 취소
                             </button>
                             <button 
                                 @click="handleModalConfirm"
                                 class="flex-1 py-6 text-white rounded-3xl text-3xl font-black transition-all shadow-lg hover:scale-[1.02] active:scale-95"
                                 :class="generalModal.type === 'confirm' ? 'bg-orange-500 hover:bg-orange-600 shadow-orange-900/30' : 'bg-blue-500 hover:bg-blue-600 shadow-blue-900/30'"
                             >
                                 확인
                             </button>
                         </div>
                     </div>
                 </div>
             </div>
          </Transition>




  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useSlideshowStore } from '@/stores/slideshow'
import { useAlertStore } from '@/stores/alert'
import { useVoiceStore } from '@/stores/voice'

const slideshowStore = useSlideshowStore()
const alertStore = useAlertStore()
const voiceStore = useVoiceStore()
const activeTab = ref(null) 

const toggleTab = (tab) => {
    if (activeTab.value === tab) activeTab.value = null
    else activeTab.value = tab
}

const tabTitle = computed(() => {
    switch (activeTab.value) {
        case 'alert': return '최근 알림'
        case 'chat': return '가족 메시지'
        case 'schedule': return '오늘의 일정'
        case 'settings': return '기기 설정'
        default: return ''
    }
})

const isLoading = ref(false)
const tabData = ref([])

const displayData = computed(() => {
    if (activeTab.value === 'alert') return alertStore.history
    if (activeTab.value === 'chat') {
        const combined = [...voiceStore.voiceMessages]
        return combined.sort((a, b) => {
             const tA = a.created_at ? a.created_at * 1000 : new Date(a.timestamp).getTime()
             const tB = b.created_at ? b.created_at * 1000 : new Date(b.timestamp).getTime()
             return tB - tA 
        })
    }
    if (activeTab.value === 'schedule') {
        const flattened = []
        tabData.value.forEach(item => {
            if (item.data?.events_for_today) {
                item.data.events_for_today.forEach((event, eIdx) => {
                    const today = new Date();
                    const dateStr = today.toISOString().split('T')[0];
                    flattened.push({
                        id: `${item.id}-${eIdx}`,
                        type: 'SCHEDULE',
                        kind: 'schedule',
                        title: '오늘의 일정',
                        message: `[${event.time}] ${event.title}`,
                        startAt: `${dateStr}T${event.time}:00Z`,
                        data: {} 
                    })
                })
            } else {
                flattened.push(item)
            }
        })
        return flattened
    }
    return tabData.value
})

const fetchTabData = async (tab) => {
    if (!tab || tab === 'alert' || tab === 'settings') return

    isLoading.value = true
    try {
        const apiUrl = import.meta.env.VITE_API_URL || ''
        const token = localStorage.getItem('iotAccessToken')
        let endpoint = ''
        
        if (tab === 'schedule') endpoint = '/api/iot/device/schedules'
        else if (tab === 'chat') endpoint = '/api/iot/device/sync/voice?lastLogId=0'

        if (!endpoint) return

        const response = await fetch(`${apiUrl}${endpoint}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        })
        const result = await response.json()
        if (result.data) {
            if (tab === 'chat') {
                
                const items = result.data.items || []
                
                voiceStore.voiceMessages = items.map(item => ({
                    id: item.id,
                    sender: item.sender?.name || '알 수 없음',
                    content: item.description || '', 
                    created_at: item.created_at, 
                    profile_image: item.sender?.profile_image_url,
                    type: 'VOICE',
                    status: 'pending'
                }))
            } else {
                tabData.value = result.data
            }
        }
    } catch (e) {
        console.error('Fetch failed:', e)
        tabData.value = []
    } finally {
        isLoading.value = false
    }
}

const confirmClearAlerts = async () => {
    if (activeTab.value !== 'alert' || displayData.value.length === 0) return
    
    if (await showConfirm('모든 알림을 삭제하시겠습니까?\n삭제된 알림은 복구할 수 없습니다.', '전체 삭제')) {
        alertStore.clearHistory()
        await showAlert('모든 알림이 삭제되었습니다.', '삭제 완료')
    }
}


const currentPhoto = computed(() => slideshowStore.currentSlide)
const isDetailView = ref(false)
const selectedItem = ref(null)
const toggleDetailView = () => {
    isDetailView.value = !isDetailView.value
    if (isDetailView.value) activeTab.value = null
}
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
    const days = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일']
    return days[now.value.getDay()]
})
const currentDateFormatted = computed(() => {
    
    return now.value.toLocaleDateString()
})

onMounted(() => {
  slideshowStore.startStream()
  slideshowStore.updateWifiStatus()
  voiceStore.connect()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => { 
    if(timer) clearInterval(timer)
    voiceStore.disconnect()
})

const formatTime = (timeStr) => {
    if (!timeStr) return ''
    try {
        const d = new Date(timeStr)
        return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
    } catch (e) {
        return ''
    }
}

const formatDate = (dateStr) => dateStr || ''

const getSenderColor = (sender) => {
    
    let hash = 0
    const str = sender.trim()
    
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i)
        hash = ((hash << 5) - hash) + char
        hash = hash & hash 
    }
    
    
    hash = Math.abs(hash + str.length * 7919)
    
    
    const colorPalettes = [
        'bg-gradient-to-br from-blue-500/30 to-cyan-500/30 border-blue-400/40',        
        'bg-gradient-to-br from-purple-500/30 to-pink-500/30 border-purple-400/40',    
        'bg-gradient-to-br from-green-500/30 to-emerald-500/30 border-green-400/40',   
        'bg-gradient-to-br from-orange-500/30 to-amber-500/30 border-orange-400/40',   
        'bg-gradient-to-br from-rose-500/30 to-red-500/30 border-rose-400/40',         
        'bg-gradient-to-br from-indigo-500/30 to-violet-500/30 border-indigo-400/40',  
        'bg-gradient-to-br from-teal-500/30 to-cyan-500/30 border-teal-400/40',        
        'bg-gradient-to-br from-fuchsia-500/30 to-purple-500/30 border-fuchsia-400/40',
        'bg-gradient-to-br from-lime-500/30 to-green-500/30 border-lime-400/40',       
        'bg-gradient-to-br from-amber-500/30 to-yellow-500/30 border-amber-400/40',    
        'bg-gradient-to-br from-sky-500/30 to-blue-500/30 border-sky-400/40',          
        'bg-gradient-to-br from-pink-500/30 to-rose-500/30 border-pink-400/40'         
    ]
    
    const index = hash % colorPalettes.length
    return colorPalettes[index]
}

const getTypeColor = (type) => {
    if (!type) return 'bg-orange-500'
    const t = type.toLowerCase()
    if (t === 'emergency') return 'bg-red-500'
    if (t === 'voice') return 'bg-indigo-500'
    if (t === 'medication') return 'bg-rose-500'
    if (t === 'schedule') return 'bg-amber-500'
    return 'bg-orange-500'
}


const brightness = ref(80)
const volume = ref(50)


const wifiApiBase = 'http://localhost:8080'
const wifiScanning = ref(false)
const wifiConnecting = ref(false)
const wifiConnectionError = ref(null)
const connectingSSID = ref(null)
const wifiAPs = ref([])
const wifiProfiles = ref([])
const activeSSID = ref(null)
const selectedAP = ref(null)
const wifiPassword = ref('')
const showPasswordModal = ref(false)
const wifiPingInterval = ref(null)
const wifiActiveInterval = ref(null)

const availableSSIDs = computed(() => {
    return new Set(wifiAPs.value.map(ap => ap.ssid))
})


const pingWifiUI = async () => {
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/ui/ping`, { method: 'POST' })
        if (!response.ok && response.status !== 401) {
            console.warn('WiFi UI ping failed:', response.status)
        }
    } catch (e) {
        
    }
}


const generalModal = ref({
    show: false,
    type: 'alert', 
    title: '',
    message: '',
    onConfirm: null,
    onCancel: null
})

const showAlert = (message, title = '알림') => {
    return new Promise((resolve) => {
        generalModal.value = {
            show: true,
            type: 'alert',
            title,
            message,
            onConfirm: () => {
                generalModal.value.show = false
                resolve()
            },
            onCancel: () => {
                generalModal.value.show = false
                resolve()
            }
        }
    })
}

const showConfirm = (message, title = '확인') => {
    return new Promise((resolve) => {
        generalModal.value = {
            show: true,
            type: 'confirm',
            title,
            message,
            onConfirm: () => {
                generalModal.value.show = false
                resolve(true)
            },
            onCancel: () => {
                generalModal.value.show = false
                resolve(false)
            }
        }
    })
}

const handleModalConfirm = () => {
    if (generalModal.value.onConfirm) generalModal.value.onConfirm()
}

const handleModalCancel = () => {
    if (generalModal.value.onCancel) generalModal.value.onCancel()
}


const scanWiFi = async (forceRescan = false) => {
    if (wifiScanning.value) return
    wifiScanning.value = true
    
    try {
        const url = `${wifiApiBase}/api/wifi/scan${forceRescan ? '?scan=true' : ''}`
        const response = await fetch(url)
        
        if (!response.ok) {
            if (response.status === 401) {
                console.warn('WiFi API requires authentication')
            }
            return
        }
        
        const data = await response.json()
        
        if (data.ok) {
            activeSSID.value = data.active_ssid
            wifiAPs.value = data.aps || []
            
            if (data.skipped) {
                console.log('WiFi scan skipped (busy)')
            }
        }
    } catch (e) {
        console.error('WiFi scan failed:', e)
    } finally {
        wifiScanning.value = false
    }
}


const getActiveWiFi = async () => {
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/active`)
        
        if (!response.ok) {
            if (response.status === 401) {
                
                return
            }
            slideshowStore.wifiStatus = false
            return
        }
        
        const data = await response.json()
        activeSSID.value = data.ssid
        
        
        if (data.ssid) {
            slideshowStore.wifiStatus = true
        } else {
            slideshowStore.wifiStatus = false
        }
    } catch (e) {
        
        slideshowStore.wifiStatus = false
    }
}


const getWiFiProfiles = async (refresh = false) => {
    try {
        const url = `${wifiApiBase}/api/wifi/profiles${refresh ? '?refresh=true' : ''}`
        const response = await fetch(url)
        
        if (!response.ok) {
            if (response.status === 401) {
                console.warn('WiFi API requires authentication')
            }
            return
        }
        
        const data = await response.json()
        
        if (data.ok) {
            activeSSID.value = data.active_ssid
            wifiProfiles.value = data.profiles || []
        }
    } catch (e) {
        console.error('Get WiFi profiles failed:', e)
    }
}


const connectWiFi = async (ssid, password) => {
    if (wifiConnecting.value) return
    wifiConnecting.value = true
    connectingSSID.value = ssid
    wifiConnectionError.value = null
    
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/connect`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ssid, password })
        })
        const data = await response.json()
        
        if (data.ok) {
            if (data.skipped) {
                wifiConnecting.value = false
                await showAlert('이미 연결되어 있습니다.')
                await slideshowStore.updateWifiStatus()
            } else {
                console.log(`Connecting to WiFi: ${ssid}...`)
                
                let attempts = 8
                const checkInterval = setInterval(async () => {
                    await getActiveWiFi()
                    attempts--
                    console.log(`Polling WiFi status... (${attempts} attempts left). Current: ${activeSSID.value}`)
                    if (activeSSID.value === ssid || attempts <= 0) {
                        clearInterval(checkInterval)
                        if (activeSSID.value === ssid) {
                            await showAlert(`${ssid}에 연결되었습니다.`, '연결 성공')
                            await scanWiFi()
                            await getWiFiProfiles()
                            await slideshowStore.updateWifiStatus()
                        } else {
                            wifiConnectionError.value = '연결에 실패했습니다. 비밀번호를 확인해주세요.'
                            await showAlert(wifiConnectionError.value, '연결 실패')
                        }
                        wifiConnecting.value = false
                        connectingSSID.value = null
                    }
                }, 1000)
            }
        } else {
            wifiConnectionError.value = `연결 실패: ${data.message || '알 수 없는 오류'}`
            await showAlert(wifiConnectionError.value, '오류')
            wifiConnecting.value = false
            connectingSSID.value = null
        }
    } catch (e) {
        console.error('WiFi connect failed:', e)
        wifiConnectionError.value = '연결 요청 중 오류가 발생했습니다.'
        await showAlert(wifiConnectionError.value, '오류')
        wifiConnecting.value = false
        connectingSSID.value = null
    }
}


const connectProfile = async (profileName) => {
    if (wifiConnecting.value) return
    
    const profile = wifiProfiles.value.find(p => p.name === profileName)
    const targetSSID = profile ? profile.ssid : null
    
    wifiConnecting.value = true
    connectingSSID.value = targetSSID
    wifiConnectionError.value = null
    
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/profile/connect`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: profileName })
        })
        const data = await response.json()
        
        if (data.ok) {
            if (data.skipped) {
                wifiConnecting.value = false
                await showAlert('이미 연결되어 있습니다.')
                await slideshowStore.updateWifiStatus()
            } else {
                console.log(`Connecting to Profile: ${profileName}...`)
                
                let attempts = 8
                const checkInterval = setInterval(async () => {
                    await getActiveWiFi()
                    const profile = wifiProfiles.value.find(p => p.name === profileName)
                    const targetSSID = profile ? profile.ssid : null
                    
                    attempts--
                    console.log(`Polling Profile status... (${attempts} attempts left). Active: ${activeSSID.value}, Target: ${targetSSID}`)
                    
                    if ((targetSSID && activeSSID.value === targetSSID) || attempts <= 0) {
                         clearInterval(checkInterval)
                         if (targetSSID && activeSSID.value === targetSSID) {
                             await showAlert(`${targetSSID}에 연결되었습니다.`, '연결 성공')
                             await scanWiFi()
                             await slideshowStore.updateWifiStatus()
                         } else {
                             wifiConnectionError.value = '연결에 실패했습니다. 다시 시도해주세요.'
                             await showAlert(wifiConnectionError.value, '연결 실패')
                         }
                         wifiConnecting.value = false
                         connectingSSID.value = null
                    }
                }, 1000)
            }
        }
    } catch (e) {
        console.error('Profile connect failed:', e)
        wifiConnectionError.value = '연결 권한이 없거나 오류가 발생했습니다.'
        wifiConnecting.value = false
        connectingSSID.value = null
    }
}


const deleteProfile = async (profileName) => {
    if (!await showConfirm(`${profileName}\n프로필을 삭제하시겠습니까?`, '삭제 확인')) return
    
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/profile/delete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: profileName })
        })
        const data = await response.json()
        
        if (data.ok) {
            await showAlert('프로필이 삭제되었습니다.', '삭제 완료')
            await getWiFiProfiles(true)
        } else {
            await showAlert(`삭제 실패: ${data.message || '알 수 없는 오류'}`, '오류')
        }
    } catch (e) {
        console.error('Profile delete failed:', e)
        await showAlert('프로필 삭제 중 오류가 발생했습니다.', '오류')
    }
}


const selectAP = (ap) => {
    if (ap.in_use) {
        showAlert('이미 연결된 네트워크입니다.')
        return
    }
    selectedAP.value = ap
    wifiPassword.value = ''
    showPasswordModal.value = true
}


const submitPassword = async () => {
    if (!selectedAP.value || !wifiPassword.value) return
    
    showPasswordModal.value = false
    await connectWiFi(selectedAP.value.ssid, wifiPassword.value)
    selectedAP.value = null
    wifiPassword.value = ''
}


watch(activeTab, async (newTab, oldTab) => {
    if (newTab === 'settings' && oldTab !== 'settings') {
        
        await pingWifiUI()
        await scanWiFi()
        await getWiFiProfiles()
        await getActiveWiFi()
        
        
        wifiPingInterval.value = setInterval(pingWifiUI, 3000)
        
        
        wifiActiveInterval.value = setInterval(getActiveWiFi, 1000)
    } else if (oldTab === 'settings' && newTab !== 'settings') {
        
        if (wifiPingInterval.value) {
            clearInterval(wifiPingInterval.value)
            wifiPingInterval.value = null
        }
        if (wifiActiveInterval.value) {
            clearInterval(wifiActiveInterval.value)
            wifiActiveInterval.value = null
        }
    }
    
    
    if (newTab) fetchTabData(newTab)
    else tabData.value = []
})

const adjustBrightness = (value) => {
    brightness.value = Math.max(0, Math.min(100, value))
    console.log('Brightness set to:', brightness.value)
}

const adjustVolume = (value) => {
    volume.value = Math.max(0, Math.min(100, value))
    console.log('Volume set to:', volume.value)
}


const slideInterval = ref(60)

const toggleSlideshow = () => {
    if (slideshowStore.isPlaying) {
        slideshowStore.controlPause()
    } else {
        slideshowStore.controlPlay(slideInterval.value)
    }
}

const updateInterval = () => {
    if (slideshowStore.isPlaying) {
        slideshowStore.controlPlay(slideInterval.value)
    }
}

const restartDevice = async () => {
    if (!await showConfirm('기기를 재시작하시겠습니까?', '재시작 확인')) return
    
    try {
        const apiUrl = import.meta.env.VITE_API_URL || ''
        const token = localStorage.getItem('iotAccessToken')
        const response = await fetch(`${apiUrl}/api/iot/device/restart`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        })
        if (response.ok) {
            await showAlert('기기를 재시작합니다.', '명령 전송됨')
        } else {
             throw new Error('Response not ok') 
        }
    } catch (e) {
        console.error('Device restart failed:', e)
        await showAlert('기기 재시작에 실패했습니다.', '오류')
    }
}


const touchStartX = ref(0)
const touchEndX = ref(0)

const handleTouchStart = (e) => {
    touchStartX.value = e.changedTouches[0].screenX
}

const handleTouchEnd = (e) => {
    touchEndX.value = e.changedTouches[0].screenX
    handleSwipe()
}

const handleSwipe = () => {
    const threshold = 50 
    const distance = touchStartX.value - touchEndX.value
    
    
    if (isDetailView.value || activeTab.value !== null || generalModal.value.show || showPasswordModal.value || selectedItem.value) return

    if (Math.abs(distance) > threshold) {
        if (distance > 0) {
             
             console.log('Swipe Left -> Next')
             slideshowStore.controlNext()
        } else {
             
             console.log('Swipe Right -> Prev')
             slideshowStore.controlPrev()
        }
    }
}
</script>

<style scoped>
.post-it-yellow { background: linear-gradient(135deg, #fefce8 0%, #fef08a 100%); color: #1f2937; border-radius: 4px; }
.post-it-blue { background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); color: #1e3a8a; border-radius: 4px; }


.menu-card {
    @apply w-full bg-white/5 border border-white/10 rounded-[32px] p-6 transition-all duration-300 flex items-center opacity-70 hover:opacity-100 hover:bg-white/10 hover:scale-[1.03] active:scale-95;
}
.menu-card.active {
    @apply opacity-100 bg-gradient-to-br from-orange-500/30 to-rose-600/30 border-orange-400 shadow-[0_15px_40px_rgba(249,115,22,0.3)] scale-[1.05];
}
.menu-card.active .icon-box-premium {
    @apply bg-orange-500 text-white shadow-[0_0_30px_rgba(249,115,22,0.6)] border-orange-400;
}
.icon-box-premium {
    @apply w-24 h-24 rounded-[24px] bg-black/40 border border-white/10 flex items-center justify-center text-white shadow-lg transition-all duration-300;
}


.no-scrollbar::-webkit-scrollbar { display: none; }
.no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }


.fade-bg-enter-active, .fade-bg-leave-active { transition: opacity 2000ms ease; }
.fade-bg-enter-from, .fade-bg-leave-to { opacity: 0; }

.fade-photo-enter-active, .fade-photo-leave-active { transition: opacity 800ms ease; }
.fade-photo-enter-from, .fade-photo-leave-to { opacity: 0; transform: scale(0.98); }

.fade-ui-enter-active, .fade-ui-leave-active { transition: opacity 500ms ease; }
.fade-ui-enter-from, .fade-ui-leave-to { opacity: 0; }


.slide-panel-enter-active, .slide-panel-leave-active { transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1); }
.slide-panel-enter-from, .slide-panel-leave-to { opacity: 0; transform: translateX(20px); }


.fade-modal-enter-active, .fade-modal-leave-active { 
    transition: opacity 0.4s ease;
}
.fade-modal-enter-from, .fade-modal-leave-to { 
    opacity: 0;
}

.modal-backdrop {
    backdrop-filter: blur(0px);
    transition: backdrop-filter 0.4s ease;
}

.fade-modal-enter-active .modal-backdrop,
.fade-modal-leave-active .modal-backdrop {
    backdrop-filter: blur(24px);
}

@keyframes scaleUp {
    from { opacity: 0; transform: scale(0.95); }
    to { opacity: 1; transform: scale(1); }
}
.animate-scale-up {
    animation: scaleUp 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes fadeInUp {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-up {
    animation: fadeInUp 0.8s ease-out forwards;
}

.shadow-3xl {
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}


.slider::-webkit-slider-thumb {
  appearance: none;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(249, 115, 22, 0.4);
  transition: all 0.2s;
  margin-top: -16px; 
}

.slider::-webkit-slider-thumb:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.6);
}

.slider::-moz-range-thumb {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  cursor: pointer;
  border: none;
  box-shadow: 0 4px 12px rgba(249, 115, 22, 0.4);
  transition: all 0.2s;
}

.slider::-moz-range-thumb:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.6);
}

.slider::-webkit-slider-runnable-track {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 9999px;
  height: 16px;
}

.slider::-moz-range-track {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 9999px;
  height: 16px;
}
</style>
