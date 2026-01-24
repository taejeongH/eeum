<script setup>
import { useSamsungHealth } from '../composables/useSamsungHealth.js';
// import { useSamsungHealth } from '@/composables/useSamsungHealth.js';
const { heartRate, steps, isLoading, fetchHeartRate, fetchSteps } = useSamsungHealth();
</script>

<template>
  <div>
    <button @click="fetchHeartRate" :disabled="isLoading">
      {{ isLoading ? '조회 중...' : '실시간 심박수 가져오기' }}
    </button>
    
    <div v-if="heartRate">
      <p>심박수: {{ heartRate.heart_rate }} BPM</p>
    </div>
  </div>

  <div class="data-card steps">
    <h3>오늘의 걸음수</h3>
    <div v-if="steps" class="result">
        <p class="value">{{ steps.count || 0 }} <span>걸음</span></p>
    </div>
    <div v-else class="no-data">데이터를 불러와주세요.</div>
</div>

<button @click="fetchSteps" :disabled="isLoading" class="fetch-btn steps-btn">
  걸음수 업데이트
</button>
</template>