import { ref } from 'vue';

export function useSamsungHealth() {
  const heartRate = ref(null);
  const steps = ref(null); // 걸음수 상태 추가
  const isLoading = ref(false);

  // 1. 네이티브에 데이터 요청
  const fetchHeartRate = () => {
    // window.Android 존재 여부 확인 (JS는 런타임에 체크)
    if (window.Android && typeof window.Android.fetchHeartRate === 'function') {
      isLoading.value = true;
      window.Android.fetchHeartRate(); 
    } else {
      console.warn("안드로이드 브릿지(Android.fetchHeartRate)를 찾을 수 없습니다.");
    }
  };

  // 2. 네이티브로부터 데이터를 받기 위한 전역 콜백 함수
  window.onReceiveHeartData = (data) => {
    isLoading.value = false;
    if (data) {
      heartRate.value = data;
      console.log("삼성 헬스 수신 데이터:", data);
    } else {
      console.error("수신된 데이터가 비어있습니다.");
    }
  };

  return {
    heartRate,
    isLoading,
    fetchHeartRate
  };

  const fetchSteps = () => {
    if (window.Android) {
        console.log("안드로이드 호출 시도")
      isLoading.value = true;
      window.Android.fetchSteps(); // 네이티브 호출
    }else {
    console.error("Android 객체를 찾을 수 없습니다. 브릿지 연결 실패!");
  }
  };

  // 걸음수 수신 콜백
  window.onReceiveStepsData = (data) => {
    isLoading.value = false;
    steps.value = data;
    console.log("걸음수 데이터:", data);
  };

  return { heartRate, steps, isLoading, fetchSteps, fetchHeartRate };

}