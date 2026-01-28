<template>
  <div class="bg-[#fcfcfc] text-slate-800 min-h-screen flex flex-col pb-24">
    <header class="sticky top-0 z-20 bg-[#fcfcfc]/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex justify-between items-center">
        <button @click="$router.push('/home')" class="p-2 -ml-2 text-slate-600">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <div class="flex gap-4">
          <button class="p-2 text-slate-600">
            <span class="material-symbols-outlined">menu</span>
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
      <div class="calendar-grid text-center mb-6">
        <div class="py-2 text-sm font-semibold text-red-400">일</div>
        <div class="py-2 text-sm font-semibold text-slate-500">월</div>
        <div class="py-2 text-sm font-semibold text-slate-500">화</div>
        <div class="py-2 text-sm font-semibold text-slate-500">수</div>
        <div class="py-2 text-sm font-semibold text-slate-500">목</div>
        <div class="py-2 text-sm font-semibold text-slate-500">금</div>
        <div class="py-2 text-sm font-semibold text-blue-400">토</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30 text-red-500">25</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">26</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">27</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">28</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">29</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30">30</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 opacity-30 text-blue-500">31</div>
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
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>8</span>
          <div class="absolute bottom-4 flex gap-0.5">
            <div class="w-2.5 h-1 bg-blue-300 rounded-full"></div>
            <div class="w-2.5 h-1 bg-primary/40 rounded-full"></div>
          </div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>9</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-lavender rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">10</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <div class="absolute inset-0 border-2 border-primary/50 rounded-2xl bg-primary/5"></div>
          <span class="relative z-10 font-bold">11</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-lavender rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">12</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>13</span>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">14</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500">15</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>16</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-sage rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>17</span>
          <div class="absolute bottom-4 w-6 h-1 bg-red-200 rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-red-500 relative">
          <span>18</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-sage rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 relative">
          <span>19</span>
          <div class="absolute bottom-4 w-6 h-1 bg-accent-peach rounded-full"></div>
        </div>
        <div class="h-16 flex flex-col items-center justify-start pt-2">20</div>
        <div class="h-16 flex flex-col items-center justify-start pt-2 text-blue-500">21</div>
      </div>
      <div class="h-px bg-slate-200 w-full mb-6"></div>
      <div class="space-y-4 px-2 pb-24">
        <div class="flex justify-between items-center mb-2">
          <span class="text-sm text-slate-500">음력 12월 24일</span>
          <button class="text-slate-400"><span class="material-symbols-outlined">sentiment_satisfied</span></button>
        </div>
        <div @click="$router.push('/calendar/detail')" class="bg-[#FFFBF7] p-5 rounded-3xl ios-shadow border border-slate-100 transition-all active:scale-[0.98] cursor-pointer">
          <div class="flex items-start gap-4">
            <div class="flex flex-col items-center mt-1">
              <span class="text-base font-bold text-slate-800">12:00</span>
            </div>
            <div class="w-1.5 h-10 bg-accent-lavender rounded-full"></div>
            <div class="flex-1">
              <h3 class="text-xl font-bold text-slate-900 mb-0.5">졸업식</h3>
              <p class="text-xs text-slate-500">오후 12:00 - 오후 1:00</p>
            </div>
          </div>
        </div>
      </div>
      <div class="fixed bottom-32 right-6 z-30">
        <button @click="$router.push('/calendar/create')" class="bg-primary text-white w-14 h-14 rounded-full flex items-center justify-center shadow-lg shadow-primary/30 active:scale-95 transition-transform">
          <span class="material-symbols-outlined text-3xl" style="font-variation-settings: 'FILL' 0, 'wght' 600">add</span>
        </button>
      </div>
    </main>
    <BottomNav />
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import BottomNav from '@/components/layout/BottomNav.vue';

// TODO: Replace with actual API call
const fetchCalendarEvents = async () => {
    console.log("Fetching calendar events...");
    // Mock API call
    /*
    try {
        const response = await api.get('/calendar/events');
        events.value = response.data;
    } catch (error) {
        console.error("Failed to fetch events", error);
    }
    */
};

onMounted(() => {
    fetchCalendarEvents();
});
</script>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}
.ios-shadow {
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.05);
}
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined'; /* Ensure font family is applied */
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  font-size: 28px;
}
</style>
