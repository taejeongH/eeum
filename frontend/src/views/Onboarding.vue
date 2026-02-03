<template>
  <div class="onboarding-page fixed inset-0 z-[999] flex flex-col overflow-hidden select-none" style="background-color: #ffffff !important;">
    
    <div class="absolute top-0 left-0 right-0 h-1 bg-gray-100 z-[1000]">
      <div 
        class="h-full transition-all duration-700 ease-out"
        :style="{ 
          width: `${((currentSlide + 1) / slides.length) * 100}%`,
          backgroundColor: '#e76f51' 
        }"
      ></div>
    </div>
    
    <header class="w-full px-8 pt-12 flex justify-between items-end z-[1000]">
      <div class="flex flex-col text-left">
        <span class="text-[10px] font-black tracking-[0.1em] uppercase mb-1" style="color: #e76f51;">Step</span>
        <div class="flex items-baseline gap-1">
          <span class="text-4xl font-black text-gray-900 leading-none tracking-tighter">{{ currentSlide + 1 }}</span>
          <span class="text-xl font-bold text-gray-200">/ {{ slides.length }}</span>
        </div>
      </div>
      
      <button 
        v-if="!isLastSlide"
        type="button"
        @click="finishOnboarding"
        class="group flex items-center gap-2"
      >
        <span class="text-xs font-black tracking-widest text-gray-400 group-hover:text-[#e76f51] uppercase transition-all">skip</span>
        <div class="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center text-gray-400 group-hover:bg-[#e76f51]/10 transition-all">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 5l7 7-7 7" />
          </svg>
        </div>
      </button>
    </header>

    <main class="flex-1 flex flex-col justify-center items-center px-10 text-center relative -mt-10">
      <transition name="slide-fade" mode="out-in">
        <div :key="currentSlide" class="w-full flex flex-col items-center">
          <div class="relative w-full aspect-square max-w-[340px] xs:max-w-[420px] mb-12 flex items-center justify-center">
            <div 
              class="absolute inset-0 rounded-full blur-[80px] animate-pulse opacity-60" 
              :style="{ backgroundColor: slides[currentSlide].color || '#fde8e3' }"
            ></div>
            <img 
              :src="slides[currentSlide].image" 
              class="relative w-full h-full animate-float scale-110 object-contain mix-blend-multiply"
            />
          </div>
          
          <div class="space-y-6">
            <h2 class="text-[2.2rem] xs:text-[2.5rem] font-black text-gray-900 tracking-tight leading-[1.1] break-keep px-4">
              {{ slides[currentSlide].title }}
            </h2>
            <p class="text-base xs:text-lg text-gray-400 font-medium leading-relaxed break-keep max-w-[300px] mx-auto whitespace-pre-line">
              {{ slides[currentSlide].description }}
            </p>
          </div>
        </div>
      </transition>
    </main>

    <footer class="w-full px-8 pb-20 pt-6 flex flex-col items-center z-[1000]">
      <div class="w-full max-w-sm flex justify-center items-center h-20">
        <button 
          type="button"
          @click="handleNext"
          class="flex items-center justify-center transition-all duration-500 ease-[cubic-bezier(0.34,1.56,0.64,1)] active:scale-95 shadow-xl overflow-hidden h-16"
          :style="{ 
            backgroundColor: isLastSlide ? '#111827' : '#e76f51',
            width: isLastSlide ? '100%' : '64px',
            borderRadius: isLastSlide ? '1rem' : '50%',
            color: '#ffffff'
          }"
        >
          <div class="flex items-center justify-center w-full px-4 overflow-hidden">
            <transition name="fade-fast" mode="out-in">
              <div v-if="isLastSlide" :key="'last'" class="flex items-center justify-center gap-3 min-w-max">
                <span class="font-black text-lg tracking-tight whitespace-nowrap" style="color: #ffffff !important;">마음을 전하러 가볼까요?</span>
                <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 shrink-0" fill="none" viewBox="0 0 24 24" stroke="#ffffff">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M13 7l5 5m0 0l-5 5m5-5H6" />
                </svg>
              </div>
              <div v-else :key="'next'" class="flex items-center justify-center">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="#ffffff">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </div>
            </transition>
          </div>
        </button>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';

// 이미지 import
import welcomeImg from '@/assets/onboarding/welcome_v2.png';
import messageImg from '@/assets/onboarding/message_v2.png';
import careImg from '@/assets/onboarding/care_3d.png';
import voiceImg from '@/assets/onboarding/voice.png';

const router = useRouter();
const currentSlide = ref(0);

const slides = [
  { title: '나와 가족의 이음', description: '떨어져 있어도 마음은 항상 곁에,\n우리 가족을 하나로 잇는 공간.', image: welcomeImg, color: '#fde8e3' },
  { title: '전해지는 진심', description: '글자 속에 담긴 가족의 따뜻한 안부,\n매일 아침 설레는 소식을 확인하세요.', image: messageImg, color: '#fff1bb' },
  { title: '똑똑한 건강 비서', description: '복약 알림부터 집안 대소사까지,\n이음이 당신의 하루를 기억할게요.', image: careImg, color: '#e0f2fe' },
  { title: '반가운 목소리', description: 'AI로 되살린 사랑하는 이의 목소리,\n가장 듣고 싶은 이야기를 들려드립니다.', image: voiceImg, color: '#f3e8ff' }
];

const isLastSlide = computed(() => currentSlide.value === slides.length - 1);

const handleNext = () => {
  if (isLastSlide.value) {
    finishOnboarding();
  } else {
    currentSlide.value++;
  }
};

const finishOnboarding = () => {
  localStorage.setItem('hasSeenOnboarding', 'true');
  router.push('/login');
};
</script>

<style scoped>
.onboarding-page { 
  touch-action: none;
}

.animate-float {
  animation: float 6s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0px) scale(1.1); }
  50% { transform: translateY(-15px) scale(1.13); }
}

.slide-fade-enter-active, .slide-fade-leave-active {
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-fade-enter-from { opacity: 0; transform: translateX(40px); }
.slide-fade-leave-to { opacity: 0; transform: translateX(-40px); }

.fade-fast-enter-active, .fade-fast-leave-active {
  transition: opacity 0.2s ease;
}
.fade-fast-enter-from, .fade-fast-leave-to { opacity: 0; }
</style>