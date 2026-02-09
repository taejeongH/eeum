<template>
  <div class="flex items-center justify-center min-h-screen">
    <div class="text-center">
      <p class="text-lg text-gray-600">로그아웃 중입니다...</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { Logger } from '@/services/logger';
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
    
    await logout();
  } catch (e) {
    Logger.error('Logout failed:', e);
  } finally {
    
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
    
    
    localStorage.removeItem('health-store');

    
    if (window.AndroidBridge) {
      if (window.AndroidBridge.logout) window.AndroidBridge.logout();
      if (window.AndroidBridge.saveAccessToken) window.AndroidBridge.saveAccessToken(""); 
    }
    
    
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
    
    
    router.replace('/login');
  }
});
</script>
