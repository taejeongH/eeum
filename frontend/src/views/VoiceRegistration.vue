<template>
  <div class="bg-background-light min-h-screen flex justify-center items-center">
    <div class="relative w-full h-full min-h-screen bg-background-light flex flex-col">
      
      <!-- Header -->
      <header class="flex items-center justify-between px-6 py-4 mt-6">
        <button @click="$router.push('/')" class="w-10 h-10 flex items-center justify-center rounded-full bg-white hover:bg-gray-100 transition-colors shadow-sm">
          <span class="material-symbols-rounded text-slate-700">arrow_back_ios_new</span>
        </button>
        <div class="w-10"></div> 
      </header>

      <main class="flex flex-col items-center px-8 pt-6 flex-1">
        <div class="text-center mb-12">
          <p class="text-primary font-semibold mb-1 text-lg">Hello, Sarah!</p>
          <h1 class="text-3xl font-bold leading-tight text-gray-900">Let's set up your <br/>voice signature</h1>
        </div>

        <!-- Wave Animation Area -->
        <div class="relative flex-1 flex items-center justify-center w-full min-h-[300px]">
          <template v-if="isRecording">
             <div class="absolute w-72 h-72 rounded-full bg-primary/10 wave-animation"></div>
             <div class="absolute w-56 h-56 rounded-full bg-primary/20 wave-animation" style="animation-delay: 0.5s;"></div>
             <div class="absolute w-40 h-40 rounded-full bg-primary/30 wave-animation" style="animation-delay: 1s;"></div>
          </template>
          
          <div class="relative z-10 w-28 h-28 bg-white rounded-full flex items-center justify-center shadow-lg border-4 border-primary/20">
            <span class="material-symbols-rounded text-6xl text-primary leading-none">family_history</span>
          </div>
        </div>

        <!-- Action Area -->
        <div class="w-full flex flex-col items-center pb-12 mt-auto">
          <p class="text-slate-500 mb-8 text-base font-medium transition-all" :class="{'opacity-0': isRecording}">
             {{ isRecording ? 'Recording...' : 'Tap to start voice registration' }}
          </p>
          
          <button 
            @click="toggleRecording"
            class="group relative w-24 h-24 bg-primary rounded-full flex items-center justify-center mic-shadow active:scale-95 transition-transform"
          >
            <span class="material-symbols-rounded text-5xl text-white">
                {{ isRecording ? 'stop' : 'mic' }}
            </span>
            <span v-if="isRecording" class="absolute inset-0 rounded-full border-4 border-primary animate-ping opacity-25"></span>
          </button>

          <!-- Guide Card -->
          <div class="mt-12 px-6 py-5 bg-orange-50 rounded-2xl border border-orange-100 w-full shadow-sm">
            <div class="flex gap-4">
              <span class="material-symbols-rounded text-primary text-2xl">info</span>
              <div>
                <p class="text-xs font-bold text-orange-900 uppercase tracking-wider mb-1">Recording Guide</p>
                <p class="text-sm text-slate-600 leading-relaxed">
                    Please find a quiet environment for the best quality. Hold your phone about 10 inches away from your face.
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
    console.log("Started Recording...");
    
    // Auto-stop simulation after 3 seconds
    setTimeout(() => {
        if(isRecording.value) stopRecording();
    }, 3000);
};

const stopRecording = () => {
    isRecording.value = false;
    alert("Voice Registered Successfully! (Mock)");
    console.log("Stopped Recording.");
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
    box-shadow: 0 10px 25px -5px rgba(231, 111, 81, 0.4); /* Primary color shadow */
}
</style>
