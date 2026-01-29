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
    // 2. 토큰 저장 (LocalStorage + Native)
    localStorage.setItem('accessToken', token);
    
    // [NEW] 앱 네이티브 저장소(SharedPreferences)에도 백업
    if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
        window.AndroidBridge.saveAccessToken(token);
        console.log("✅ 토큰이 앱 네이티브 저장소에 백업되었습니다.");
    }
    
    console.log("✅ 새로운 토큰 저장 성공!");

    // 3. [핵심] 쿼리 파라미터를 지우고 프로필 페이지로 부드럽게 이동
    // window.location.href 대신 router.replace를 써야 흐름이 안 끊깁니다.
    await router.replace('/home');
  }

  // 4. [NEW] 네이티브 세션 복구 (Retry Logic 포함)
  const restoreSession = async (retryCount = 0) => {
      // 이미 로컬스토리지에 토큰이 있으면 복구할 필요 없음
      if (localStorage.getItem('accessToken')) {
          await userStore.fetchUser(); // 유저 정보는 로드
          return;
      }

      // AndroidBridge가 준비되었는지 확인
      if (window.AndroidBridge && window.AndroidBridge.getAccessToken) {
          try {
              const nativeToken = window.AndroidBridge.getAccessToken();
              // nativeToken이 'null' 문자열이거나 실제 null일 수 있음
              if (nativeToken && nativeToken !== "null" && nativeToken.length > 0) {
                  localStorage.setItem('accessToken', nativeToken);
                  console.log("♻️ 네이티브 저장소에서 토큰을 복구했습니다:", nativeToken);
                  
                  // 유저 정보 로드 시도
                  await userStore.fetchUser();
                  
                  // 🎉 복구 성공 시 홈으로 이동 (로그인 페이지에 갇혀있지 않도록)
                  router.replace('/home');
                  return;
              }
          } catch (e) {
              console.error("❌ Failed to restore Native Token:", e);
          }
      }

      // 아직 브릿지가 없거나 토큰이 없으면 재시도 (최대 5번, 0.5초 간격)
      if (retryCount < 5) {
          console.log(`⏳ Waiting for AndroidBridge... (${retryCount + 1}/5)`);
          setTimeout(() => restoreSession(retryCount + 1), 500);
      }
  };

  // 세션 복구 시도 시작
  restoreSession();

  // 5. [NEW] Android FCM Token 연동 (Retry Logic 추가)
  const syncFcmToken = async (retryCount = 0) => {
  // (기존 FCM sync 로직 유지...)
      if (retryCount > 10) { // 최대 10번 (10초) 시도
         console.warn("⚠️ FCM Token Fetch Timeout");
         return;
      }

      if (window.AndroidBridge && window.AndroidBridge.getFcmToken) {
          try {
              const fcmToken = window.AndroidBridge.getFcmToken();
              if (fcmToken && fcmToken.length > 0) {
                  console.log("📱 FCM Token from Android:", fcmToken);
                  const { updateFcmToken } = await import('@/services/api');
                  await updateFcmToken(fcmToken);
                  console.log("✅ FCM Token Updated on Server");
                  return; // 성공 시 종료
              } else {
                  console.log(`⏳ FCM Token not ready yet (Attempt ${retryCount + 1}). Retrying...`);
              }
          } catch (e) {
              console.error("❌ Failed to update FCM Token:", e);
          }
      }
      
      // 토큰이 없으면 1초 뒤 재시도
      setTimeout(() => syncFcmToken(retryCount + 1), 1000);
  };

  // FCM 토큰 동기화 시작 (조금 늦게 시작해도 됨)
  setTimeout(() => syncFcmToken(), 1000);
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