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
  window.onReceiveHealthData = (data) => {
    console.log("네이티브로부터 받은 원본 데이터:", data);
    isLoading.value = false;

    // 안드로이드가 'null' 문자열을 보냈는지, 실제 null을 보냈는지 체크
    if (!data || data === "null") {
      heartRate.value = null; // '데이터 없음' 상태 명시
    } else {
      // 문자열로 왔을 경우를 대비해 파싱
      heartRate.value = typeof data === 'string' ? JSON.parse(data) : data;
    }
  };
  // 🔥 [가장 중요] 정의한 변수와 함수를 반드시 반환해야 합니다!
  return {
    heartRate,
    steps,
    isLoading,
    fetchHeartRate
  };
}


//   // 3. 걸음수 데이터 요청
//   const fetchSteps = () => {
//     if (window.Android) {
//         console.log("안드로이드 호출 시도")
//       isLoading.value = true;
//       window.Android.fetchSteps(); // 네이티브 호출
//     }else {
//     console.error("Android 객체를 찾을 수 없습니다. 브릿지 연결 실패!");
//   }
//   };

//   // 4. 걸음수 수신 콜백
//   window.onReceiveStepsData = (data) => {
//     isLoading.value = false;
//     steps.value = data;
//     console.log("걸음수 데이터:", data);
//   };

//   return {
//     heartRate,
//     steps,
//     isLoading,
//     fetchHeartRate,
//     fetchSteps
//   };
// }