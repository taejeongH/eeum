<template>
  <div class="bg-[#fcfcfc] min-h-screen text-slate-800 pb-32 relative overflow-x-hidden">
    <!-- Premium Header Area (from HealthReportView) -->
    <div class="relative w-full h-56 bg-[var(--color-primary)] rounded-b-[3rem] shadow-2xl overflow-hidden shrink-0">
      <!-- Gradient Overlay -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      
      <!-- Decorative Pattern -->
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>
      
      <!-- Navigation Bar -->
      <div class="relative z-30 flex justify-between items-start p-5 pt-6 pb-2">
        <button @click="$router.back()" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
          <IconBack />
        </button>
        <h1 class="text-xl font-bold text-white tracking-tight pt-1.5">실시간 건강 정보</h1>
        <div class="flex items-center gap-2">
            <button v-if="isUserDependent" @click="fetchAllData" class="p-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
                <span class="material-symbols-outlined text-base" :class="{'animate-spin': syncLoading}">sync</span>
            </button>
        </div>
      </div>

      <!-- Date Display -->
      <div class="relative z-30 px-10 mt-1 flex flex-col items-center justify-center text-white">
          <p class="text-[10px] font-bold opacity-80 mb-0.5 tracking-widest">{{ formattedYear }}</p>
          <div class="flex items-center gap-2 mb-3">
              <h2 class="text-xl font-black tracking-tight">{{ formattedDate }}</h2>
          </div>
          
          <!-- Last Update Badge -->
          <div class="flex items-center gap-1.5 px-3 py-1 bg-white/10 backdrop-blur-md rounded-full text-white/90 border border-white/20">
            <span class="material-symbols-outlined text-[12px] opacity-70">history</span>
            <span class="text-[9px] font-bold tracking-tight">{{ lastUpdateTime }}</span>
          </div>
      </div>
    </div>
    
    <div v-if="hasDependent" class="px-5 -mt-10 relative z-40 space-y-6">
      
      <!-- Sync Status -->
      <div v-if="syncMessage" class="w-full p-3 bg-blue-50 text-blue-600 rounded-xl text-center text-sm font-medium animate-pulse shadow-sm">
          {{ syncMessage }}
      </div>

      <!-- 1. Real Vitals Section -->
      <div class="space-y-4 pt-4">

          <!-- Quick Stats Grid -->
          <div class="grid grid-cols-2 gap-3">
              <!-- Heart Rate -->
              <div class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50">
                 <span class="material-symbols-outlined text-[#FF5252] text-2xl mb-1">monitor_heart</span>
                 <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1">심박수</span>
                 <p class="text-lg font-black text-slate-900">{{ healthStore.latestMetrics.averageHeartRate || '--' }} <span class="text-[9px] font-normal text-slate-400">bpm</span></p>
                 <div class="flex gap-2 mt-1 text-[8px] text-slate-400">
                    <span>최저:{{ healthStore.latestMetrics.restingHeartRate || '--' }}</span>
                    <span>최고:{{ healthStore.latestMetrics.maxHeartRate || '--' }}</span>
                 </div>
              </div>
              <!-- Steps -->
              <div class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50">
                 <span class="material-symbols-outlined text-[#4CAF50] text-2xl mb-1">directions_walk</span>
                 <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1">걸음 수</span>
                 <p class="text-lg font-black text-slate-900">{{ (healthStore.latestMetrics.steps || 0).toLocaleString() }}</p>
              </div>
          </div>

          <!-- Secondary Stats Grid (Calories & Active Time) -->
          <div class="grid grid-cols-2 gap-3">
              <!-- Active Calories -->
              <div class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50">
                 <span class="material-symbols-outlined text-[#FF9800] text-2xl mb-1">local_fire_department</span>
                 <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1">활동 칼로리</span>
                 <p class="text-lg font-black text-slate-900">{{ healthStore.latestMetrics.activeCalories || 0 }} <span class="text-[9px] font-normal text-slate-400">kcal</span></p>
              </div>
              <!-- Active Time -->
              <div class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50">
                 <span class="material-symbols-outlined text-[#9C27B0] text-2xl mb-1">avg_pace</span>
                 <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1">활동 시간</span>
                 <p class="text-lg font-black text-slate-900">{{ healthStore.latestMetrics.activeMinutes || 0 }} <span class="text-[9px] font-normal text-slate-400">분</span></p>
              </div>
          </div>

          <!-- Blood Pressure & SpO2 List -->
          <div class="space-y-3">
              <!-- Blood Pressure -->
              <div class="bg-white rounded-3xl shadow-sm p-5 border border-slate-50 flex items-center justify-between">
                  <div class="flex items-center space-x-4">
                      <div class="w-10 h-10 bg-red-50 rounded-2xl flex items-center justify-center">
                          <span class="material-symbols-outlined text-red-500 text-xl">blood_pressure</span>
                      </div>
                      <div>
                          <p class="text-sm font-bold text-slate-900">혈압</p>
                      </div>
                  </div>
                  <div class="text-right">
                      <p v-if="healthStore.latestMetrics.systolicPressure" class="text-base font-black text-slate-900">
                          {{ healthStore.latestMetrics.systolicPressure }}/{{ healthStore.latestMetrics.diastolicPressure }}
                          <span class="text-[9px] font-normal text-slate-400 ml-1">mmHg</span>
                      </p>
                      <p v-else class="text-slate-300 font-bold text-sm">데이터 없음</p>
                  </div>
              </div>

               <!-- SpO2 -->
              <div class="bg-white rounded-3xl shadow-sm p-5 border border-slate-50 flex items-center justify-between">
                  <div class="flex items-center space-x-4">
                      <div class="w-10 h-10 bg-blue-50 rounded-2xl flex items-center justify-center">
                          <span class="material-symbols-outlined text-blue-500 text-xl">oxygen_saturation</span>
                      </div>
                      <div>
                          <p class="text-sm font-bold text-slate-900">혈중 산소</p>
                      </div>
                  </div>
                  <div class="text-right">
                      <p v-if="healthStore.latestMetrics.bloodOxygen" class="text-base font-black text-slate-900">
                          {{ healthStore.latestMetrics.bloodOxygen }} <span class="text-[9px] font-normal text-slate-400 ml-1">%</span>
                      </p>
                      <p v-else class="text-slate-300 font-bold text-sm">데이터 없음</p>
                  </div>
              </div>


              <!-- Blood Glucose -->
              <div class="bg-white rounded-3xl shadow-sm p-5 border border-slate-50 flex items-center justify-between">
                  <div class="flex items-center space-x-4">
                      <div class="w-10 h-10 bg-orange-50 rounded-2xl flex items-center justify-center">
                          <span class="material-symbols-outlined text-orange-500 text-xl">glucose</span>
                      </div>
                      <div>
                          <p class="text-sm font-bold text-slate-900">혈당</p>
                      </div>
                  </div>
                  <div class="text-right">
                      <p v-if="healthStore.latestMetrics.bloodGlucose" class="text-base font-black text-slate-900">
                          {{ healthStore.latestMetrics.bloodGlucose }} <span class="text-[9px] font-normal text-slate-400 ml-1">mg/dL</span>
                      </p>
                      <p v-else class="text-slate-300 font-bold text-sm">데이터 없음</p>
                  </div>
              </div>
          </div>

          <!-- Sleep Card -->
          <div class="bg-white rounded-[2rem] p-6 shadow-sm border border-slate-100 flex flex-col gap-5">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-500 flex items-center justify-center">
                   <span class="material-symbols-outlined text-xl">bedtime</span>
                </div>
                <div>
                  <p class="text-xs font-bold text-slate-400">수면 분석</p>
                  <p class="text-lg font-black text-slate-900">
                      {{ healthStore.latestMetrics.sleepTotalMinutes ? Math.floor(healthStore.latestMetrics.sleepTotalMinutes / 60) + '시간 ' + (healthStore.latestMetrics.sleepTotalMinutes % 60) + '분' : '기록 없음' }}
                  </p>
                </div>
              </div>
            </div>
            
            <div v-if="healthStore.latestMetrics.sleepTotalMinutes" class="space-y-2">
              <div class="w-full h-4 bg-slate-50 rounded-lg overflow-hidden flex">
                 <div class="h-full bg-indigo-200" :style="{width: (healthStore.latestMetrics.sleepRemMinutes / healthStore.latestMetrics.sleepTotalMinutes * 100) + '%'}" title="REM"></div>
                 <div class="h-full bg-indigo-400" :style="{width: (healthStore.latestMetrics.sleepLightMinutes / healthStore.latestMetrics.sleepTotalMinutes * 100) + '%'}" title="LIGHT"></div>
                 <div class="h-full bg-indigo-600" :style="{width: (healthStore.latestMetrics.sleepDeepMinutes / healthStore.latestMetrics.sleepTotalMinutes * 100) + '%'}" title="DEEP"></div>
              </div>
              <div class="flex justify-between text-[9px] font-bold text-slate-400 px-1">
                 <div class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-indigo-200"></span> REM</div>
                 <div class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span> 얕은 수면</div>
                 <div class="flex items-center gap-1"><span class="w-1.5 h-1.5 rounded-full bg-indigo-600"></span> 깊은 수면</div>
              </div>
            </div>
          </div>
      </div>

      <!-- 2. AI Health Analysis (NOW AT BOTTOM) -->
      <div id="ai-analysis" class="space-y-6 pt-2">
          <div class="flex items-center justify-between px-2">
             <div class="flex items-center gap-2">
                <h3 class="text-lg font-bold text-slate-900">AI 건강 도우미 분석</h3>
                <button @click="showHelpModal = true" class="w-6 h-6 flex items-center justify-center rounded-full bg-slate-100 text-slate-400 hover:bg-slate-200 transition-colors">
                    <span class="material-symbols-outlined text-[16px]">help_outline</span>
                </button>
             </div>
             <button 
                @click="handleAnalyze" 
                :disabled="analyzeLoading"
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[var(--color-primary)] text-white text-[11px] font-bold shadow-lg shadow-orange-100 disabled:opacity-50 transition-all active:scale-95">
                <span class="material-symbols-outlined text-sm" :class="{'animate-spin': analyzeLoading}">auto_awesome</span>
                {{ analyzeLoading ? '분석 중...' : '분석하기' }}
             </button>
          </div>

          <!-- Expert Comment Card (Focused View) -->
          <div class="bg-slate-900 rounded-[2.5rem] p-8 text-white relative overflow-hidden shadow-2xl mb-8 border border-white/5">
             <div class="absolute top-0 right-0 p-10 opacity-10">
               <span class="material-symbols-outlined text-8xl">clinical_notes</span>
             </div>
             <div class="relative z-10 space-y-5">
                <div class="flex items-center gap-3">
                    <div class="w-10 h-10 rounded-2xl bg-white/10 flex items-center justify-center backdrop-blur-md">
                        <span class="material-symbols-outlined text-orange-400">lightbulb</span>
                    </div>
                    <h3 class="text-xl font-bold">AI 어드바이저 코멘트</h3>
                </div>
                <div class="space-y-6">
                    <div class="text-slate-200 text-[14px] leading-relaxed font-medium space-y-4">
                      <template v-for="(item, index) in formattedAdvisorComment" :key="index">
                        <!-- Ultra-Premium Title -->
                        <div v-if="item.isTitle" 
                             class="flex items-center gap-3 mt-8 mb-4">
                           <div class="relative">
                              <div class="absolute inset-0 bg-orange-500 blur-md opacity-20 animate-pulse"></div>
                              <div class="w-1 h-5 bg-gradient-to-b from-orange-400 to-orange-600 rounded-full relative"></div>
                           </div>
                           <h4 class="text-[15px] font-black text-white/95 tracking-tight uppercase tracking-[0.05em]">{{ item.text }}</h4>
                        </div>
                        
                        <!-- High-End Insight Card -->
                        <div v-else 
                             class="group relative bg-[#1a1c23]/40 backdrop-blur-3xl rounded-[1.5rem] p-5 border border-white/10 shadow-[0_8px_32px_rgba(0,0,0,0.3)] transition-all duration-500 hover:translate-y-[-2px] hover:bg-[#21242e]/60 hover:border-white/20">
                           <!-- Elegant selection glow -->
                           <div class="absolute -inset-[1px] bg-gradient-to-br from-white/10 to-transparent rounded-[1.5rem] pointer-events-none opacity-50"></div>
                           
                           <div class="flex gap-4 relative z-10">
                              <div class="flex flex-col items-center">
                                 <div :class="[
                                    'w-10 h-10 rounded-2xl flex items-center justify-center transition-colors duration-500 shadow-lg',
                                    item.text.includes('조심') || item.text.includes('주의') || item.text.includes('위험') 
                                      ? 'bg-red-500/10 text-red-400 border border-red-500/20 shadow-red-500/5' 
                                      : 'bg-orange-500/10 text-orange-400 border border-orange-500/20 shadow-orange-500/5'
                                 ]">
                                    <span class="material-symbols-outlined text-[20px] font-light">
                                        {{ item.text.includes('조심') || item.text.includes('주의') || item.text.includes('위험') ? 'error' : 'auto_awesome' }}
                                    </span>
                                 </div>
                                 <div class="w-px flex-grow bg-gradient-to-b from-white/10 to-transparent mt-3 h-4"></div>
                              </div>
                              
                              <div class="flex-grow pt-1.5">
                                 <p class="text-slate-300 leading-[1.6] text-[14.5px] font-medium tracking-tight">
                                    {{ item.text.replace(/\.$/, '') }}<!-- remove trailing period for modern look if desired, but keeping content intact -->
                                 </p>
                              </div>
                           </div>
                        </div>
                      </template>
                      
                      <!-- Empty State with Glass Design -->
                      <div v-if="!reportDescription && !healthStore.currentReport" 
                           class="flex flex-col items-center justify-center py-16 px-6 text-center border-2 border-dashed border-white/5 rounded-[2.5rem] bg-white/[0.02]">
                         <div class="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center mb-4 backdrop-blur-md">
                            <span class="material-symbols-outlined text-white/20 text-3xl animate-pulse">clinical_notes</span>
                         </div>
                         <p class="text-white/40 text-[13px] font-bold leading-relaxed">
                           아직 분석된 리포트가 없습니다.<br/>
                           <span class="text-orange-400/60 font-black">AI 어드바이저</span>의 특별한 조언을 받아보세요.
                         </p>
                      </div>
                    </div>
                </div>
             </div>
          </div>
      </div>
    </div>

    <!-- Empty State for Groups without Dependent -->
    <div v-else class="px-5 -mt-10 relative z-40">
        <div class="bg-white rounded-[2.5rem] p-10 shadow-xl border border-slate-50 flex flex-col items-center text-center space-y-6">
            <div class="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center">
                <span class="material-symbols-outlined text-slate-300 text-5xl">person_off</span>
            </div>
            <div class="space-y-2">
                <h3 class="text-xl font-bold text-slate-800">피부양자가 없습니다</h3>
                <p class="text-sm text-slate-500 leading-relaxed">
                    이 그룹에는 건강 정보를 확인할 피부양자(어르신)가 설정되어 있지 않습니다.<br/>
                    그룹 설정에서 피부양자를 추가해 주세요.
                </p>
            </div>
            <button @click="$router.push('/home')" class="px-8 py-3 bg-slate-900 text-white rounded-2xl font-bold text-sm hover:bg-slate-800 transition-all shadow-lg active:scale-95">
                홈으로 돌아가기
            </button>
        </div>
    </div>
    
    <BottomNav />

    <!-- Help Modal (GMS Analysis) -->
    <div v-if="showHelpModal" class="fixed inset-0 z-[60] flex items-center justify-center p-6">
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="showHelpModal = false"></div>
        <div class="relative w-full max-w-sm bg-white rounded-[2.5rem] p-8 shadow-2xl animate-fade-in">
            <div class="flex items-center gap-3 mb-6">
                <div class="w-10 h-10 rounded-2xl bg-orange-50 flex items-center justify-center">
                    <span class="material-symbols-outlined text-orange-500">live_help</span>
                </div>
                <h3 class="text-xl font-bold text-slate-900">도움말</h3>
            </div>
            
            <div class="space-y-6 mb-8 text-slate-600">
                <div class="flex gap-4">
                    <span class="flex-shrink-0 w-6 h-6 rounded-full bg-orange-100 flex items-center justify-center text-xs font-bold text-orange-600">1</span>
                    <p class="text-sm leading-relaxed"><strong>GMS 종합 분석</strong><br/>기기에서 측정된 심박수, 혈압, 활동량 등 다양한 생체 데이터를 통합하여 분석합니다.</p>
                </div>
                <div class="flex gap-4">
                    <span class="flex-shrink-0 w-6 h-6 rounded-full bg-orange-100 flex items-center justify-center text-xs font-bold text-orange-600">2</span>
                    <p class="text-sm leading-relaxed"><strong>AI 어드바이저 코멘트</strong><br/>의학적 지식을 학습한 AI가 어르신의 일일 건강 패턴을 분석하여 맞춤형 조언을 드립니다.</p>
                </div>
                <div class="flex gap-4">
                    <span class="flex-shrink-0 w-6 h-6 rounded-full bg-orange-100 flex items-center justify-center text-xs font-bold text-orange-600">3</span>
                    <p class="text-sm leading-relaxed"><strong>주의 및 상담</strong><br/>제공된 정보는 단순 참고용이며, 건강에 이상이 느껴질 경우 반드시 전문 의료진과 상담하시기 바랍니다.</p>
                </div>
            </div>

            <button 
                @click="showHelpModal = false"
                class="w-full py-4 rounded-2xl bg-slate-900 text-white font-bold text-base hover:bg-slate-800 transition-all">
                확인했습니다
            </button>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useFamilyStore } from '@/stores/family';
import { useHealthStore } from '@/stores/health';
import api from '@/services/api';
import healthService from '@/services/healthService';
import BottomNav from '@/components/layout/BottomNav.vue';
import IconBack from '@/components/icons/IconBack.vue';

const route = useRoute();
const userStore = useUserStore();
const familyStore = useFamilyStore();
const healthStore = useHealthStore();

const syncLoading = ref(false);
const analyzeLoading = ref(false);
const showHelpModal = ref(false);
const syncMessage = ref('');
const members = ref([]);

const isUserDependent = computed(() => {
    const myId = userStore.profile?.id;
    if (!myId || !members.value.length) return false;
    const dependent = members.value.find(m => m.dependent);
    return dependent && String(dependent.userId) === String(myId);
});

const hasDependent = computed(() => {
    return members.value.some(m => m.dependent);
});

const healthMetrics = computed(() => healthStore.latestMetrics);
const reportDescription = computed(() => healthStore.currentReport?.description || '');

const currentDate = ref(new Date());
const formattedYear = computed(() => currentDate.value.getFullYear() + '년');
const formattedDate = computed(() => {
  const options = { month: 'long', day: 'numeric', weekday: 'short' };
  return currentDate.value.toLocaleDateString('ko-KR', options);
});

const lastUpdateTime = computed(() => {
  const time = healthStore.latestMetrics.recordDate;
  if (!time) return '데이터 없음';
  
  try {
    const date = new Date(time);
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) + ' 업데이트';
  } catch (e) {
    return '최근 업데이트';
  }
});

const formattedAdvisorComment = computed(() => {
    const text = reportDescription.value;
    if (!text) return [];
    
    // Split by newlines and clean up
    const lines = text.split(/\n+/).map(p => p.trim()).filter(p => p.length > 0);
    
    return lines.map(line => {
        // Remove leading bullets and dashes (•, -, *, 1., 등)
        let cleanedText = line.replace(/^[•\-\*\d\.]+\s*/, '').trim();
        
        // Detect if it's a title (ends with colon or is relatively short and doesn't end with a period)
        const isTitle = cleanedText.endsWith(':') || (cleanedText.length < 25 && !cleanedText.endsWith('.'));
        
        // Remove trailing colon for visual cleaner titles
        if (isTitle && cleanedText.endsWith(':')) {
            cleanedText = cleanedText.slice(0, -1);
        }

        return {
            text: cleanedText,
            isTitle: isTitle
        };
    });
});

const fetchLatestData = async () => {
    const familyId = familyStore.selectedFamily?.id;
    if (!familyId) return;

    try {
        const today = currentDate.value.toISOString().split('T')[0];
        
        // Parallel fetch using store actions
        await Promise.all([
            healthStore.fetchLatestMetrics(familyId),
            healthStore.fetchDailyReport(familyId, today)
        ]);

        if (healthStore.latestMetrics.recordDate) {
            // Update currentDate to the date of the record
            const recordDate = new Date(healthStore.latestMetrics.recordDate);
            currentDate.value = recordDate;
        }
    } catch (error) {
        console.error("Failed to fetch health data:", error);
    }
};

const handleAnalyze = async () => {
    const familyId = familyStore.selectedFamily?.id;
    if (!familyId) return;

    try {
        analyzeLoading.value = true;
        const today = currentDate.value.toISOString().split('T')[0];
        await healthStore.reanalyzeReport(familyId, today);
        syncMessage.value = 'AI 건강 분석이 완료되었습니다.';
        setTimeout(() => syncMessage.value = '', 3000);
    } catch (error) {
        console.error("Analysis failed:", error);
        syncMessage.value = 'AI 분석 중 오류가 발생했습니다.';
        setTimeout(() => syncMessage.value = '', 3000);
    } finally {
        analyzeLoading.value = false;
    }
};

const fetchAllData = () => {
    if (window.AndroidBridge && window.AndroidBridge.fetchAllHealthMetrics) {
        syncLoading.value = true;
        syncMessage.value = '삼성 헬스 데이터 동기화 중...';
        currentDate.value = new Date(); // Update to today when starting sync
        window.AndroidBridge.fetchAllHealthMetrics();
    } else {
        console.warn("AndroidBridge not found. Browser mode: No mock data will be stored.");
        syncMessage.value = '모바일 기기에서만 연동이 가능합니다.';
        setTimeout(() => syncMessage.value = '', 3000);
    }
};

// Callback from Android
window.onReceiveAllHealthData = (dataString) => {

    syncLoading.value = false;
    
    if (!dataString || dataString === "null") {
        syncMessage.value = '삼성 헬스 권한을 확인해주세요. 권한 승인 후 다시 동기화 버튼을 눌러주세요.';
        setTimeout(() => syncMessage.value = '', 5000);
        return;
    }

    try {
        const data = JSON.parse(dataString);
        const now = new Date();
        const localIso = new Date(now.getTime() - (now.getTimezoneOffset() * 60000)).toISOString().split('.')[0];
        
        // Map snake_case from Android to camelCase for Backend
        const mappedData = {
            recordDate: (data.record_date ? data.record_date.replace(' ', 'T') : localIso).split('.')[0],
            steps: data.steps,
            restingHeartRate: data.resting_heart_rate,
            averageHeartRate: data.average_heart_rate,
            maxHeartRate: data.max_heart_rate,
            sleepTotalMinutes: data.sleep_total_minutes,
            sleepDeepMinutes: data.sleep_deep_minutes,
            sleepLightMinutes: data.sleep_light_minutes,
            sleepRemMinutes: data.sleep_rem_minutes,
            bloodOxygen: data.blood_oxygen,
            bloodGlucose: data.blood_glucose,
            systolicPressure: data.systolic_pressure,
            diastolicPressure: data.diastolic_pressure,
            activeCalories: data.active_calories,
            activeMinutes: data.active_minutes
        };
        

        healthStore.latestMetrics = mappedData; // Update store immediately for UI feedback
        currentDate.value = now; // Ensure UI reflects today
        syncMessage.value = '데이터를 서버에 저장 중...';
        uploadToBackend(mappedData);
    } catch (e) {
        console.error("Parse/Mapping Error:", e);
        syncMessage.value = '데이터 처리 중 오류 발생: ' + e.message;
        setTimeout(() => syncMessage.value = '', 3000);
    }
};

const uploadToBackend = async (mappedData) => {
    const groupId = familyStore.selectedFamily?.id;
    if (!groupId) return;

    try {
        await healthService.saveHealthMetrics(groupId, [mappedData]);

        // Check if some desired data is missing
        const hasMissingData = !mappedData.steps || !mappedData.activeMinutes || !mappedData.sleepTotalMinutes;
        
        if (hasMissingData) {
            syncMessage.value = '데이터가 저장되었지만, 일부 항목(걸음 수 등)을 가져오지 못했습니다.';
        } else {
            syncMessage.value = '모든 데이터가 정상적으로 동기화되었습니다.';
        }

        fetchLatestData(); // Refresh after upload
        setTimeout(() => syncMessage.value = '', 5000);
    } catch (error) {
        console.error("Upload failed details:", error);
        syncMessage.value = '서버 저장에 실패했습니다.';
        setTimeout(() => syncMessage.value = '', 3000);
    }
};

// Aliases for Android callbacks
window.onReceiveHealthData = window.onReceiveAllHealthData;
window.onReceiveSteps = window.onReceiveAllHealthData;
window.onReceiveSleep = window.onReceiveAllHealthData;

onMounted(async () => {
    if (route.query.scrollTo === 'analysis') {
        const checkElement = setInterval(() => {
            const el = document.getElementById('ai-analysis');
            if (el) {
                el.scrollIntoView({ behavior: 'smooth' });
                clearInterval(checkElement);
            }
        }, 100);
        setTimeout(() => clearInterval(checkElement), 3000); // safety clear
    } else {
        window.scrollTo(0, 0);
    }

    if (!familyStore.selectedFamily) {
        await familyStore.fetchFamilies();
    }
    
    // Fetch members to determine role
    if (familyStore.selectedFamily?.id) {
        try {
            const response = await api.get(`/families/${familyStore.selectedFamily.id}/members`);
            members.value = response.data;
        } catch (e) {
            console.error("Failed to fetch members:", e);
        }
    }

    fetchLatestData();
});

// Re-fetch data when the selected family group changes
watch(() => familyStore.selectedFamily?.id, async (newFamilyId) => {
    if (!newFamilyId) {
        members.value = [];
        return;
    }

    try {
        // Fetch members for the new family to update permissions
        const response = await api.get(`/families/${newFamilyId}/members`);
        members.value = response.data;
    } catch (e) {
        console.error("Failed to fetch members for the new group:", e);
        members.value = [];
    }

    // Fetch the latest health data for the new group
    fetchLatestData();
}, { immediate: false });

onUnmounted(() => {
    delete window.onReceiveAllHealthData;
    delete window.onReceiveHealthData;
    delete window.onReceiveSteps;
    delete window.onReceiveSleep;
});
</script>

<style scoped>
.material-symbols-outlined {
    font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
.animate-spin {
    animation: spin 1s linear infinite;
}
@keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
}
@keyframes fade-in {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
.animate-fade-in {
  animation: fade-in 0.2s ease-out;
}
</style>
