<script setup>
import { ref, computed } from 'vue';

/**
 * Props 정의
 * @property {object} alert - 표시할 알림 객체 (종류, 내용, 시간 등 포함)
 */
const props = defineProps({
  alert: {
    type: Object,
    required: true,
  },
});

/**
 * Emits 정의
 * @event close - 닫기 버튼 클릭 등으로 닫기 요청 시 (현재 사용 안 함)
 * @event dismiss - 알림을 스와이프하여 화면에서 제거할 때 발생
 */
const emit = defineEmits(['close', 'dismiss']);

// --- 상수 (Constants) ---

/**
 * 아이콘 표시를 위한 SVG 경로 모음
 * @constant {object}
 */
const ICON_PATHS = {
  medication:
    'M6 3h12v2H6zm11 3H7c-1.1 0-2 .9-2 2v11c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-1 9h-2.5v2.5h-3V15H8v-3h2.5V9.5h3V12H16v3z',
  calendar_today:
    'M20 3h-1V1h-2v2H7V1H5v2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 18H4V8h16v13z',
  record_voice_over:
    'M9 9c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3zm0 2.25c-2.33 0-7 1.17-7 3.5V18h14v-3.25c0-2.33-4.67-3.5-7-3.5z M16.76 5.36l-1.68 1.69c.84 1.18.84 2.71 0 3.89l1.68 1.69c2.02-2.02 2.02-5.07 0-7.27zM20.07 2l-1.63 1.63c2.77 3.02 2.77 7.56 0 10.74L20.07 16c3.9-4.07 3.9-10.09 0-14z',
  chat: 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z',
  image:
    'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z',
  notifications:
    'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
};

/**
 * 알림 종류(kind)에 따른 스타일 및 텍스트 설정
 * @constant {object}
 */
const ALERT_STYLES = {
  medication: {
    bg: 'bg-white',
    iconBg: 'bg-gradient-to-br from-[#FF4081] to-[#C2185B]',
    textTitle: 'text-[#C2185B]',
    icon: 'medication',
    title: '복약 시간 알림',
  },
  schedule: {
    bg: 'bg-white',
    iconBg: 'bg-gradient-to-br from-[#2196F3] to-[#1976D2]',
    textTitle: 'text-[#1976D2]',
    icon: 'calendar_today',
    title: '오늘의 일정',
  },
  voice: {
    bg: 'bg-white',
    iconBg: 'bg-gradient-to-br from-[#FF9800] to-[#F57C00]',
    textTitle: 'text-[#E65100]',
    icon: 'record_voice_over',
    title: '가족 메시지',
  },
  message: {
    bg: 'bg-white',
    iconBg: 'bg-gradient-to-br from-[#4CAF50] to-[#388E3C]',
    textTitle: 'text-[#388E3C]',
    icon: 'chat',
    title: '새로운 메시지',
  },
  photo: {
    bg: 'bg-white',
    iconBg: 'bg-gradient-to-br from-[#AB47BC] to-[#7B1FA2]',
    textTitle: 'text-[#7B1FA2]',
    icon: 'image',
    title: '새 사진',
  },
  default: {
    bg: 'bg-white',
    iconBg: 'bg-gradient-to-br from-gray-500 to-gray-700',
    textTitle: 'text-gray-700',
    icon: 'notifications',
    title: '알림',
  },
};

// --- 헬퍼 함수 ---

/**
 * Unix 타임스탬프를 한국 시간 형식(오전/오후 HH:mm)으로 변환합니다.
 * @param {number} timestamp - 초 단위 Unix 타임스탬프
 * @returns {string} 포맷팅된 시간 문자열
 */
const formatTime = (timestamp) => {
  if (!timestamp) return '';
  const date = new Date(timestamp * 1000);
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
};

/**
 * 알림 종류에 따른 스타일 객체를 반환하는 Computed 속성
 * @type {import('vue').ComputedRef<object>}
 */
const styles = computed(() => {
  return ALERT_STYLES[props.alert.kind] || ALERT_STYLES.default;
});

// --- 스와이프 로직 (Swipe Logic) ---

const touchStartX = ref(0);
const swipeOffset = ref(0); // 기존 touchCurrentX를 의미에 맞게 변경
const isSwiping = ref(false);

/**
 * 스와이프 시작
 * @param {number} clientX - 터치/마우스 시작 X 좌표
 */
const startSwipe = (clientX) => {
  touchStartX.value = clientX;
  isSwiping.value = true;
};

/**
 * 스와이프 이동 중 처리
 * 오른쪽으로만 스와이프 허용
 * @param {number} clientX - 현재 터치/마우스 X 좌표
 */
const moveSwipe = (clientX) => {
  if (!isSwiping.value) return;
  const diff = clientX - touchStartX.value;

  if (diff > 0) {
    swipeOffset.value = diff;
  }
};

/**
 * 스와이프 종료 및 동작 결정
 * 일정 거리(150px) 이상 이동 시 'dismiss' 이벤트 발생
 */
const endSwipe = () => {
  if (!isSwiping.value) return;
  isSwiping.value = false;

  if (swipeOffset.value > 150) {
    // 스와이프 성공: 화면 밖으로 애니메이션 처리 후 삭제 요청
    swipeOffset.value = 1000;
    setTimeout(() => {
      emit('dismiss');
    }, 200);
  } else {
    // 스와이프 취소: 제자리로 복귀
    swipeOffset.value = 0;
  }
};

// 이벤트 핸들러 바인딩
const onTouchStart = (e) => startSwipe(e.touches[0].clientX);
const onTouchMove = (e) => moveSwipe(e.touches[0].clientX);
const onTouchEnd = () => endSwipe();

const onMouseDown = (e) => startSwipe(e.clientX);
const onMouseMove = (e) => moveSwipe(e.clientX);
const onMouseUp = () => endSwipe();
const onMouseLeave = () => endSwipe();
</script>

<template>
  <div
    class="relative rounded-[2rem] shadow-2xl p-8 bg-white/95 backdrop-blur-md border border-white/20 overflow-hidden cursor-grab active:cursor-grabbing touch-pan-y select-none"
    :style="{
      transform: `translateX(${swipeOffset}px)`,
      transition: isSwiping ? 'none' : 'transform 0.3s ease-out',
    }"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @mousedown="onMouseDown"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @mouseleave="onMouseLeave"
  >
    <div class="flex items-start gap-8 pointer-events-none">
      <!-- 아이콘 컨테이너 -->
      <div
        class="flex-shrink-0 w-28 h-28 rounded-full flex items-center justify-center text-white shadow-lg mt-1 overflow-hidden"
        :class="styles.iconBg"
      >
        <img
          v-if="alert.profile_image"
          :src="alert.profile_image"
          class="w-full h-full object-cover"
          alt="Profile"
        />
        <svg
          v-else-if="ICON_PATHS[styles.icon]"
          xmlns="http://www.w3.org/2000/svg"
          class="w-16 h-16"
          viewBox="0 0 24 24"
          fill="currentColor"
        >
          <path :d="ICON_PATHS[styles.icon]" />
        </svg>
        <span v-else class="text-4xl">!</span>
      </div>

      <!-- 콘텐츠 컨테이너 -->
      <div class="flex-1 min-w-0 py-2">
        <div class="flex justify-between items-baseline mb-4">
          <h3
            class="font-extrabold text-4xl leading-tight tracking-tight"
            :class="styles.textTitle"
          >
            {{ alert.kind === 'voice' && alert.title ? alert.title : styles.title }}
          </h3>
          <span class="text-2xl text-gray-400 font-medium">
            {{ formatTime(alert.sent_at) || '방금 전' }}
          </span>
        </div>

        <div class="prose prose-xl max-w-none">
          <!-- 복약 알림 내용 -->
          <div v-if="alert.kind === 'medication'">
            <div class="flex flex-col gap-2">
              <p class="text-gray-800 text-3xl font-black leading-tight break-keep">
                {{ alert.content }}
              </p>
              <p
                v-if="alert.data?.text_message && alert.data.text_message !== alert.content"
                class="text-2xl font-bold text-rose-500/70 italic"
              >
                {{ alert.data.text_message }}
              </p>
            </div>
          </div>

          <!-- 일정 알림 내용 -->
          <div v-else-if="alert.kind === 'schedule'">
            <p class="text-gray-500 text-2xl mb-4 font-bold">오늘의 주요 일정</p>
            <div class="space-y-4">
              <div
                v-for="(event, idx) in alert.data?.events_for_today"
                :key="idx"
                class="flex items-center gap-5 bg-gray-50 p-4 rounded-2xl border border-gray-100"
              >
                <div class="w-3 h-3 rounded-full bg-blue-500 shrink-0"></div>
                <span class="font-black text-3xl text-gray-900 min-w-[100px]">{{
                  event.time
                }}</span>
                <span class="font-bold text-3xl text-gray-700 truncate">{{ event.title }}</span>
              </div>
            </div>
          </div>

          <!-- 기본 알림 내용 -->
          <div v-else>
            <p class="text-gray-800 text-3xl font-medium leading-normal">{{ alert.content }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 스와이프 표시기 (하단 바) -->
    <div
      class="absolute bottom-2 left-1/2 transform -translate-x-1/2 w-16 h-1.5 bg-gray-200 rounded-full opacity-50"
    ></div>

    <!-- 진행 바 (자동 삭제 타이머 표시용) -->
    <div class="absolute bottom-0 left-0 h-1.5 bg-gray-100 w-full pointer-events-none">
      <div
        class="h-full w-full animate-shrink origin-left"
        :class="styles.textTitle.replace('text-', 'bg-')"
      ></div>
    </div>
  </div>
</template>

<style scoped>
.shadow-2xl {
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.15);
}

@keyframes shrink {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}

.animate-shrink {
  animation: shrink 10s linear forwards;
}
</style>
