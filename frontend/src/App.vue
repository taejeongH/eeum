<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import GlobalConfirmModal from '@/components/common/GlobalConfirmModal.vue'

const userStore = useUserStore()
const router = useRouter()

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

  // 5. [NEW] Android FCM Token 연동 (Retry Logic 추가)
  const syncFcmToken = async (retryCount = 0) => {
      if (retryCount > 10) { // 최대 10번 (10초) 시도

         return;
      }

      if (window.AndroidBridge && window.AndroidBridge.getFcmToken) {
          try {
              const fcmToken = window.AndroidBridge.getFcmToken();
              if (fcmToken && fcmToken.length > 0) {
                  const { updateFcmToken } = await import('@/services/api');
                  await updateFcmToken(fcmToken);
                  return; // 성공 시 종료
              }
          } catch (e) {

          }
      }
      
      // 토큰이 없으면 1초 뒤 재시도
      setTimeout(() => syncFcmToken(retryCount + 1), 1000);
  };

  // 6. [NEW] 알림 클릭 처리 (Android Bridge)
  const checkNotificationFromNative = async (retryCount = 0) => {
    if (window.AndroidBridge && window.AndroidBridge.consumeNotificationId) {
      try {
        const notificationId = window.AndroidBridge.consumeNotificationId();
        if (notificationId) {
           // API 호출을 위해 axios/api 인스턴스 가져오기
           if (!userStore.profile) {
               try {
                   await userStore.fetchUser();
               } catch (e) {

               }
           }
           const currentUserId = userStore.profile?.id;

           if (currentUserId) {
              const { default: api } = await import('@/services/api'); 
              await api.post('/notifications/read', {
                 notificationId: Number(notificationId),
                 userId: currentUserId
              });
           } else {

           }
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
          (async () => {
             try {
                 // 1. 토큰 확인 & 복구
                 let token = localStorage.getItem('accessToken');
                 if (!token) {
                     if (window.AndroidBridge && window.AndroidBridge.getAccessToken) {
                         token = window.AndroidBridge.getAccessToken();
                         if (token && token !== "null" && token.length > 0) {
                             localStorage.setItem('accessToken', token);
                         } else {

                         }
                     }
                 }

                 // 2. 유저 정보 확인 & 로드
                 if (!userStore.profile) {
                     await userStore.fetchUser();
                 }
                 
                 const currentUserId = userStore.profile?.id;

                 if (currentUserId) {
                     const { default: api } = await import('@/services/api');
                     
                     await api.post('/notifications/read', {
                         notificationId: Number(notificationId),
                         userId: currentUserId
                     });
                     
                 } else {

                 }
             } catch(e) {

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