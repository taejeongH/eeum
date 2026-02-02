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

const userStore = useUserStore()
const emergencyStore = useEmergencyStore()
const familyStore = useFamilyStore()
const notificationStore = useNotificationStore()
const router = useRouter()

// 5. [NEW] Android FCM Token 연동 (Retry Logic 추가)
const syncFcmToken = async (retryCount = 0) => {
    // 로그인이 안되어 있으면 동기화 건너뜀 (인증 후 watcher가 다시 트리거함)
    if (!userStore.isAuthenticated) {

        return;
    }

    if (retryCount > 10) { // 최대 10번 (10초) 시도

        return;
    }

    if (window.AndroidBridge && window.AndroidBridge.getFcmToken) {
        try {
            const fcmToken = window.AndroidBridge.getFcmToken();
            if (fcmToken && fcmToken.length > 0) {

                const { updateFcmToken } = await import('@/services/api');
                const response = await updateFcmToken(fcmToken);

                return; // 성공 시 종료
            } else {

            }
        } catch (e) {
            console.error("FCM: Sync failed. Status:", e.response?.status);
            console.error("FCM: Error data:", JSON.stringify(e.response?.data || e.message || e));
        }
    }
    
    // 토큰이 없거나 브릿지가 안 잡히면 1초 뒤 재시도
    setTimeout(() => syncFcmToken(retryCount + 1), 1000);
};

// [NEW] 로그인 성공 시 FCM 토큰 동기화 및 멤버 정보 트리거
watch(() => userStore.isAuthenticated, async (isAuth) => {
  if (isAuth) {

    syncFcmToken();
    await familyStore.fetchFamilies();
  }
});

// [NEW] Native에서 직접 토큰을 밀어넣어줄 때 호출되는 함수
window.onFcmTokenReceived = (fcmToken) => {
  if (fcmToken && userStore.isAuthenticated) {

    import('@/services/api').then(({ updateFcmToken }) => {
      updateFcmToken(fcmToken);
    });
  }
};

  window.handlePushRoute = (route) => {

    if (route) {
      // 만약 응급 상황 경로라면 모달을 띄웁니다.
      if (route.includes('/emergency')) {

        emergencyStore.open();
        return;
      }
      
      try {

      router.push(route);

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

              await familyStore.fetchFamilies();

              
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
                  await familyStore.fetchFamilies();
                  
                  // 🎉 복구 성공 시 홈으로 이동
                  router.replace('/home');
                  return;
              }
          } catch (e) {
             console.error("❌ Native Token Restore Failed", e);
             // [FIX] 복구 실패 시 잘못된 토큰이 로컬에 남지 않도록 삭제
             localStorage.removeItem('accessToken');
             localStorage.removeItem('refreshToken');
             if (window.AndroidBridge) {
                 if (window.AndroidBridge.logout) window.AndroidBridge.logout();
                 if (window.AndroidBridge.saveAccessToken) window.AndroidBridge.saveAccessToken(""); // Explicitly clear
             }
             router.replace('/login');
             return; // Stop further retries
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
        const data = window.AndroidBridge.consumeNotificationId();
        if (data) {
           const parts = data.split('|');
           const id = parts[0];
           const type = parts[1];
           const familyId = parts[2];
           // Extract title/message/groupName from parts
           const title = parts[3] || "";
           const message = parts[4] || "";
           const groupName = parts[5] || "";
           
           // onNativeNotification 호출하여 통합 처리
           if (window.onNativeNotification) {
               window.onNativeNotification(id, type, familyId, title, message, groupName);
           }
        }
      } catch (e) {
         // ignore
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
  window.onNativeNotification = async (notificationId, type, familyId, title, message, groupName) => {
      if (!notificationId) return;

      
      try {
          // 1. 필수 데이터 선행 로드 (가족 정보가 없으면 로딩 대기)
          if (familyStore.families.length === 0) {

              await familyStore.fetchFamilies();
          }

          // 2. 알림에 해당하는 가족으로 자동 전환
          if (familyId && familyStore.selectFamilyById) {
              familyStore.selectFamilyById(familyId);
          }
          
          const currentFamilyId = familyStore.selectedFamily?.id;

          // 3. 유형별 UI 동작 분기 (그룹 전환 후 수행)
          if (type === 'EMERGENCY' || type === 'FALL') {
              
              // Robust Search Logic (Fallback)
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

              emergencyStore.open({
                  eventId: notificationId,
                  familyId: familyId,
                  groupName: groupName || currentFamily?.groupName || '가족 그룹',
                  // Use message directly if available (e.g. "Grandma fell"), otherwise computed name
                  dependentName: message ? message : (dependent ? (dependent.relationship || dependent.name) : '대상자 정보 없음'), 
                  type: 'FALL',
                  location: null,
                  timestamp: Date.now(),
                  messageContent: message || title // Pass fully just in case
              });
          } else if (['ACTIVITY', 'OUTING', 'RETURN'].includes(type)) {

              
              // [User Request] 활동/외출 알림 시 현재 화면 위에 모달 표시
              notificationStore.openModal({
                  type: type,
                  groupName: groupName || familyStore.selectedFamily?.groupName || '우리 가족',
                  dependentName: '피부양자', // 실제 데이터가 있다면 개선 가능
                  message: type === 'OUTING' ? '외출이 감지되었습니다.' : (type === 'RETURN' ? '귀가가 확인되었습니다.' : '활동이 감지되었습니다.')
              });
          }
          
          // 4. 백그라운드 데이터 처리 (읽음 처리 및 목록 새로고침)
          let token = localStorage.getItem('accessToken');
          if (!token && window.AndroidBridge?.getAccessToken) {
              token = window.AndroidBridge.getAccessToken();
              if (token && token !== "null") localStorage.setItem('accessToken', token);
          }

          if (!userStore.profile) await userStore.fetchUser();
          const currentUserId = userStore.profile?.id;

          if (currentUserId) {
              const { default: api } = await import('@/services/api');
              
              // 알림 읽음 처리
              try {
                  await api.post('/notifications/read', {
                      notificationId: Number(notificationId),
                      userId: currentUserId
                  });
              } catch (e) {
                  console.warn('FCM: Mark as read failed', e.message);
              }
              
              // 실시간 목록 업데이트 (최신 데이터 보장)
              if (currentFamilyId) {
                  await notificationStore.fetchHistory(currentFamilyId, notificationId);

              }
          }
      } catch (e) {
          console.error('FCM: Error in onNativeNotification processing:', e);
      }
  };

  // 초기 실행
  setTimeout(() => checkNotificationFromNative(), 500);
  setTimeout(() => syncFcmToken(), 1000);

  // [RESTORED] Global Health Sync Callback
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
});
</script>

<template>
  <div id="app">
    <router-view />
    <GlobalConfirmModal />
    <GlobalEmergencyModal />
    <GlobalNotificationModal />
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