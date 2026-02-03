<template>
  <div class="bg-background-light min-h-screen flex flex-col relative pb-20">
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background-light/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex items-center">
        <button @click="$router.back()" class="p-2 -ml-2 rounded-full hover:bg-gray-100 transition-colors">
          <IconBack class="text-slate-600" />
        </button>
        <h1 class="flex-1 text-center text-xl font-bold text-slate-900 mr-8">목소리 등록</h1>
      </div>
    </header>

    <main class="flex-1 px-6 pt-4 space-y-6">
      
      <!-- Progress Card -->
      <div class="bg-white rounded-3xl p-6 shadow-sm border border-slate-100">
         <div class="flex justify-between items-end mb-4">
            <div>
                <p class="text-sm text-slate-500 mb-1">현재 진행 상황</p>
                <h2 class="text-2xl font-bold text-slate-900">{{ completedCount }} / {{ voiceSamples.length }}</h2>
            </div>
            <div class="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-primary">mic</span>
            </div>
         </div>
         <div class="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
            <div class="bg-primary h-full rounded-full transition-all duration-500" :style="{ width: `${progressPercentage}%` }"></div>
         </div>
      </div>

      <!-- Sample List -->
      <div class="space-y-4">
        <h3 class="text-lg font-bold text-slate-900">녹음 문장</h3>
        
        <div v-for="(sample, index) in voiceSamples" :key="sample.id" 
             class="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 flex items-center justify-between active:scale-[0.98] transition-all"
             @click="openRecorder(sample)">
            <div class="flex-1 mr-4">
                <div class="flex items-center gap-2 mb-1">
                    <span class="text-xs font-bold text-primary px-2 py-0.5 bg-primary/10 rounded-md">문장 {{ index + 1 }}</span>
                    <span v-if="sample.isRecorded" class="text-xs font-bold text-green-600 flex items-center">
                        <span class="material-symbols-outlined text-[14px] mr-1">check_circle</span> 완료
                    </span>
                </div>
                <p class="text-slate-700 font-medium line-clamp-1">{{ sample.text }}</p>
            </div>
            <button class="w-10 h-10 rounded-full flex items-center justify-center transition-colors"
                :class="sample.isRecorded ? 'bg-green-50 text-green-600' : 'bg-slate-50 text-slate-400'">
                <span class="material-symbols-outlined">{{ sample.isRecorded ? 'play_arrow' : 'mic_none' }}</span>
            </button>
        </div>
      </div>
    </main>

    <!-- Onboarding Footer -->
    <footer v-if="isInitialSetup" class="fixed bottom-0 left-0 right-0 p-6 bg-white/80 backdrop-blur-md border-t border-slate-100 flex gap-4 z-40">
        <button @click="handleSkip" class="flex-1 py-4 px-6 rounded-2xl bg-slate-100 text-slate-600 font-bold hover:bg-slate-200 transition-all">
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

    <!-- Recorder Modal (Mock) -->
    <div v-if="selectedSample" class="fixed inset-0 z-50 flex items-end">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="selectedSample = null"></div>
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

            <!-- Visualizer Mock -->
            <div class="flex justify-center items-center gap-1 h-16 mb-10">
                 <div v-for="n in 20" :key="n" 
                      class="w-1.5 bg-primary rounded-full animate-bounce"
                      :style="{ height: `${Math.random() * 100}%`, animationDelay: `${n * 0.05}s` }">
                 </div>
            </div>

            <div class="flex items-center justify-center gap-6">
                <button @click="selectedSample = null" class="w-14 h-14 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center">
                    <span class="material-symbols-outlined">close</span>
                </button>
                <button @click="toggleRecord" class="w-20 h-20 rounded-full bg-red-500 text-white flex items-center justify-center shadow-lg shadow-red-500/30 active:scale-95 transition-all">
                     <span class="material-symbols-outlined text-4xl">{{ isRecording ? 'stop' : 'mic' }}</span>
                </button>
                <button v-if="!isRecording && selectedSample.isRecorded" class="w-14 h-14 rounded-full bg-primary/10 text-primary flex items-center justify-center">
                    <span class="material-symbols-outlined">check</span>
                </button>
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useModalStore } from '@/stores/modal';
import IconBack from '@/components/icons/IconBack.vue';

const router = useRouter();
const route = useRoute();
const modalStore = useModalStore();

const isInitialSetup = computed(() => route.query.flow === 'initial');

// Mock Data
const voiceSamples = ref([
    { id: 1, text: "안녕하세요, 저는 김철수입니다. 만나서 반갑습니다.", isRecorded: false },
    { id: 2, text: "오늘 날씨가 참 좋네요. 산책이라도 다녀올까요?", isRecorded: false },
    { id: 3, text: "밥은 먹었니? 언제나 건강 챙기고 아프지 마라.", isRecorded: false },
    { id: 4, text: "사랑하는 우리 딸, 항상 응원한다.", isRecorded: false },
    { id: 5, text: "도움이 필요하면 언제든 말하렴.", isRecorded: false },
]);

const selectedSample = ref(null);
const isRecording = ref(false);

const completedCount = computed(() => voiceSamples.value.filter(s => s.isRecorded).length);
const progressPercentage = computed(() => (completedCount.value / voiceSamples.value.length) * 100);

const openRecorder = (sample) => {
    selectedSample.value = sample;
    isRecording.value = false;
};

const toggleRecord = () => {
    if (isRecording.value) {
        // Stop recording
        isRecording.value = false;
        if (selectedSample.value) {
            selectedSample.value.isRecorded = true;
            setTimeout(() => {
                selectedSample.value = null; // Close modal after short delay
            }, 500);
        }
    } else {
        // Start recording
        isRecording.value = true;
    }
};

const handleSkip = async () => {
    const confirmed = await modalStore.openConfirm(
        "목소리 등록을 건너뛰시겠습니까?",
        "목소리를 등록하면 가족들이 회원님의 목소리로 알림을 받을 수 있어요."
    );
    
    if (confirmed) {
        router.push('/setup-complete');
    }
};

const handleCompleteSetup = () => {
    router.push('/setup-complete');
};
</script>

<style scoped>
/* Added padding for bottom fixed button */
.min-h-screen {
    padding-bottom: 5rem;
}

.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}

@keyframes slide-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
.animate-slide-up {
  animation: slide-up 0.3s ease-out;
}
.word-keep-all {
    word-break: keep-all;
}
</style>
