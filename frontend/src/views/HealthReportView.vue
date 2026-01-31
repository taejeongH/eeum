<template>
  <div class="bg-[#fcfcfc] min-h-screen text-slate-800 pb-24 relative overflow-x-hidden">
    <!-- Premium Header Area -->
    <div class="relative w-full h-56 bg-[var(--color-primary)] rounded-b-[3rem] shadow-2xl overflow-hidden shrink-0">
      <!-- Gradient Overlay -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      
      <!-- Decorative Pattern -->
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>
      
      <!-- Navigation Bar -->
      <div class="relative z-30 flex justify-between items-start p-5 pt-6 pb-2">
        <button @click="goBack" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 class="text-xl font-bold text-white tracking-tight pt-1.5">건강 리포트</h1>
        <button class="p-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
          </svg>
        </button>
      </div>

      <!-- Date Selection with EeumDatePicker (Moved Up) -->
      <div class="relative z-30 px-10 mt-1 flex items-center justify-center text-white">
        <div class="w-full max-w-[240px]">
          <EeumDatePicker v-model="selectedDateStr" class="custom-picker">
            <template #trigger>
              <div class="flex flex-col items-center bg-white/20 backdrop-blur-md rounded-[2rem] py-2 px-5 border border-white/10 shadow-lg cursor-pointer hover:bg-white/30 transition-colors">
                <p class="text-[10px] font-bold opacity-80 mb-0.5 tracking-widest">{{ formattedYear }}</p>
                <div class="flex items-center gap-2">
                  <h2 class="text-xl font-black tracking-tight">{{ formattedDate }}</h2>
                  <span class="material-symbols-outlined text-base opacity-80">calendar_month</span>
                </div>
              </div>
            </template>
          </EeumDatePicker>
        </div>
      </div>
    </div>

    <!-- Content Container -->
    <div class="px-5 -mt-10 relative z-40 space-y-6">
      <!-- Goal Progress Card -->
      <div class="bg-white rounded-[2.5rem] shadow-xl shadow-orange-100/50 p-7 border border-white relative overflow-hidden">
        <div class="flex items-start justify-between mb-6">
          <div class="space-y-1">
            <span class="px-3 py-1 bg-orange-50 text-[var(--color-primary)] text-[11px] font-bold rounded-full">DAILY SUMMARY</span>
            <h3 class="text-2xl font-bold text-slate-900">오늘의 요약</h3>
          </div>
          <div class="w-16 h-16 rounded-3xl bg-orange-50 flex items-center justify-center">
            <span class="material-symbols-outlined text-[var(--color-primary)] text-3xl">auto_awesome</span>
          </div>
        </div>
        
        <p class="text-slate-600 leading-relaxed font-medium text-[15px]">
          {{ healthStore.currentReport?.summary || '데이터를 분석 중입니다...' }}
        </p>

        <!-- Mini Stats Row -->
        <div class="grid grid-cols-3 gap-4 mt-8 pt-6 border-t border-slate-50">
          <div class="text-center">
             <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">상태</p>
             <p class="text-sm font-bold text-[var(--color-primary)]">매우 양호</p>
          </div>
          <div class="text-center border-x border-slate-100">
             <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">활동</p>
             <p class="text-sm font-bold text-slate-800">활발함</p>
          </div>
          <div class="text-center">
             <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-1">수면</p>
             <p class="text-sm font-bold text-slate-800">충분함</p>
          </div>
        </div>
      </div>

      <!-- Metrics Section -->
      <div class="space-y-4">
        <h4 class="text-lg font-bold text-slate-900 ml-2">세부 지표</h4>
        
        <div class="grid grid-cols-1 gap-4">
          <!-- Steps Card -->
          <div class="bg-white rounded-[2rem] p-6 shadow-sm border border-slate-100 flex flex-col gap-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-2xl bg-orange-50 text-[var(--color-primary)] flex items-center justify-center">
                   <span class="material-symbols-outlined text-xl">steps</span>
                </div>
                <div>
                  <p class="text-xs font-bold text-slate-400">걸음 수</p>
                  <p class="text-lg font-black text-slate-900">12,480 <span class="text-xs font-bold text-slate-400">걸음</span></p>
                </div>
              </div>
              <span class="text-[11px] font-bold text-[var(--color-primary)] bg-orange-50 px-2 py-1 rounded-lg">목표 달성!</span>
            </div>
            <!-- Progress Bar -->
            <div class="w-full h-3 bg-slate-50 rounded-full overflow-hidden">
               <div class="h-full bg-gradient-to-r from-orange-400 to-[var(--color-primary)]" style="width: 85%"></div>
            </div>
          </div>

          <!-- Heart Rate Card -->
          <div class="bg-white rounded-[2rem] p-6 shadow-sm border border-slate-100 flex flex-col gap-4">
             <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-2xl bg-red-50 text-red-500 flex items-center justify-center">
                   <span class="material-symbols-outlined text-xl">favorite</span>
                </div>
                <div>
                  <p class="text-xs font-bold text-slate-400">평균 심박수</p>
                  <p class="text-lg font-black text-slate-900">72 <span class="text-xs font-bold text-slate-400">BPM</span></p>
                </div>
              </div>
               <!-- Pulsing dot -->
               <div class="flex items-center gap-1.5 px-2.5 py-1 bg-red-50 rounded-lg">
                  <span class="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse"></span>
                  <span class="text-[10px] font-bold text-red-500">실시간 연동</span>
               </div>
            </div>
            <!-- Heart Rate Wave -->
            <div class="h-12 w-full opacity-60">
              <svg viewBox="0 0 400 50" class="w-full h-full text-red-400">
                <path d="M0 25 L40 25 L50 5 L65 45 L75 25 L120 25 L130 10 L145 40 L155 25 L200 25" fill="none" stroke="currentColor" stroke-width="2" />
                <path d="M200 25 L240 25 L250 5 L265 45 L275 25 L320 25 L330 10 L345 40 L355 25 L400 25" fill="none" stroke="currentColor" stroke-width="2" />
              </svg>
            </div>
          </div>

          <!-- Sleep Card -->
          <div class="bg-white rounded-[2rem] p-6 shadow-sm border border-slate-100 flex flex-col gap-5">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-2xl bg-orange-50 text-[var(--color-primary)] flex items-center justify-center">
                   <span class="material-symbols-outlined text-xl">bedtime</span>
                </div>
                <div>
                  <p class="text-xs font-bold text-slate-400">총 수면 시간</p>
                  <p class="text-lg font-black text-slate-900">7<span class="text-xs font-bold text-slate-400 tracking-tighter mx-0.5">시간</span> 24<span class="text-xs font-bold text-slate-400 tracking-tighter ml-0.5">분</span></p>
                </div>
              </div>
              <div class="text-right">
                <p class="text-[10px] font-bold text-slate-400">수면 효율</p>
                <p class="text-sm font-bold text-[var(--color-primary)] italic">92%</p>
              </div>
            </div>
            
            <!-- Sleep Phases Bar -->
            <div class="space-y-2">
              <div class="w-full h-4 bg-slate-50 rounded-lg overflow-hidden flex">
                 <div class="h-full bg-orange-200" style="width: 20%" title="REM"></div>
                 <div class="h-full bg-orange-400" style="width: 45%" title="LIGHT"></div>
                 <div class="h-full bg-[var(--color-primary)]" style="width: 35%" title="DEEP"></div>
              </div>
              <div class="flex justify-between text-[9px] font-bold text-slate-400 px-1">
                 <div class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-orange-200"></span> 램 수면</div>
                 <div class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-orange-400"></span> 얕은 수면</div>
                 <div class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-[var(--color-primary)]"></span> 깊은 수면</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="bg-slate-900 rounded-[2.5rem] p-8 text-white relative overflow-hidden shadow-2xl">
         <div class="absolute top-0 right-0 p-10 opacity-10">
           <span class="material-symbols-outlined text-8xl">clinical_notes</span>
         </div>
         <div class="relative z-10 space-y-4">
            <h3 class="text-xl font-bold flex items-center gap-2">
               <span class="material-symbols-outlined text-orange-400">lightbulb</span>
               전문가 코멘트
            </h3>
            <p class="text-slate-300 text-[15px] leading-relaxed">
              {{ healthStore.currentReport?.description || '데이터를 기반으로 건강 코멘트를 준비 중입니다.' }}
            </p>
            <div class="pt-4">
               <button class="w-full py-4 bg-white/10 hover:bg-white/15 backdrop-blur-md rounded-2xl text-sm font-bold border border-white/10 transition">
                 지난 리포트 보러가기
               </button>
            </div>
         </div>
      </div>
    </div>

    <BottomNav />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useHealthStore } from '@/stores/health';
import { useFamilyStore } from '@/stores/family';
import BottomNav from '@/components/layout/BottomNav.vue';
import EeumDatePicker from '@/components/common/EeumDatePicker.vue';

const route = useRoute();
const router = useRouter();
const healthStore = useHealthStore();
const familyStore = useFamilyStore();

const currentDate = ref(new Date());
const selectedDateStr = ref(new Date().toISOString().split('T')[0]);

const formattedYear = computed(() => currentDate.value.getFullYear() + '년');
const formattedDate = computed(() => {
  const options = { month: 'long', day: 'numeric', weekday: 'short' };
  return currentDate.value.toLocaleDateString('ko-KR', options);
});

const goBack = () => {
    router.back();
};

const fetchReport = async () => {
  const familyId = route.params.familyId || familyStore.selectedFamily?.id;
  if (!familyId) return;
  
  await healthStore.fetchDailyReport(familyId, selectedDateStr.value);
};

watch(selectedDateStr, (newVal) => {
    currentDate.value = new Date(newVal);
    fetchReport();
});

onMounted(() => {
    if (!familyStore.selectedFamily) {
        familyStore.fetchFamilies().then(() => fetchReport());
    } else {
        fetchReport();
    }
});
</script>

<style scoped>
.animate-pop {
  animation: pop 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes pop {
  from { opacity: 0; transform: scale(0.9) translateY(20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.material-symbols-outlined {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
