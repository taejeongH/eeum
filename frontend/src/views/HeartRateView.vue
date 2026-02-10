<template>
  <div class="bg-gray-50 min-h-screen flex flex-col relative pb-20">
    <!-- 프리미엄 헤더 영역 -->
    <div
      class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0"
    >
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      <div
        class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10"
        style="
          background-image: radial-gradient(#fff 1px, transparent 1px);
          background-size: 24px 24px;
        "
      ></div>

      <div class="relative z-30 flex items-center justify-between p-5 pt-6">
        <div class="flex items-center gap-4">
          <button
            @click="$router.back()"
            class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm"
          >
            <IconBack />
          </button>
          <h1 class="text-xl font-bold text-white tracking-wide">정밀 심박수 측정</h1>
        </div>
      </div>
    </div>

    <!-- 콘텐츠 영역 -->
    <main class="flex-1 px-6 -mt-16 relative z-30 space-y-6">
      <div
        class="bg-white rounded-[2.5rem] p-8 shadow-lg border border-slate-100 flex flex-col items-center text-center"
      >
        <!-- 심장 애니메이션 -->
        <div class="relative mb-6">
          <div
            class="absolute inset-0 bg-red-100 rounded-full blur-3xl opacity-30 animate-pulse"
          ></div>
          <div class="heart relative text-7xl select-none" :class="{ beating: isMeasuring }">
            ❤️
          </div>
        </div>

        <div class="data-display mb-4">
          <div v-if="isMeasuring" class="flex flex-col items-center">
            <span class="text-3xl font-bold text-slate-400 animate-pulse tracking-tight"
              >수집 중...</span
            >
            <span class="text-sm text-slate-400 mt-2">안정적인 상태를 유지하세요</span>
          </div>
          <div v-else class="flex flex-col items-center gap-4">
            <!-- 메인 평균 표시 -->
            <div>
              <span class="value text-7xl font-black text-slate-900 tracking-tighter">{{
                heartRate
              }}</span>
              <span class="unit text-xl font-bold text-slate-400 ml-2 tracking-tight">BPM</span>
              <p class="text-sm text-slate-500 font-medium">평균</p>
            </div>

            <!-- 최소/최대 표시 -->
            <div v-if="avgCount > 0" class="flex items-center gap-8 mt-2">
              <div class="flex flex-col items-center">
                <span class="text-2xl font-bold text-blue-600">{{ minMetric }}</span>
                <span class="text-xs text-slate-400">최소</span>
              </div>
              <div class="w-px h-8 bg-slate-200"></div>
              <div class="flex flex-col items-center">
                <span class="text-2xl font-bold text-health-heart">{{ maxMetric }}</span>
                <span class="text-xs text-slate-400">최대</span>
              </div>
            </div>
          </div>
        </div>

        <div
          class="status-box px-6 py-3 bg-slate-50 rounded-2xl border border-slate-100 mb-8 inline-block"
        >
          <p
            v-if="isMeasuring"
            class="text-sm font-bold text-[var(--color-primary)] flex flex-col items-center gap-2"
          >
            <span class="text-lg">남은 시간: {{ timer }}초</span>
            <span class="text-xs text-slate-400">30초간의 데이터를 분석합니다.</span>
          </p>
          <p v-else-if="avgCount > 0" class="text-sm font-bold text-emerald-600">분석 완료</p>
          <p v-else class="text-sm font-bold text-slate-400">워치를 착용하고 측정을 시작하세요.</p>
        </div>

        <!-- Controls -->
        <!-- ... (unchanged) ... -->

        <!-- 제어 -->
        <div class="w-full space-y-3">
          <button
            @click="startPrecesionMeasuring"
            :disabled="isMeasuring"
            class="w-full py-4 rounded-2xl bg-[var(--color-primary)] text-white font-black text-lg shadow-lg shadow-orange-200 active:scale-[0.98] transition-all flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <span class="material-symbols-outlined">{{
              isMeasuring ? 'hourglass_empty' : 'bolt'
            }}</span>
            30초 정밀 측정 시작
          </button>
          <button
            @click="stopMonitoring"
            class="w-full py-4 rounded-2xl bg-slate-100 text-slate-500 font-bold text-base active:scale-[0.98] transition-all flex items-center justify-center gap-2"
          >
            측정 중단
          </button>
        </div>
      </div>

      <!-- 정보 팁 제거됨 -->
    </main>
  </div>
</template>

<script setup>
/**
 * @component HeartRateView
 * @description 워치를 이용한 정밀 심박수 측정 화면입니다.
 * 30초 동안의 심박수 데이터를 수집하여 평균, 최소, 최대 심박수를 분석합니다.
 *
 * [주요 기능]
 * - 워치 심박수 측정 요청 (`healthStore.requestMeasurement`)
 * - 30초 타이머 및 측정 상태 시각화
 * - 측정 완료 후 결과 조회 및 표시 (`healthStore.fetchLatestHeartRate`)
 *
 * @dependency healthStore - 심박수 측정 명령 및 결과 조회
 * @dependency familyStore - 측정 대상 가족 그룹 식별
 */
import { ref, onMounted, onUnmounted } from 'vue';
import IconBack from '@/components/icons/IconBack.vue';
import { useFamilyStore } from '@/stores/family';
import { useHealthStore } from '@/stores/health';
import { Logger } from '@/services/logger';

const familyStore = useFamilyStore();
const healthStore = useHealthStore();

/**
 * @type {Ref<number>} 현재 심박수 (BPM)
 */
const heartRate = ref(0);

/**
 * @type {Ref<number>} 측정된 최소 심박수
 */
const minMetric = ref(0);

/**
 * @type {Ref<number>} 측정된 최대 심박수
 */
const maxMetric = ref(0);

/**
 * @type {Ref<boolean>} 정밀 측정 진행 중 여부
 */
const isMeasuring = ref(false);

/**
 * @type {Ref<number>} 남은 측정 시간 (초)
 */
const timer = ref(30);

/**
 * @type {Ref<number>} 측정된 데이터 샘플 개수
 */
const avgCount = ref(0);

// 타이머 참조 변수
let timerInterval = null;
let realTimeInterval = null;

const currentEventId = ref(null);

/**
 * 정밀 측정 시작
 * 워치 앱을 실행하고 30초 카운트다운을 시작합니다.
 */
const startPrecesionMeasuring = async () => {
  try {
    isMeasuring.value = true;
    timer.value = 30;
    heartRate.value = 0;
    minMetric.value = 0;
    maxMetric.value = 0;
    avgCount.value = 0;
    currentEventId.value = null;

    // 1. 워치 측정 명령 전송 (Event ID 없음)
    if (familyStore.selectedFamily?.id) {
      await healthStore.requestMeasurement(familyStore.selectedFamily.id);
    }

    // 2. 30초 카운트다운 시작
    timerInterval = setInterval(() => {
      timer.value--;
      if (timer.value <= 0) {
        finishMeasurement();
      }
    }, 1000);
  } catch (e) {
    Logger.error('측정 시작 실패:', e);
    isMeasuring.value = false;
  }
};

/**
 * 측정 종료 및 결과 조회
 * 타이머가 종료되면 호출되어 서버로부터 최종 측정 결과를 가져옵니다.
 */
const finishMeasurement = async () => {
  stopTimers();
  isMeasuring.value = false;

  if (!familyStore.selectedFamily?.id) {
    Logger.error('가족 ID가 없어 결과를 조회할 수 없습니다.');
    return;
  }

  try {
    // 백엔드에 결과 요청 (최근 측정값) - Store Action 사용
    const data = await healthStore.fetchLatestHeartRate(familyStore.selectedFamily.id);

    if (data) {
      heartRate.value = Math.round(data.avgRate || 0);
      minMetric.value = data.minRate || 0;
      maxMetric.value = data.maxRate || 0;
      avgCount.value = data.sampleCount || 0;
    } else {
      Logger.warn('최신 측정 데이터가 없습니다.');
    }
  } catch (e) {
    Logger.error('결과 조회 실패:', e);
  }
};

/**
 * 타이머 정지 및 초기화
 */
const stopTimers = () => {
  if (timerInterval) clearInterval(timerInterval);
  if (realTimeInterval) clearInterval(realTimeInterval);
};

/**
 * 측정 중지 (사용자 취소 등)
 */
const stopMonitoring = () => {
  isMeasuring.value = false;
  stopTimers();
};

onMounted(async () => {
  if (!familyStore.selectedFamily) {
    await familyStore.fetchFamilies();
  }
});

onUnmounted(() => {
  stopTimers();
});
</script>

<style scoped>
.heart.beating {
  animation: beat 1s infinite cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes beat {
  0% {
    transform: scale(1);
  }
  15% {
    transform: scale(1.15);
    filter: drop-shadow(0 0 10px rgba(255, 82, 82, 0.4));
  }
  30% {
    transform: scale(1);
  }
  45% {
    transform: scale(1.08);
    filter: drop-shadow(0 0 5px rgba(255, 82, 82, 0.2));
  }
  60% {
    transform: scale(1);
  }
}

.material-symbols-outlined {
  font-variation-settings:
    'FILL' 1,
    'wght' 600,
    'GRAD' 0,
    'opsz' 24;
}
</style>
