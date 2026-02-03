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
             class="p-5 rounded-2xl shadow-sm border flex items-center justify-between active:scale-[0.98] transition-all"
             :class="sample.isRecorded ? 'bg-green-50/50 border-green-100' : 'bg-white border-slate-100'"
             @click="openRecorder(sample)">
            <div class="flex-1 mr-4">
                <div class="flex items-center gap-2 mb-1">
                    <span class="text-xs font-bold px-2 py-0.5 rounded-md"
                          :class="sample.isRecorded ? 'bg-green-100 text-green-700' : 'bg-primary/10 text-primary'">
                        문장 {{ index + 1 }}
                    </span>
                    <span v-if="sample.isRecorded" class="text-xs font-bold text-green-600 flex items-center">
                        <span class="material-symbols-outlined text-[14px] mr-1">check_circle</span> 완료
                    </span>
                </div>
                <p class="text-slate-700 font-medium line-clamp-1">{{ sample.text }}</p>
            </div>
            <button class="w-10 h-10 rounded-full flex items-center justify-center transition-colors"
                :class="sample.isRecorded ? 'bg-green-50 text-green-600' : 'bg-slate-50 text-slate-400'">
                <span class="material-symbols-outlined">{{ sample.isRecorded ? 'refresh' : 'mic_none' }}</span>
            </button>
        </div>

        <button v-if="completedCount >= 1" 
                @click="goToSettings"
                class="w-full py-4 mt-8 rounded-2xl bg-primary text-white font-bold text-lg shadow-lg shadow-primary/30 active:scale-95 transition-all flex items-center justify-center gap-2">
            <span>목소리 설정으로 이동</span>
            <span class="material-symbols-outlined">arrow_forward</span>
        </button>
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
                <button v-if="!isRecording && recordedBlob" @click="saveRecording" class="w-14 h-14 rounded-full bg-primary/10 text-primary flex items-center justify-center animate-bounce-small">
                    <span class="material-symbols-outlined">check</span>
                </button>
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import * as voiceService from '@/services/voiceService';

const router = useRouter();
const route = useRoute();
const modalStore = useModalStore();

const isInitialSetup = computed(() => route.query.flow === 'initial');

// State
const voiceSamples = ref([]);
const selectedSample = ref(null);
const isRecording = ref(false);
const isLoading = ref(false);
const recorder = ref(null);
const audioChunks = ref([]);
const audioUrl = ref(null);
const recordingDuration = ref(0);
const recordingTimer = ref(null);
const recordedBlob = ref(null);

// Computeds
const completedCount = computed(() => Math.max(voiceSamples.value.filter(s => s.isRecorded).length, serverSampleCount.value));
const progressPercentage = computed(() => {
    if (voiceSamples.value.length === 0) return 0;
    return Math.min((completedCount.value / voiceSamples.value.length) * 100, 100);
});

// Lifecycle
onMounted(async () => {
    await fetchScripts();
});

onUnmounted(() => {
    stopStream();
});

// Methods
const serverSampleCount = ref(0); // [NEW] Count from backend

// ... existing code ...

const fetchScripts = async () => {
    try {
        isLoading.value = true;
        const scripts = await voiceService.getScripts();
        // Backend returns: { id, content, scriptOrder }
        // We map it to our UI model
        voiceSamples.value = scripts.map(s => ({
            id: s.id,
            text: s.content,
            isRecorded: false // Initial state
        }));
        
        // Status Check
        const statusData = await voiceService.getVoiceStatus();
        if (statusData) {
             console.log("Status Data:", statusData);
             serverSampleCount.value = statusData.sampleCount || 0;
             
             // Sync 'isRecorded' state from server samples
             if (statusData.samples && Array.isArray(statusData.samples)) {
                 statusData.samples.forEach(sample => {
                     // 1. Try to match by explicit scriptId
                     let matchedScriptId = sample.scriptId;

                     // 2. Fallback: Parse testAudioUrl for "script_1" pattern
                     if (!matchedScriptId && sample.testAudioUrl) {
                         const match = sample.testAudioUrl.match(/script_(\d+)/i);
                         if (match && match[1]) {
                             matchedScriptId = parseInt(match[1]);
                         }
                     }

                     // 3. Fallback: Parse nickname "Sample {id}"
                     if (!matchedScriptId && sample.nickname) {
                         const match = sample.nickname.match(/Sample\s+(\d+)/i);
                         if (match && match[1]) {
                             matchedScriptId = parseInt(match[1]);
                         }
                     }
                     
                     if (matchedScriptId) {
                         const script = voiceSamples.value.find(s => String(s.id) === String(matchedScriptId));
                         if (script) {
                             script.isRecorded = true;
                         }
                     }
                 });
             }
        }
    } catch (error) {
        console.error("Failed to load scripts:", error);
    } finally {
        isLoading.value = false;
    }
};

const openRecorder = async (sample) => {
    // Check if max samples reached (6 or more) AND we are recording a new script (unlikely here as scripts are fixed)
    // Actually, logic is: Re-recording a script = New Sample. 
    // If it replaces old sample => Count stays same.
    // If it keeps old sample (because it was representative) => Count +1.
    // Max limit is 6 (1 active rep + 5 new).
    
    // Check current count
    try {
        const status = await voiceService.getVoiceStatus();
        const currentCount = status.sampleCount;
        
        // If current script is already recorded (locally known), warn user
        if (sample.isRecorded) {
            if (!confirm("이미 녹음된 문장입니다. 다시 녹음하시겠습니까?\n(새로운 모델이 생성됩니다)")) {
                return;
            }
        } else {
             // If not recorded yet, but global count is high (e.g. 5 or 6).
             // If 6, we can't add more.
             if (currentCount >= 6) {
                 alert("목소리 모델은 최대 6개까지만 저장할 수 있습니다.\n설정 페이지에서 불필요한 모델을 삭제해주세요.");
                 return;
             }
        }
    } catch (e) {
        console.error(e);
    }

    selectedSample.value = sample;
    isRecording.value = false;
    audioUrl.value = null;
    recordedBlob.value = null;
    recordingDuration.value = 0;
};
const stopStream = () => {
    if (recorder.value && recorder.value.stream) {
        recorder.value.stream.getTracks().forEach(track => track.stop());
    }
    if (recordingTimer.value) {
        clearInterval(recordingTimer.value);
    }
};

const toggleRecord = async () => {
    if (isRecording.value) {
        // Stop Loop
        if (recorder.value && recorder.value.state !== 'inactive') {
            recorder.value.stop();
            stopStream();
        }
        isRecording.value = false;
    } else {
        // Start Loop
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            audioChunks.value = [];
            
            // Try to use a mime type that is widely supported. 
            // Ideally we want 'audio/wav', but browsers usually support 'audio/webm'
            const mimeType = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '';
            
            const mediaRecorder = new MediaRecorder(stream, { mimeType });
            
            mediaRecorder.ondataavailable = (event) => {
                if (event.data.size > 0) {
                    audioChunks.value.push(event.data);
                }
            };

            mediaRecorder.onstop = () => {
                const blob = new Blob(audioChunks.value, { type: mimeType || 'audio/webm' });
                recordedBlob.value = blob;
                audioUrl.value = URL.createObjectURL(blob);
                
                // Validate duration
                if (recordingDuration.value < 3) {
                    alert("녹음은 최소 3초 이상이어야 합니다.");
                    audioUrl.value = null; // Reset
                    return;
                }
            };

            mediaRecorder.start();
            recorder.value = mediaRecorder;
            isRecording.value = true;
            recordingDuration.value = 0;
            
            recordingTimer.value = setInterval(() => {
                recordingDuration.value += 0.1;
            }, 100);

        } catch (err) {
            console.error("Error accessing microphone:", err);
            alert("마이크 접근 권한이 필요합니다.");
        }
    }
};

const saveRecording = async () => {
    if (!recordedBlob.value || !selectedSample.value) return;
    
    if (recordingDuration.value < 3 || recordingDuration.value > 10) {
        alert("녹음 길이는 3초 이상 10초 이하여야 합니다.");
        return;
    }

    try {
        isLoading.value = true;
        
        await voiceService.uploadVoiceSample(
            recordedBlob.value, 
            selectedSample.value.id, 
            parseFloat(recordingDuration.value.toFixed(1))
        );

        selectedSample.value.isRecorded = true;
        
        // [NEW] Update server count locally to reflect change immediately
        // Note: Logic is complex (replace vs add). 
        // If we assumed replacement: count same. 
        // If we assumed add: count + 1. 
        // Safe bet: Fetch status again or just accept user will see button if already >= 5.
        // Let's refetch status to be sure.
        try {
            const status = await voiceService.getVoiceStatus();
            serverSampleCount.value = status.sampleCount;
        } catch(e) {}

        alert("저장되었습니다.");
        
        setTimeout(() => {
            selectedSample.value = null; 
        }, 500);

    } catch (error) {
        console.error("Upload failed:", error);
        alert("업로드에 실패했습니다.\n" + error.message);
    } finally {
        isLoading.value = false;
    }
};

const goToSettings = () => {
    router.push('/voice-settings');
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
