<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './stores/user'

const userStore = useUserStore()
const router = useRouter()

onMounted(async () => {
  // 1. URL에서 accessToken 추출 (Hash 모드 대응)
  const fullUrl = window.location.href;
  const tokenMatch = fullUrl.match(/accessToken=([^&?]*)/);
  const token = tokenMatch ? tokenMatch[1] : null;

  if (token) {
    // 2. 토큰 저장
    localStorage.setItem('accessToken', token);
    console.log("✅ 새로운 토큰 저장 성공!");

    // 3. [핵심] 쿼리 파라미터를 지우고 프로필 페이지로 부드럽게 이동
    // window.location.href 대신 router.replace를 써야 흐름이 안 끊깁니다.
    await router.replace('/home');
  }

  // 4. 저장된 토큰이 있다면 유저 정보 불러오기
  const savedToken = localStorage.getItem('accessToken');
  if (savedToken) {
    try {
      await userStore.fetchUser();
      console.log("✅ 유저 정보 로드 완료");
    } catch (error) {
      console.error("❌ 유저 정보 로드 실패:", error);
    }
  }
});
</script>

<template>
  <div id="app">
    <router-view />
  </div>
</template>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
}
</style>