<script setup>
import { onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import { useEmergencyStore } from './stores/emergency'
import { useFamilyStore } from './stores/family'
import { useNotificationStore } from './stores/notification'
import GlobalConfirmModal from '@/components/common/GlobalConfirmModal.vue'
import GlobalEmergencyModal from '@/components/common/GlobalEmergencyModal.vue'
import GlobalNotificationModal from '@/components/common/GlobalNotificationModal.vue'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'

const userStore = useUserStore()
const emergencyStore = useEmergencyStore()
const familyStore = useFamilyStore()
const notificationStore = useNotificationStore()
const router = useRouter()


const syncFcmToken = async (retryCount = 0) => {
    
    if (!userStore.isAuthenticated) {

        return;
    }

    if (retryCount > 10) { 

        return;
    }

    if (window.AndroidBridge && window.AndroidBridge.getFcmToken) {
        try {
            const fcmToken = window.AndroidBridge.getFcmToken();
            if (fcmToken && fcmToken.length > 0) {

                const { updateFcmToken } = await import('@/services/api');
                const response = await updateFcmToken(fcmToken);

                return; 
            } else {

            }
        } catch (e) {
            Logger.error("FCM: Sync failed. Status:", e.response?.status);
            Logger.error("FCM: Error data:", JSON.stringify(e.response?.data || e.message || e));
        }
    }
    
    
    setTimeout(() => syncFcmToken(retryCount + 1), 1000);
};


watch(() => userStore.isAuthenticated, async (isAuth) => {
  if (isAuth) {

    syncFcmToken();
    await familyStore.fetchFamilies();
  }
});


window.onFcmTokenReceived = (fcmToken) => {
  if (fcmToken && userStore.isAuthenticated) {

    import('@/services/api').then(({ updateFcmToken }) => {
      updateFcmToken(fcmToken);
    });
  }
};

  window.handlePushRoute = (route) => {

    if (route) {
      
      if (route.includes('/emergency')) {

        emergencyStore.open();
        return;
      }
      
      try {

      router.push(route);

    } catch (e) {
      Logger.error("FCM: router.push failed:", e);
    }
  } else {
    Logger.warn("FCM: handlePushRoute received empty route");
  }
};

onMounted(async () => {
  
  const fullUrl = window.location.href;
  const tokenMatch = fullUrl.match(/accessToken=([^&?#]*)/);
  const token = tokenMatch ? tokenMatch[1] : null;

  if (token) {
    
    localStorage.setItem('accessToken', token);
    
    
    if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
        window.AndroidBridge.saveAccessToken(token);
    }


    
    
    await router.replace('/home');
  }

  
  const restoreSession = async (retryCount = 0) => {
      
      const uiStore = (await import('./stores/ui')).useUiStore();
      if (retryCount === 0) uiStore.resetLoading();
      
      
      let token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
      
      
      if (token && token !== "null") {
          if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
             window.AndroidBridge.saveAccessToken(token);
          }
          try {
              await userStore.fetchUser();
              await familyStore.fetchFamilies();
              
              
              const currentPath = router.currentRoute.value.path;
              if (currentPath === '/login' || currentPath === '/') {
                  router.replace('/home');
              }
          } catch (e) { 
              Logger.error("유저 정보 로드 실패 (토큰 만료 등):", e);
              localStorage.removeItem('accessToken');
              sessionStorage.removeItem('accessToken');
              if (window.AndroidBridge?.saveAccessToken) window.AndroidBridge.saveAccessToken("");
              router.replace('/login');
          }
          return;
      }

      
      if (window.AndroidBridge && window.AndroidBridge.getAccessToken) {
          try {
              const nativeToken = window.AndroidBridge.getAccessToken();
              
              if (nativeToken && nativeToken !== "null" && nativeToken.length > 0) {
                  
                  localStorage.setItem('accessToken', nativeToken);
                  
                  
                  await userStore.fetchUser();
                  await familyStore.fetchFamilies();
                  
                  
                  router.replace('/home');
                  return;
              }
          } catch (e) {
             Logger.error("Native Token Restore Failed", e);
             localStorage.removeItem('accessToken');
             sessionStorage.removeItem('accessToken');
             if (window.AndroidBridge?.saveAccessToken) window.AndroidBridge.saveAccessToken("");
             router.replace('/login');
             return; 
          }
      }

      
      if (retryCount < 5) {
          setTimeout(() => restoreSession(retryCount + 1), 500);
      }
  };

  
  restoreSession();

  
  const checkNotificationFromNative = async (retryCount = 0) => {
    if (window.AndroidBridge && window.AndroidBridge.consumeNotificationId) {
      try {
        const data = window.AndroidBridge.consumeNotificationId();
        if (data) {
           const parts = data.split('|');
           const id = parts[0];
           const type = parts[1];
           const familyId = parts[2];
           
           const title = parts[3] || "";
           const message = parts[4] || "";
           const groupName = parts[5] || "";
           
           
           if (window.onNativeNotification) {
               window.onNativeNotification(id, type, familyId, title, message, groupName);
           }
        }
      } catch (e) {
         
      }
    } else {
       
       if (retryCount < 5) {
          setTimeout(() => checkNotificationFromNative(retryCount + 1), 500);
       }
    }
  };

  
  const handleAppVisible = () => {
    if (document.visibilityState === 'visible') {
      checkNotificationFromNative();
      
      setTimeout(checkNotificationFromNative, 1000);
      setTimeout(checkNotificationFromNative, 2000);
    }
  };

  
  window.onNativeNotification = async (notificationId, type, familyId, title, message, groupName) => {
      if (!notificationId) return;

      
      try {
          
          if (familyStore.families.length === 0) {

              await familyStore.fetchFamilies();
          }

          
          if (familyId && familyStore.selectFamilyById) {
              familyStore.selectFamilyById(familyId);
          }
          
          const currentFamilyId = familyStore.selectedFamily?.id;

          
          if (type === 'EMERGENCY' || type === 'FALL') {
              
              
              let currentFamily = familyStore.selectedFamily;
              let dependent = null;

              if (familyId && familyStore.families.length > 0) {
                   const targetedFamily = familyStore.families.find(f => String(f.id) === String(familyId));
                   if (targetedFamily) currentFamily = targetedFamily;
              }

              if (!currentFamily || !currentFamily.members) {
                  const candidateFamily = familyStore.families.find(f => f.members && f.members.some(m => m.dependent || m.role === 'DEPENDENT' || m.relationship === '피부양자'));
                  if (candidateFamily) currentFamily = candidateFamily;
                  if (!currentFamily && familyStore.families.length > 0) currentFamily = familyStore.families[0];
              }

              if (currentFamily) {
                  if (familyStore.selectedFamily?.id !== currentFamily.id) familyStore.selectFamily(currentFamily);
                  if (currentFamily.members) dependent = currentFamily.members.find(m => m.dependent === true || m.role === 'DEPENDENT' || m.relationship === '피부양자');
              }

              
              let resolvedEventId = notificationId; 
              try {
                  
                  await notificationStore.fetchHistory(familyId, notificationId);
                  
                  
                  const matchingNoti = notificationStore.notifications.find(n => String(n.id) === String(notificationId));
                  if (matchingNoti && (matchingNoti.eventId || matchingNoti.event_id || matchingNoti.related_id)) {
                      resolvedEventId = matchingNoti.eventId || matchingNoti.event_id || matchingNoti.related_id;
                  } else {
                      Logger.warn(`[onNativeNotification] Could not find eventId in history for notificationId: ${notificationId}. Falling back to notificationId.`);
                  }
              } catch (err) {
                  Logger.error('[onNativeNotification] Failed to resolve real eventId:', err);
              }

              emergencyStore.open({
                  notificationId: notificationId,
                  eventId: resolvedEventId,
                  familyId: familyId,
                  groupName: groupName || currentFamily?.name || '가족 그룹',
                  
                  dependentName: message ? message : (dependent ? (dependent.relationship || dependent.name) : '대상자 정보 없음'), 
                  type: 'FALL',
                  location: null,
                  timestamp: Date.now(),
                  messageContent: message || title 
              });
          } else if (['ACTIVITY', 'OUTING', 'RETURN'].includes(type)) {
              
              const targetFamily = familyStore.families.find(f => String(f.id) === String(familyId)) || familyStore.selectedFamily;
              
              notificationStore.openModal({
                  notificationId: notificationId,
                  type: type,
                  groupName: groupName || targetFamily?.name || '우리 가족',
                  dependentName: targetFamily?.dependentName || '피부양자',
                  message: type === 'OUTING' ? '외출이 감지되었습니다.' : (type === 'RETURN' ? '귀가가 확인되었습니다.' : '활동이 감지되었습니다.'),
                  createdAt: new Date().toISOString()
              });
          }
          
          
          let token = localStorage.getItem('accessToken');
          if (!token && window.AndroidBridge?.getAccessToken) {
              token = window.AndroidBridge.getAccessToken();
              if (token && token !== "null") localStorage.setItem('accessToken', token);
          }

          if (!userStore.profile) await userStore.fetchUser();
          const currentUserId = userStore.profile?.id;

          if (currentUserId) {
              
              await notificationStore.markAsRead(Number(notificationId));
              
              
              if (currentFamilyId) {
                  await notificationStore.fetchHistory(currentFamilyId, notificationId);

              }
          }
      } catch (e) {
          Logger.error('FCM: Error in onNativeNotification processing:', e);
      }
  };

  
  setTimeout(() => checkNotificationFromNative(), 500);
  setTimeout(() => syncFcmToken(), 1000);

  
  window.onReceiveAllHealthData = async (dataString) => {

    try {
        if (!dataString || dataString === 'null' || dataString === '') {
            const hs = (await import('@/stores/health')).useHealthStore();
            hs.setSyncStatus(false, '건강 데이터를 가져오지 못했습니다.');
            return;
        }
        const healthStore = (await import('@/stores/health')).useHealthStore();
        const { mapSamsungHealthToBackend } = await import('@/utils/healthMapper');
        const { default: healthService } = await import('@/services/healthService');
        const { useFamilyStore } = await import('@/stores/family');
        const familyStore = useFamilyStore();
        
        if (!familyStore.selectedFamily) await familyStore.fetchFamilies();
        const familyId = familyStore.selectedFamily?.id;
        if (!familyId) throw new Error('그룹 정보를 찾을 수 없습니다.');

        healthStore.setSyncStatus(true, '데이터 처리 중...');
        const mappedData = mapSamsungHealthToBackend(dataString);
        if (!mappedData) throw new Error('데이터 변환에 실패했습니다.');

        await healthService.saveHealthMetrics(familyId, [mappedData]);
        await Promise.all([
          healthStore.fetchLatestMetrics(familyId),
          healthStore.fetchDailyReport(familyId, new Date().toISOString().split('T')[0])
        ]);
        healthStore.setSyncStatus(false, '성공적으로 동기화되었습니다.');
    } catch (e) {
        const hs = (await import('@/stores/health')).useHealthStore();
        hs.setSyncStatus(false, '동기화 오류: ' + (e.message || 'Error'));
    }
  };

  
  
  window.onNativeBackPressed = () => {
    const currentRouteName = router.currentRoute.value.name;
    const currentPath = router.currentRoute.value.path;

    
    
    if (currentRouteName === 'HomePage' || currentPath === '/home' || currentPath === '/') {
        if (window.AndroidBridge && window.AndroidBridge.finishApp) {
            window.AndroidBridge.finishApp();
        } else {
            Logger.warn("Back Pressed on Home, but no finishApp method found.");
        }
        return;
    }

    
    
    const middleLevelPages = ['FamilyMessages', 'GalleryPage', 'CalendarPage', 'DeviceManagement', 'HealthDetail'];
    if (middleLevelPages.includes(currentRouteName)) {
        router.push({ name: 'HomePage' });
        return;
    }

    
    
    
    router.back();
  };
});
</script>

<template>
  <div id="app">
    <router-view />
    <GlobalConfirmModal />
    <GlobalEmergencyModal />
    <GlobalNotificationModal />
    <LoadingOverlay />
  </div>
</template>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  background-color: white; 
  min-height: 100vh;
}
</style>