<template>
  <div class="min-h-screen bg-background-light flex flex-col items-center">
    <header class="w-full p-4 flex items-center bg-white shadow-sm h-[60px]">
        <button @click="$router.back()" class="p-2 rounded-full hover:bg-gray-100 transition-colors">
            <span class="material-symbols-outlined text-[#1c140d]">arrow_back</span>
        </button>
        <h1 class="ml-2 text-xl font-bold text-[#1c140d]">건강 상세</h1>
    </header>
    
    <div class="flex-1 w-full max-w-md p-6 flex flex-col items-center justify-start space-y-8 mt-4">
      <!-- Heart Rate Card -->
      <div class="w-full bg-white rounded-2xl shadow-md p-6 flex flex-col items-center space-y-4">
        <div class="flex items-center space-x-2 text-[#FF5252]">
           <span class="material-symbols-outlined text-4xl">monitor_heart</span>
           <span class="text-lg font-semibold">최근 심박수</span>
        </div>
        
        <div class="text-center">
            <p v-if="heartRate" class="text-5xl font-bold text-[#1c140d]">
                {{ heartRate }} <span class="text-xl text-gray-500 font-normal">bpm</span>
            </p>
            <p v-else class="text-gray-400 text-lg">
                {{ statusMessage }}
            </p>
        </div>
        
        <p class="text-xs text-gray-400" v-if="lastUpdated">
            마지막 업데이트: {{ lastUpdated }}
        </p>

        <button 
            @click="fetchHeartRate" 
            :disabled="isLoading"
            class="w-full py-3 px-6 bg-[#FF5252] text-white rounded-xl font-medium shadow-sm active:scale-95 transition-transform flex items-center justify-center space-x-2"
        >
            <span v-if="isLoading" class="animate-spin material-symbols-outlined text-sm">progress_activity</span>
            <span>{{ isLoading ? '측정 데이터 가져오는 중...' : '지금 측정하기' }}</span>
        </button>
      </div>

       <div class="w-full bg-white rounded-2xl p-4 shadow-sm">
           <p class="text-sm text-gray-500 leading-relaxed">
               * 삼성 헬스 앱에서 측정된 최신 심박수 데이터를 가져옵니다.
               <br>
               (데이터가 안 보이면 삼성 헬스 권한을 확인해주세요)
           </p>
       </div>

      <!-- Add Steps Card -->
      <div class="w-full bg-white rounded-2xl shadow-md p-6 flex flex-col items-center space-y-4">
        <div class="flex items-center space-x-2 text-[#4CAF50]">
           <span class="material-symbols-outlined text-4xl">directions_walk</span>
           <span class="text-lg font-semibold">오늘 걸음 수</span>
        </div>
        
        <div class="text-center">
            <p v-if="stepsData !== null" class="text-5xl font-bold text-[#1c140d]">
                {{ stepsData }} <span class="text-xl text-gray-500 font-normal">걸음</span>
            </p>
            <p v-else class="text-gray-400 text-lg">
                {{ stepsStatus }}
            </p>
        </div>
        <button 
            @click="fetchSteps" 
            class="mt-2 text-sm text-[#4CAF50] underline focus:outline-none"
        >
            새로고침
        </button>
      </div>

      <!-- Add Sleep Card -->
      <div class="w-full bg-white rounded-2xl shadow-md p-6 flex flex-col items-center space-y-4">
        <div class="flex items-center space-x-2 text-[#673AB7]">
           <span class="material-symbols-outlined text-4xl">bedtime</span>
           <span class="text-lg font-semibold">어젯밤 수면</span>
        </div>
        
        <div class="text-center">
            <div v-if="sleepData !== null">
                <p class="text-5xl font-bold text-[#1c140d]">
                    {{ Math.floor(sleepData / 60) }}<span class="text-xl text-gray-500 font-normal">시간</span>
                    {{ sleepData % 60 }}<span class="text-xl text-gray-500 font-normal">분</span>
                </p>
            </div>
            <p v-else class="text-gray-400 text-lg">
                {{ sleepStatus }}
            </p>
        </div>
        <button 
            @click="fetchSleep" 
            class="mt-2 text-sm text-[#673AB7] underline focus:outline-none"
        >
             새로고침
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const heartRate = ref(null);
const stepsData = ref(null);
const sleepData = ref(null);

const statusMessage = ref('데이터를 불러오는 중...');
const stepsStatus = ref('준비 중');
const sleepStatus = ref('준비 중');

const isLoading = ref(false);
const lastUpdated = ref('');

const fetchAllData = () => {
    fetchHeartRate();
    fetchSteps();
    fetchSleep();
};

const fetchHeartRate = () => {
    if (window.AndroidBridge && window.AndroidBridge.fetchHeartRate) {
        isLoading.value = true;
        statusMessage.value = '요청 중...';
        window.AndroidBridge.fetchHeartRate();
    } else {
        mockHeartRate();
    }
};

const fetchSteps = () => {
    if (window.AndroidBridge && window.AndroidBridge.fetchSteps) {
        stepsStatus.value = '요청 중...';
        window.AndroidBridge.fetchSteps();
    } else {
        mockSteps();
    }
};

const fetchSleep = () => {
    if (window.AndroidBridge && window.AndroidBridge.fetchSleep) {
        sleepStatus.value = '요청 중...';
        window.AndroidBridge.fetchSleep();
    } else {
         mockSleep();
    }
};

// Mocks
const mockHeartRate = () => {
    console.warn("Bridge not found, mocking HeartRate");
    setTimeout(() => {
        window.onReceiveHealthData(JSON.stringify({ heart_rate: 88 }));
    }, 1000);
};
const mockSteps = () => {
    setTimeout(() => {
        window.onReceiveSteps(JSON.stringify({ steps: 5432 }));
    }, 1200);
};
const mockSleep = () => {
    setTimeout(() => {
        window.onReceiveSleep(JSON.stringify({ sleep_minutes: 450 })); // 7h 30m
    }, 1400);
};

// Callbacks
window.onReceiveSteps = (dataString) => {
    console.log("Steps Data:", dataString);
    if (!dataString || dataString === "null") {
        stepsStatus.value = '기록 없음';
        return;
    }
    try {
        const data = JSON.parse(dataString);
        if (data.steps !== undefined) {
            stepsData.value = data.steps;
        }
    } catch (e) { console.error(e); stepsStatus.value = '에러'; }
};

window.onReceiveSleep = (dataString) => {
    console.log("Sleep Data:", dataString);
    if (!dataString || dataString === "null") {
        sleepStatus.value = '기록 없음';
        return;
    }
    try {
        const data = JSON.parse(dataString);
        if (data.sleep_minutes !== undefined) {
            sleepData.value = data.sleep_minutes;
        }
    } catch (e) { console.error(e); sleepStatus.value = '에러'; }
};


// Global callback for Android to call
window.onReceiveHealthData = (dataString) => {
    console.log("Received Health Data from Native:", dataString);
    isLoading.value = false;
    
    if (!dataString || dataString === "null") {
        statusMessage.value = '측정된 데이터가 없습니다.';
        return;
    }

    try {
        const data = JSON.parse(dataString);
        // data structure depends on SDK response, assuming typical Samsung Health structure or simplified logic
        // If the native code sends the raw SDK object, we might need to inspect it. 
        // Based on previous SamsungHealthManager.kt, it sends `Gson().toJson(latestData)`.
        
        // Let's assume 'heart_rate' or similar field exists, or 'count' if valid.
        // If it's the raw HealthData object, we usually look for specific fields.
        // For HeartRate, standard fields usually include 'heart_rate', 'heart_beat_count', 'bpm' etc.
        // Let's handle generic parsing or look for common keys.
        
        if (data.heart_rate) {
             heartRate.value = Math.round(data.heart_rate);
        } else if (data.bpm) {
             heartRate.value = Math.round(data.bpm);
        } else {
            // Fallback: dump the raw JSON to see what we got (for debugging)
             // Or if it's just a number
            if(typeof data === 'number') {
                heartRate.value = data;
            } else {
                 // Try finding any number in the object
                 const val = Object.values(data).find(v => typeof v === 'number');
                 heartRate.value = val ? Math.round(val) : 'N/A';
            }
        }

        const now = new Date();
        lastUpdated.value = `${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}`;
        
    } catch (e) {
        console.error("Failed to parse health data:", e);
        statusMessage.value = '데이터 처리 오류';
    }
};

onMounted(() => {
    // Auto-fetch on mount
    fetchAllData();
});

onUnmounted(() => {
    // Cleanup global callback to prevent memory leaks or unwanted calls
    delete window.onReceiveHealthData;
    delete window.onReceiveSteps;
    delete window.onReceiveSleep;
});
</script>

<style scoped>
.material-symbols-outlined {
    font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
