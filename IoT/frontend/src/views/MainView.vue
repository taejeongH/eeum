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
          <div v-if="currentPhoto" class="absolute top-0 bottom-0 left-0 w-[520px] flex flex-col justify-center items-center pointer-events-auto pl-10">
              
              <!-- Modern Sticky Notes Board -->
              <div 
                  class="w-full py-12 px-6 rounded-r-3xl shadow-2xl relative flex flex-col gap-8 items-center transform transition-transform duration-500 hover:scale-[1.02] origin-left border-y border-r border-white/10"
                  style="background: linear-gradient(135deg, #f5e6d3 0%, #e8d5c4 100%);"
                  @click.stop
              >
                  <!-- Texture overlay -->
                  <div class="absolute inset-0 opacity-10 pointer-events-none rounded-r-3xl" style="background-image: url('data:image/svg+xml,%3Csvg width=\'100\' height=\'100\' xmlns=\'http://www.w3.org/2000/svg\'%3E%3Cfilter id=\'noise\'%3E%3CfeTurbulence baseFrequency=\'0.9\' numOctaves=\'4\'/%3E%3C/filter%3E%3Crect width=\'100\' height=\'100\' filter=\'url(%23noise)\' opacity=\'0.4\'/%3E%3C/svg%3E');"></div>

                  <!-- Message Note -->
                  <div v-if="currentPhoto" class="relative group w-[440px] transform rotate-1 hover:-rotate-1 transition-transform duration-300">
                      <!-- Pin -->
                      <div class="absolute -top-4 left-1/2 -translate-x-1/2 z-20 w-7 h-7 rounded-full bg-gradient-to-br from-red-500 to-red-600 shadow-lg border-2 border-white/40"></div>
                      
                      <!-- Note Content -->
                      <div class="bg-gradient-to-br from-yellow-100 to-amber-50 rounded-2xl p-8 shadow-xl border border-yellow-200/50 relative">
                          <!-- Subtle paper texture -->
                          <div class="absolute inset-0 opacity-5 rounded-2xl" style="background-image: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.03) 2px, rgba(0,0,0,0.03) 4px);"></div>
                          
                          <div class="relative z-10">
                              <div class="flex items-start gap-3 mb-3">
                                  <div class="flex-shrink-0 w-10 h-10 rounded-lg bg-gradient-to-br from-orange-400 to-amber-500 flex items-center justify-center shadow-md">
                                      <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                                      </svg>
                                  </div>
                                  <div class="flex-1">
                                      <h3 class="text-xs font-black text-orange-600 uppercase tracking-wider mb-2">Message</h3>
                                      <p class="text-3xl font-black text-gray-800 leading-snug break-keep font-serif">
                                          {{ currentPhoto.message || '오늘도 건강하고 행복한 하루 보내세요. 항상 사랑합니다!' }}
                                      </p>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>



                  <!-- Info Note -->
                  <div v-if="currentPhoto" class="relative group w-[380px] transform -rotate-2 hover:rotate-2 transition-transform duration-300">
                      <div class="bg-gradient-to-br from-blue-100 to-indigo-50 rounded-2xl p-6 shadow-lg border border-blue-200/50 relative">
                          <!-- Subtle paper texture -->
                          <div class="absolute inset-0 opacity-5 rounded-2xl" style="background-image: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.03) 2px, rgba(0,0,0,0.03) 4px);"></div>
                          
                          <div class="relative z-10 flex items-center gap-4">
                              <!-- Avatar -->
                              <div class="flex-shrink-0 w-16 h-16 rounded-xl bg-gradient-to-br from-blue-500 via-indigo-500 to-purple-600 flex items-center justify-center text-white text-2xl font-black shadow-lg ring-4 ring-blue-100 overflow-hidden">
                                  <img 
                                      v-if="currentPhoto.sender?.profile_image_url" 
                                      :src="currentPhoto.sender.profile_image_url" 
                                      class="w-full h-full object-cover"
                                  >
                                  <span v-else>{{ (currentPhoto.sender?.name || currentPhoto.uploader || '가')[0] }}</span>
                              </div>
                              
                              <!-- Info (Left-aligned) -->
                              <div class="text-left">
                                  <div class="text-xs font-black text-blue-600 uppercase tracking-wider mb-1">From</div>
                                  <div class="text-2xl font-black text-gray-800">
                                      {{ currentPhoto.sender?.name || currentPhoto.uploader || '가족' }}
                                  </div>
                                  <div class="flex items-center gap-1.5 text-gray-600 mt-2">
                                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                      </svg>
                                      <span class="text-sm font-mono font-bold">{{ formatDate(currentPhoto.takenAt) }}</span>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>

              </div>
          </div>

          <!-- Central Playback Controls -->
          <div class="absolute bottom-16 left-1/2 -translate-x-1/2 flex items-center gap-8 pointer-events-auto z-30">
              <button 
                  @click.stop="slideshowStore.controlPrev()"
                  class="p-5 bg-stone-900/60 backdrop-blur-3xl rounded-full border border-white/10 hover:bg-stone-800/80 transition-all text-white/50 hover:text-white hover:scale-110 shadow-2xl"
              >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M15 19l-7-7 7-7" />
                  </svg>
              </button>
              
              <button 
                  @click.stop="toggleSlideshow"
                  class="w-28 h-28 bg-gradient-to-br from-orange-400 to-orange-600 rounded-full flex items-center justify-center shadow-[0_0_50px_rgba(249,115,22,0.4)] transition-all hover:scale-110 active:scale-95 text-white relative overflow-hidden group"
              >
                <!-- Sparkle effect -->
                <div class="absolute inset-0 bg-gradient-to-tr from-white/20 to-transparent rotate-45 translate-y-full group-hover:translate-y-0 transition-transform duration-500"></div>
                
                <svg v-if="slideshowStore.isPlaying" xmlns="http://www.w3.org/2000/svg" class="w-14 h-14 relative z-10" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-14 h-14 relative z-10 ml-2" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M8 5v14l11-7z" />
                </svg>
              </button>

              <button 
                  @click.stop="slideshowStore.controlNext()"
                  class="p-5 bg-stone-900/60 backdrop-blur-3xl rounded-full border border-white/10 hover:bg-stone-800/80 transition-all text-white/50 hover:text-white hover:scale-110 shadow-2xl"
              >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M9 5l7 7-7 7" />
                  </svg>
              </button>
          </div>



          <!-- 4. Right Panel: Control (Menu) -->
          <!-- Floats on the Right "Wing" -->
          <div class="absolute top-0 bottom-0 right-0 w-[180px] flex flex-col pointer-events-auto py-8 pr-8">
              
              <!-- Control Bar Container -->
              <!-- Glassmorphism pill -->
              <div class="flex-1 bg-stone-900/80 backdrop-blur-xl rounded-full border border-white/10 shadow-2xl flex flex-col items-center py-10 gap-8 text-white overflow-hidden relative" @click.stop>
                  
                  <!-- 1. Clock (Top) -->
                   <div class="flex flex-col items-center mb-4">
                        <div class="text-5xl font-black tracking-tighter font-mono text-white/90">
                            {{ currentTimeFormatted }}
                        </div>
                        <div class="text-sm text-white/50 font-bold mt-2 uppercase tracking-widest">
                            {{ currentDayStr }}
                        </div>
                   </div>

                  <!-- Divider -->
                  <div class="w-16 h-[1px] bg-white/10"></div>

                  <!-- 2. Wifi -->
                   <div class="flex flex-col items-center gap-2">
                      <div class="p-5 bg-black/40 rounded-full border border-white/10">
                        <svg v-if="slideshowStore.wifiStatus" xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                        </svg>
                         <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3l18 18M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                        </svg>
                      </div>
                      <span class="text-xs text-white/40 font-bold uppercase tracking-tight">WiFi</span>
                   </div>

                  <!-- Divider -->
                  <div class="w-16 h-[1px] bg-white/10"></div>

                  <!-- 3. Menu Items List -->
                   <nav class="flex-1 flex flex-col items-center gap-6 w-full overflow-y-auto no-scrollbar pt-2">
                        
                        <!-- Alert -->
                        <button class="nav-btn group" :class="{ active: activeTab === 'alert' }" @click="toggleTab('alert')">
                            <div class="icon-box">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                                 </svg>
                            </div>
                            <span class="label">알림</span>
                        </button>

                        <!-- Message -->
                        <button class="nav-btn group" :class="{ active: activeTab === 'chat' }" @click="toggleTab('chat')">
                            <div class="icon-box">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                                 </svg>
                            </div>
                            <span class="label">메시지</span>
                        </button>

                        <!-- Schedule -->
                        <button class="nav-btn group" :class="{ active: activeTab === 'schedule' }" @click="toggleTab('schedule')">
                            <div class="icon-box">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                 </svg>
                            </div>
                            <span class="label">일정</span>
                        </button>

                         <!-- Settings -->
                        <button class="nav-btn group mt-auto mb-4" :class="{ active: activeTab === 'settings' }" @click="toggleTab('settings')">
                            <div class="icon-box">
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                 </svg>
                            </div>
                            <span class="label">설정</span>
                        </button>

                   </nav>

              </div>
          </div>

          <!-- 5. Sidebar Detailed Panel -->
          <Transition name="slide-panel">
            <div v-if="activeTab && activeTab !== 'none'" class="absolute inset-y-0 right-[180px] w-[600px] z-10 pointer-events-auto p-8 flex flex-col" @click.stop>
                <div class="flex-1 bg-stone-900/95 backdrop-blur-3xl rounded-[40px] border border-white/10 shadow-3xl text-white flex flex-col overflow-hidden">
                    <!-- Header -->
                    <div class="p-8 flex items-center justify-between border-b border-white/10">
                        <h3 class="text-3xl font-black tracking-tight">{{ tabTitle }}</h3>
                        <div class="flex items-center gap-4">
                            <button 
                                v-if="activeTab === 'alert' && displayData.length > 0"
                                @click="confirmClearAlerts"
                                class="px-5 py-2 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-full text-sm font-bold transition-colors"
                            >
                                전체 삭제
                            </button>
                            <button @click="activeTab = null" class="p-3 hover:bg-white/10 rounded-full transition-colors">
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
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
                                </svg>
                                <span class="text-xl font-medium">데이터가 없습니다</span>
                            </div>

                            <!-- List -->
                            <!-- Chat Message Style (for chat tab) -->
                            <template v-if="activeTab === 'chat'">
                                <div 
                                    v-for="(item, idx) in displayData" 
                                    :key="item.id || idx" 
                                    class="group relative flex items-start gap-4 mb-4"
                                >
                                    <!-- Avatar with dynamic color -->
                                    <div 
                                        class="flex-shrink-0 w-16 h-16 rounded-full flex items-center justify-center text-white text-2xl font-black shadow-lg ring-2 ring-white/10"
                                        :class="getSenderColor(item.sender || '가족')"
                                    >
                                        {{ (item.sender || '가족')[0] }}
                                    </div>

                                    <!-- Message Content -->
                                    <div class="flex-1 min-w-0">
                                        <!-- Sender Name & Time -->
                                        <div class="flex items-baseline gap-3 mb-2">
                                            <span class="text-2xl font-black text-white">{{ item.sender || '가족' }}</span>
                                            <span class="text-sm font-mono text-orange-400/60">{{ formatTime(item.timestamp) }}</span>
                                        </div>

                                        <!-- Message Bubble (unified color) -->
                                        <div class="relative group/bubble cursor-pointer" @click.stop="selectedItem = item">
                                            <div class="bg-gradient-to-br from-orange-500/20 to-amber-500/20 border border-orange-400/30 rounded-3xl p-6 pr-16 backdrop-blur-sm shadow-lg hover:shadow-xl transition-all hover:scale-[1.02]">
                                                <p class="text-2xl text-white leading-relaxed break-keep">
                                                    {{ item.content }}
                                                </p>
                                            </div>
                                            
                                            <!-- Delete Button (X icon only) -->
                                            <button 
                                                @click.stop="alertStore.removeChatHistory(item.id)"
                                                class="absolute top-2 right-2 w-8 h-8 flex items-center justify-center transition-all hover:scale-110"
                                                title="삭제"
                                            >
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 drop-shadow-lg" fill="none" viewBox="0 0 24 24" stroke="white">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12" />
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </template>

                            <!-- Alert/Schedule Style (for other tabs) -->
                            <template v-else-if="activeTab !== 'settings'">
                                <div 
                                    v-for="(item, idx) in displayData" 
                                    :key="item.id || idx" 
                                    class="group relative bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 hover:bg-white/10 transition-all duration-300 overflow-hidden"
                                >
                                    <!-- Left Color Bar -->
                                    <div class="absolute left-0 top-0 bottom-0 w-1" :class="getTypeColor(item.type || item.kind)"></div>

                                    <div class="flex items-stretch">
                                        <!-- Content Area -->
                                        <div class="flex-1 p-6 pr-4 flex flex-col gap-2 min-w-0 cursor-pointer" @click.stop="selectedItem = item">
                                            <div class="flex items-baseline gap-3 mb-3">
                                                <span class="text-3xl font-black text-white">
                                                    {{ item.title || item.sender || (activeTab === 'alert' ? '알림' : '') }}
                                                </span>
                                                <span class="text-base font-mono text-orange-400/60">
                                                    {{ formatTime(item.createdAt || item.startAt || item.timestamp) }}
                                                </span>
                                            </div>
                                            
                                            <p class="text-2xl text-white/90 leading-relaxed break-keep">
                                                {{ item.message || item.content || item.description || '' }}
                                            </p>

                                            <div v-if="item.type" class="text-xs font-black text-white/30 tracking-widest uppercase mt-3">
                                                {{ item.type }}
                                            </div>
                                        </div>

                                        <!-- Delete Button -->
                                        <button 
                                            v-if="activeTab === 'alert'"
                                            @click.stop="alertStore.removeHistory(item.id)"
                                            class="w-20 bg-red-500/5 hover:bg-red-500/20 text-red-500/50 hover:text-red-500 flex flex-col items-center justify-center gap-1 transition-all border-l border-white/5"
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                            </svg>
                                            <span class="text-xs font-black uppercase">삭제</span>
                                        </button>
                                    </div>
                                </div>
                            </template>

                            <!-- Settings UI -->
                            <template v-else-if="activeTab === 'settings'">
                                <div class="space-y-6">
                                    <!-- Current WiFi Status -->
                                    <div class="bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 p-8">
                                        <div class="flex items-center gap-4">
                                            <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0M1.394 9.393c5.857-5.857 15.355-5.857 21.213 0" />
                                                </svg>
                                            </div>
                                            <div class="flex-1">
                                                <h4 class="text-2xl font-black text-white">현재 연결</h4>
                                                <p class="text-lg text-white/60 mt-1">{{ activeSSID || '연결 안 됨' }}</p>
                                            </div>
                                            <button 
                                                @click="scanWiFi(true)"
                                                :disabled="wifiScanning || wifiConnecting"
                                                class="px-8 py-4 bg-blue-500/20 hover:bg-blue-500/30 text-blue-400 hover:text-blue-300 rounded-2xl text-xl font-black transition-all disabled:opacity-50"
                                            >
                                                {{ wifiScanning ? '스캔 중...' : '재스캔' }}
                                            </button>
                                        </div>
                                    </div>

                                    <!-- Available Networks -->
                                    <div class="bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 p-8">
                                        <h4 class="text-2xl font-black text-white mb-6">사용 가능한 네트워크</h4>
                                        <div class="space-y-3 max-h-96 overflow-y-auto">
                                            <div 
                                                v-for="ap in wifiAPs" 
                                                :key="ap.ssid"
                                                @click="selectAP(ap)"
                                                class="flex items-center justify-between p-5 bg-white/5 hover:bg-white/10 rounded-2xl cursor-pointer transition-all border border-white/5"
                                                :class="{ 'bg-blue-500/20 border-blue-500/30': ap.in_use }"
                                            >
                                                <div class="flex items-center gap-4 flex-1">
                                                    <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-400 to-cyan-400 flex items-center justify-center">
                                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.111 16.404a5.5 5.5 0 017.778 0M12 20h.01m-7.08-7.071c3.904-3.905 10.236-3.905 14.141 0" />
                                                        </svg>
                                                    </div>
                                                    <div class="flex-1">
                                                        <div class="text-xl font-black text-white">{{ ap.ssid }}</div>
                                                        <div class="text-sm text-white/60 mt-1">{{ ap.security }}</div>
                                                    </div>
                                                </div>
                                                <div class="flex items-center gap-4">
                                                    <div class="text-white/60 text-lg font-bold">{{ ap.signal }}%</div>
                                                    <div v-if="ap.in_use" class="px-4 py-2 bg-blue-500/30 text-blue-300 rounded-xl text-sm font-black">
                                                        연결됨
                                                    </div>
                                                </div>
                                            </div>
                                            <div v-if="wifiAPs.length === 0" class="text-center py-12 text-white/40">
                                                사용 가능한 네트워크가 없습니다
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Saved Profiles -->
                                    <div class="bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 p-8">
                                        <div class="flex items-center justify-between mb-6">
                                            <h4 class="text-2xl font-black text-white">저장된 네트워크</h4>
                                            <button 
                                                @click="getWiFiProfiles(true)"
                                                class="px-6 py-3 bg-white/10 hover:bg-white/20 text-white rounded-xl text-base font-black transition-all"
                                            >
                                                새로고침
                                            </button>
                                        </div>
                                        <div class="space-y-3">
                                            <div 
                                                v-for="profile in wifiProfiles" 
                                                :key="profile.name"
                                                class="flex items-center justify-between p-5 bg-white/5 rounded-2xl border border-white/5"
                                            >
                                                <div class="flex items-center gap-4 flex-1">
                                                    <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-purple-400 to-pink-400 flex items-center justify-center">
                                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                                                        </svg>
                                                    </div>
                                                    <div>
                                                        <div class="text-lg font-black text-white">{{ profile.ssid }}</div>
                                                        <div class="text-sm text-white/60 mt-1">
                                                            {{ profile.autoconnect ? '자동 연결' : '수동 연결' }}
                                                        </div>
                                                    </div>
                                                </div>
                                                <div class="flex items-center gap-3">
                                                    <button 
                                                        @click="connectProfile(profile.name)"
                                                        :disabled="wifiConnecting"
                                                        class="px-6 py-3 bg-blue-500/20 hover:bg-blue-500/30 text-blue-400 rounded-xl text-base font-black transition-all disabled:opacity-50"
                                                    >
                                                        연결
                                                    </button>
                                                    <button 
                                                        @click="deleteProfile(profile.name)"
                                                        class="px-6 py-3 bg-red-500/20 hover:bg-red-500/30 text-red-400 rounded-xl text-base font-black transition-all"
                                                    >
                                                        삭제
                                                    </button>
                                                </div>
                                            </div>
                                            <div v-if="wifiProfiles.length === 0" class="text-center py-12 text-white/40">
                                                저장된 네트워크가 없습니다
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Brightness Control -->
                                    <div class="bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 p-8">
                                        <div class="flex items-center gap-4 mb-6">
                                            <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-amber-500 to-yellow-500 flex items-center justify-center">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                                                </svg>
                                            </div>
                                            <div class="flex-1">
                                                <h4 class="text-2xl font-black text-white">밝기</h4>
                                                <p class="text-lg text-white/60 mt-1">{{ brightness }}%</p>
                                            </div>
                                        </div>
                                        <input 
                                            type="range" 
                                            v-model="brightness" 
                                            @input="adjustBrightness(brightness)"
                                            min="0" 
                                            max="100" 
                                            class="w-full h-3 bg-white/10 rounded-full appearance-none cursor-pointer slider"
                                        />
                                    </div>

                                    <!-- Volume Control -->
                                    <div class="bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 p-8">
                                        <div class="flex items-center gap-4 mb-6">
                                            <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                                                </svg>
                                            </div>
                                            <div class="flex-1">
                                                <h4 class="text-2xl font-black text-white">볼륨</h4>
                                                <p class="text-lg text-white/60 mt-1">{{ volume }}%</p>
                                            </div>
                                        </div>
                                        <input 
                                            type="range" 
                                            v-model="volume" 
                                            @input="adjustVolume(volume)"
                                            min="0" 
                                            max="100" 
                                            class="w-full h-3 bg-white/10 rounded-full appearance-none cursor-pointer slider"
                                        />
                                    </div>

                                    <!-- Restart Button -->
                                    <div class="bg-white/5 backdrop-blur-xl rounded-3xl border border-white/10 p-8">
                                        <div class="flex items-center justify-between">
                                            <div class="flex items-center gap-4">
                                                <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-orange-500 flex items-center justify-center">
                                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                                    </svg>
                                                </div>
                                                <div>
                                                    <h4 class="text-2xl font-black text-white">기기 재시작</h4>
                                                    <p class="text-lg text-white/60 mt-1">시스템을 재시작합니다</p>
                                                </div>
                                            </div>
                                            <button 
                                                @click="restartDevice"
                                                class="px-8 py-4 bg-red-500/20 hover:bg-red-500/30 text-red-400 hover:text-red-300 rounded-2xl text-xl font-black transition-all"
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
                  <div class="relative w-[800px] bg-stone-900/90 border border-white/10 rounded-[4rem] shadow-3xl overflow-hidden flex flex-col transform transition-all animate-scale-up backdrop-blur-md" @click.stop>
                      <!-- Top Color Header -->
                      <div class="h-4 w-full" :class="selectedItem.type === 'EMERGENCY' ? 'bg-red-500' : 'bg-orange-500'"></div>
                      
                      <div class="p-12 flex flex-col gap-10">
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
                          <p class="text-4xl text-white/90 leading-tight font-medium break-keep">
                              {{ selectedItem.message || selectedItem.content || selectedItem.description }}
                          </p>

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
                  <div class="relative w-[700px] bg-stone-900/90 border border-white/10 rounded-[3rem] shadow-3xl overflow-hidden flex flex-col transform transition-all animate-scale-up backdrop-blur-md" @click.stop>
                      <!-- Top Color Header -->
                      <div class="h-4 w-full bg-blue-500"></div>
                      
                      <div class="p-12 flex flex-col gap-8">
                          <!-- Header -->
                          <div class="flex flex-col gap-2">
                              <div class="text-blue-400 font-bold text-xl uppercase tracking-widest">
                                  Wi-Fi 연결
                              </div>
                              <h2 class="text-5xl font-black text-white">
                                  {{ selectedAP?.ssid }}
                              </h2>
                          </div>

                          <!-- Password Input -->
                          <div class="flex flex-col gap-4">
                              <label class="text-2xl font-bold text-white/80">비밀번호</label>
                              <input 
                                  type="password"
                                  v-model="wifiPassword"
                                  @keyup.enter="submitPassword"
                                  placeholder="Wi-Fi 비밀번호를 입력하세요"
                                  class="w-full px-8 py-6 bg-white/10 border border-white/20 rounded-2xl text-3xl text-white placeholder-white/40 focus:outline-none focus:border-blue-500/50 focus:bg-white/15 transition-all"
                                  autofocus
                              />
                          </div>

                          <!-- Footer Buttons -->
                          <div class="flex justify-end gap-4 mt-4">
                              <button 
                                  @click="showPasswordModal = false"
                                  class="px-10 py-5 bg-white/10 hover:bg-white/20 text-white rounded-3xl text-2xl font-black transition-all"
                              >
                                  취소
                              </button>
                              <button 
                                  @click="submitPassword"
                                  :disabled="!wifiPassword"
                                  class="px-10 py-5 bg-blue-500 hover:bg-blue-600 text-white rounded-3xl text-2xl font-black transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                              >
                                  연결
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
                 
                 <div class="relative w-[600px] bg-stone-900/95 border border-white/10 rounded-[3rem] shadow-3xl overflow-hidden flex flex-col transform transition-all animate-scale-up backdrop-blur-md" @click.stop>
                     <div class="h-4 w-full" :class="generalModal.type === 'confirm' ? 'bg-orange-500' : 'bg-blue-500'"></div>
                     
                     <div class="p-10 flex flex-col gap-8 text-center items-center">
                         <div class="flex flex-col gap-3">
                             <div class="text-lg font-bold uppercase tracking-widest" :class="generalModal.type === 'confirm' ? 'text-orange-500' : 'text-blue-500'">
                                 {{ generalModal.title }}
                             </div>
                             <p class="text-2xl text-white font-bold leading-relaxed break-keep whitespace-pre-wrap">{{ generalModal.message }}</p>
                         </div>
                         
                         <div class="flex justify-center gap-4 w-full">
                             <button 
                                 v-if="generalModal.type === 'confirm'"
                                 @click="handleModalCancel"
                                 class="flex-1 py-5 bg-white/10 hover:bg-white/20 text-white rounded-3xl text-xl font-black transition-all"
                             >
                                 취소
                             </button>
                             <button 
                                 @click="handleModalConfirm"
                                 class="flex-1 py-5 text-white rounded-3xl text-xl font-black transition-all shadow-lg hover:scale-[1.02] active:scale-95"
                                 :class="generalModal.type === 'confirm' ? 'bg-orange-500 hover:bg-orange-600' : 'bg-blue-500 hover:bg-blue-600'"
                             >
                                 확인
                             </button>
                         </div>
                     </div>
                 </div>
             </div>
          </Transition>

      </div>
    </Transition>

  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useSlideshowStore } from '@/stores/slideshow'
import { useAlertStore } from '@/stores/alert'

const slideshowStore = useSlideshowStore()
const alertStore = useAlertStore()
const activeTab = ref(null) // null for closed

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
    if (activeTab.value === 'chat') return alertStore.chatHistory
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
                alertStore.chatHistory = result.data.added || []
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
    const days = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일']
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
    // Better hash function for more unique distribution
    let hash = 0
    const str = sender.trim()
    
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i)
        hash = ((hash << 5) - hash) + char
        hash = hash & hash // Convert to 32bit integer
    }
    
    // Add position-based variation for better distribution
    hash = Math.abs(hash + str.length * 7919)
    
    // Expanded color palette with 12 distinct colors
    const colorPalettes = [
        'bg-gradient-to-br from-blue-500/30 to-cyan-500/30 border-blue-400/40',        // 파랑
        'bg-gradient-to-br from-purple-500/30 to-pink-500/30 border-purple-400/40',    // 보라
        'bg-gradient-to-br from-green-500/30 to-emerald-500/30 border-green-400/40',   // 초록
        'bg-gradient-to-br from-orange-500/30 to-amber-500/30 border-orange-400/40',   // 오렌지
        'bg-gradient-to-br from-rose-500/30 to-red-500/30 border-rose-400/40',         // 로즈
        'bg-gradient-to-br from-indigo-500/30 to-violet-500/30 border-indigo-400/40',  // 인디고
        'bg-gradient-to-br from-teal-500/30 to-cyan-500/30 border-teal-400/40',        // 틸
        'bg-gradient-to-br from-fuchsia-500/30 to-purple-500/30 border-fuchsia-400/40',// 푸시아
        'bg-gradient-to-br from-lime-500/30 to-green-500/30 border-lime-400/40',       // 라임
        'bg-gradient-to-br from-amber-500/30 to-yellow-500/30 border-amber-400/40',    // 앰버
        'bg-gradient-to-br from-sky-500/30 to-blue-500/30 border-sky-400/40',          // 스카이
        'bg-gradient-to-br from-pink-500/30 to-rose-500/30 border-pink-400/40'         // 핑크
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

// Settings functionality
const brightness = ref(80)
const volume = ref(50)

// WiFi Management
const wifiApiBase = 'http://localhost:8080'
const wifiScanning = ref(false)
const wifiConnecting = ref(false)
const wifiAPs = ref([])
const wifiProfiles = ref([])
const activeSSID = ref(null)
const selectedAP = ref(null)
const wifiPassword = ref('')
const showPasswordModal = ref(false)
const wifiPingInterval = ref(null)
const wifiActiveInterval = ref(null)

// WiFi UI Ping - heartbeat to keep scan active
const pingWifiUI = async () => {
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/ui/ping`, { method: 'POST' })
        if (!response.ok && response.status !== 401) {
            console.warn('WiFi UI ping failed:', response.status)
        }
    } catch (e) {
        // Silently fail - this is just a heartbeat
    }
}

// Global Modal State
const generalModal = ref({
    show: false,
    type: 'alert', // 'alert' | 'confirm'
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

// Scan WiFi APs
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

// Get active WiFi status
const getActiveWiFi = async () => {
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/active`)
        
        if (!response.ok) {
            if (response.status === 401) {
                // Silently fail for 401
                return
            }
            slideshowStore.wifiStatus = false
            return
        }
        
        const data = await response.json()
        activeSSID.value = data.ssid
        
        // Sync global store status for Top Bar Icon
        if (data.ssid) {
            slideshowStore.wifiStatus = true
        } else {
            slideshowStore.wifiStatus = false
        }
    } catch (e) {
        // Silently fail
        slideshowStore.wifiStatus = false
    }
}

// Get saved WiFi profiles
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

// Connect to WiFi with password
const connectWiFi = async (ssid, password) => {
    if (wifiConnecting.value) return
    wifiConnecting.value = true
    
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/connect`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ssid, password })
        })
        const data = await response.json()
        
        if (data.ok) {
            if (data.skipped) {
                await showAlert('이미 연결되어 있습니다.')
                wifiConnecting.value = false
                await slideshowStore.updateWifiStatus()
            } else {
                await showAlert('연결 요청을 보냈습니다.\n잠시 후 연결 상태를 확인합니다.')
                // Poll active status for ~15 seconds
                let attempts = 15
                const checkInterval = setInterval(async () => {
                    await getActiveWiFi()
                    attempts--
                    if (activeSSID.value === ssid || attempts <= 0) {
                        clearInterval(checkInterval)
                        if (activeSSID.value === ssid) {
                            await showAlert(`${ssid}에 연결되었습니다.`, '연결 성공')
                            await scanWiFi()
                            await getWiFiProfiles()
                            await slideshowStore.updateWifiStatus()
                        } else {
                            await showAlert('연결에 실패했습니다.\n비밀번호를 확인해주세요.', '연결 실패')
                        }
                        wifiConnecting.value = false
                    }
                }, 1000)
            }
        } else {
            await showAlert(`연결 실패: ${data.message || '알 수 없는 오류'}`, '오류')
            wifiConnecting.value = false
        }
    } catch (e) {
        console.error('WiFi connect failed:', e)
        await showAlert('연결 요청 중 오류가 발생했습니다.', '오류')
        wifiConnecting.value = false
    }
}

// Connect to saved profile
const connectProfile = async (profileName) => {
    if (wifiConnecting.value) return
    wifiConnecting.value = true
    
    try {
        const response = await fetch(`${wifiApiBase}/api/wifi/profile/connect`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: profileName })
        })
        const data = await response.json()
        
        if (data.ok) {
            if (data.skipped) {
                await showAlert('이미 연결되어 있습니다.')
                wifiConnecting.value = false
                await slideshowStore.updateWifiStatus()
            } else {
                await showAlert('연결 요청을 보냈습니다.\n잠시 후 연결 상태를 확인합니다.')
                // Robust Polling like connectWiFi
                let attempts = 10
                const checkInterval = setInterval(async () => {
                    await getActiveWiFi()
                    // We need to check if the activeSSID matches the profile's SSID.
                    // But we only have profileName here. 
                    // Let's assume the profile list is up to date or find it locally.
                    const profile = wifiProfiles.value.find(p => p.name === profileName)
                    const targetSSID = profile ? profile.ssid : null
                    
                    attempts--
                    
                    if ((targetSSID && activeSSID.value === targetSSID) || attempts <= 0) {
                         clearInterval(checkInterval)
                         if (targetSSID && activeSSID.value === targetSSID) {
                             await showAlert(`${targetSSID}에 연결되었습니다.`, '연결 성공')
                             await scanWiFi()
                             await slideshowStore.updateWifiStatus()
                         } else {
                             await showAlert('연결에 실패했습니다.\n다시 시도해주세요.', '연결 실패')
                         }
                         wifiConnecting.value = false
                    }
                }, 1000)
            }
        }
    } catch (e) {
        console.error('Profile connect failed:', e)
        wifiConnecting.value = false
    }
}

// Delete WiFi profile
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

// Handle AP selection
const selectAP = (ap) => {
    if (ap.in_use) {
        showAlert('이미 연결된 네트워크입니다.')
        return
    }
    selectedAP.value = ap
    wifiPassword.value = ''
    showPasswordModal.value = true
}

// Submit password and connect
const submitPassword = async () => {
    if (!selectedAP.value || !wifiPassword.value) return
    
    showPasswordModal.value = false
    await connectWiFi(selectedAP.value.ssid, wifiPassword.value)
    selectedAP.value = null
    wifiPassword.value = ''
}

// Start WiFi management when settings tab opens
watch(activeTab, async (newTab, oldTab) => {
    if (newTab === 'settings' && oldTab !== 'settings') {
        // Start WiFi management
        await pingWifiUI()
        await scanWiFi()
        await getWiFiProfiles()
        await getActiveWiFi()
        
        // Start ping interval (3 seconds)
        wifiPingInterval.value = setInterval(pingWifiUI, 3000)
        
        // Start active status polling (1 second)
        wifiActiveInterval.value = setInterval(getActiveWiFi, 1000)
    } else if (oldTab === 'settings' && newTab !== 'settings') {
        // Stop WiFi management
        if (wifiPingInterval.value) {
            clearInterval(wifiPingInterval.value)
            wifiPingInterval.value = null
        }
        if (wifiActiveInterval.value) {
            clearInterval(wifiActiveInterval.value)
            wifiActiveInterval.value = null
        }
    }
    
    // Existing tab data fetch
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

// Slideshow Control
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
             throw new Error('Response not ok') // Trigger catch
        }
    } catch (e) {
        console.error('Device restart failed:', e)
        await showAlert('기기 재시작에 실패했습니다.', '오류')
    }
}

// Touch Sweep Logic (API Control)
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
    const threshold = 50 // Minimum distance for swipe
    const distance = touchStartX.value - touchEndX.value
    
    // If detail view or any modal is open, do not trigger slide change
    if (isDetailView.value || activeTab.value !== null || generalModal.value.show || showPasswordModal.value || selectedItem.value) return

    if (Math.abs(distance) > threshold) {
        if (distance > 0) {
             // Swipe Left (Gesture) -> Go Next
             console.log('Swipe Left -> Next')
             slideshowStore.controlNext()
        } else {
             // Swipe Right (Gesture) -> Go Prev
             console.log('Swipe Right -> Prev')
             slideshowStore.controlPrev()
        }
    }
}
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
    @apply bg-orange-500 text-white shadow-xl border-orange-400 scale-110;
}
.nav-btn .icon-box {
    @apply w-20 h-20 rounded-[28px] bg-black/40 border border-white/10 flex items-center justify-center text-white/90 shadow-lg transition-all duration-300;
}
.nav-btn .label {
    @apply text-base font-black text-white/90 mt-1 uppercase tracking-tight;
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

/* Slide Panel Transition */
.slide-panel-enter-active, .slide-panel-leave-active { transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1); }
.slide-panel-enter-from, .slide-panel-leave-to { opacity: 0; transform: translateX(20px); }

/* Modal Transition with Smooth Backdrop Blur */
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

/* Slider Styles */
.slider::-webkit-slider-thumb {
  appearance: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f97316 0%, #fb923c 100%);
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(249, 115, 22, 0.4);
  transition: all 0.2s;
  margin-top: -6px; /* (12px track / 2) - (24px thumb / 2) */
}

.slider::-webkit-slider-thumb:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.6);
}

.slider::-moz-range-thumb {
  width: 24px;
  height: 24px;
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
  height: 12px;
}

.slider::-moz-range-track {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 9999px;
  height: 12px;
}
</style>
