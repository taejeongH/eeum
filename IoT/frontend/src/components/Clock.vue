<template>
  <!-- 
    시계 컴포넌트
    - 위치: 고정 (Fixed), 기본적으로 우측 상단에 배치됨
    - Z-Index: 20 (콘텐츠보다 위에 표시)
    - 전환 효과: 픽셀 이동(번인 방지) 시 부드럽게 움직이도록 설정
  -->
  <div
    class="pointer-events-none fixed z-20 transition-transform duration-[3000ms] ease-in-out select-none text-right"
    :style="{ transform: `translate3d(${offsetX}px, ${offsetY}px, 0)` }"
    :class="positionClasses"
  >
    <!-- 시간 표시 -->
    <div
      class="text-[140px] font-bold text-white leading-[0.9] drop-shadow-lg font-mono tracking-tighter"
    >
      {{ timeDisplay }}
    </div>
    <!-- 날짜 표시 -->
    <div class="text-4xl text-white/90 font-medium mt-2 mr-2 drop-shadow-md">
      {{ dateDisplay }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';

const props = defineProps({
  position: {
    type: String,
    default: 'top-right', // 'top-right'(우상단), 'top-left'(좌상단) 지원
  },
});

/**
 * 위치에 따른 클래스를 반환합니다.
 */
const positionClasses = computed(() => {
  return props.position === 'top-left' ? 'top-10 left-10 text-left' : 'top-10 right-10 text-right';
});

const now = ref(new Date());
const offsetX = ref(0);
const offsetY = ref(0);

// --- 포맷터 (Formatters) ---

/**
 * 시간을 HH:mm 형식으로 표시
 */
const timeDisplay = computed(() => {
  const hours = String(now.value.getHours()).padStart(2, '0');
  const minutes = String(now.value.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
});

/**
 * 날짜를 YYYY.MM.DD (요일) 형식으로 표시
 */
const dateDisplay = computed(() => {
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  const y = now.value.getFullYear();
  const m = String(now.value.getMonth() + 1).padStart(2, '0');
  const d = String(now.value.getDate()).padStart(2, '0');
  const dayName = days[now.value.getDay()];
  return `${y}.${m}.${d} (${dayName})`;
});

// 타이머 변수
let timer = null;
let burnInTimer = null;

const updateTime = () => {
  now.value = new Date();
};

/**
 * 픽셀 이동 (번인 방지)
 * 매 분마다 작은 범위(예: +/- 20px) 내에서 무작위로 위치를 이동시킵니다.
 * OLED/LCD 화면의 잔상을 방지하기 위함입니다.
 */
const shiftPixels = () => {
  const range = 20;
  offsetX.value = Math.floor(Math.random() * (range * 2 + 1)) - range;
  offsetY.value = Math.floor(Math.random() * (range * 2 + 1)) - range;
};

// --- 라이프사이클 ---

onMounted(() => {
  updateTime();
  // 1초마다 시간 갱신
  timer = setInterval(updateTime, 1000);

  // 번인 방지: 최초 실행 및 1분마다 위치 이동
  shiftPixels();
  burnInTimer = setInterval(shiftPixels, 60000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  if (burnInTimer) clearInterval(burnInTimer);
});
</script>

<style scoped>
/* 필요한 경우 커스텀 폰트 등을 추가 */
</style>
