<template>
  <div class="min-h-screen" style="background-color: var(--bg-page);">
    <header class="sticky top-0 z-10 shadow-sm" style="background-color: var(--color-primary);">
      <div class="max-w-2xl mx-auto px-4 py-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="p-2 bg-white/20 backdrop-blur-sm rounded-xl">
            <svg class="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
            </svg>
          </div>
          <div>
            <h1 class="eeum-title text-2xl text-white">가족 메시지</h1>
            <p class="text-white/80 text-sm">따뜻한 마음을 나누세요</p>
          </div>
        </div>

        <button
          @click="goToNewMessage"
          class="group relative p-3 bg-white/20 backdrop-blur-sm rounded-xl hover:bg-white/30 transition-all duration-300 hover:scale-110"
        >
          <div class="absolute inset-0 bg-white rounded-xl opacity-0 group-hover:opacity-20 transition-opacity"></div>
          <svg class="relative w-6 h-6 text-white group-hover:rotate-90 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
          </svg>
        </button>
      </div>
    </header>

    <main class="max-w-2xl mx-auto px-4 py-6">
      <div v-if="loading" class="flex justify-center items-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2" style="border-color: var(--color-primary);"></div>
      </div>

      <div v-else-if="messages.length === 0" class="text-center py-20">
        <div class="mb-6">
          <svg class="w-24 h-24 mx-auto" style="color: var(--text-sub);" fill="currentColor" viewBox="0 0 24 24">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
          </svg>
        </div>
        <h3 class="eeum-title text-xl mb-2" style="color: var(--text-title);">No messages sent yet</h3>
        <p class="eeum-sub mb-6">Share your thoughts with your family</p>
        <button
          @click="goToNewMessage"
          class="eeum-btn-primary hover:scale-105 transition-transform"
        >
          Send Your First Message
        </button>
      </div>

      <div v-else class="space-y-4">
        <div
          v-for="message in messages"
          :key="message.id"
          class="group relative shadow-md hover:shadow-xl transition-all duration-300 p-6 border"
          style="
            background: linear-gradient(135deg, var(--bg-page) 0%, var(--color-primary-soft) 100%);
            border-color: var(--border-default);
            border-radius: var(--radius-xl);
          "
        >
          <div class="flex items-start justify-between mb-5">
            <div class="flex items-center gap-4">
              <div class="relative">
                <div 
                  class="absolute inset-0 rounded-full blur-md opacity-30 group-hover:opacity-50 transition-opacity"
                  style="background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);"
                ></div>
                <img 
                  :src="getFullImageUrl(message.senderProfileImage, message.senderName)"
                  :alt="message.senderName || 'User'"
                  class="relative w-14 h-14 rounded-full object-cover border-3 border-white shadow-lg"
                />
              </div>
              
              <div class="flex-1">
                <div class="flex items-center gap-3 mb-2">
                  <span class="font-bold text-xl" style="color: var(--text-title);">{{ message.senderRelationship || message.senderName }}</span>
                </div>
                <div class="flex items-center gap-2 text-sm" style="color: var(--text-sub);">
                  <svg class="w-4 h-4" style="color: var(--color-primary);" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd"/>
                  </svg>
                  <span>{{ formatTime(message.createdAt) }}</span>
                </div>
              </div>
            </div>
            
            <div class="flex flex-col items-end gap-2">
              <span
                class="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold transition-all shadow-md"
                :class="message.isRead 
                  ? 'text-white' 
                  : 'text-white animate-pulse'"
                :style="message.isRead 
                  ? 'background: linear-gradient(135deg, #10b981 0%, #059669 100%);'
                  : 'background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);'"
              >
                <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/>
                  <path fill-rule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clip-rule="evenodd"/>
                </svg>
                {{ message.isRead ? '읽음' : '안읽음' }}
              </span>
            </div>
          </div>

          <div class="relative">
            <div 
              class="absolute inset-0 rounded-2xl blur-sm"
              style="background: linear-gradient(135deg, var(--color-primary-soft) 0%, rgba(255,255,255,0.5) 100%);"
            ></div>
            <div 
              class="relative p-5 border shadow-inner"
              style="
                background-color: rgba(255,255,255,0.9);
                border-color: var(--border-default);
                border-radius: var(--radius-lg);
              "
            >
              <p class="text-base font-medium leading-relaxed whitespace-pre-wrap break-words" style="color: var(--text-body);" :class="{ 'line-clamp-3': !message.expanded }">
                {{ message.content }}
              </p>
              <button 
                v-if="message.content.length > 80"
                @click="message.expanded = !message.expanded"
                class="text-sm font-medium mt-2 transition-colors"
                style="color: var(--color-primary);"
              >
                {{ message.expanded ? '접기' : '더보기...' }}
              </button>
            </div>
          </div>

          <div class="flex items-center justify-between mt-4 pt-4" style="border-color: var(--border-default);">
            <div class="flex items-center gap-4">
              <span class="flex items-center gap-2 text-sm px-3 py-1.5 rounded-full" style="color: var(--text-sub); background-color: var(--color-primary-soft);">
                <svg class="w-4 h-4" style="color: var(--color-primary);" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
                </svg>
                메시지
              </span>
              <span v-if="message.enableTTS" class="flex items-center gap-2 text-sm px-3 py-1.5 rounded-full" style="color: var(--color-primary); background-color: var(--color-primary-soft);">
                <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
                </svg>
                TTS 활성화
              </span>
            </div>
          </div>
        </div>
      </div>

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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { messageService } from '@/services/messageService'

const router = useRouter()
const route = useRoute()

const messages = ref([])
const loading = ref(false)
const currentPage = ref(0)
const totalPages = ref(0)
const familyId = ref(null)

const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/'

// 이미지 URL 생성 함수 추가
const getFullImageUrl = (path, name) => {
  if (!path) {
    return `https://ui-avatars.com/api/?name=${name || 'User'}&background=FF9B6A&color=fff&size=48`
  }
  // http로 시작하면(절대경로) 그대로 쓰고, 아니면 S3 주소를 붙임
  return path.startsWith('http') ? path : `${S3_BASE_URL}${path}`
}

// Computed
const hasMessages = computed(() => messages.value.length > 0)

// Methods
const goToNewMessage = () => {
  router.push(`/families/${familyId.value}/message/new`)
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`
  
  return date.toLocaleDateString('en-US', { 
    month: 'short', 
    day: 'numeric',
    year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined
  })
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
    console.error('메시지 조회 실패:', error)
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
onMounted(() => {
  familyId.value = route.params.familyId
  if (familyId.value) {
    fetchMessages()
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

.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>