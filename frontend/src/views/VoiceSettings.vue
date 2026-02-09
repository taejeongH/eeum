<template>
  <div class="bg-slate-900 min-h-screen flex flex-col relative pb-20 overflow-hidden">
    <!-- Background Effects -->
    <div class="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
        <div class="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/20 rounded-full blur-[100px]"></div>
        <div class="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-blue-500/10 rounded-full blur-[100px]"></div>
    </div>

    <!-- Header -->
    <header class="sticky top-0 z-10 px-6 pt-12 pb-6 flex items-center justify-between">
      <button @click="$router.back()" class="p-2 -ml-2 rounded-full hover:bg-white/10 text-white transition-colors">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <h1 class="text-xl font-bold text-white">Voice Mixer</h1>
      <div class="w-10"></div> <!-- Spacer -->
    </header>

    <main class="flex-1 px-6 relative z-0 flex flex-col items-center">
      <!-- Info Text -->
      <div class="text-center mb-10">
        <p class="text-slate-400 text-sm mb-1">나만의 목소리 믹서</p>
        <h2 class="text-2xl font-bold text-white">원하는 목소리를<br/>선택하고 설정해보세요</h2>
      </div>

      <!-- Mixer Grid -->
      <div class="grid grid-cols-2 gap-x-8 gap-y-10 w-full max-w-sm">
        <!-- Sample Nodes -->
        <div v-for="sample in samples" :key="sample.id" class="flex flex-col items-center group">
            <!-- Knob/Button -->
            <div class="relative w-32 h-32 rounded-full flex items-center justify-center transition-all duration-300"
                 :class="[
                    sample.id === representativeId ? 'ring-4 ring-primary ring-offset-4 ring-offset-slate-900 shadow-[0_0_30px_rgba(255,111,97,0.4)]' : 'hover:scale-105',
                    isPlaying && currentAudio === sample.testAudioUrl ? 'scale-110 shadow-[0_0_40px_rgba(255,255,255,0.2)]' : '',
                    !sample.testAudioUrl ? 'cursor-not-allowed opacity-70' : 'cursor-pointer'
                 ]"
                 @click="sample.testAudioUrl ? handleSampleClick(sample) : null">
                
                <!-- Background Gradient -->
                <div class="absolute inset-0 rounded-full bg-gradient-to-br from-slate-700 to-slate-800 shadow-inner border border-slate-600/50"></div>
                
                <!-- Rotating Indicator (Mock) -->
                <div class="absolute inset-2 rounded-full border border-white/5 border-t-white/30 rotate-45"></div>

                <!-- Icon/Status -->
                <div class="relative z-10 flex flex-col items-center justify-center">
                    <template v-if="!sample.testAudioUrl">
                        <span class="material-symbols-outlined text-4xl text-white/50 animate-spin">progress_activity</span>
                        <span class="text-[10px] font-bold text-slate-400 mt-1 uppercase tracking-wider">제작중</span>
                    </template>
                    <template v-else>
                        <span class="material-symbols-outlined text-4xl text-white/90 drop-shadow-md transition-all"
                              :class="isPlaying && currentAudio === sample.testAudioUrl ? 'text-primary scale-110' : ''">
                            {{ isPlaying && currentAudio === sample.testAudioUrl ? 'equalizer' : 'graphic_eq' }}
                        </span>
                        <span v-if="sample.id === representativeId" class="text-[10px] font-bold text-primary mt-1 uppercase tracking-wider">Main</span>
                    </template>
                </div>

                <!-- Playing Ripple Effect -->
                <div v-if="isPlaying && currentAudio === sample.testAudioUrl" class="absolute inset-0 rounded-full border-2 border-primary/50 animate-ping"></div>
            </div>

            <!-- Nickname & Edit -->
            <div class="mt-4 flex items-center gap-2">
                <div class="flex flex-col items-center">
                    <div class="flex items-center gap-1 group/edit cursor-pointer" @click.stop="openEditModal(sample)">
                        <span class="text-white font-medium text-lg truncate max-w-[100px]">{{ sample.nickname || `Voice ${sample.id}` }}</span>
                        <span class="material-symbols-outlined text-slate-500 text-sm group-hover/edit:text-white transition-colors">edit</span>
                    </div>
                </div>
            </div>
        </div>

      </div>
    </main>

    <!-- Bottom Sheet / Action Modal -->
    <div v-if="selectedSample" class="fixed inset-0 z-50 flex items-end">
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="selectedSample = null"></div>
        <div class="relative w-full bg-slate-800 rounded-t-[2rem] p-8 pb-10 animate-slide-up border-t border-white/10">
            <div class="flex justify-center mb-6">
                <div class="w-12 h-1.5 bg-slate-600 rounded-full"></div>
            </div>

            <div class="flex items-center gap-4 mb-8">
                <div class="w-16 h-16 rounded-full bg-slate-700 flex items-center justify-center text-2xl text-white font-bold">
                    {{ (selectedSample.nickname || '')[0] }}
                </div>
                <div>
                    <h3 class="text-xl font-bold text-white">{{ selectedSample.nickname }}</h3>
                    <p class="text-slate-400 text-sm">{{ formatDate(selectedSample.createdAt) }}</p>
                </div>
            </div>

            <div class="space-y-3">
                 <button @click="togglePlay(selectedSample)" 
                        class="w-full py-4 rounded-xl flex items-center justify-center gap-3 font-bold transition-all"
                        :class="isPlaying && currentAudio === selectedSample.testAudioUrl ? 'bg-primary text-white' : 'bg-white text-slate-900'">
                    <span class="material-symbols-outlined">{{ isPlaying && currentAudio === selectedSample.testAudioUrl ? 'stop' : 'play_arrow' }}</span>
                    {{ isPlaying && currentAudio === selectedSample.testAudioUrl ? '재생 중지' : '미리 듣기' }}
                </button>

                <button v-if="selectedSample.id !== representativeId" 
                        @click="setRepresentative(selectedSample)"
                        class="w-full py-4 rounded-xl bg-slate-700 text-white font-bold hover:bg-slate-600">
                    대표 목소리로 설정
                </button>

                <button v-if="selectedSample.id !== representativeId" 
                        @click="confirmDelete(selectedSample)"
                        class="w-full py-4 rounded-xl border border-red-500/30 text-red-400 font-bold hover:bg-red-500/10">
                    삭제하기
                </button>
            </div>
        </div>
    </div>

    <!-- Edit Nickname Modal -->
    <div v-if="isEditing" class="fixed inset-0 z-[60] flex items-center justify-center px-6">
        <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="closeEditModal"></div>
        <div class="relative w-full max-w-sm bg-white rounded-2xl p-6 animate-scale-in">
            <h3 class="text-lg font-bold text-slate-900 mb-4">별칭 수정</h3>
            <input v-model="editNickname" 
                   type="text" 
                   class="w-full px-4 py-3 rounded-xl bg-slate-100 border-none outline-none focus:ring-2 focus:ring-primary mb-6 text-slate-900 font-medium"
                   placeholder="별명을 입력하세요 (예: 차분한 목소리)"
                   @keyup.enter="saveNickname">
            <div class="flex gap-3">
                <button @click="closeEditModal" class="flex-1 py-3 rounded-xl bg-slate-100 text-slate-600 font-bold">취소</button>
                <button @click="saveNickname" class="flex-1 py-3 rounded-xl bg-primary text-white font-bold">저장</button>
            </div>
        </div>
    </div>

    <audio ref="audioPlayer" @ended="onAudioEnded" class="hidden"></audio>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import * as voiceService from '@/services/voiceService';
import { Logger } from '@/services/logger';

const samples = ref([]);
const representativeId = ref(null);
const currentAudio = ref(null);
const isPlaying = ref(false);
const audioPlayer = ref(null);

const selectedSample = ref(null); // For action modal
const isEditing = ref(false);     // For nickname edit
const editSampleId = ref(null);
const editNickname = ref("");

onMounted(async () => {
    await loadData();
});

const loadData = async () => {
    try {
        // 1. Get Status (includes samples and rep ID)
        const statusData = await voiceService.getVoiceStatus();
        representativeId.value = statusData.representativeSampleId;
        
        // Use samples from status directly
        // Backend returns samples array in status
        const fetchedSamples = statusData.samples || [];
        
        // Check if any sample lacks testAudioUrl, if so trigger generation in background
        // but display list immediately
        const needsGeneration = fetchedSamples.some(s => !s.testAudioUrl);
        if (needsGeneration) {
            voiceService.generateTestAudio().catch(err => Logger.error("음성 생성 트리거 실패:", err));
            // We don't await loop here, just show "Processing" state
        }
        
        samples.value = fetchedSamples;
    } catch (error) {
        Logger.error("목소리 설정 로드 실패:", error);
    }
};

const formatDate = (isoString) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    return date.toLocaleDateString();
};

const handleSampleClick = (sample) => {
    // If playing this sample, stop it? Or open modal?
    // UX: Click opens modal for actions. Playing is one action.
    selectedSample.value = sample;
};

const togglePlay = (sample) => {
    if (!sample.testAudioUrl) {
        alert("테스트 음성이 준비되지 않았습니다.");
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

const onAudioEnded = () => {
    isPlaying.value = false;
};

const setRepresentative = async (sample) => {
    try {
        await voiceService.setRepresentativeSample(sample.id);
        representativeId.value = sample.id;
        selectedSample.value = null; // Close modal
    } catch (error) {
        Logger.error("대표 목소리 설정 실패:", error);
        alert("설정에 실패했습니다.");
    }
};

const confirmDelete = async (sample) => {
    if (!confirm("정말 삭제하시겠습니까?")) return;
    
    try {
        await voiceService.deleteSample(sample.id);
        await loadData();
        selectedSample.value = null;
    } catch (error) {
        Logger.error("삭제 실패:", error);
        alert("삭제에 실패했습니다.");
    }
};

// Edit Logic
const openEditModal = (sample) => {
    editSampleId.value = sample.id;
    editNickname.value = sample.nickname || '';
    isEditing.value = true;
};

const closeEditModal = () => {
    isEditing.value = false;
    editSampleId.value = null;
    editNickname.value = "";
};

const saveNickname = async () => {
    if (!editNickname.value.trim()) {
        alert("별명을 입력해주세요.");
        return;
    }
    
    try {
        await voiceService.updateNickname(editSampleId.value, editNickname.value);
        // Update local list directly to reflect change fast
        const target = samples.value.find(s => s.id === editSampleId.value);
        if (target) target.nickname = editNickname.value;
        
        closeEditModal();
    } catch (error) {
        Logger.error("별명 수정 실패:", error);
        alert("수정에 실패했습니다.");
    }
};

</script>

<style scoped>
@keyframes slide-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
.animate-slide-up {
  animation: slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes scale-in {
  from { transform: scale(0.9); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
.animate-scale-in {
  animation: scale-in 0.2s ease-out;
}
</style>
