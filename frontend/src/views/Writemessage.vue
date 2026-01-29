<template>
  <div class="min-h-screen bg-gray-50">
    <header class="bg-white shadow-sm sticky top-0 z-10">
      <div class="max-w-2xl mx-auto px-4 py-4 flex items-center justify-between">
        <button 
          @click="goBack"
          class="p-2 hover:bg-gray-100 rounded-full transition-colors"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
        </button>
        
        <h1 class="text-xl font-bold">메세지 작성하기</h1>
        
        <div class="w-10"></div>
      </div>
    </header>

    <main class="max-w-2xl mx-auto px-4 py-6 pb-24">
      <div class="bg-white rounded-2xl p-4 mb-6 shadow-sm">
        <div class="flex items-center gap-4">
          <div class="relative">
            <img 
              :src="getFullImageUrl(family.profileImage, family.name)"
              :alt="family.name || 'User'"
              class="w-14 h-14 rounded-full object-cover border-2 border-orange-100 shadow-sm"
            />
          </div>
          
          <div class="flex-1">
            <h2 class="text-xl font-bold text-gray-800">To: {{ family.name }}</h2>
            <div class="flex items-center gap-2 text-sm text-gray-600">
              <span>{{ family.deviceName }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 메시지 작성 폼 -->
      <div class="mb-8">
        <div class="mb-4">
          <textarea
            v-model="message.content"
            @input="updateCharCount"
            placeholder="따뜻한 메세지를 적어보세요!!"
            class="eeum-input resize-none"
            rows="6"
            maxlength="100"
          ></textarea>
          <div class="flex justify-between items-center mt-2">
            <span class="eeum-sub">
              {{ charCount }}/100자
            </span>
          </div>
        </div>

        <!-- TTS 옵션 -->
        <div class="p-4 border" style="border-color: var(--border-default); border-radius: var(--radius-lg);">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-3">
              <svg class="w-5 h-5" style="color: var(--color-primary);" fill="currentColor" viewBox="0 0 24 24">
                <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
              </svg>
              <span class="font-medium" style="color: var(--text-title);">TTS 음성 읽기</span>
            </div>
            <button
              @click="showTTSSettings = true"
              class="px-3 py-1 text-sm transition-colors rounded-lg"
              style="color: var(--color-primary); background-color: var(--color-primary-soft);"
            >
              설정
            </button>
          </div>
        </div>
      </div>

      <!-- 하단 전송 버튼 -->
      <div class="fixed bottom-0 left-0 right-0 p-4 shadow-lg" style="background-color: var(--bg-page);">
        <div class="max-w-2xl mx-auto">
          <button
            @click="sendMessage"
            :disabled="!canSend || sending"
            class="eeum-btn-primary disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <svg v-if="!sending" class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
              <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
            </svg>
            <svg v-else class="animate-spin h-5 w-5" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>{{ sending ? 'Sending...' : 'Send Message' }}</span>
          </button>
        </div>
      </div>

    </main>

    <!-- TTS 설정 모달 -->
    <transition name="modal">
      <div 
        v-if="showTTSSettings"
        class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50"
        @click.self="showTTSSettings = false"
      >
        <div 
          class="bg-white p-6 max-w-md w-full max-h-[80vh] overflow-y-auto shadow-xl"
          style="border-radius: var(--radius-xl);"
        >
          <div class="flex items-center justify-between mb-4">
            <h3 class="eeum-title text-xl">TTS 세팅</h3>
            <button 
              @click="showTTSSettings = false"
              class="p-2 rounded-lg transition-colors"
              style="color: var(--text-sub);"
            >
              <svg class="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
              </svg>
            </button>
          </div>
          
          <!-- 음성 선택 -->
          <div class="mb-4">
            <label class="eeum-sub block mb-2 font-medium">음성</label>
            <div class="relative">
              <button
                @click="showVoiceDropdown = !showVoiceDropdown"
                class="eeum-input w-full text-left flex items-center justify-between"
              >
                <span>{{ getVoiceLabel(ttsSettings.voice) }}</span>
                <svg class="w-4 h-4 transition-transform" :class="{ 'rotate-180': showVoiceDropdown }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                </svg>
              </button>
              
              <div v-if="showVoiceDropdown" class="eeum-dropdown">
                <div
                  v-for="option in voiceOptions"
                  :key="option.value"
                  @click="selectVoice(option.value)"
                  class="eeum-dropdown-item"
                >
                  {{ option.label }}
                </div>
              </div>
            </div>
          </div>
          
          <!-- 속도 조절 -->
          <div class="mb-4">
            <label class="eeum-sub block mb-2 font-medium">속도: {{ ttsSettings.speed }}x</label>
            <input 
              v-model="ttsSettings.speed"
              type="range" 
              min="0.5" 
              max="2" 
              step="0.1"
              class="w-full"
              style="accent-color: var(--color-primary);"
            >
          </div>
          
          <!-- 볼륨 조절 -->
          <div class="mb-6">
            <label class="eeum-sub block mb-2 font-medium">음량: {{ ttsSettings.volume }}%</label>
            <input 
              v-model="ttsSettings.volume"
              type="range" 
              min="0" 
              max="100" 
              step="5"
              class="w-full"
              style="accent-color: var(--color-primary);"
            >
          </div>
          
          <button
            @click="showTTSSettings = false"
            class="eeum-btn-primary"
          >
            적용하기
          </button>
        </div>
      </div>
    </transition>

    <!-- 성공 토스트 -->
    <transition name="fade">
      <div 
        v-if="showSuccessToast"
        class="fixed bottom-24 left-1/2 transform -translate-x-1/2 px-6 py-3 rounded-full shadow-lg flex items-center gap-2 z-50"
        style="background-color: #10b981; color: white;"
      >
        <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
        </svg>
        Message sent successfully!
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { messageService } from '@/services/messageService'
import api from '@/services/api'

const router = useRouter()
const route = useRoute()

// S3 베이스 URL 정의
const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/'

// 가족 정보
const family = ref({
  id: null,
  name: '',
  memberCount: 0,
  deviceName: '디바이스 이름',
  profileImage: null
})

// 메시지 데이터
const message = ref({
  content: '',
  enableTTS: true
})

// TTS 설정
const ttsSettings = ref({
  voice: 'female1',
  speed: 1.0,
  volume: 80
})

// UI 상태
const sending = ref(false)
const showTTSSettings = ref(false)
const showSuccessToast = ref(false)
const charCount = ref(0)
const placeholder = ref('따뜻한 메세지를 적어보세요!!')
const showVoiceDropdown = ref(false)

// 음성 옵션
const voiceOptions = [
  { value: 'female1', label: '구수~한 손자 목소리' },
  { value: 'female2', label: '우리 큰아들 목소리' },
  { value: 'male1', label: '며느래기 목소리' },
  { value: 'male2', label: '우리 아들 목소리' }
]

// 이미지 URL 생성 함수 추가
const getFullImageUrl = (path, name) => {
  if (!path) {
    return `https://ui-avatars.com/api/?name=${name || 'User'}&background=FF9B6A&color=fff&size=56`
  }
  return path.startsWith('http') ? path : `${S3_BASE_URL}${path}`
}

// Computed
const canSend = computed(() => {
  return message.value.content.trim().length > 0 && charCount.value <= 100
})

// Methods
const goBack = () => {
  router.back()
}

const updateCharCount = () => {
  charCount.value = message.value.content.length
}

const sendMessage = async () => {
  if (!canSend.value) return
  sending.value = true
  
  try {
    await messageService.sendGroupMessage(family.value.id, message.value.content)
    showSuccessToast.value = true
    setTimeout(() => {
      showSuccessToast.value = false
      router.push(`/families/${family.value.id}/messages`)
    }, 2000)
  } catch (error) {
    console.error('메시지 전송 실패:', error)
    alert('메시지 전송에 실패했습니다. 다시 시도해주세요.')
  } finally {
    sending.value = false
  }
}

// 초기화
onMounted(() => {
  const familyId = route.params.familyId
  if (familyId) {
    family.value.id = familyId
    loadFamilyInfo(familyId)
  }

  if (typeof route.query.draft === 'string' && route.query.draft.trim()) {
    message.value.content = route.query.draft
    updateCharCount()
  }
})

const loadFamilyInfo = async (familyId) => {
  try {
    const response = await api.get(`/families/${familyId}/details`)
    const data = response.data
    
    // API 응답에서 부양 대상자(dependent) 찾기
    if (data?.members) {
      const dependent = data.members.find(m => m.dependent)
      if (dependent) {
        family.value.name = dependent.relationship || dependent.name
        family.value.profileImage = dependent.profileImage
        family.value.memberCount = data.members.length
        family.value.deviceName = data.deviceName || 'IoT 스피커'
      }
    }
  } catch (error) {
    console.error('가족 정보 로드 실패:', error)
  }
}

// 음성 드롭다운 함수
const getVoiceLabel = (voiceValue) => {
  const option = voiceOptions.find(opt => opt.value === voiceValue)
  return option ? option.label : voiceValue
}

const selectVoice = (voiceValue) => {
  ttsSettings.value.voice = voiceValue
  showVoiceDropdown.value = false
}
</script>

<style scoped>
/* 기존 스타일 유지 */
.modal-enter-active, .modal-leave-active { transition: opacity 0.3s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-active .bg-white, .modal-leave-active .bg-white { transition: transform 0.3s ease; }
.modal-enter-from .bg-white, .modal-leave-to .bg-white { transform: scale(0.9); }
.fade-enter-active, .fade-leave-active { transition: all 0.3s ease; }
.fade-enter-from { opacity: 0; transform: translate(-50%, 20px); }
.fade-leave-to { opacity: 0; transform: translate(-50%, -20px); }
input[type="checkbox"]:checked + div { background-color: #FF9B6A; }
</style>