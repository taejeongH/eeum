<script setup>
import { onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import { useEmergencyStore } from './stores/emergency'
import GlobalConfirmModal from '@/components/common/GlobalConfirmModal.vue'
import GlobalEmergencyModal from '@/components/common/GlobalEmergencyModal.vue'

const userStore = useUserStore()
const emergencyStore = useEmergencyStore()
const router = useRouter()

// 5. [NEW] Android FCM Token 연동 (Retry Logic 추가)
const syncFcmToken = async (retryCount = 0) => {
    // 로그인이 안되어 있으면 동기화 건너뜀 (인증 후 watcher가 다시 트리거함)
    if (!userStore.isAuthenticated) {
        console.log("FCM: Not authenticated, skipping sync.");
        return;
    }

    if (retryCount > 10) { // 최대 10번 (10초) 시도
        console.log("FCM: Max retry reached.");
        return;
    }

    if (window.AndroidBridge && window.AndroidBridge.getFcmToken) {
        try {
            const fcmToken = window.AndroidBridge.getFcmToken();
            if (fcmToken && fcmToken.length > 0) {
                console.log("FCM: Token found:", fcmToken.substring(0, 10) + "...");
                const { updateFcmToken } = await import('@/services/api');
                const response = await updateFcmToken(fcmToken);
                console.log("✅ FCM Token Synced Successfully:", response.status);
                return; // 성공 시 종료
            } else {
                console.log("FCM: Token is empty or null from bridge.");
            }
        } catch (e) {
            console.error("FCM: Sync failed. Status:", e.response?.status);
            console.error("FCM: Error data:", JSON.stringify(e.response?.data || e.message || e));
        }
    }
    
    // 토큰이 없거나 브릿지가 안 잡히면 1초 뒤 재시도
    setTimeout(() => syncFcmToken(retryCount + 1), 1000);
};

// [NEW] 로그인 성공 시 FCM 토큰 동기화 트리거
watch(() => userStore.isAuthenticated, (isAuth) => {
  if (isAuth) {
    console.log("FCM: User authenticated, triggering sync...");
    syncFcmToken();
  }
});

// [NEW] Native에서 직접 토큰을 밀어넣어줄 때 호출되는 함수
window.onFcmTokenReceived = (fcmToken) => {
  if (fcmToken && userStore.isAuthenticated) {
    console.log("FCM: Received from native, syncing...");
    import('@/services/api').then(({ updateFcmToken }) => {
      updateFcmToken(fcmToken);
    });
  }
};

  window.handlePushRoute = (route) => {
    console.log("FCM handlePushRoute called with:", route);
    if (route) {
      // 만약 응급 상황 경로라면 모달을 띄웁니다.
      if (route.includes('/emergency')) {
        console.log("FCM: Emergency detected, opening modal...");
        emergencyStore.open();
        // 홈으로 이동하여 모달이 홈 위에서 보이게 함
        router.push('/home');
        return;
      }
      
      try {
      console.log("FCM: Current path before push:", router.currentRoute.value.path);
      router.push(route);
      console.log("FCM: router.push executed for:", route);
    } catch (e) {
      console.error("FCM: router.push failed:", e);
    }
  } else {
    console.warn("FCM: handlePushRoute received empty route");
  }
};

onMounted(async () => {
  // 1. URL에서 accessToken 추출 (Hash 모드 대응)
  const fullUrl = window.location.href;
  const tokenMatch = fullUrl.match(/accessToken=([^&?#]*)/);
  const token = tokenMatch ? tokenMatch[1] : null;

  if (token) {
    // 2. 토큰 저장 (LocalStorage + Native)
    localStorage.setItem('accessToken', token);
    
    // [NEW] 앱 네이티브 저장소(SharedPreferences)에도 백업
    if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
        window.AndroidBridge.saveAccessToken(token);
    }
    console.log("✅ 새로운 토큰 저장 성공!");

    // 3. [핵심] 쿼리 파라미터를 지우고 프로필 페이지로 부드럽게 이동
    // window.location.href 대신 router.replace를 써야 흐름이 안 끊깁니다.
    await router.replace('/home');
  }

  // 4. [NEW] 네이티브 세션 복구 및 동기화 (Self-Healing)
  const restoreSession = async (retryCount = 0) => {
      const localToken = localStorage.getItem('accessToken');
      
      // 1) 로컬에 토큰이 있는 경우 -> 네이티브에도 백업(동기화)
      if (localToken) {
          if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
             window.AndroidBridge.saveAccessToken(localToken);
          }
          try {
              await userStore.fetchUser();
              console.log("✅ 유저 정보 로드 완료");
              // 유효한 토큰이면 홈으로 이동 (로그인 페이지에 갇히지 않도록)
              if (router.currentRoute.value.path === '/login' || router.currentRoute.value.path === '/') {
                  router.replace('/home');
              }
          } catch (e) { 
              console.error("❌ 유저 정보 로드 실패 (토큰 만료 등):", e);
              // 토큰이 유효하지 않으면 삭제하여 로그인 페이지로 갈 수 있게 함
              localStorage.removeItem('accessToken');
              router.replace('/login');
          }
          return;
      }

      // 2) 로컬에는 없는데 네이티브에는 있는지 확인 (복구 시도)
      if (window.AndroidBridge && window.AndroidBridge.getAccessToken) {
          try {
              const nativeToken = window.AndroidBridge.getAccessToken();
              // nativeToken이 'null' 문자열이거나 실제 null일 수 있음
              if (nativeToken && nativeToken !== "null" && nativeToken.length > 0) {
                  localStorage.setItem('accessToken', nativeToken);
                  
                  // 유저 정보 로드 시도
                  await userStore.fetchUser();
                  
                  // 🎉 복구 성공 시 홈으로 이동
                  router.replace('/home');
                  return;
              }
          } catch (e) {
             console.error("❌ Native Token Restore Failed", e);
          }
      }

      // 아직 브릿지가 없거나 토큰이 없으면 재시도 (최대 5번, 0.5초 간격)
      if (retryCount < 5) {
          setTimeout(() => restoreSession(retryCount + 1), 500);
      }
  };

  // 세션 복구 시도 시작
  restoreSession();

  // 6. [NEW] 알림 클릭 처리 (Android Bridge)
  const checkNotificationFromNative = async (retryCount = 0) => {
    if (window.AndroidBridge && window.AndroidBridge.consumeNotificationId) {
      try {
        const notificationId = window.AndroidBridge.consumeNotificationId();
        if (notificationId) {
           console.log("✅ Consumed Notification ID from Native:", notificationId);
           
           // onNativeNotification 호출하여 통합 처리
           if (window.onNativeNotification) {
               window.onNativeNotification(notificationId);
           }
        } else {

        }
      } catch (e) {

      }
    } else {
       // 브릿지가 아직 로드되지 않았을 수 있으므로 잠시 후 재시도
       if (retryCount < 5) {
          setTimeout(() => checkNotificationFromNative(retryCount + 1), 500);
       }
    }
  };

  // 7. [NEW] 앱이 백그라운드에서 돌아왔을 때 알림 체크 (visibilitychange + focus)
  const handleAppVisible = () => {
    if (document.visibilityState === 'visible') {
      checkNotificationFromNative();
      // Race Condition 방지용 재시도
      setTimeout(checkNotificationFromNative, 1000);
      setTimeout(checkNotificationFromNative, 2000);
    }
  };

  // 8. [NEW] Native -> Notification Push 방식 지원
  window.onNativeNotification = (notificationId) => {
      
      if (notificationId) {
          console.log('onNativeNotification called with ID:', notificationId);
          
          // 백그라운드에서 알림 정보 가져오기 및 처리
          (async () => {
             try {
                 // 토큰 확인 & 복구
                 let token = localStorage.getItem('accessToken');
                 if (!token) {
                     if (window.AndroidBridge && window.AndroidBridge.getAccessToken) {
                         token = window.AndroidBridge.getAccessToken();
                         if (token && token !== "null" && token.length > 0) {
                             localStorage.setItem('accessToken', token);
                         }
                     }
                 }

                 // 유저 정보 확인 & 로드
                 if (!userStore.profile) {
                     await userStore.fetchUser();
                 }
                 
                 const currentUserId = userStore.profile?.id;

                 if (currentUserId) {
                     const { default: api } = await import('@/services/api');
                     
                     // 알림 정보 가져오기
                     const notificationResponse = await api.get(`/notifications/${notificationId}`);
                     const notificationInfo = notificationResponse.data;
                     
                     console.log('Notification info:', notificationInfo);
                     
                     // 해당 familyId의 그룹 선택
                     if (notificationInfo.familyId) {
                         const { useFamilyStore } = await import('@/stores/family');
                         const familyStore = useFamilyStore();
                         
                         // 패밀리 목록 가져오기
                         await familyStore.fetchFamilies();
                         
                         // 해당 familyId의 그룹 찾아서 선택
                         const targetFamily = familyStore.families.find(f => f.id === notificationInfo.familyId);
                         if (targetFamily) {
                             familyStore.selectFamily(targetFamily);
                             console.log('Selected family:', targetFamily.name);
                         }
                     }
                     
                     // Emergency modal 열기
                     emergencyStore.open({
                         groupName: '우리 가족',
                         dependentName: '피부양자',
                         type: 'FALL',
                         location: null
                     });
                     
                     // 홈으로 이동
                     router.push('/home');
                     
                     // 알림 읽음 처리
                     await api.post('/notifications/read', {
                         notificationId: Number(notificationId),
                         userId: currentUserId
                     });
                     
                     console.log('Notification marked as read:', notificationId);
                 } else {
                     console.warn('User not authenticated, skipping notification read');
                     
                     // 인증 실패해도 모달은 열기
                     emergencyStore.open({
                         groupName: '우리 가족',
                         dependentName: '피부양자',
                         type: 'FALL',
                         location: null
                     });
                     router.push('/home');
                 }
             } catch(e) {
                 console.error('Error in onNativeNotification:', e);
                 
                 // 에러 발생해도 모달은 열기
                 emergencyStore.open({
                     groupName: '우리 가족',
                     dependentName: '피부양자',
                     type: 'FALL',
                     location: null
                 });
                 router.push('/home');
             }
          })();
      }
  };

  // 초기 실행
  setTimeout(() => checkNotificationFromNative(), 500);
  setTimeout(() => syncFcmToken(), 1000);
});
</script>

<template>
  <div id="app">
    <router-view />
    <GlobalConfirmModal />
    <GlobalEmergencyModal />
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