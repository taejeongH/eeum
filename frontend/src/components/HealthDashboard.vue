<script setup>
import { useSamsungHealth } from '../composables/useSamsungHealth.js';

// 💡 여기서 반드시 fetchHeartRate를 꺼내와야 템플릿에서 쓸 수 있습니다.
const { heartRate, isLoading, fetchHeartRate } = useSamsungHealth();
</script>

<template>
  <div class="health-card">
    <h3 class="title">💓 심박수 측정</h3>

    <div v-if="heartRate" class="data-container">
      <p class="bpm-value">
        {{ heartRate.heart_rate }} <span class="unit">BPM</span>
      </p>
      <p class="time-stamp">측정 시간: {{ formatTime(heartRate.start_time) }}</p>
    </div>

    <div v-else class="no-data">
      <div class="warning-box">
        <p class="emoji">👵</p>
        <p class="main-text">어르신, 아직 기록이 없어요.</p>
        <p class="sub-text">워치를 손목에 꼭 차고 계신가요?</p>
      </div>
    </div>

    <button 
      @click="fetchHeartRate" 
      :disabled="isLoading"
      class="update-btn"
      :class="{ 'loading': isLoading }"
    >
      {{ isLoading ? '기록 찾는 중...' : '심박수 다시 확인하기' }}
    </button>
  </div>
</template>

<!-- <template>
  <div class="health-card">
    <h3>심박수 측정</h3>

    <div v-if="heartRate">
      <p class="bpm-value">{{ heartRate.heart_rate }} <span>BPM</span></p>
    </div>

    <div v-else class="no-data">
      <p>최근 측정된 심박수 데이터가 없습니다.</p>
    </div>

    <button 
      @click="fetchHeartRate" 
      :disabled="isLoading"
      class="update-btn"
    >
      {{ isLoading ? '데이터 조회 중...' : '심박수 업데이트' }}
    </button>
  </div>
</template> -->