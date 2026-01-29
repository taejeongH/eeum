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
              <span class="bg-orange-100 text-orange-700 px-2 py-1 rounded-full text-xs font-medium">{{ family.memberCount }}명</span>
              <span>{{ family.deviceName }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="mb-6">
        <label class="block text-lg font-semibold mb-3">메세지 내용</label>
        <div class="relative">
          <textarea
            v-model="message.content"
            :placeholder="placeholder"
            rows="8"
            class="w-full px-5 py-4 bg-white border-2 border-gray-200 rounded-2xl resize-none focus:outline-none focus:border-orange-400 focus:ring-4 focus:ring-orange-100 transition-all text-gray-700 placeholder-amber-600/60"
            @input="updateCharCount"
          ></textarea>
          <div class="absolute bottom-4 right-4 text-sm text-gray-400">
            {{ charCount }} / 500
          </div>
        </div>
      </div>

      <div class="bg-gradient-to-br from-orange-50 to-orange-100 rounded-2xl p-5 mb-6 shadow-sm">
        <div class="flex items-start gap-3 mb-3">
          <div class="p-2 bg-orange-400 rounded-full">
            <svg class="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
            </svg>
          </div>
          <div class="flex-1">
            <h3 class="font-semibold text-gray-800 mb-1">TTS</h3>
            <p class="text-sm text-amber-700 mb-3">
              메세지는 IoT 스피커를 통해 자동으로 읽어집니다.
            </p>
            
            <div class="flex items-center justify-between">
              <label class="relative inline-flex items-center cursor-pointer">
                <input 
                  v-model="message.enableTTS" 
                  type="checkbox" 
                  class="sr-only peer"
                >
                <div class="w-14 h-7 bg-gray-300 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-orange-200 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-6 after:w-6 after:transition-all peer-checked:bg-orange-400"></div>
                <span class="ml-3 text-sm font-medium text-gray-700">
                  {{ message.enableTTS ? '허용' : '비허용' }}
                </span>
              </label>
              
              <button 
                v-if="message.enableTTS"
                @click="showTTSSettings = true"
                class="px-3 py-1 text-sm text-orange-600 hover:bg-orange-200 rounded-lg transition-colors"
              >
                설정
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <div class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 p-4 shadow-lg">
      <div class="max-w-2xl mx-auto">
        <button
          @click="sendMessage"
          :disabled="!canSend || sending"
          class="w-full py-4 bg-orange-400 hover:bg-orange-500 text-white font-semibold rounded-full text-lg flex items-center justify-center gap-2 transition-all transform hover:scale-[1.02] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none shadow-lg"
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

    <transition name="modal">
      <div 
        v-if="showTTSSettings"
        class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50"
        @click.self="showTTSSettings = false"
      >
        <div class="bg-white rounded-3xl p-6 max-w-md w-full max-h-[80vh] overflow-y-auto">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-xl font-bold">TTS 세팅</h3>
            <button 
              @click="showTTSSettings = false"
              class="p-2 hover:bg-gray-100 rounded-full"
            >
              <svg class="w-6 h-6" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
              </svg>
            </button>
          </div>
          
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2">음성</label>
            <select 
              v-model="ttsSettings.voice"
              class="w-full px-4 py-2 border-2 border-gray-200 rounded-xl focus:outline-none focus:border-orange-400"
            >
              <option value="female1">구수~한 손자 목소리</option>
              <option value="female2">우리 큰아들 목소리</option>
              <option value="male1">며느래기 목소리</option>
              <option value="male2">우리 아들 목소리</option>
            </select>
          </div>
          
          <div class="mb-4">
            <label class="block text-sm font-medium mb-2">속도: {{ ttsSettings.speed }}x</label>
            <input 
              v-model="ttsSettings.speed"
              type="range" min="0.5" max="2" step="0.1"
              class="w-full accent-orange-400"
            >
          </div>
          
          <div class="mb-6">
            <label class="block text-sm font-medium mb-2">음량: {{ ttsSettings.volume }}%</label>
            <input 
              v-model="ttsSettings.volume"
              type="range" min="0" max="100" step="5"
              class="w-full accent-orange-400"
            >
          </div>
          
          <button
            @click="showTTSSettings = false"
            class="w-full py-3 bg-orange-400 hover:bg-orange-500 text-white font-semibold rounded-full transition-colors"
          >
            적용하기
          </button>
        </div>
      </div>
    </transition>

    <transition name="fade">
      <div 
        v-if="showSuccessToast"
        class="fixed bottom-24 left-1/2 transform -translate-x-1/2 bg-green-500 text-white px-6 py-3 rounded-full shadow-lg flex items-center gap-2 z-50"
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

// 이미지 URL 생성 함수 추가
const getFullImageUrl = (path, name) => {
  if (!path) {
    return `https://ui-avatars.com/api/?name=${name || 'User'}&background=FF9B6A&color=fff&size=56`
  }
  return path.startsWith('http') ? path : `${S3_BASE_URL}${path}`
}

// Computed
const canSend = computed(() => {
  return message.value.content.trim().length > 0 && charCount.value <= 500
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