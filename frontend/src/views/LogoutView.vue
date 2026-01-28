<template>
  <div class="flex items-center justify-center min-h-screen">
    <div class="text-center">
      <p class="text-lg text-gray-600">로그아웃 중입니다...</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { logout } from '@/services/api';

const router = useRouter();
const userStore = useUserStore();

onMounted(async () => {
  try {
    // 1. 서버 로그아웃 요청 (토큰 만료 등)
    await logout();
  } catch (e) {
    console.error('Logout failed:', e);
  } finally {
    // 2. 클라이언트 상태 초기화
    localStorage.removeItem('accessToken');
    sessionStorage.removeItem('accessToken');
    userStore.clearUser();
    
    // 3. 로그인 페이지로 이동
    router.replace('/login');
  }
});
</script>
