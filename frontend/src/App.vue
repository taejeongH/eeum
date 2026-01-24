<script setup>
// 이전에 만든 대시보드 컴포넌트를 가져옵니다.
import HealthDashboard from './components/HealthDashboard.vue'
</script>

<template>
  <main>
    <HealthDashboard />
  </main>
</template>

<style>
/* 전체 화면 스타일링 */
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
}
</style>