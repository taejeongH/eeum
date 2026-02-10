<template>
  <div class="bg-slate-900 min-h-screen flex flex-col relative pb-20 overflow-hidden">
    <!-- 배경 효과 -->
    <div class="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
      <div
        class="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/20 rounded-full blur-[100px]"
      ></div>
      <div
        class="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-blue-500/10 rounded-full blur-[100px]"
      ></div>
    </div>

    <!-- 헤더 -->
    <header class="sticky top-0 z-10 px-6 pt-12 pb-6 flex items-center justify-between">
      <button
        @click="$router.back()"
        class="p-2 -ml-2 rounded-full hover:bg-white/10 text-white transition-colors"
      >
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <h1 class="text-xl font-bold text-white">Voice Mixer</h1>
      <div class="w-10"></div>
      <!-- 여백 -->
    </header>

    <main class="flex-1 px-6 relative z-0 flex flex-col items-center">
      <!-- 정보 텍스트 -->
      <div class="text-center mb-10">
        <p class="text-slate-400 text-sm mb-1">나만의 목소리 믹서</p>
        <h2 class="text-2xl font-bold text-white">원하는 목소리를<br />선택하고 설정해보세요</h2>
      </div>

      <!-- 믹서 그리드 -->
      <div class="grid grid-cols-2 gap-x-8 gap-y-10 w-full max-w-sm">
        <!-- 샘플 노드 -->
        <div v-for="sample in samples" :key="sample.id" class="flex flex-col items-center group">
          <!-- 노브/버튼 -->
          <div
            class="relative w-32 h-32 rounded-full flex items-center justify-center transition-all duration-300"
            :class="[
              sample.id === representativeId
                ? 'ring-4 ring-primary ring-offset-4 ring-offset-slate-900 shadow-[0_0_30px_rgba(255,111,97,0.4)]'
                : 'hover:scale-105',
              isPlaying && currentAudio === sample.testAudioUrl
                ? 'scale-110 shadow-[0_0_40px_rgba(255,255,255,0.2)]'
                : '',
              !sample.testAudioUrl ? 'cursor-not-allowed opacity-70' : 'cursor-pointer',
            ]"
            @click="sample.testAudioUrl ? handleSampleClick(sample) : null"
          >
            <!-- 배경 그라데이션 -->
            <div
              class="absolute inset-0 rounded-full bg-gradient-to-br from-slate-700 to-slate-800 shadow-inner border border-slate-600/50"
            ></div>

            <!-- 회전 표시기 (모의) -->
            <div
              class="absolute inset-2 rounded-full border border-white/5 border-t-white/30 rotate-45"
            ></div>

            <!-- 아이콘/상태 -->
            <div class="relative z-10 flex flex-col items-center justify-center">
              <template v-if="!sample.testAudioUrl">
                <span class="material-symbols-outlined text-4xl text-white/50 animate-spin"
                  >progress_activity</span
                >
                <span class="text-[10px] font-bold text-slate-400 mt-1 uppercase tracking-wider"
                  >제작중</span
                >
              </template>
              <template v-else>
                <span
                  class="material-symbols-outlined text-4xl text-white/90 drop-shadow-md transition-all"
                  :class="
                    isPlaying && currentAudio === sample.testAudioUrl
                      ? 'text-primary scale-110'
                      : ''
                  "
                >
                  {{
                    isPlaying && currentAudio === sample.testAudioUrl ? 'equalizer' : 'graphic_eq'
                  }}
                </span>
                <span
                  v-if="sample.id === representativeId"
                  class="text-[10px] font-bold text-primary mt-1 uppercase tracking-wider"
                  >Main</span
                >
              </template>
            </div>

            <!-- 재생 파문 효과 -->
            <div
              v-if="isPlaying && currentAudio === sample.testAudioUrl"
              class="absolute inset-0 rounded-full border-2 border-primary/50 animate-ping"
            ></div>
          </div>

          <!-- 별명 & 수정 -->
          <div class="mt-4 flex items-center gap-2">
            <div class="flex flex-col items-center">
              <div
                class="flex items-center gap-1 group/edit cursor-pointer"
                @click.stop="openEditModal(sample)"
              >
                <span class="text-white font-medium text-lg truncate max-w-[100px]">{{
                  sample.nickname || `Voice ${sample.id}`
                }}</span>
                <span
                  class="material-symbols-outlined text-slate-500 text-sm group-hover/edit:text-white transition-colors"
                  >edit</span
                >
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 바텀 시트 / 액션 모달 -->
    <div v-if="selectedSample" class="fixed inset-0 z-50 flex items-end">
      <div
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="selectedSample = null"
      ></div>
      <div
        class="relative w-full bg-slate-800 rounded-t-[2rem] p-8 pb-10 animate-slide-up border-t border-white/10"
      >
        <div class="flex justify-center mb-6">
          <div class="w-12 h-1.5 bg-slate-600 rounded-full"></div>
        </div>

        <div class="flex items-center gap-4 mb-8">
          <div
            class="w-16 h-16 rounded-full bg-slate-700 flex items-center justify-center text-2xl text-white font-bold"
          >
            {{ (selectedSample.nickname || '')[0] }}
          </div>
          <div>
            <h3 class="text-xl font-bold text-white">{{ selectedSample.nickname }}</h3>
            <p class="text-slate-400 text-sm">{{ formatDate(selectedSample.createdAt) }}</p>
          </div>
        </div>

        <div class="space-y-3">
          <button
            @click="togglePlay(selectedSample)"
            class="w-full py-4 rounded-xl flex items-center justify-center gap-3 font-bold transition-all"
            :class="
              isPlaying && currentAudio === selectedSample.testAudioUrl
                ? 'bg-primary text-white'
                : 'bg-white text-slate-900'
            "
          >
            <span class="material-symbols-outlined">{{
              isPlaying && currentAudio === selectedSample.testAudioUrl ? 'stop' : 'play_arrow'
            }}</span>
            {{
              isPlaying && currentAudio === selectedSample.testAudioUrl ? '재생 중지' : '미리 듣기'
            }}
          </button>

          <button
            v-if="selectedSample.id !== representativeId"
            @click="setRepresentative(selectedSample)"
            class="w-full py-4 rounded-xl bg-slate-700 text-white font-bold hover:bg-slate-600"
          >
            대표 목소리로 설정
          </button>

          <button
            v-if="selectedSample.id !== representativeId"
            @click="confirmDelete(selectedSample)"
            class="w-full py-4 rounded-xl border border-red-500/30 text-red-400 font-bold hover:bg-red-500/10"
          >
            삭제하기
          </button>
        </div>
      </div>
    </div>

    <!-- 별명 수정 모달 -->
    <div v-if="isEditing" class="fixed inset-0 z-[60] flex items-center justify-center px-6">
      <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="closeEditModal"></div>
      <div class="relative w-full max-w-sm bg-white rounded-2xl p-6 animate-scale-in">
        <h3 class="text-lg font-bold text-slate-900 mb-4">별칭 수정</h3>
        <input
          v-model="editNickname"
          type="text"
          class="w-full px-4 py-3 rounded-xl bg-slate-100 border-none outline-none focus:ring-2 focus:ring-primary mb-6 text-slate-900 font-medium"
          placeholder="별명을 입력하세요 (예: 차분한 목소리)"
          @keyup.enter="saveNickname"
        />
        <div class="flex gap-3">
          <button
            @click="closeEditModal"
            class="flex-1 py-3 rounded-xl bg-slate-100 text-slate-600 font-bold"
          >
            취소
          </button>
          <button
            @click="saveNickname"
            class="flex-1 py-3 rounded-xl bg-primary text-white font-bold"
          >
            저장
          </button>
        </div>
      </div>
    </div>

    <audio ref="audioPlayer" @ended="onAudioEnded" class="hidden"></audio>
  </div>
</template>

<script setup>
/**
 * @component VoiceSettings
 * @description 사용자 음성 모델(보이스 믹서) 관리 화면입니다.
 * 등록된 목소리 샘플들을 확인하고, 대표 목소리를 설정하거나 관리할 수 있습니다.
 *
 * [주요 기능]
 * - 목소리 샘플 목록 조회 및 상태 표시 (`voiceService.getVoiceStatus`)
 * - 샘플 미리듣기 (오디오 재생)
 * - 대표 목소리 설정 (`setRepresentative`)
 * - 샘플 별명 수정 및 삭제
 *
 * @dependency voiceService - 목소리 설정 및 샘플 관리 API
 */
import { ref, onMounted } from 'vue';
import * as voiceService from '@/services/voiceService';
import { Logger } from '@/services/logger';

/**
 * @type {Ref<Array>} 음성 샘플 목록
 */
const samples = ref([]);

/**
 * @type {Ref<string|null>} 현재 대표 목소리 ID
 */
const representativeId = ref(null);

/**
 * @type {Ref<string|null>} 현재 재생 중인 오디오 URL
 */
const currentAudio = ref(null);

/**
 * @type {Ref<boolean>} 오디오 재생 여부
 */
const isPlaying = ref(false);

/**
 * @type {Ref<HTMLAudioElement|null>} 오디오 플레이어 요소 참조
 */
const audioPlayer = ref(null);

/**
 * @type {Ref<Object|null>} 선택된 샘플 객체 (액션 모달용)
 */
const selectedSample = ref(null); // 액션 모달용

/**
 * @type {Ref<boolean>} 별명 수정 모달 표시 여부
 */
const isEditing = ref(false); // 별명 수정용

/**
 * @type {Ref<string|null>} 수정 중인 샘플 ID
 */
const editSampleId = ref(null);

/**
 * @type {Ref<string>} 수정할 별명 입력값
 */
const editNickname = ref('');

onMounted(async () => {
  await loadData();
});

/**
 * 서버 데이터 로드
 * 음성 메타데이터 및 상태를 조회하여 목록을 초기화합니다.
 */
const loadData = async () => {
  try {
    // 1. 상태 조회 (샘플 및 대표 ID 포함)
    const statusData = await voiceService.getVoiceStatus();
    representativeId.value = statusData.representativeSampleId;

    // 상태의 샘플 직접 사용
    // 백엔드가 상태 내에 샘플 배열 반환
    const fetchedSamples = statusData.samples || [];

    // testAudioUrl이 없는 샘플 확인, 있다면 백그라운드에서 생성 트리거
    // 단, 목록은 즉시 표시
    const needsGeneration = fetchedSamples.some((s) => !s.testAudioUrl);
    if (needsGeneration) {
      // voiceService.generateTestAudio().catch(err => Logger.error("음성 생성 트리거 실패:", err));
      Logger.info('Some samples are missing testAudioUrl. Waiting for backend generation...');
    }

    samples.value = fetchedSamples;
  } catch (error) {
    Logger.error('목소리 설정 로드 실패:', error);
  }
};

/**
 * 날짜 포맷팅 함수
 * @param {string} isoString - ISO 날짜 문자열
 * @returns {string} 로컬 날짜 문자열
 */
const formatDate = (isoString) => {
  if (!isoString) return '';
  const date = new Date(isoString);
  return date.toLocaleDateString();
};

/**
 * 샘플 클릭 핸들러
 * 클릭 시 액션 모달을 엽니다.
 * @param {Object} sample - 선택된 샘플
 */
const handleSampleClick = (sample) => {
  // 이 샘플 재생 중이면 중지? 아니면 모달 열기?
  // UX: 클릭 시 액션 모달 열기. 재생은 하나의 액션.
  selectedSample.value = sample;
};

/**
 * 조회/재생 토글
 * 현재 재생 중이면 중지, 아니면 재생합니다.
 * @param {Object} sample - 재생할 샘플
 */
const togglePlay = (sample) => {
  if (!sample.testAudioUrl) {
    alert('테스트 음성이 준비되지 않았습니다.');
    return;
  }

  if (currentAudio.value === sample.testAudioUrl && isPlaying.value) {
    audioPlayer.value.pause();
    isPlaying.value = false;
  } else {
    currentAudio.value = sample.testAudioUrl;
    audioPlayer.value.src = sample.testAudioUrl;
    audioPlayer.value.play();
    isPlaying.value = true;
  }
};

/**
 * 오디오 재생 종료 이벤트 핸들러
 */
const onAudioEnded = () => {
  isPlaying.value = false;
};

/**
 * 대표 목소리 설정
 * @param {Object} sample - 대표로 설정할 샘플
 */
const setRepresentative = async (sample) => {
  try {
    await voiceService.setRepresentativeSample(sample.id);
    representativeId.value = sample.id;
    selectedSample.value = null; // 모달 닫기
  } catch (error) {
    Logger.error('대표 목소리 설정 실패:', error);
    alert('설정에 실패했습니다.');
  }
};

/**
 * 샘플 삭제 확인 및 처리
 * @param {Object} sample - 삭제할 샘플
 */
const confirmDelete = async (sample) => {
  if (!confirm('정말 삭제하시겠습니까?')) return;

  try {
    await voiceService.deleteSample(sample.id);
    await loadData();
    selectedSample.value = null;
  } catch (error) {
    Logger.error('삭제 실패:', error);
    alert('삭제에 실패했습니다.');
  }
};

// 수정 로직
/**
 * 별명 수정 모달 열기
 * @param {Object} sample - 수정할 샘플
 */
const openEditModal = (sample) => {
  editSampleId.value = sample.id;
  editNickname.value = sample.nickname || '';
  isEditing.value = true;
};

/**
 * 별명 수정 모달 닫기 및 초기화
 */
const closeEditModal = () => {
  isEditing.value = false;
  editSampleId.value = null;
  editNickname.value = '';
};

/**
 * 별명 변경 사항 저장
 */
const saveNickname = async () => {
  if (!editNickname.value.trim()) {
    alert('별명을 입력해주세요.');
    return;
  }

  try {
    await voiceService.updateNickname(editSampleId.value, editNickname.value);
    // 빠른 변경 반영을 위해 로컬 목록 직접 업데이트
    const target = samples.value.find((s) => s.id === editSampleId.value);
    if (target) target.nickname = editNickname.value;

    closeEditModal();
  } catch (error) {
    Logger.error('별명 수정 실패:', error);
    alert('수정에 실패했습니다.');
  }
};
</script>

<style scoped>
@keyframes slide-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
.animate-slide-up {
  animation: slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes scale-in {
  from {
    transform: scale(0.9);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
.animate-scale-in {
  animation: scale-in 0.2s ease-out;
}
</style>
