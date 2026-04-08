import { ref, onMounted, onUnmounted } from 'vue';
import { useHealthStore } from '@/stores/health';
import { useFamilyStore } from '@/stores/family';
import api from '@/services/api';
import { Logger } from '@/services/logger';

export function useHealthSync() {
  const healthStore = useHealthStore();
  const familyStore = useFamilyStore();

  const syncLoading = ref(false);
  const syncMessage = ref('');

  /**
   * 동기화 요청 핸들러 (UI에서 호출)
   * - 본인이 피보호자면: 로컬 동기화 (Android Bridge)
   * - 보호자라면: 원격 동기화 요청 (API)
   * @param {boolean} isUserDependent - 현재 사용자가 피보호자인지 여부
   */
  const handleSync = async (isUserDependent) => {
    if (isUserDependent) {
      fetchAllData();
    } else {
      await requestRemoteSync();
    }
  };

  /**
   * 로컬 삼성 헬스 데이터 가져오기 (Android Bridge)
   */
  const fetchAllData = () => {
    if (window.AndroidBridge && window.AndroidBridge.fetchAllHealthMetrics) {
      syncLoading.value = true;
      syncMessage.value = '삼성 헬스 데이터 동기화 중...';
      window.AndroidBridge.fetchAllHealthMetrics();
    } else {
      Logger.warn('AndroidBridge not found.');
      syncMessage.value = '모바일 기기에서만 연동이 가능합니다.';
      setTimeout(() => (syncMessage.value = ''), 3000);
    }
  };

  /**
   * 원격 기기에 동기화 요청 (보호자 모드)
   */
  const requestRemoteSync = async () => {
    const groupId = familyStore.selectedFamily?.id;
    if (!groupId) return;

    try {
      syncLoading.value = true;
      syncMessage.value = '피부양자 기기에 동기화 요청 중...';

      // TODO: 이 API 호출도 healthStore로 옮기는 것이 이상적이지만,
      // 현재 api.post 호출이 간단하므로 여기서 처리하거나 store에 추가 가능
      await api.post(`/health/request-sync?groupId=${groupId}`);

      syncMessage.value = '동기화 요청을 보냈습니다. 잠시만 기다려주세요.';

      // 5초 후 데이터 폴링
      setTimeout(async () => {
        await healthStore.fetchLatestMetrics(groupId);
        syncLoading.value = false;
        syncMessage.value = '최신 데이터를 불러왔습니다.';
        setTimeout(() => (syncMessage.value = ''), 3000);
      }, 5000);
    } catch (error) {
      Logger.error('동기화 요청 실패:', error);
      syncLoading.value = false;
      syncMessage.value = '동기화 요청에 실패했습니다.';
      setTimeout(() => (syncMessage.value = ''), 3000);
    }
  };

  /**
   * Android Bridge로부터 데이터 수신 (Callback)
   */
  const onReceiveHealthData = async (dataString) => {
    syncLoading.value = false;

    if (!dataString || dataString === 'null') {
      syncMessage.value = '삼성 헬스 권한을 확인해주세요.';
      setTimeout(() => (syncMessage.value = ''), 5000);
      return;
    }

    try {
      const data = JSON.parse(dataString);
      const now = new Date();
      // 로컬 시간 ISO 문자열 생성
      const localIso = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
        .toISOString()
        .split('.')[0];

      // Snake Case -> Camel Case 매핑
      const mappedData = {
        recordDate: (data.record_date ? data.record_date.replace(' ', 'T') : localIso).split(
          '.',
        )[0],
        steps: data.steps,
        restingHeartRate: data.resting_heart_rate,
        averageHeartRate: data.average_heart_rate,
        maxHeartRate: data.max_heart_rate,
        sleepTotalMinutes: data.sleep_total_minutes,
        sleepDeepMinutes: data.sleep_deep_minutes,
        sleepLightMinutes: data.sleep_light_minutes,
        sleepRemMinutes: data.sleep_rem_minutes,
        bloodOxygen: data.blood_oxygen,
        bloodGlucose: data.blood_glucose,
        systolicPressure: data.systolic_pressure,
        diastolicPressure: data.diastolic_pressure,
        activeCalories: data.active_calories,
        activeMinutes: data.active_minutes,
      };

      syncMessage.value = '데이터를 서버에 저장 중...';

      const groupId = familyStore.selectedFamily?.id;
      if (groupId) {
        await healthStore.saveHealthMetrics(groupId, mappedData);
        syncMessage.value = '모든 데이터가 정상적으로 동기화되었습니다.';
      } else {
        syncMessage.value = '선택된 가족 그룹이 없습니다.';
      }

      setTimeout(() => (syncMessage.value = ''), 5000);
    } catch (e) {
      Logger.error('파싱/매핑 오류:', e);
      syncMessage.value = '데이터 처리 중 오류 발생';
      setTimeout(() => (syncMessage.value = ''), 3000);
    }
  };

  onMounted(() => {
    // 전역 콜백 등록
    window.onReceiveAllHealthData = onReceiveHealthData;
    // 호환성을 위한 별칭 등록
    window.onReceiveHealthData = onReceiveHealthData;
    window.onReceiveSteps = onReceiveHealthData;
    window.onReceiveSleep = onReceiveHealthData;
  });

  onUnmounted(() => {
    delete window.onReceiveAllHealthData;
    delete window.onReceiveHealthData;
    delete window.onReceiveSteps;
    delete window.onReceiveSleep;
  });

  return {
    syncLoading,
    syncMessage,
    handleSync,
  };
}
