<script setup>
import { onMounted } from 'vue'
import { useUserStore } from './stores/user'

const userStore = useUserStore()

// App.vue 수정
onMounted(async () => {
  // Hash 모드 대응: 현재 전체 URL에서 accessToken 추출
  const fullUrl = window.location.href;
  const tokenMatch = fullUrl.match(/accessToken=([^&?]*)/);
  const token = tokenMatch ? tokenMatch[1] : null;

  if (token) {
    localStorage.setItem('accessToken', token);
    // 주소창에서 토큰 제거 (정규표현식으로 깔끔하게)
    const cleanUrl = fullUrl.split('?')[0];
    window.location.href = cleanUrl;
    console.log("새로운 토큰 저장 성공!");
  }

  await userStore.fetchUser();
});
</script>

<template>
  <router-view />
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