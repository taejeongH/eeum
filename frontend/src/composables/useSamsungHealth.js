import { ref } from 'vue';
import { Logger } from '@/services/logger';

export function useSamsungHealth() {
  const heartRate = ref(null);
  const steps = ref(null); 
  const isLoading = ref(false);

  
  const fetchHeartRate = () => {
    
    if (window.Android && typeof window.Android.fetchHeartRate === 'function') {
      isLoading.value = true;
      window.Android.fetchHeartRate();
    } else {
      Logger.warn("안드로이드 브릿지(Android.fetchHeartRate)를 찾을 수 없습니다.");

      
      if (import.meta.env.VITE_USE_MOCK === 'true') {

        setTimeout(() => {
          
          const mockData = JSON.stringify({
            heartRate: 75,
            timestamp: new Date().toISOString()
          });
          window.onReceiveHealthData(mockData);
        }, 1000);
      }
    }
  };

  
  window.onReceiveHealthData = (data) => {

    isLoading.value = false;

    
    if (!data || data === "null") {
      heartRate.value = null; 
    } else {
      
      heartRate.value = typeof data === 'string' ? JSON.parse(data) : data;
    }
  };
  
  return {
    heartRate,
    steps,
    isLoading,
    fetchHeartRate
  };
}