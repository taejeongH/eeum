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
import { logout } from '@/services/api';
import { useFamilyStore } from '@/stores/family';
import { useGroupSetupStore } from '@/stores/groupSetup';
import { useUserStore } from '@/stores/user';
import { useHealthStore } from '@/stores/health';
import { useEmergencyStore } from '@/stores/emergency';
import { useNotificationStore } from '@/stores/notification';

const router = useRouter();
const userStore = useUserStore();

onMounted(async () => {
  try {
    // 1. 서버 로그아웃 요청 (토큰 만료 등)
    await logout();
  } catch (e) {
    console.error('Logout failed:', e);
  } finally {
    // 2. 클라이언트 브라우저 스토리지 초기화
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
    
    // [Fix] persisted Pinia store 강제 삭제
    localStorage.removeItem('health-store');

    // 3. 모바일 네이티브 토큰 삭제
    if (window.AndroidBridge) {
      if (window.AndroidBridge.logout) window.AndroidBridge.logout();
      if (window.AndroidBridge.saveAccessToken) window.AndroidBridge.saveAccessToken(""); 
    }
    
    // 4. 모든 Pinia Store 초기화 (Ghost state 방지)
    userStore.clearUser();
    
    const familyStore = useFamilyStore();
    const setupStore = useGroupSetupStore();
    const healthStore = useHealthStore();
    const emergencyStore = useEmergencyStore();
    const notificationStore = useNotificationStore();
    
    familyStore.clearFamily();
    setupStore.reset();
    healthStore.reset();
    emergencyStore.close();
    notificationStore.clearNotifications();
    
    // 5. 로그인 페이지로 이동
    router.replace('/login');
  }
});
</script>
