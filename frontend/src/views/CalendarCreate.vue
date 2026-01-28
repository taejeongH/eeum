<template>
  <div class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-24 relative overflow-hidden">
    <!-- Background Content (Calendar Page Look) -->
    <header class="sticky top-0 z-10 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex justify-between items-center">
        <button class="p-2 -ml-2 text-slate-600">
          <span class="material-symbols-outlined">menu</span>
        </button>
        <div class="flex gap-4">
          <button class="p-2 text-slate-600">
            <span class="material-symbols-outlined">search</span>
          </button>
          <div class="relative p-2">
            <span class="material-symbols-outlined text-slate-600">notifications</span>
            <span class="absolute top-1 right-1 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full border-2 border-[#fcfcfc]">22</span>
          </div>
        </div>
      </div>
      <div class="mt-4 text-center">
        <h1 class="text-3xl font-bold text-slate-900">2월</h1>
      </div>
    </header>
    
    <main class="flex-1 px-4 relative">
       <!-- Duplicate Grid for background visual -->
       <div class="calendar-grid text-center mb-6 opacity-30">
        <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>
        <!-- Simplified grid items for visual background -->
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50 text-red-500">25</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">26</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">27</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">28</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">29</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50">30</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-50 text-blue-500">31</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 font-medium">1</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">2</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">3</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>4</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-peach rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">5</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">6</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">7</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500">8</div>
        <!-- Truncated rest for brevity as it is just background -->
      </div>
    </main>
    
    <BottomNav />

    <!-- Modal Overlay -->
    <div class="fixed inset-0 z-[60] flex items-end">
      <div class="absolute inset-0 modal-overlay" @click="$router.back()"></div>
      <div class="relative w-full modal-content bg-white max-h-[92vh] overflow-y-auto shadow-2xl animate-slide-up">
        <div class="flex justify-center pt-4 pb-2">
          <div class="w-12 h-1.5 bg-slate-300 rounded-full"></div>
        </div>
        <div class="px-6 pb-12 pt-4">
          <header class="flex justify-between items-center mb-8">
            <h2 class="text-2xl font-bold text-slate-900">일정 추가</h2>
            <button @click="$router.back()" class="text-slate-400 hover:text-slate-600">
              <span class="material-symbols-outlined">close</span>
            </button>
          </header>
          <form class="space-y-8" @submit.prevent>
            <div class="space-y-2">
              <label class="text-base font-semibold text-slate-700 ml-1">제목</label>
              <input class="w-full bg-white border-none rounded-2xl p-4 text-lg ios-shadow focus:ring-2 focus:ring-primary/50 text-slate-900 placeholder:text-slate-400" placeholder="일정 제목을 입력하세요" type="text"/>
            </div>
            <div class="space-y-3">
              <label class="text-base font-semibold text-slate-700 ml-1">일정 구분</label>
              <div class="flex flex-wrap gap-2">
                <button class="px-5 py-2.5 rounded-full bg-primary text-white font-semibold text-sm ios-shadow" type="button">건강</button>
                <button class="px-5 py-2.5 rounded-full bg-white text-slate-600 font-semibold text-sm border border-slate-100 ios-shadow" type="button">가족</button>
                <button class="px-5 py-2.5 rounded-full bg-white text-slate-600 font-semibold text-sm border border-slate-100 ios-shadow" type="button">개인</button>
                <button class="px-5 py-2.5 rounded-full bg-white text-slate-600 font-semibold text-sm border border-slate-100 ios-shadow" type="button">기타</button>
              </div>
            </div>
            <div class="grid grid-cols-1 gap-4">
              <div class="bg-white p-4 rounded-2xl ios-shadow space-y-4">
                <div class="flex items-center justify-between">
                  <span class="text-slate-600 font-medium">시작</span>
                  <div class="flex gap-2">
                    <button class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold" type="button">2024. 02. 11</button>
                    <button class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold" type="button">12:00</button>
                  </div>
                </div>
                <div class="h-px bg-slate-100"></div>
                <div class="flex items-center justify-between">
                  <span class="text-slate-600 font-medium">종료</span>
                  <div class="flex gap-2">
                    <button class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold" type="button">2024. 02. 11</button>
                    <button class="bg-slate-100 px-3 py-1.5 rounded-lg text-sm font-semibold" type="button">13:00</button>
                  </div>
                </div>
              </div>
            </div>
            <div class="space-y-2">
              <label class="text-base font-semibold text-slate-700 ml-1">메모</label>
              <textarea class="w-full bg-white border-none rounded-2xl p-4 text-base ios-shadow focus:ring-2 focus:ring-primary/50 text-slate-900 placeholder:text-slate-400 resize-none" placeholder="상세 내용을 입력하세요" rows="4"></textarea>
            </div>
            <div class="flex gap-3 pt-4">
              <button @click="$router.back()" class="flex-1 py-4 rounded-2xl bg-slate-100 text-slate-600 font-bold text-lg" type="button">취소</button>
              <button class="flex-[2] py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-lg shadow-primary/20" type="button">저장하기</button>
            </div>
          </form>
        </div>
        <div class="h-8"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import BottomNav from '@/components/layout/BottomNav.vue';
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}
.ios-shadow {
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.05);
}
.modal-overlay {
  background-color: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
}
.modal-content {
  border-top-left-radius: 2.5rem;
  border-top-right-radius: 2.5rem;
}
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
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
  animation: slide-up 0.3s ease-out;
}
</style>
