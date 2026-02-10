<template>
  <div class="bg-gray-50 h-screen flex flex-col relative overflow-hidden">
    <div
      class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0 transition-all duration-300"
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
          <h1 class="text-xl font-bold text-white tracking-wide">목소리 등록</h1>
        </div>
        <button
          @click="showHelpModal = true"
          class="w-10 h-10 flex items-center justify-center ml-auto rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm"
        >
          <span class="material-symbols-outlined text-[20px]">help_outline</span>
        </button>
      </div>

      <div
        class="absolute bottom-5 left-6 right-6 z-30 flex p-1.5 bg-white/20 backdrop-blur-md rounded-2xl border border-white/20"
      >
        <button
          @click="currentMode = 'script'"
          class="flex-1 py-3 px-4 rounded-xl font-bold text-sm transition-all duration-300 flex items-center justify-center gap-2 relative overflow-hidden"
          :class="
            currentMode === 'script'
              ? 'bg-white text-primary shadow-lg scale-100'
              : 'text-white/80 hover:bg-white/10'
          "
        >
          <span class="material-symbols-outlined text-[20px]">description</span>
          <span>문장 녹음</span>
        </button>
        <button
          @click="currentMode = 'free'"
          class="flex-1 py-3 px-4 rounded-xl font-bold text-sm transition-all duration-300 flex items-center justify-center gap-2 relative overflow-hidden"
          :class="
            currentMode === 'free'
              ? 'bg-white text-primary shadow-lg scale-100'
              : 'text-white/80 hover:bg-white/10'
          "
        >
          <span class="material-symbols-outlined text-[20px]">graphic_eq</span>
          <span>자유 대본</span>
        </button>
      </div>
    </div>

    <main class="flex-1 px-6 -mt-6 relative z-30 pt-4 flex flex-col min-h-0">
      <transition
        enter-active-class="transition ease-out duration-300 transform"
        enter-from-class="opacity-0 translate-y-4"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition ease-in duration-200 transform"
        leave-from-class="opacity-100 translate-y-0"
        leave-to-class="opacity-0 translate-y-4"
        mode="out-in"
      >
        <div
          v-if="currentMode === 'script'"
          key="script"
          class="h-full overflow-y-auto pb-24 no-scrollbar"
        >
          <div class="space-y-4 pt-6">
            <h3 class="text-lg font-bold text-slate-900">녹음 문장</h3>

            <div
              v-for="(sample, index) in voiceSamples"
              :key="sample.id"
              class="p-5 rounded-2xl shadow-sm border flex items-center justify-between active:scale-[0.98] transition-all"
              :class="
                sample.isRecorded ? 'bg-green-50/50 border-green-100' : 'bg-white border-slate-100'
              "
              @click="openRecorder(sample)"
            >
              <div class="flex-1 mr-4">
                <div class="flex items-center gap-2 mb-1">
                  <span
                    class="text-xs font-bold px-2 py-0.5 rounded-md"
                    :class="
                      sample.isRecorded
                        ? 'bg-green-100 text-green-700'
                        : 'bg-primary/10 text-primary'
                    "
                  >
                    문장 {{ index + 1 }}
                  </span>
                  <span
                    v-if="sample.isRecorded"
                    class="text-xs font-bold text-green-600 flex items-center"
                  >
                    <span class="material-symbols-outlined text-[14px] mr-1">check_circle</span>
                    완료
                  </span>
                </div>
                <p class="text-slate-700 font-medium line-clamp-1">{{ sample.text }}</p>
              </div>
              <button
                class="w-10 h-10 rounded-full flex items-center justify-center transition-colors"
                :class="
                  sample.isRecorded ? 'bg-green-50 text-green-600' : 'bg-slate-50 text-slate-400'
                "
              >
                <span class="material-symbols-outlined">{{
                  sample.isRecorded ? 'refresh' : 'mic_none'
                }}</span>
              </button>
            </div>

            <button
              v-if="completedCount >= 1"
              @click="goToSettings"
              class="w-full py-4 mt-8 rounded-2xl bg-primary text-white font-bold text-lg shadow-lg shadow-primary/30 active:scale-95 transition-all flex items-center justify-center gap-2"
            >
              <span>목소리 설정으로 이동</span>
              <span class="material-symbols-outlined">arrow_forward</span>
            </button>
          </div>
        </div>

        <div v-else key="free" class="flex flex-col flex-1 h-full">
          <div class="text-center mb-4 shrink-0 mt-10">
            <h3 class="text-slate-500 text-sm font-semibold mb-1">자유롭게 말씀해 보세요</h3>
            <p class="text-xl font-bold text-slate-900">AI가 목소리를 텍스트로 변환합니다</p>
          </div>

          <div
            class="flex-1 bg-white rounded-3xl p-6 mb-4 overflow-y-auto border border-slate-100 relative shadow-sm min-h-0"
          >
            <div
              v-if="transcribedText"
              class="text-lg text-slate-800 leading-relaxed whitespace-pre-wrap animate-fade-in"
            >
              {{ transcribedText }}
            </div>
            <div
              v-else
              class="h-full flex flex-col items-center justify-center text-slate-400 gap-2"
            >
              <span class="material-symbols-outlined text-4xl opacity-20">text_fields</span>
              <p class="text-sm font-medium">녹음 후 변환된 텍스트가 이곳에 표시됩니다.</p>
            </div>

            <!-- STT Error Overlay -->
            <div
              v-if="sttError"
              class="absolute inset-0 bg-red-50/90 backdrop-blur-sm flex flex-col items-center justify-center rounded-2xl z-10 p-6 text-center"
            >
              <div
                class="w-12 h-12 bg-red-100 text-red-500 rounded-full flex items-center justify-center mb-4"
              >
                <span class="material-symbols-outlined text-2xl">error_outline</span>
              </div>
              <p class="text-red-600 font-bold mb-1">음성 변환에 실패했습니다</p>
              <p class="text-red-400 text-xs mb-4">
                마이크 연동이나 인터넷 연결을 확인한 후<br />다시 녹음해 주세요.
              </p>
              <button
                @click="resetFreeTalk"
                class="px-6 py-2 bg-red-500 text-white rounded-xl text-xs font-bold shadow-md active:scale-95 transition-all"
              >
                다시 시도
              </button>
            </div>

            <div
              v-if="isLoading"
              class="absolute inset-0 bg-white/80 backdrop-blur-sm flex flex-col items-center justify-center rounded-2xl z-10"
            >
              <div
                class="w-10 h-10 border-4 border-primary border-t-transparent rounded-full animate-spin mb-3"
              ></div>
              <p class="text-primary font-bold animate-pulse">변환 중...</p>
            </div>
          </div>

          <div
            v-if="isFreeTalkSaved"
            class="px-6 mb-2 shrink-0 flex justify-center w-full animate-fade-in"
          >
            <button
              @click="goToSettings"
              class="w-full py-4 rounded-2xl bg-white border border-primary text-primary font-bold text-lg shadow-sm active:scale-95 transition-all flex items-center justify-center gap-2"
            >
              <span>목소리 설정으로 이동</span>
              <span class="material-symbols-outlined">arrow_forward</span>
            </button>
          </div>

          <div
            v-if="!isFreeTalkSaved"
            class="flex items-center justify-center gap-6 shrink-0 pb-10 mt-auto"
          >
            <button
              v-if="transcribedText"
              @click="resetFreeTalk"
              class="w-14 h-14 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center hover:bg-slate-200 transition-colors"
            >
              <span class="material-symbols-outlined">refresh</span>
            </button>

            <p
              v-if="isRecording"
              class="text-primary font-bold bg-white/50 px-3 py-1 rounded-full backdrop-blur-sm min-w-[60px] text-center"
            >
              {{ recordingDuration.toFixed(1) }}s
            </p>

            <button
              @click="toggleFreeTalkRecord"
              class="w-20 h-20 rounded-full flex items-center justify-center shadow-lg active:scale-95 transition-all relative overflow-hidden"
              :class="
                isRecording
                  ? 'bg-red-500 text-white shadow-red-500/30'
                  : 'bg-[var(--color-primary)] text-white shadow-orange-200'
              "
            >
              <div v-if="isRecording" class="absolute inset-0 bg-red-600 animate-pulse"></div>
              <span class="material-symbols-outlined text-4xl relative z-10">{{
                isRecording ? 'stop' : 'mic'
              }}</span>
            </button>

            <button
              v-if="transcribedText && recordedBlob && !isRecording && !sttError"
              @click="saveFreeTalk"
              class="w-14 h-14 rounded-full bg-primary text-white flex items-center justify-center hover:bg-primary-dark transition-colors shadow-md"
            >
              <span class="material-symbols-outlined">save</span>
            </button>
          </div>
        </div>
      </transition>
    </main>

    <footer
      v-if="isInitialSetup"
      class="fixed bottom-0 left-0 right-0 p-6 bg-white/80 backdrop-blur-md border-t border-slate-100 flex gap-4 z-40"
    >
      <button
        @click="handleSkip"
        class="flex-1 py-4 px-6 rounded-2xl bg-slate-100 text-slate-600 font-bold hover:bg-slate-200 transition-all"
      >
        건너뛰기
      </button>
      <button
        @click="handleCompleteSetup"
        :disabled="completedCount === 0"
        class="flex-[2] py-4 px-6 rounded-2xl bg-[var(--color-primary)] text-white font-bold shadow-lg shadow-orange-200 hover:bg-orange-600 transition-all disabled:opacity-50 disabled:shadow-none"
      >
        등록 완료
      </button>
    </footer>

    <div v-if="selectedSample" class="fixed inset-0 z-50 flex items-end">
      <div
        class="absolute inset-0 bg-black/40 backdrop-blur-sm"
        @click="selectedSample = null"
      ></div>
      <div class="relative w-full bg-white rounded-t-[2.5rem] p-8 pb-12 animate-slide-up">
        <div class="flex justify-center mb-8">
          <div class="w-12 h-1.5 bg-slate-300 rounded-full"></div>
        </div>
        <div class="text-center mb-10">
          <h3 class="text-slate-500 text-sm font-semibold mb-6">아래 문장을 읽어주세요</h3>
          <p class="text-2xl font-bold text-slate-900 leading-relaxed word-keep-all">
            "{{ selectedSample.text }}"
          </p>
        </div>
        <div class="flex justify-center items-center gap-1 h-16 mb-10">
          <div
            v-for="n in 20"
            :key="n"
            class="w-1.5 bg-primary rounded-full animate-bounce"
            :style="{ height: `${Math.random() * 100}%`, animationDelay: `${n * 0.05}s` }"
          ></div>
        </div>
        <div class="flex items-center justify-center gap-6">
          <button
            @click="selectedSample = null"
            class="w-14 h-14 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center"
          >
            <span class="material-symbols-outlined">close</span>
          </button>
          <button
            @click="toggleRecord"
            class="w-20 h-20 rounded-full bg-red-500 text-white flex items-center justify-center shadow-lg shadow-red-500/30 active:scale-95 transition-all"
          >
            <span class="material-symbols-outlined text-4xl">{{
              isRecording ? 'stop' : 'mic'
            }}</span>
          </button>
          <button
            v-if="!isRecording && recordedBlob"
            @click="saveRecording"
            class="w-14 h-14 rounded-full bg-primary/10 text-primary flex items-center justify-center animate-bounce-small"
          >
            <span class="material-symbols-outlined">check</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="showHelpModal" class="fixed inset-0 z-[60] flex items-center justify-center p-6">
      <div
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="showHelpModal = false"
      ></div>
      <div
        class="relative w-full max-w-sm bg-white rounded-[2.5rem] p-8 shadow-2xl animate-fade-in"
      >
        <div class="flex items-center gap-3 mb-6">
          <div class="w-10 h-10 rounded-2xl bg-primary/10 flex items-center justify-center">
            <span class="material-symbols-outlined text-primary">live_help</span>
          </div>
          <h3 class="text-xl font-bold text-slate-900">도움말</h3>
        </div>

        <div class="space-y-6 mb-8 text-slate-600">
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-500"
              >1</span
            >
            <p class="text-sm leading-relaxed">
              <strong>조용한 장소에서 녹음해 주세요.</strong><br />주변 소음이 섞이면 고품질의
              목소리 생성이 어려울 수 있습니다.
            </p>
          </div>
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-500"
              >2</span
            >
            <p class="text-sm leading-relaxed">
              <strong>일정한 거리를 유지해 주세요.</strong><br />마이크와 입 사이의 거리를 약 25cm
              정도로 일정하게 유지하는 것이 좋습니다.
            </p>
          </div>
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-500"
              >3</span
            >
            <p class="text-sm leading-relaxed">
              <strong>또박또박 읽어 주세요.</strong><br />문장을 평소 대화하듯 자연스럽고
              또박또박하게 끝까지 읽어 주세요.
            </p>
          </div>
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs font-bold text-slate-500"
              >4</span
            >
            <p class="text-sm leading-relaxed">
              <strong>최소 3초 이상 녹음해 주세요.</strong><br />너무 짧은 녹음은 학습에 활용될 수
              없습니다. (3~10초 사이 권장)
            </p>
          </div>
        </div>

        <button
          @click="showHelpModal = false"
          class="w-full py-4 rounded-2xl bg-slate-900 text-white font-bold text-base hover:bg-slate-800 transition-all"
        >
          확인했습니다
        </button>
      </div>
    </div>

    <div v-if="showLimitModal" class="fixed inset-0 z-[60] flex items-center justify-center px-6">
      <div
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="showLimitModal = false"
      ></div>
      <div
        class="relative w-full max-w-sm bg-white rounded-3xl p-8 animate-scale-in text-center shadow-2xl"
      >
        <div
          class="w-16 h-16 bg-red-100 text-red-500 rounded-full flex items-center justify-center mx-auto mb-6"
        >
          <span class="material-symbols-outlined text-3xl">error</span>
        </div>

        <h3 class="text-xl font-bold text-slate-900 mb-2">목소리 모델 가득 참</h3>
        <p class="text-slate-500 mb-8 leading-relaxed">
          생성된 샘플이 가득 찼습니다.<br />
          기존 샘플을 삭제 후 다시 시도해주세요.
        </p>

        <div class="flex flex-col gap-3">
          <button
            @click="goToSettings"
            class="w-full py-4 rounded-2xl bg-primary text-white font-bold shadow-lg shadow-primary/30 active:scale-95 transition-all"
          >
            목소리 설정으로 이동
          </button>
          <button
            @click="showLimitModal = false"
            class="w-full py-4 rounded-2xl bg-slate-100 text-slate-600 font-bold active:scale-95 transition-all"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @component VoiceRegistration
 * @description 사용자 목소리 등록을 위한 화면입니다.
 * 정해진 문장을 읽는 '대본 녹음' 모드와 자유롭게 이야기하는 '자유 대본' 모드를 제공합니다.
 *
 * [주요 기능]
 * - 대본 목록 조회 및 녹음 상태 표시 (`voiceStore`)
 * - 오디오 녹음 제어 (시작/중지/저장) (`useAudioRecorder`)
 * - 자유 대본 모드 지원 (STT 변환 및 저장)
 * - 도움말 및 제한 알림 모달
 *
 * @dependency voiceStore - 목소리 샘플 및 대본 관리
 * @dependency useAudioRecorder - 오디오 녹음 및 Blob 생성 로직
 * @dependency modalStore - 알림 메시지 표시
 */
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useModalStore } from '@/stores/modal';
import { useVoiceStore } from '@/stores/voice';
import { useAudioRecorder } from '@/composables/useAudioRecorder';
import IconBack from '@/components/icons/IconBack.vue';
import { Logger } from '@/services/logger';

const router = useRouter();
const route = useRoute();
const modalStore = useModalStore();
const voiceStore = useVoiceStore();

const isInitialSetup = computed(() => route.query.flow === 'initial');

// 상태
/**
 * @type {Ref<string>} 현재 녹음 모드 ('script': 대본, 'free': 자유 대화)
 */
const currentMode = ref('script'); // 'script' | 'free'

/**
 * @type {Ref<Object|null>} 선택된 스크립트 샘플 객체
 */
const selectedSample = ref(null);

/**
 * @type {Ref<boolean>} 도움말 모달 표시 여부
 */
const showHelpModal = ref(false);

// 녹음기 Composables (필요 시 별도 인스턴스, 또는 재사용)
// 모드가 배타적이므로 하나의 인스턴스 사용 가능
/**
 * @type {Object} 오디오 녹음 관련 상태 및 함수
 * @property {Ref<boolean>} isRecording - 녹음 진행 중 여부
 * @property {Ref<number>} recordingDuration - 현재 녹음 길이(초)
 * @property {Ref<Blob>} recordedBlob - 녹음된 오디오 데이터 (Blob)
 * @property {Function} startRecording - 녹음 시작 함수
 * @property {Function} stopRecording - 녹음 중지 함수
 */
const {
  isRecording,
  recordingDuration,
  recordedBlob,
  audioUrl,
  error: recorderError,
  startRecording,
  stopRecording,
  cleanup: cleanupRecorder,
} = useAudioRecorder();

// 자유 대화 상태
/**
 * @type {Ref<string>} STT 변환된 텍스트
 */
const transcribedText = ref('');

/**
 * @type {Ref<boolean>} STT 변환 실패 여부
 */
const sttError = ref(false);

/**
 * @type {Ref<boolean>} 자유 대화 저장 완료 여부
 */
const isFreeTalkSaved = ref(false);

/**
 * @type {Ref<boolean>} 녹음 제한(6개) 초과 알림 모달 표시 여부
 */
const showLimitModal = ref(false);

// 계산된 속성 (Computeds)
/**
 * @type {ComputedRef<Array>} 대본 목록
 */
const voiceSamples = computed(() => voiceStore.scripts);

/**
 * @type {ComputedRef<boolean>} 로딩 중 여부
 */
const isLoading = computed(() => voiceStore.loading);

/**
 * @type {ComputedRef<number>} 녹음이 완료된 샘플 개수
 */
const completedCount = computed(() =>
  Math.max(voiceSamples.value.filter((s) => s.isRecorded).length, voiceStore.sampleCount()),
);

// 감시자 (Watchers)
watch(currentMode, (newMode) => {
  if (isRecording.value) {
    stopRecording();
  }
  cleanupRecorder();
  selectedSample.value = null;

  if (newMode === 'script') {
    transcribedText.value = '';
  }
  isFreeTalkSaved.value = false;
});

// 라이프사이클
onMounted(async () => {
  await voiceStore.fetchScripts();
  await voiceStore.fetchStatus();
});

// 메서드

/**
 * 녹음 모달 열기 핸들러
 * 이미 녹음된 문장이거나 제한 개수를 초과한 경우 경고를 표시합니다.
 * @param {Object} sample - 선택한 대본 샘플
 */
const openRecorder = async (sample) => {
  const currentCount = voiceStore.sampleCount();
  if (sample.isRecorded) {
    alert(
      '이미 녹음이 완료된 문장입니다. 수정이 필요한 경우 기존 목소리를 삭제하고 다시 등록해 주세요.',
    );
    return;
  } else if (currentCount >= 6) {
    showLimitModal.value = true;
    return;
  }

  selectedSample.value = sample;
  cleanupRecorder();
};

/**
 * 녹음 시작/중지 토글 (대본 모드)
 */
const toggleRecord = async () => {
  if (isRecording.value) {
    await stopRecording();
  } else {
    await startRecording();
  }
};

/**
 * 녹음 파일 저장 (대본 모드)
 * 녹음 길이를 검증하고 서버에 업로드합니다.
 */
const saveRecording = async () => {
  if (!recordedBlob.value || !selectedSample.value) return;
  if (recordingDuration.value < 3 || recordingDuration.value > 10) {
    alert('녹음 길이는 3초 이상 10초 이하여야 합니다.');
    return;
  }
  try {
    await voiceStore.uploadSample(
      recordedBlob.value,
      selectedSample.value.id,
      parseFloat(recordingDuration.value.toFixed(1)),
    );
    selectedSample.value = null; // 성공 시 모달 닫기
  } catch (error) {
    alert(`저장 실패: ${error.message}`);
  }
};

// useAudioRecorder를 재사용한 자유 대화 로직
/**
 * 녹음 시작/중지 토글 (자유 대화 모드)
 * 녹음 중지 시 자동으로 STT 변환을 요청합니다.
 */
const toggleFreeTalkRecord = async () => {
  if (isRecording.value) {
    const blob = await stopRecording();

    if (blob) {
      try {
        sttError.value = false;
        transcribedText.value = await voiceStore.transcribeAudio(blob);
      } catch (e) {
        sttError.value = true;
        transcribedText.value = '';
      }
    }
  } else {
    await startRecording();
  }
};

/**
 * 자유 대화 녹음 저장
 * STT 변환 텍스트와 함께 오디오를 업로드합니다.
 */
const saveFreeTalk = async () => {
  if (!recordedBlob.value || !transcribedText.value) return;

  if (recordingDuration.value < 3 || recordingDuration.value > 10) {
    alert('녹음 길이는 3초 이상 10초 이하여야 합니다.');
    return;
  }
  try {
    await voiceStore.uploadSample(
      recordedBlob.value,
      null,
      parseFloat(recordingDuration.value.toFixed(1)),
      transcribedText.value,
    );
    isFreeTalkSaved.value = true;
    alert('자유 대본이 저장되었습니다.');
  } catch (e) {
    alert(`저장 실패: ${e.message}`);
  }
};

/**
 * 자유 대화 초기화 (재녹음 준비)
 */
const resetFreeTalk = () => {
  transcribedText.value = '';
  sttError.value = false;
  isFreeTalkSaved.value = false;
  cleanupRecorder();
};

const goToSettings = () => router.push('/settings/voice');

// 초기 설정 핸들러
const handleSkip = () => router.push('/home');
const handleCompleteSetup = () => router.push('/home');
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
@keyframes slide-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
.animate-slide-up {
  animation: slide-up 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
</style>
