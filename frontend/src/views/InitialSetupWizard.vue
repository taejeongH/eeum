<template>
  <div class="min-h-screen bg-white font-sans flex flex-col">
    <!-- 상단 인디케이터 헤더 -->
    <header class="sticky top-0 z-50 bg-white/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="max-w-md mx-auto">
        <div class="flex items-center justify-between mb-4">
          <button
            v-if="currentStep > 1"
            @click="prevStep"
            class="p-2 -ml-2 text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 19l-7-7 7-7"
              />
            </svg>
          </button>
          <div v-else class="w-10"></div>

          <div class="flex gap-1.5">
            <div
              v-for="step in totalSteps"
              :key="step"
              class="h-1.5 rounded-full transition-all duration-500"
              :class="step <= currentStep ? 'w-6 bg-[var(--color-primary)]' : 'w-2 bg-gray-100'"
            ></div>
          </div>

          <div class="w-10"></div>
        </div>
      </div>
    </header>

    <!-- 메인 콘텐츠 영역 -->
    <main class="flex-1 max-w-md mx-auto w-full px-7 pt-4 pb-32 flex flex-col">
      <transition name="fade-slide" mode="out-in">
        <div :key="currentStep" class="flex-1">
          <!-- Step 1: 기본 정보 -->
          <div v-if="currentStep === 1" class="space-y-10">
            <div class="space-y-3">
              <h2 class="text-3xl font-black text-gray-900 leading-tight">
                반가워요!<br />누구신지 알려주세요.
              </h2>
              <p class="text-gray-500 font-medium">
                가족들이 회원님임을 알 수 있도록 입력해주세요.
              </p>
            </div>

            <div class="space-y-6">
              <div class="space-y-2">
                <label class="block text-sm font-bold text-gray-400"
                  >성함 <span class="text-red-400">*</span></label
                >
                <input
                  v-model="form.name"
                  type="text"
                  placeholder="성함을 입력해주세요"
                  class="eeum-input !h-14 !text-lg !rounded-2xl"
                />
              </div>
              <div class="space-y-2">
                <label class="block text-sm font-bold text-gray-400"
                  >전화번호 <span class="text-red-400">*</span></label
                >
                <input
                  v-model="form.phone"
                  type="tel"
                  placeholder="010-0000-0000"
                  maxlength="13"
                  class="eeum-input !h-14 !text-lg !rounded-2xl"
                />
              </div>
            </div>
          </div>

          <!-- Step 2: 성별 및 생년월일 -->
          <div v-if="currentStep === 2" class="space-y-10">
            <div class="space-y-3">
              <h2 class="text-3xl font-black text-gray-900 leading-tight">
                성별과 생일도<br />궁금해요.
              </h2>
              <p class="text-gray-500 font-medium">맞춤 관리를 위해 필요한 정보입니다.</p>
            </div>

            <div class="space-y-8">
              <div class="space-y-3">
                <label class="block text-sm font-bold text-gray-400"
                  >성별 <span class="text-red-400">*</span></label
                >
                <div class="flex gap-3">
                  <button
                    @click="form.gender = 'M'"
                    :class="
                      form.gender === 'M'
                        ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)] shadow-lg shadow-orange-100'
                        : 'bg-gray-50 border-gray-100 text-gray-400'
                    "
                    class="flex-1 h-14 rounded-2xl border font-bold transition-all active:scale-95"
                  >
                    남성
                  </button>
                  <button
                    @click="form.gender = 'F'"
                    :class="
                      form.gender === 'F'
                        ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)] shadow-lg shadow-orange-100'
                        : 'bg-gray-50 border-gray-100 text-gray-400'
                    "
                    class="flex-1 h-14 rounded-2xl border font-bold transition-all active:scale-95"
                  >
                    여성
                  </button>
                </div>
              </div>

              <div class="space-y-2">
                <label class="block text-sm font-bold text-gray-400"
                  >생년월일 <span class="text-red-400">*</span></label
                >
                <div class="flex gap-2">
                  <input
                    v-model="birth.year"
                    type="text"
                    inputmode="numeric"
                    placeholder="연도"
                    maxlength="4"
                    class="eeum-input flex-[1.4] text-center !h-14 !text-lg !rounded-2xl"
                  />
                  <input
                    v-model="birth.month"
                    type="text"
                    inputmode="numeric"
                    placeholder="월"
                    maxlength="2"
                    class="eeum-input flex-1 text-center !h-14 !text-lg !rounded-2xl"
                  />
                  <input
                    v-model="birth.day"
                    type="text"
                    inputmode="numeric"
                    placeholder="일"
                    maxlength="2"
                    class="eeum-input flex-1 text-center !h-14 !text-lg !rounded-2xl"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- Step 3: 주소 정보 -->
          <div v-if="currentStep === 3" class="space-y-10">
            <div class="space-y-3">
              <h2 class="text-3xl font-black text-gray-900 leading-tight">
                현재 어디에<br />살고 계신가요?
              </h2>
              <p class="text-gray-500 font-medium">가족 돌봄을 위해 주소가 필요합니다.</p>
            </div>

            <div class="space-y-6">
              <div @click="openAddressSearch" class="space-y-2 cursor-pointer">
                <label class="block text-sm font-bold text-gray-400"
                  >도로명 주소 <span class="text-red-400">*</span></label
                >
                <div
                  class="eeum-input !h-14 !rounded-2xl flex justify-between items-center"
                  :class="
                    form.address ? 'text-gray-800 border-[var(--color-primary)]' : 'text-gray-300'
                  "
                >
                  <span class="!text-lg font-bold">{{ form.address || '주소 검색하기' }}</span>
                  <span class="material-symbols-outlined text-gray-400">search</span>
                </div>
              </div>

              <div v-if="form.address" class="space-y-2 animate-fade-in">
                <label class="block text-sm font-bold text-[var(--color-primary)]">상세 주소</label>
                <input
                  v-model="form.detailAddress"
                  type="text"
                  placeholder="나머지 주소를 입력해주세요"
                  class="eeum-input !h-14 !text-lg !rounded-2xl"
                />
              </div>
            </div>
          </div>

          <!-- 단계 4: 목소리 등록 -->
          <div v-if="currentStep === 4" class="space-y-6">
            <div class="space-y-3">
              <h2 class="text-3xl font-black text-gray-900 leading-tight">
                가족들을 위한<br />목소리 등록
              </h2>
              <p class="text-gray-500 font-medium">따뜻한 목소리로 문장을 읽어주세요.</p>
            </div>

            <!-- 진행 상황 카드 -->
            <div class="bg-white rounded-3xl p-6 shadow-sm border border-slate-100">
              <div class="flex justify-between items-end mb-4">
                <div>
                  <p class="text-sm text-slate-500 mb-1">현재 진행 상황</p>
                  <h3 class="text-2xl font-bold text-slate-900">
                    {{ completedCount }} / {{ voiceSamples.length }}
                  </h3>
                </div>
                <div class="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center">
                  <span class="material-symbols-outlined text-[var(--color-primary)]">mic</span>
                </div>
              </div>
              <div class="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
                <div
                  class="bg-[var(--color-primary)] h-full rounded-full transition-all duration-500"
                  :style="{ width: `${progressPercentage}%` }"
                ></div>
              </div>
            </div>

            <!-- 녹음 문장 목록 -->
            <div class="space-y-3">
              <h3 class="text-lg font-bold text-slate-900">녹음 문장</h3>

              <div
                v-for="(sample, index) in voiceSamples"
                :key="sample.id"
                class="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex items-center justify-between active:scale-[0.98] transition-all cursor-pointer"
                @click="openRecorder(sample)"
              >
                <div class="flex-1 mr-4">
                  <div class="flex items-center gap-2 mb-1">
                    <span
                      class="text-xs font-bold text-[var(--color-primary)] px-2 py-0.5 bg-orange-50 rounded-md"
                      >문장 {{ index + 1 }}</span
                    >
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
                    sample.isRecorded ? 'play_arrow' : 'mic_none'
                  }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </main>

    <!-- 하단 고정 네비게이션 푸터 -->
    <footer
      class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 px-6 pt-5 pb-7 z-40 shadow-[0_-4px_24px_rgba(0,0,0,0.04)]"
    >
      <div class="max-w-md mx-auto">
        <div v-if="currentStep === 4" class="flex gap-4">
          <button
            @click="skipVoice"
            class="flex-1 h-16 rounded-2xl bg-gray-50 text-gray-400 font-bold hover:bg-gray-100 transition-all"
          >
            나중에 할게요
          </button>
          <button @click="nextStep" class="eeum-btn-primary flex-[1.5] !h-16 !text-xl !font-black">
            설정 완료 →
          </button>
        </div>
        <button
          v-else
          @click="nextStep"
          :disabled="!isCurrentStepValid"
          class="eeum-btn-primary !h-16 !text-xl !font-black"
        >
          <span v-if="isLoading">정보 저장 중...</span>
          <span v-else>{{ isLastProfileStep ? '저장 후 다음 단계로 →' : '다음 단계로 →' }}</span>
        </button>
      </div>
    </footer>

    <!-- 주소 검색 모달 -->
    <Teleport to="body">
      <div
        v-if="showAddressModal"
        class="fixed inset-0 z-[9999] overflow-y-auto bg-black/60 backdrop-blur-sm"
        @click="showAddressModal = false"
      >
        <div class="flex min-h-full items-center justify-center p-4">
          <div
            class="relative z-10 bg-white rounded-3xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col min-h-[500px] pointer-events-auto"
            role="dialog"
            aria-modal="true"
            @click.stop
          >
            <!-- 헤더 영역 -->
            <div
              class="p-5 border-b border-gray-100 flex justify-between items-center bg-gray-50 flex-shrink-0"
            >
              <h3 class="text-lg font-bold text-gray-800">주소 검색</h3>
              <button
                @click="showAddressModal = false"
                class="w-8 h-8 rounded-full bg-white text-gray-400 hover:text-gray-600 hover:bg-gray-100 flex items-center justify-center transition shadow-sm border border-gray-100"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M6 18L18 6M6 6l12 12"
                  ></path>
                </svg>
              </button>
            </div>
            <!-- 본문 영역 -->
            <div id="postcode-layer" class="w-full bg-white overflow-y-auto h-[500px]">
              <!-- 다음 주소 서비스가 여기에 임베드됩니다. -->
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 보이스 레코더 모달 -->
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

        <!-- 시각화 요소 (모형) -->
        <div class="flex justify-center items-center gap-1 h-16 mb-10">
          <div
            v-for="n in 20"
            :key="n"
            class="w-1.5 bg-[var(--color-primary)] rounded-full animate-bounce"
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
            v-if="!isRecording && selectedSample.isRecorded"
            class="w-14 h-14 rounded-full bg-green-50 text-green-600 flex items-center justify-center"
          >
            <span class="material-symbols-outlined">check</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { updateUserProfile } from '@/services/api';
import { useModalStore } from '@/stores/modal';
import { Logger } from '@/services/logger';

const router = useRouter();
const userStore = useUserStore();
const modalStore = useModalStore();

const currentStep = ref(1);
const totalSteps = 4;
const isLoading = ref(false);
const showAddressModal = ref(false);

const form = ref({
  name: '',
  phone: '',
  gender: 'M',
  address: '',
  detailAddress: '',
});

const birth = ref({
  year: '',
  month: '',
  day: '',
});

/** 목소리 등록을 위한 상태 관리 */
const voiceSamples = ref([
  { id: 1, text: '안녕하세요, 저는 김철수입니다. 만나서 반갑습니다.', isRecorded: false },
  { id: 2, text: '오늘 날씨가 참 좋네요. 산책이라도 다녀올까요?', isRecorded: false },
  { id: 3, text: '밥은 먹었니? 언제나 건강 챙기고 아프지 마라.', isRecorded: false },
  { id: 4, text: '사랑하는 우리 딸, 항상 응원한다.', isRecorded: false },
  { id: 5, text: '도움이 필요하면 언제든 말하렴.', isRecorded: false },
]);

const selectedSample = ref(null);
const isRecording = ref(false);

const completedCount = computed(() => voiceSamples.value.filter((s) => s.isRecorded).length);
const progressPercentage = computed(() => (completedCount.value / voiceSamples.value.length) * 100);

/** 전화번호 형식 자동 변환 (하이픈 추가) */
watch(
  () => form.value.phone,
  (newPhone) => {
    const digits = newPhone.replace(/\D/g, '');
    let formatted = '';
    if (digits.length <= 3) {
      formatted = digits;
    } else if (digits.length <= 7) {
      formatted = `${digits.slice(0, 3)}-${digits.slice(3)}`;
    } else {
      formatted = `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`;
    }
    form.value.phone = formatted;
  },
);

/** 단계별 입력 유효성 검사 */
const isCurrentStepValid = computed(() => {
  if (currentStep.value === 1) return form.value.name.length >= 2 && form.value.phone.length >= 12;
  if (currentStep.value === 2) return birth.value.year && birth.value.month && birth.value.day;
  if (currentStep.value === 3) return form.value.address.length > 5;
  return true;
});

const isLastProfileStep = computed(() => currentStep.value === 3);

/**
 * 다음 단계로 이동하거나 최종 저장을 처리합니다.
 */
const nextStep = async () => {
  if (!isCurrentStepValid.value) return;

  if (isLastProfileStep.value) {
    await handleProfileSave();
  } else if (currentStep.value === 4) {
    router.push('/setup-complete');
  } else {
    currentStep.value++;
  }
};

/**
 * 이전 단계로 이동합니다.
 */
const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--;
  }
};

/**
 * 목소리 등록을 건너뛰고 완료 페이지로 이동합니다.
 */
const skipVoice = async () => {
  const confirm = await modalStore.openConfirm(
    '목소리 등록을 나중에 하시겠습니까?',
    '지금 등록하지 않아도 프로필 설정에서 언제든지 등록할 수 있습니다.',
  );
  if (confirm) {
    router.push('/setup-complete');
  }
};

/**
 * 특정 문장의 녹음 모달을 엽니다.
 * @param {Object} sample
 */
const openRecorder = (sample) => {
  selectedSample.value = sample;
  isRecording.value = false;
};

/**
 * 녹음 시작/중지를 토글합니다.
 */
const toggleRecord = () => {
  if (isRecording.value) {
    stopRecording();
  } else {
    startRecording();
  }
};

/**
 * 녹음을 시작합니다. (Internal)
 */
const startRecording = () => {
  isRecording.value = true;
};

/**
 * 녹음을 중지하고 상태를 업데이트합니다.
 */
const stopRecording = () => {
  isRecording.value = false;
  if (selectedSample.value) {
    selectedSample.value.isRecorded = true;
    setTimeout(() => {
      selectedSample.value = null;
    }, 500);
  }
};

/**
 * 프로필 정보를 서버에 저장합니다.
 */
const handleProfileSave = async () => {
  isLoading.value = true;
  try {
    const formData = prepareProfileFormData();
    await updateUserProfile(formData);
    await userStore.fetchUser(true);

    currentStep.value = 4;
  } catch (error) {
    modalStore.openAlert('프로필 저장 중 오류가 발생했습니다.');
    Logger.error(error);
  } finally {
    isLoading.value = false;
  }
};

/**
 * 프로필 저장을 위한 FormData를 생성합니다.
 * @returns {FormData}
 */
const prepareProfileFormData = () => {
  const birthDate = `${birth.value.year}-${String(birth.value.month).padStart(2, '0')}-${String(birth.value.day).padStart(2, '0')}`;
  const fullAddress =
    form.value.address + (form.value.detailAddress ? ` ${form.value.detailAddress}` : '');

  const requestDto = {
    name: form.value.name,
    phone: form.value.phone,
    birthDate: birthDate,
    gender: form.value.gender,
    address: fullAddress,
  };

  const formData = new FormData();
  formData.append('request', new Blob([JSON.stringify(requestDto)], { type: 'application/json' }));
  return formData;
};

/**
 * 주소 검색 모달을 엽니다.
 */
const openAddressSearch = () => {
  showAddressModal.value = true;
};

/** 주소 검색 모달 표시 상태 감시 */
watch(showAddressModal, (isShown) => {
  if (isShown) {
    document.body.style.overflow = 'hidden';
    nextTick(() => {
      setTimeout(() => {
        const container = document.getElementById('postcode-layer');
        if (!window.daum || !window.daum.Postcode) {
          modalStore.openAlert('주소 검색 서비스를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.');
          showAddressModal.value = false;
          return;
        }
        if (container) {
          container.innerHTML = '';
          new window.daum.Postcode({
            oncomplete: (data) => {
              form.value.address = data.address;
              showAddressModal.value = false;
            },
            width: '100%',
            height: '100%',
          }).embed(container);
        }
      }, 100);
    });
  } else {
    document.body.style.overflow = '';
  }
});

onMounted(async () => {
  if (!userStore.profile) {
    await userStore.fetchUser();
  }

  if (userStore.profile) {
    form.value.name = userStore.profile.name || '';
    form.value.phone = userStore.profile.phone || '';
    form.value.gender = userStore.profile.gender || 'M';
    if (userStore.profile.birthDate) {
      const [y, m, d] = userStore.profile.birthDate.split('-');
      birth.value = { year: parseInt(y), month: parseInt(m), day: parseInt(d) };
    }
  }
});
</script>

<style scoped>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(30px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-30px);
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
  animation: slide-up 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes wave {
  0%,
  100% {
    transform: scaleY(1);
  }
  50% {
    transform: scaleY(2.5);
  }
}
.animate-wave {
  animation: wave 1s ease-in-out infinite;
  transform-origin: center;
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out forwards;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

input::-webkit-outer-spin-button,
input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.word-keep-all {
  word-break: keep-all;
}

.line-clamp-1 {
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  line-clamp: 1;
}

.material-symbols-outlined {
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}
</style>
