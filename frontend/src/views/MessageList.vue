<template>
  <div 
    class="min-h-screen relative overflow-x-hidden" 
    style="background-color: var(--bg-page);"
    @touchstart="handleRefreshTouchStart"
    @touchmove="handleRefreshTouchMove"
    @touchend="handleRefreshTouchEnd"
  >
    <!-- Modern Pull-to-Refresh Indicator -->
    <div 
      class="fixed top-0 left-0 right-0 flex justify-center items-center z-[110] pointer-events-none transition-all duration-300 ease-out"
      :style="{ 
        transform: `translateY(${refreshPullDistance > 0 ? Math.min(refreshPullDistance - 40, 80) : -60}px)`,
        opacity: Math.min(refreshPullDistance / 60, 1)
      }"
    >
      <div class="bg-white rounded-full shadow-2xl border border-gray-100 flex items-center justify-center w-12 h-12 mt-4 relative overflow-hidden">
        <!-- Modern SVG Spinner -->
        <svg v-if="isRefreshing" class="animate-spin h-7 w-7 text-[var(--color-primary)]" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <span 
          v-else 
          class="material-symbols-outlined text-[var(--color-primary)] text-2xl transition-transform duration-200"
          :style="{ transform: `rotate(${Math.min(refreshPullDistance * 3, 360)}deg)` }"
        >
          refresh
        </span>
      </div>
    </div>
    <MainHeader @modal-state-change="handleModalStateChange" :show-profiles="false">
      <template #actions>
         <button 
           @click="toggleSearch"
           class="p-2 rounded-full hover:bg-gray-100 transition-colors text-[#1c140d] -mr-2"
         >
           <IconClose v-if="isSearchOpen" />
           <IconSearch v-else />
         </button>
      </template>
    </MainHeader>
    
    <div class="bg-white border-b border-gray-200">
      <div class="max-w-2xl mx-auto px-4 py-3">
        <!-- Search Bar (Expanded) -->
        <div v-if="isSearchOpen" class="mb-4">
          <div class="relative">
            <input 
              v-model="searchQuery" 
              type="text" 
              placeholder="보낸 사람, 내용 검색"
              class="w-full pl-10 pr-10 py-2 bg-gray-100 rounded-full text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-[#e76f51]"
            />
            <div class="absolute left-3 top-2.5 text-gray-400">
               <IconSearch class="w-5 h-5" />
            </div>
            <!-- Close button for search input not strictly needed if we have toggle in header, but keeping clear button logic -->
            <button 
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="absolute right-3 top-2.5 text-gray-400 hover:text-gray-600"
            >
               <IconClose class="w-5 h-5" />
            </button>
          </div>
        </div>

        <!-- Patient Profile Section -->
        <div v-if="!familyLoading" class="flex flex-col items-center py-4">
          <div class="relative mb-3">
            <img 
              :src="patientImage || getFullImageUrl(null, 'Family')"
              :alt="patientName || 'Family'"
              class="w-20 h-20 rounded-full object-cover border-4 border-white shadow-md"
              style="background-color: #8b9a8f;"
            />
          </div>
          <h2 class="text-lg font-bold text-gray-800 mb-1">{{ patientName || '먼저 피부양자를 등록해주세요' }}</h2>
          <p class="text-sm text-gray-500">따뜻한 마음을 전해주세요!</p>
        </div>
      </div>
    </div>

    <main 
      class="max-w-2xl mx-auto px-4 py-6 pb-32 transition-transform duration-300 ease-out"
      :style="{ transform: `translateY(${isRefreshing ? 60 : refreshPullDistance * 0.4}px)` }"
    >
      <div v-if="loading" class="flex justify-center items-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2" style="border-color: var(--color-primary);"></div>
      </div>

      <div v-else-if="messages.length === 0" class="text-center py-20">
        <div class="mb-6">
          <svg class="w-24 h-24 mx-auto" style="color: var(--text-sub);" fill="currentColor" viewBox="0 0 24 24">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
          </svg>
        </div>
        <h3 class="eeum-title text-xl mb-2" style="color: var(--text-title);">아직 메세지가 없습니다</h3>
        <p class="eeum-sub mb-6">가족에게 따뜻한 메시지를 보내보세요!</p>

      </div>

      <div v-else class="space-y-3">
        <div
          v-for="message in filteredMessages"
          :key="message.id"
          @click="openMessageDetail(message)"
          class="bg-white rounded-2xl p-4 shadow-sm border border-gray-100 hover:shadow-md transition-shadow cursor-pointer"
        >
          <div class="flex items-start gap-3 mb-3">
            <img 
              :src="getFullImageUrl(message.senderProfileImage, message.senderName)"
              :alt="message.senderName || 'User'"
              class="w-10 h-10 rounded-full object-cover flex-shrink-0"
            />
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between mb-1">
                <span class="font-semibold text-gray-800 text-sm">{{ message.senderRelationship || message.senderName }}</span>
              </div>
              <p class="text-sm text-gray-600 line-clamp-2 leading-relaxed">{{ message.content }}</p>
            </div>
          </div>
          <div class="flex items-center justify-between text-xs text-gray-400">
            <span>{{ formatTime(message.createdAt) }}</span>
          </div>
        </div>
      </div>


      <!-- Message Detail Modal (Centered Popup without Dark Backdrop) -->
      <Teleport to="body">
        <div 
          v-if="selectedMessage"
          class="fixed inset-0 z-[140] flex items-center justify-center p-4"
          @click.self="closeMessageDetail"
        >
          <!-- Backdrop is transparent, but clickable to close -->
          <!-- We can add a slight bg-black/10 if needed to show it's modal, but user asked for no dark backdrop earlier -->
          <!-- Keep it transparent or extremely subtle per user request -->
          <div class="absolute inset-0 bg-black/10 backdrop-blur-[2px]" @click="closeMessageDetail"></div>
          
          <div class="bg-white rounded-3xl shadow-2xl w-[85%] max-w-md max-h-[60vh] flex flex-col min-h-[200px] border border-gray-100 relative z-10 animate-scale-in">
            <!-- Detail Header -->
            <div class="flex-none flex items-center justify-between p-5 border-b border-gray-100">
              <div class="flex items-center gap-3">
                <img 
                  :src="getFullImageUrl(selectedMessage.senderProfileImage, selectedMessage.senderName)"
                  :alt="selectedMessage.senderName || 'User'"
                  class="w-12 h-12 rounded-full object-cover border border-gray-100 shadow-sm"
                />
                <div>
                  <h3 class="font-bold text-gray-800 text-lg">{{ selectedMessage.senderRelationship || selectedMessage.senderName }}</h3>
                  <p class="text-xs text-gray-500 font-medium">{{ formatFullDate(selectedMessage.createdAt) }}</p>
                </div>
              </div>
              <button 
                @click="closeMessageDetail"
                class="p-2 hover:bg-gray-100 rounded-full transition-colors"
                aria-label="Close detail"
              >
                <svg class="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>

            <!-- Detail Content -->
            <div class="flex-1 overflow-y-auto p-6 custom-scrollbar">
              <p class="text-base text-gray-800 leading-relaxed whitespace-pre-wrap break-words font-medium">{{ selectedMessage.content }}</p>
            </div>
          </div>
        </div>
      </Teleport>


      <!-- Message Composer Modal: Teleported for Proper Z-Index -->
      <Teleport to="body">
        <transition name="fade">
          <div 
            v-if="showMessageModal"
            class="fixed inset-0 bg-black/60 z-[9998]"
            @click="closeMessageModal"
          ></div>
        </transition>

        <transition name="slide-up">
          <div 
            v-if="showMessageModal"
            ref="messageSheet"
            class="fixed inset-x-0 bottom-0 z-[9999] bg-white rounded-t-3xl shadow-2xl min-h-[0] h-auto max-h-[60vh] overflow-y-auto touch-pan-y pb-8 safe-area-bottom"
            @touchstart="onTouchStart"
            @touchmove="onTouchMove"
            @touchend="onTouchEnd"
          >
            <!-- Drag Handle -->
            <div class="sticky top-0 bg-white z-20 w-full flex justify-center pt-3 pb-2" @click="closeMessageModal">
               <div class="w-12 h-1.5 bg-gray-300 rounded-full"></div>
            </div>

            <!-- Modal Header -->
            <div class="sticky top-5 bg-white z-10 flex items-center px-5 pb-3 border-b border-gray-100 relative">
              <h2 class="text-lg font-bold text-gray-800 w-full text-center">메시지 작성</h2>
              <button 
                @click="closeMessageModal"
                class="absolute right-4 p-2 hover:bg-gray-100 rounded-full transition-colors"
              >
                <svg class="w-6 h-6 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>

            <!-- Modal Content -->
            <div class="p-5 space-y-4 pb-8">
              <!-- Recipient Info -->
              <div class="bg-orange-50 rounded-xl p-3 flex items-center gap-3">
                <img 
                  :src="patientImage || getFullImageUrl(null, 'Family')"
                  :alt="patientName || 'Family'"
                  class="w-10 h-10 rounded-full object-cover border-2 border-orange-200"
                />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-bold text-gray-800 truncate">To: {{ patientName || '우리 가족' }}</p>
                  <p class="text-xs text-gray-500 truncate">{{ deviceName || 'IoT 스피커' }}</p>
                </div>
              </div>

              <!-- Message Input -->
              <div class="relative">
                <textarea
                  :value="newMessage.content"
                  @input="handleInput"
                  placeholder="따뜻한 메시지를 적어보세요!"
                  class="w-full bg-gray-50 border border-gray-200 rounded-2xl p-4 pr-12 resize-none focus:outline-none focus:ring-2 focus:ring-[#e76f51] focus:border-transparent transition-all text-base leading-relaxed placeholder-gray-400"
                  rows="3"
                  maxlength="100"
                ></textarea>
                <div class="absolute bottom-3 right-4 text-xs font-medium text-gray-400">
                  {{ charCount }}/100
                </div>
              </div>

              <!-- TTS Guidance Message -->
              <div class="bg-blue-50/70 rounded-xl p-3 flex items-center gap-2">
                <span class="material-symbols-outlined text-blue-600 text-lg">volume_up</span>
                <span class="text-xs font-semibold text-blue-700">이 메세지는 TTS로 보내집니다</span>
              </div>

              <!-- Send Button -->
              <button
                @click="sendMessage"
                :disabled="!canSend || sending"
                class="w-full eeum-btn-primary hover:brightness-95 disabled:bg-gray-300 disabled:cursor-not-allowed text-white text-lg font-bold py-3.5 rounded-2xl shadow-sm active:scale-[0.98] transition-all flex items-center justify-center gap-2"
              >
                <span v-if="!sending" class="material-symbols-outlined text-xl">send</span>
                <svg v-else class="animate-spin h-5 w-5" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>{{ sending ? '전송 중...' : '메시지 보내기' }}</span>
              </button>
            </div>
          </div>
        </transition>
      </Teleport>

      <div v-if="totalPages > 1" class="mt-8 flex justify-center">
        <div class="flex gap-2">
          <button
            @click="prevPage"
            :disabled="currentPage === 0"
            class="px-4 py-2 eeum-input disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Previous
          </button>
          
          <span class="px-4 py-2 font-semibold" style="color: var(--color-primary); background-color: var(--color-primary-soft); border-radius: var(--radius-lg);">
            {{ currentPage + 1 }} / {{ totalPages }}
          </span>
          
          <button
            @click="nextPage"
            :disabled="currentPage >= totalPages - 1"
            class="px-4 py-2 eeum-input disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Next
          </button>
        </div>
      </div>
    </main>

    <!-- Floating Action Button (FAB) -->
    <button 
      @click="openMessageModal"
      class="fixed bottom-32 right-6 z-30 bg-primary text-white w-14 h-14 rounded-full flex items-center justify-center shadow-lg shadow-primary/30 active:scale-95 transition-transform"
    >
      <span class="material-symbols-outlined text-3xl" style="font-variation-settings: 'FILL' 0, 'wght' 600">add</span>
    </button>

    <!-- Bottom Navigation -->
    <BottomNav v-if="!isModalOpen" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { messageService } from '@/services/messageService'
import { familyService } from '@/services/familyService'
import { useFamilyStore } from '@/stores/family'
import BottomNav from '@/components/layout/BottomNav.vue'
import { useModalStore } from '@/stores/modal'
import MainHeader from '@/components/MainHeader.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconClose from '@/components/icons/IconClose.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const modalStore = useModalStore()
const familyStore = useFamilyStore()
const userStore = useUserStore()

const isModalOpen = ref(false)
const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen
}

const messages = ref([])
const loading = ref(false)
const familyLoading = ref(true)
const currentPage = ref(0)
const totalPages = ref(0)
const familyId = ref(null)

const patientName = ref('')
const patientImage = ref(null)
const deviceName = ref('')

const isSearchOpen = ref(false)
const searchQuery = ref('')

const showMessageModal = ref(false)
const newMessage = ref({
  content: '',
  enableTTS: true
})
const charCount = ref(0)
const sending = ref(false)
const selectedMessage = ref(null)
const groupName = ref('')

// Refresh State
const isRefreshing = ref(false)
const refreshStartY = ref(0)
const refreshPullDistance = ref(0)
const canRefresh = ref(true)

const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/'

// 이미지 URL 생성 함수 추가
// 이미지 URL 생성 함수 추가
const getFullImageUrl = (path, name) => {
  if (!path) {
    return `https://ui-avatars.com/api/?name=${name || 'User'}&background=FF9B6A&color=fff&size=48`
  }
  // http로 시작하면(절대경로) 그대로 쓰고, 아니면 S3 주소를 붙임
  return path.startsWith('http') ? path : `${S3_BASE_URL}${path}`
}

/* Swipe Logic */
const messageSheet = ref(null)
let startY = 0
let currentY = 0

const onTouchStart = (e) => {
  // Only allow swipe if scrollTop is 0 (at the top)
  if (messageSheet.value && messageSheet.value.scrollTop > 0) return
  startY = e.touches[0].clientY
}

const onTouchMove = (e) => {
  if (startY === 0) return // Started not at top
  currentY = e.touches[0].clientY
  const diff = currentY - startY
  if (diff > 0 && messageSheet.value) {
     // visual feedback
     messageSheet.value.style.transform = `translateY(${diff}px)`
  }
}

const onTouchEnd = () => {
  if (startY === 0) return
  const diff = currentY - startY
  if (diff > 100) {
    closeMessageModal()
  } else if (messageSheet.value) {
    messageSheet.value.style.transform = ''
  }
  startY = 0
  currentY = 0
}

/* Pull-to-Refresh Handlers */
const handleRefreshTouchStart = (e) => {
    // Only refresh if at the very top
    if (window.scrollY > 5 || isRefreshing.value || showMessageModal.value) {
        canRefresh.value = false;
        return;
    }
    canRefresh.value = true;
    refreshStartY.value = e.touches[0].clientY;
};

const handleRefreshTouchMove = (e) => {
    if (!canRefresh.value || isRefreshing.value) return;
    
    const currentY = e.touches[0].clientY;
    const distance = currentY - refreshStartY.value;
    
    if (distance > 0) {
        if (e.cancelable) e.preventDefault();
        refreshPullDistance.value = Math.min(distance * 0.6, 150);
    }
};

const handleRefreshTouchEnd = async () => {
    if (!canRefresh.value || isRefreshing.value) return;
    
    if (refreshPullDistance.value > 100) {
        await executeRefresh();
    } else {
        refreshPullDistance.value = 0;
    }
};

const executeRefresh = async () => {
    isRefreshing.value = true;
    try {
        await fetchMessages();
    } catch (error) {
        Logger.error("메시지 목록 새로고침 실패:", error);
    } finally {
        setTimeout(() => {
            isRefreshing.value = false;
            refreshPullDistance.value = 0;
        }, 800);
    }
};

// Computed
const hasMessages = computed(() => messages.value.length > 0)

const filteredMessages = computed(() => {
  if (!searchQuery.value) return messages.value
  
  const query = searchQuery.value.toLowerCase()
  return messages.value.filter(msg => {
    const content = msg.content?.toLowerCase() || ''
    const sender = (msg.senderRelationship || msg.senderName)?.toLowerCase() || ''
    return content.includes(query) || sender.includes(query)
  })
})

// Methods
const toggleSearch = () => {
  isSearchOpen.value = !isSearchOpen.value
  if (!isSearchOpen.value) {
    searchQuery.value = ''
  }
}

const openMessageModal = () => {
  showMessageModal.value = true
  newMessage.value = { content: '' }
  charCount.value = 0
  // Reset transform if reused
  if (messageSheet.value) messageSheet.value.style.transform = ''
}

const closeMessageModal = () => {
  showMessageModal.value = false
  newMessage.value = { content: '' }
  charCount.value = 0
  if (messageSheet.value) messageSheet.value.style.transform = ''
}

const handleInput = (e) => {
  newMessage.value.content = e.target.value
  updateCharCount()
}

const updateCharCount = () => {
  charCount.value = newMessage.value.content.length
}

const canSend = computed(() => {
  return newMessage.value.content.trim().length > 0 && charCount.value <= 100
})

const sendMessage = async () => {
  if (!canSend.value) return
  sending.value = true
  
  try {
    await messageService.sendGroupMessage(familyId.value, newMessage.value.content)
    closeMessageModal()
    // Refresh messages
    await fetchMessages()
  } catch (error) {
    Logger.error('메시지 전송 실패:', error)
    Logger.error('Error response:', error.response?.data)
    Logger.error('Error status:', error.response?.status)
    await modalStore.openAlert(`메시지 전송에 실패했습니다.\n${error.response?.data?.message || error.message}`)
  } finally {
    sending.value = false
  }
}

const openMessageDetail = (message) => {

  selectedMessage.value = message
}

const closeMessageDetail = () => {
  selectedMessage.value = null
}



const goBack = () => {
  router.back()
}



const formatTime = (timestamp) => {
  if (!timestamp) return ''
  
  let date = new Date(timestamp)
  const now = new Date()
  
  // [Timezone Fix] 
  // If the date is in the future, it's likely a timezone mismatch (e.g., Server sent KST as UTC, causing +9h shift).
  // We try to correct this by subtracting 9 hours.
  if (date > now) {
     const correctedDate = new Date(date.getTime() - 9 * 60 * 60 * 1000)
     // Use corrected date if it makes sense (is now in the past or close to now)
     if (correctedDate <= now || (correctedDate - now) < 5 * 60 * 1000) { 
         date = correctedDate
     }
  }

  const diffMs = now - date
  // Handle small clock skew (future within 1 min) by treating as 0
  if (diffMs < 0) return '방금 전'

  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`
  
  return `${date.getMonth() + 1}월 ${date.getDate()}일`
}

const formatFullDate = (timestamp) => {
  if (!timestamp) return ''
  
  const date = new Date(timestamp)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  
  return `${year}년 ${month}월 ${day}일 ${hours}:${minutes}`
}

const fetchMessages = async () => {
  loading.value = true
  
  try {
    const response = await messageService.getGroupMessages(familyId.value)
    const list = response?.data ?? []

    messages.value = Array.isArray(list) ? list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).map(msg => ({
      ...msg,
      expanded: false
    })) : []
    totalPages.value = 1
  } catch (error) {
    Logger.error('메시지 조회 실패:', error)
  } finally {
    loading.value = false
  }
}

const prevPage = () => {
  if (currentPage.value > 0) {
    currentPage.value--
    fetchMessages()
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value - 1) {
    currentPage.value++
    fetchMessages()
  }
}

// 초기화
const fetchFamilyDetails = async () => {
    if (!familyId.value) return;
    familyLoading.value = true;
    
    try {
        const res = await familyService.getFamilyDetails(familyId.value);
        const data = res.data;
        deviceName.value = data.deviceName || 'IoT 스피커';
        groupName.value = data.groupName || '우리 가족';
         
        if (data.members) {
            const patient = data.members.find(m => m.dependent);
            if (patient) {
                patientName.value = patient.relationship || patient.name;
                patientImage.value = getFullImageUrl(patient.profileImage, patientName.value);
            }
        }
    } catch (err) {
         Logger.error('가족 정보 조회 실패:', err);
    } finally {
        familyLoading.value = false;
    }
};

// React to route changes
watch(() => route.params.familyId, (newId) => {
    if (newId && newId !== familyId.value) {
        familyId.value = newId;
        fetchMessages();
        fetchFamilyDetails();
    }
});

// React to store selection changes (Header dropdown)
watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily && newFamily.id) {
        // If the store changes but we are still on the old route, redirect
        if (String(newFamily.id) !== String(route.params.familyId)) {
             router.replace({ name: 'FamilyMessages', params: { familyId: newFamily.id } });
        }
    }
});


// 초기화
onMounted(async () => {
  // 1. Prefer route param if present
  if (route.params.familyId) {
      familyId.value = route.params.familyId;
  } 
  // 2. Fallback to store if no param (though route usually has it)
  else if (familyStore.selectedFamily?.id) {
      familyId.value = familyStore.selectedFamily.id;
      // Update URL to match
      router.replace({ name: 'FamilyMessages', params: { familyId: familyId.value } });
  }

  if (familyId.value) {
    // API 호출 병렬 처리로 속도 개선
    await Promise.all([
      fetchMessages(),
      fetchFamilyDetails()
    ]);
  }
})
</script>

<style scoped>
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s ease-out;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}

.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

<style scoped>
/* Custom Scrollbar for Message Detail */
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #d1d5db;
  border-radius: 20px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background-color: #9ca3af;
}

/* Match CalendarPage icon style */
.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  font-size: 28px;
}
</style>