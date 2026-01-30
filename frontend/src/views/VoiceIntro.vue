<template>
  <div class="bg-background-light min-h-screen flex justify-center items-center">
    <div class="relative w-full h-full min-h-screen bg-background-light flex flex-col">
      
      <!-- Header -->
      <header class="flex items-center justify-between px-6 py-4 mt-6">
        <button @click="$router.push('/')" class="p-2 -ml-2 rounded-full hover:bg-gray-100 transition-colors">
          <svg class="w-6 h-6 text-slate-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div class="w-10"></div> 
      </header>

      <main class="flex flex-col items-center px-8 pt-6 flex-1">
        <div class="text-center mb-12">
          <p class="text-[var(--color-primary)] font-semibold mb-1 text-lg">안녕하세요!</p>
          <h1 class="text-3xl font-bold leading-tight text-gray-900">목소리 등록을 <br/>시작해볼까요?</h1>
        </div>

        <!-- Wave Animation Area -->
        <div class="relative flex-1 flex items-center justify-center w-full min-h-[300px]">
          <template v-if="isRecording">
             <div class="absolute w-72 h-72 rounded-full bg-[var(--color-primary)]/10 wave-animation"></div>
             <div class="absolute w-56 h-56 rounded-full bg-[var(--color-primary)]/20 wave-animation" style="animation-delay: 0.5s;"></div>
             <div class="absolute w-40 h-40 rounded-full bg-[var(--color-primary)]/30 wave-animation" style="animation-delay: 1s;"></div>
          </template>
          
          <div class="relative z-10 w-28 h-28 bg-white rounded-full flex items-center justify-center shadow-lg border-4 border-[var(--color-primary)]/20">
            <span class="material-symbols-rounded text-6xl text-[var(--color-primary)] leading-none">family_history</span>
          </div>
        </div>

        <!-- Action Area -->
        <div class="w-full flex flex-col items-center pb-12 mt-auto">
          <p class="text-slate-500 mb-8 text-base font-medium transition-all" :class="{'opacity-0': isRecording}">
             {{ isRecording ? '녹음 중...' : '버튼을 눌러 목소리 등록을 시작하세요' }}
          </p>
          
          <button 
            @click="toggleRecording"
            class="group relative w-24 h-24 bg-[var(--color-primary)] rounded-full flex items-center justify-center mic-shadow active:scale-95 transition-transform"
          >
            <span class="material-symbols-rounded text-5xl text-white">
                {{ isRecording ? 'stop' : 'mic' }}
            </span>
            <span v-if="isRecording" class="absolute inset-0 rounded-full border-4 border-[var(--color-primary)] animate-ping opacity-25"></span>
          </button>

          <!-- Guide Card -->
          <div class="mt-12 px-6 py-5 bg-orange-50 rounded-2xl border border-orange-100 w-full shadow-sm">
            <div class="flex gap-4">
              <span class="material-symbols-rounded text-[var(--color-primary)] text-2xl">info</span>
              <div>
                <p class="text-xs font-bold text-orange-900 uppercase tracking-wider mb-1">녹음 가이드</p>
                <p class="text-sm text-slate-600 leading-relaxed break-keep">
                    조용한 환경에서 진행해주세요. 휴대폰을 얼굴에서 약 25cm 정도 거리를 두고 말씀해주세요.
                </p>
              </div>
            </div>
          </div>
        </div>

      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const isRecording = ref(false);

const toggleRecording = () => {
    if (isRecording.value) {
        stopRecording();
    } else {
        startRecording();
    }
};

const startRecording = () => {
    isRecording.value = true;
    
    // Auto-stop simulation after 3 seconds and go to step 1
    setTimeout(() => {
        if(isRecording.value) stopRecording();
    }, 3000);
};

const stopRecording = () => {
    isRecording.value = false;
    // Navigate to actual registration steps
    router.push('/voice-register');
};
</script>

<style scoped>
.wave-animation {
    animation: pulse-wave 2s infinite ease-in-out;
}
@keyframes pulse-wave {
    0% { transform: scale(0.8); opacity: 0.4; }
    50% { transform: scale(1.1); opacity: 0.1; }
    100% { transform: scale(0.8); opacity: 0.4; }
}
.mic-shadow {
    box-shadow: 0 10px 25px -5px rgba(231, 111, 81, 0.4); /* Primary color shadow approx */
}
</style>
