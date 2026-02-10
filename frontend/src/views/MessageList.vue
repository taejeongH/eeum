<template>
  <div
    class="min-h-screen relative overflow-x-hidden"
    style="background-color: var(--bg-page)"
    @touchstart="handleRefreshTouchStart"
    @touchmove="handleRefreshTouchMove"
    @touchend="handleRefreshTouchEnd"
  >
    <!-- 현대적인 당겨서 새로고침 인디케이터 -->
    <div
      class="fixed top-0 left-0 right-0 flex justify-center items-center z-[110] pointer-events-none transition-all duration-300 ease-out"
      :style="{
        transform: `translateY(${refreshPullDistance > 0 ? Math.min(refreshPullDistance - 40, 80) : -60}px)`,
        opacity: Math.min(refreshPullDistance / 60, 1),
      }"
    >
      <div
        class="bg-white rounded-full shadow-2xl border border-gray-100 flex items-center justify-center w-12 h-12 mt-4 relative overflow-hidden"
      >
        <!-- 현대적인 SVG 스피너 -->
        <svg
          v-if="isRefreshing"
          class="animate-spin h-7 w-7 text-[var(--color-primary)]"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="3"
          ></circle>
          <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          ></path>
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
        <!-- 검색 바 (확장 상태) -->
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
            <!-- 검색 입력창 닫기 버튼 (헤더 토글이 있어 필수는 아니지만 초기화 로직 유지) -->
            <button
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="absolute right-3 top-2.5 text-gray-400 hover:text-gray-600"
            >
              <IconClose class="w-5 h-5" />
            </button>
          </div>
        </div>

        <!-- 피부양자 프로필 섹션 -->
        <div v-if="!familyLoading" class="flex flex-col items-center py-4">
          <div class="relative mb-3">
            <img
              :src="patientImage || getFullImageUrl(null, 'Family')"
              :alt="patientName || 'Family'"
              class="w-20 h-20 rounded-full object-cover border-4 border-white shadow-md"
              style="background-color: #8b9a8f"
            />
          </div>
          <h2 class="text-lg font-bold text-gray-800 mb-1">
            {{ patientName || '먼저 피부양자를 등록해주세요' }}
          </h2>
          <p class="text-sm text-gray-500">따뜻한 마음을 전해주세요!</p>
        </div>
      </div>
    </div>

    <main
      class="max-w-2xl mx-auto px-4 py-6 pb-32 transition-transform duration-300 ease-out"
      :style="{ transform: `translateY(${isRefreshing ? 60 : refreshPullDistance * 0.4}px)` }"
    >
      <div v-if="loading" class="flex justify-center items-center py-20">
        <div
          class="animate-spin rounded-full h-12 w-12 border-b-2"
          style="border-color: var(--color-primary)"
        ></div>
      </div>

      <div v-else-if="messages.length === 0" class="text-center py-20">
        <div class="mb-6">
          <svg
            class="w-24 h-24 mx-auto"
            style="color: var(--text-sub)"
            fill="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"
            />
          </svg>
        </div>
        <h3 class="eeum-title text-xl mb-2" style="color: var(--text-title)">
          아직 메세지가 없습니다
        </h3>
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
                <span class="font-semibold text-gray-800 text-sm">{{
                  message.senderRelationship || message.senderName
                }}</span>
              </div>
              <p class="text-sm text-gray-600 line-clamp-2 leading-relaxed">
                {{ message.content }}
              </p>
            </div>
          </div>
          <div class="flex items-center justify-between text-xs text-gray-400">
            <span>{{ formatTime(message.createdAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 메시지 상세 모달 (어두운 배경 없는 중앙 팝업) -->
      <Teleport to="body">
        <div
          v-if="selectedMessage"
          class="fixed inset-0 z-[140] flex items-center justify-center p-4"
          @click.self="closeMessageDetail"
        >
          <!-- 배경은 투명하지만 클릭 시 닫힘 -->
          <!-- 모달임을 나타내기 위해 bg-black/10을 추가할 수 있지만, 이전 요청에 따라 어두운 배경은 지양함 -->
          <!-- 사용자 요청에 따라 투명하거나 극히 미묘하게 유지 -->
          <div
            class="absolute inset-0 bg-black/10 backdrop-blur-[2px]"
            @click="closeMessageDetail"
          ></div>

          <div
            class="bg-white rounded-3xl shadow-2xl w-[85%] max-w-md max-h-[60vh] flex flex-col min-h-[200px] border border-gray-100 relative z-10 animate-scale-in"
          >
            <!-- 상세 헤더 -->
            <div class="flex-none flex items-center justify-between p-5 border-b border-gray-100">
              <div class="flex items-center gap-3">
                <img
                  :src="
                    getFullImageUrl(selectedMessage.senderProfileImage, selectedMessage.senderName)
                  "
                  :alt="selectedMessage.senderName || 'User'"
                  class="w-12 h-12 rounded-full object-cover border border-gray-100 shadow-sm"
                />
                <div>
                  <h3 class="font-bold text-gray-800 text-lg">
                    {{ selectedMessage.senderRelationship || selectedMessage.senderName }}
                  </h3>
                  <p class="text-xs text-gray-500 font-medium">
                    {{ formatFullDate(selectedMessage.createdAt) }}
                  </p>
                </div>
              </div>
              <button
                @click="closeMessageDetail"
                class="p-2 hover:bg-gray-100 rounded-full transition-colors"
                aria-label="Close detail"
              >
                <svg
                  class="w-6 h-6 text-gray-400"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            </div>

            <!-- 상세 내용 -->
            <div class="flex-1 overflow-y-auto p-6 custom-scrollbar">
              <p
                class="text-base text-gray-800 leading-relaxed whitespace-pre-wrap break-words font-medium"
              >
                {{ selectedMessage.content }}
              </p>
            </div>
          </div>
        </div>
      </Teleport>

      <!-- 메시지 작성 모달: 적절한 Z-인덱스를 위해 Teleport 사용 -->
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
            <!-- 드래그 핸들 -->
            <div
              class="sticky top-0 bg-white z-20 w-full flex justify-center pt-3 pb-2"
              @click="closeMessageModal"
            >
              <div class="w-12 h-1.5 bg-gray-300 rounded-full"></div>
            </div>

            <!-- 모달 헤더 -->
            <div
              class="sticky top-5 bg-white z-10 flex items-center px-5 pb-3 border-b border-gray-100 relative"
            >
              <h2 class="text-lg font-bold text-gray-800 w-full text-center">메시지 작성</h2>
              <button
                @click="closeMessageModal"
                class="absolute right-4 p-2 hover:bg-gray-100 rounded-full transition-colors"
              >
                <svg
                  class="w-6 h-6 text-gray-500"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            </div>

            <!-- 모달 내용 -->
            <div class="p-5 space-y-4 pb-8">
              <!-- 수신자 정보 -->
              <div class="bg-orange-50 rounded-xl p-3 flex items-center gap-3">
                <img
                  :src="patientImage || getFullImageUrl(null, 'Family')"
                  :alt="patientName || 'Family'"
                  class="w-10 h-10 rounded-full object-cover border-2 border-orange-200"
                />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-bold text-gray-800 truncate">
                    To: {{ patientName || '우리 가족' }}
                  </p>
                  <p class="text-xs text-gray-500 truncate">{{ deviceName || 'IoT 스피커' }}</p>
                </div>
              </div>

              <!-- 메시지 입력 -->
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

              <!-- TTS 안내 메시지 -->
              <div class="bg-blue-50/70 rounded-xl p-3 flex items-center gap-2">
                <span class="material-symbols-outlined text-blue-600 text-lg">volume_up</span>
                <span class="text-xs font-semibold text-blue-700"
                  >이 메세지는 TTS로 보내집니다</span
                >
              </div>

              <!-- 전송 버튼 -->
              <button
                @click="sendMessage"
                :disabled="!canSend || sending"
                class="w-full eeum-btn-primary hover:brightness-95 disabled:bg-gray-300 disabled:cursor-not-allowed text-white text-lg font-bold py-3.5 rounded-2xl shadow-sm active:scale-[0.98] transition-all flex items-center justify-center gap-2"
              >
                <span v-if="!sending" class="material-symbols-outlined text-xl">send</span>
                <svg v-else class="animate-spin h-5 w-5" viewBox="0 0 24 24">
                  <circle
                    class="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    stroke-width="4"
                    fill="none"
                  ></circle>
                  <path
                    class="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  ></path>
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

          <span
            class="px-4 py-2 font-semibold"
            style="
              color: var(--color-primary);
              background-color: var(--color-primary-soft);
              border-radius: var(--radius-lg);
            "
          >
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

    <!-- 플로팅 액션 버튼 (FAB) -->
    <button
      @click="openMessageModal"
      class="fixed bottom-32 right-6 z-30 bg-primary text-white w-14 h-14 rounded-full flex items-center justify-center shadow-lg shadow-primary/30 active:scale-95 transition-transform"
    >
      <span
        class="material-symbols-outlined text-3xl"
        style="
          font-variation-settings:
            'FILL' 0,
            'wght' 600;
        "
        >add</span
      >
    </button>

    <!-- 하단 내비게이션 -->
    <BottomNav v-if="!isModalOpen" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { messageService } from '@/services/messageService';
import { familyService } from '@/services/familyService';
import { useFamilyStore } from '@/stores/family';
import BottomNav from '@/components/layout/BottomNav.vue';
import { useModalStore } from '@/stores/modal';
import MainHeader from '@/components/MainHeader.vue';
import IconSearch from '@/components/icons/IconSearch.vue';
import IconClose from '@/components/icons/IconClose.vue';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const route = useRoute();
const modalStore = useModalStore();
const familyStore = useFamilyStore();
const userStore = useUserStore();

const isModalOpen = ref(false);
const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

const messages = ref([]);
const loading = ref(false);
const familyLoading = ref(true);
const currentPage = ref(0);
const totalPages = ref(0);
const familyId = ref(null);

const patientName = ref('');
const patientImage = ref(null);
const deviceName = ref('');

const isSearchOpen = ref(false);
const searchQuery = ref('');

const showMessageModal = ref(false);
const newMessage = ref({
  content: '',
  enableTTS: true,
});
const charCount = ref(0);
const sending = ref(false);
const selectedMessage = ref(null);
const groupName = ref('');

// Refresh State
const isRefreshing = ref(false);
const refreshStartY = ref(0);
const refreshPullDistance = ref(0);
const canRefresh = ref(true);

const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/';

/**
 * 이미지 풀 경로를 생성합니다. (S3 또는 기본 이미지)
 * @param {string|null} path - 이미지 경로
 * @param {string} name - 대체 텍스트용 이름
 * @returns {string} 이미지 URL
 */
const getFullImageUrl = (path, name) => {
  if (!path) {
    return `https://ui-avatars.com/api/?name=${name || 'User'}&background=FF9B6A&color=fff&size=48`;
  }
  return path.startsWith('http') ? path : `${S3_BASE_URL}${path}`;
};

/* 스와이프 로직 */
const messageSheet = ref(null);
let startY = 0;
let currentY = 0;

/**
 * 메시지 작성 시트 터치 시작 핸들러
 * @param {TouchEvent} e
 */
const onTouchStart = (e) => {
  if (messageSheet.value && messageSheet.value.scrollTop > 0) return;
  startY = e.touches[0].clientY;
};

/**
 * 메시지 작성 시트 터치 이동 핸들러
 * @param {TouchEvent} e
 */
const onTouchMove = (e) => {
  if (startY === 0) return;
  currentY = e.touches[0].clientY;
  const diff = currentY - startY;
  if (diff > 0 && messageSheet.value) {
    messageSheet.value.style.transform = `translateY(${diff}px)`;
  }
};

/**
 * 메시지 작성 시트 터치 종료 핸들러
 */
const onTouchEnd = () => {
  if (startY === 0) return;
  const diff = currentY - startY;
  if (diff > 100) {
    closeMessageModal();
  } else if (messageSheet.value) {
    messageSheet.value.style.transform = '';
  }
  startY = 0;
  currentY = 0;
};

/* 당겨서 새로고침 핸들러 */
/**
 * 당겨서 새로고침 터치 시작 핸들러
 * @param {TouchEvent} e
 */
const handleRefreshTouchStart = (e) => {
  if (window.scrollY > 5 || isRefreshing.value || showMessageModal.value) {
    canRefresh.value = false;
    return;
  }
  canRefresh.value = true;
  refreshStartY.value = e.touches[0].clientY;
};

/**
 * 당겨서 새로고침 터치 이동 핸들러
 * @param {TouchEvent} e
 */
const handleRefreshTouchMove = (e) => {
  if (!canRefresh.value || isRefreshing.value) return;

  const currentY = e.touches[0].clientY;
  const distance = currentY - refreshStartY.value;

  if (distance > 0) {
    if (e.cancelable) e.preventDefault();
    refreshPullDistance.value = Math.min(distance * 0.6, 150);
  }
};

/**
 * 당겨서 새로고침 터치 종료 핸들러
 */
const handleRefreshTouchEnd = async () => {
  if (!canRefresh.value || isRefreshing.value) return;

  if (refreshPullDistance.value > 100) {
    await executeRefresh();
  } else {
    refreshPullDistance.value = 0;
  }
};

/**
 * 새로고침을 실행합니다.
 */
const executeRefresh = async () => {
  isRefreshing.value = true;
  try {
    await fetchMessages();
  } catch (error) {
    Logger.error('메시지 목록 새로고침 실패:', error);
  } finally {
    setTimeout(() => {
      isRefreshing.value = false;
      refreshPullDistance.value = 0;
    }, 800);
  }
};

// 계산된 속성
/** @type {import('vue').ComputedRef<boolean>} 메시지 존재 여부 */
const hasMessages = computed(() => messages.value.length > 0);

/** @type {import('vue').ComputedRef<Array<Object>>} 검색어에 의해 필터링된 메시지 목록 */
const filteredMessages = computed(() => {
  if (!searchQuery.value) return messages.value;

  const query = searchQuery.value.toLowerCase();
  return messages.value.filter((msg) => {
    const content = msg.content?.toLowerCase() || '';
    const sender = (msg.senderRelationship || msg.senderName)?.toLowerCase() || '';
    return content.includes(query) || sender.includes(query);
  });
});

// 메서드
/**
 * 검색 바 노출 여부를 토글합니다.
 */
const toggleSearch = () => {
  isSearchOpen.value = !isSearchOpen.value;
  if (!isSearchOpen.value) {
    searchQuery.value = '';
  }
};

/**
 * 메시지 작성 모달을 엽니다.
 */
const openMessageModal = () => {
  showMessageModal.value = true;
  newMessage.value = { content: '' };
  charCount.value = 0;
  if (messageSheet.value) messageSheet.value.style.transform = '';
};

/**
 * 메시지 작성 모달을 닫습니다.
 */
const closeMessageModal = () => {
  showMessageModal.value = false;
  newMessage.value = { content: '' };
  charCount.value = 0;
  if (messageSheet.value) messageSheet.value.style.transform = '';
};

/**
 * 메시지 입력 핸들러
 * @param {Event} e
 */
const handleInput = (e) => {
  newMessage.value.content = e.target.value;
  updateCharCount();
};

/**
 * 입력된 글자 수를 업데이트합니다.
 */
const updateCharCount = () => {
  charCount.value = newMessage.value.content.length;
};

/** @type {import('vue').ComputedRef<boolean>} 메시지 전송 가능 여부 */
const canSend = computed(() => {
  return newMessage.value.content.trim().length > 0 && charCount.value <= 100;
});

/**
 * 서버로 메시지를 전송합니다.
 */
const sendMessage = async () => {
  if (!canSend.value) return;
  sending.value = true;

  try {
    await messageService.sendGroupMessage(familyId.value, newMessage.value.content);
    closeMessageModal();
    await fetchMessages();
  } catch (error) {
    Logger.error('메시지 전송 실패:', error);
    await modalStore.openAlert(`메시지 전송에 실패했습니다.`);
  } finally {
    sending.value = false;
  }
};

/**
 * 메시지 상세 모달을 엽니다.
 * @param {Object} message
 */
const openMessageDetail = (message) => {
  selectedMessage.value = message;
};

/**
 * 메시지 상세 모달을 닫습니다.
 */
const closeMessageDetail = () => {
  selectedMessage.value = null;
};

const goBack = () => {
  router.back();
};

/**
 * 메시지 생성 시간을 보기 쉬운 형식으로 변환합니다.
 * @param {string|number} timestamp
 * @returns {string} (예: 방금 전, 5분 전, 10월 25일)
 */
const formatTime = (timestamp) => {
  if (!timestamp) return '';

  let date = new Date(timestamp);
  const now = new Date();

  // 타임존 차이 보정 (필요한 경우)
  if (date > now) {
    const correctedDate = new Date(date.getTime() - 9 * 60 * 60 * 1000);
    if (correctedDate <= now || correctedDate - now < 5 * 60 * 1000) {
      date = correctedDate;
    }
  }

  const diffMs = now - date;
  if (diffMs < 0) return '방금 전';

  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return '방금 전';
  if (diffMins < 60) return `${diffMins}분 전`;
  if (diffHours < 24) return `${diffHours}시간 전`;
  if (diffDays < 7) return `${diffDays}일 전`;

  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
};

/**
 * 메시지 생성 시간을 상세 형식으로 변환합니다.
 * @param {string|number} timestamp
 * @returns {string} (예: 2025년 10월 25일 14:30)
 */
const formatFullDate = (timestamp) => {
  if (!timestamp) return '';

  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');

  return `${year}년 ${month}월 ${day}일 ${hours}:${minutes}`;
};

/**
 * 서버로부터 메시지 목록을 가져옵니다.
 */
const fetchMessages = async () => {
  loading.value = true;

  try {
    const response = await messageService.getGroupMessages(familyId.value);
    const list = response?.data ?? [];

    messages.value = Array.isArray(list)
      ? list
          .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
          .map((msg) => ({
            ...msg,
            expanded: false,
          }))
      : [];
    totalPages.value = 1; // 현재 페이지네이션이 단일 페이지로 응답됨
  } catch (error) {
    Logger.error('메시지 조회 실패:', error);
  } finally {
    loading.value = false;
  }
};

/**
 * 이전 페이지로 이동합니다.
 */
const prevPage = () => {
  if (currentPage.value > 0) {
    currentPage.value--;
    fetchMessages();
  }
};

/**
 * 다음 페이지로 이동합니다.
 */
const nextPage = () => {
  if (currentPage.value < totalPages.value - 1) {
    currentPage.value++;
    fetchMessages();
  }
};

// 초기화
/**
 * 가족의 상세 정보를 가져와서 피부양자 정보를 설정합니다.
 */
const fetchFamilyDetails = async () => {
  if (!familyId.value) return;
  familyLoading.value = true;

  try {
    const res = await familyService.getFamilyDetails(familyId.value);
    const data = res.data;
    deviceName.value = data.deviceName || 'IoT 스피커';
    groupName.value = data.groupName || '우리 가족';

    if (data.members) {
      const patient = data.members.find((m) => m.dependent);
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

// 라우트 변경에 대응
watch(
  () => route.params.familyId,
  (newId) => {
    if (newId && newId !== familyId.value) {
      familyId.value = newId;
      fetchMessages();
      fetchFamilyDetails();
    }
  },
);

// 스토어 선택 변경에 대응 (헤더 드롭다운)
watch(
  () => familyStore.selectedFamily,
  (newFamily) => {
    if (newFamily && newFamily.id) {
      // 스토어가 변경되었지만 이전 라우트에 있는 경우 리다이렉트
      if (String(newFamily.id) !== String(route.params.familyId)) {
        router.replace({ name: 'FamilyMessages', params: { familyId: newFamily.id } });
      }
    }
  },
);

// 초기화
onMounted(async () => {
  // 1. 라우트 파라미터가 있으면 우선 사용
  if (route.params.familyId) {
    familyId.value = route.params.familyId;
  }
  // 2. 파라미터가 없으면 스토어 정보 사용 (보통 라우트에 파라미터가 있음)
  else if (familyStore.selectedFamily?.id) {
    familyId.value = familyStore.selectedFamily.id;
    // URL 동기화
    router.replace({ name: 'FamilyMessages', params: { familyId: familyId.value } });
  }

  if (familyId.value) {
    // API 호출 병렬 처리로 속도 개선
    await Promise.all([fetchMessages(), fetchFamilyDetails()]);
  }
});
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

/* 트랜지션 애니메이션 */
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
/* 메시지 상세용 커스텀 스크롤바 */
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

/* CalendarPage 아이콘 스타일과 통일 */
.material-symbols-outlined {
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
  font-size: 28px;
}
</style>
