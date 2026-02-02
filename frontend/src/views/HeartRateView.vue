<template>
  <div class="heart-rate-view">
    <div class="header">
      <h2>실시간 심박수</h2>
    </div>

    <div class="content">
      <div class="heart-animation">
        <div class="heart" :class="{ beating: isMeasuring }">❤️</div>
      </div>
      
      <div class="data-display">
        <span class="value">{{ heartRate }}</span>
        <span class="unit">BPM</span>
      </div>

      <div class="status-message">
        <p v-if="isMeasuring">워치에서 측정 중입니다...</p>
        <p v-else>워치 연결을 확인해주세요.</p>
        <p class="last-update" v-if="lastUpdate">마지막 수신: {{ lastUpdate }}</p>
      </div>

      <div class="controls">
        <button class="btn start" @click="startMonitoring">측정 시작</button>
        <button class="btn stop" @click="stopMonitoring">측정 종료</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const heartRate = ref(0);
const isMeasuring = ref(false);
const lastUpdate = ref('');
let resetTimer = null;

const updateHeartRate = (hr) => {
  heartRate.value = Math.round(Number(hr));
  isMeasuring.value = true;
  const now = new Date();
  lastUpdate.value = now.toLocaleTimeString();

  // Reset timer to stop animation if no data received for 5 seconds
  if (resetTimer) clearTimeout(resetTimer);
  resetTimer = setTimeout(() => {
    isMeasuring.value = false;
  }, 5000);
};

const startMonitoring = () => {
  if (window.AndroidBridge && window.AndroidBridge.startHeartRateMonitoring) {
    window.AndroidBridge.startHeartRateMonitoring();
    alert("워치에 측정 시작 신호를 보냈습니다.");
  } else {
    console.warn("Android Bridge not found");
  }
};

const stopMonitoring = () => {
  if (window.AndroidBridge && window.AndroidBridge.stopHeartRateMonitoring) {
    window.AndroidBridge.stopHeartRateMonitoring();
    isMeasuring.value = false;
    alert("워치에 측정 종료 신호를 보냈습니다.");
  } else {
    console.warn("Android Bridge not found");
  }
};

onMounted(() => {
  // Define global function for Native to call
  window.onNativeNotification = (id, type, familyId, title, message, groupName) => {
    console.log("Received Notification from Native:", id, type, title, message);
    
    // Check if it's a Heart Rate update
    // The Service emits: title="Heart Rate", message="74.0 BPM"
    if (title === "Heart Rate" || id === "HR_UPDATE") {
      // Extract number from message (e.g., "74.0 BPM" -> 74.0)
      const hrValue = parseFloat(message);
      if (!isNaN(hrValue)) {
        updateHeartRate(hrValue);
      }
    }
  };
});

onUnmounted(() => {
  if (resetTimer) clearTimeout(resetTimer);
  // Ideally, we might want to cleanup the global listener, 
  // but it might be used by other views too. 
  // Since onNativeNotification is a single global entry point, 
  // we usually leave it or replace it with a no-op if this is the only consumer.
  // For now, let's leave it as reassigning it might break other pages if they heavily rely on it.
  // A better pattern is an Event Bus, but for this specific task, this is sufficient.
});
</script>

<style scoped>
.heart-rate-view {
  padding: 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 80vh;
}

.header h2 {
  font-size: 1.5rem;
  font-weight: bold;
  margin-bottom: 40px;
  color: #333;
}

.heart-animation {
  margin-bottom: 20px;
}

.heart {
  font-size: 5rem;
  transition: transform 0.2s ease;
}

.heart.beating {
  animation: beat 1s infinite;
}

@keyframes beat {
  0% { transform: scale(1); }
  15% { transform: scale(1.3); }
  30% { transform: scale(1); }
  45% { transform: scale(1.15); }
  60% { transform: scale(1); }
}

.controls {
  margin-top: 40px;
  display: flex;
  gap: 20px;
}

.btn {
  padding: 15px 30px;
  border: none;
  border-radius: 30px;
  font-size: 1rem;
  font-weight: bold;
  cursor: pointer;
  transition: transform 0.1s;
}

.btn:active {
  transform: scale(0.95);
}

.btn.start {
  background-color: #e76f51;
  color: white;
}

.btn.stop {
  background-color: #f0f0f0;
  color: #666;
  border: 1px solid #ddd;
}

.data-display {
  display: flex;
  align-items: baseline;
  justify-content: center;
  margin-bottom: 20px;
}

.value {
  font-size: 4rem;
  font-weight: bold;
  color: #d32f2f;
}

.unit {
  font-size: 1.5rem;
  color: #666;
  margin-left: 10px;
}

.status-message {
  color: #888;
}

.last-update {
  font-size: 0.8rem;
  margin-top: 5px;
}
</style>
