import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 긴급 상황 관리 스토어
 * - 전역 긴급 알림 모달의 상태를 관리
 */
export const useEmergencyStore = defineStore('emergency', () => {
  /** @type {import('vue').Ref<boolean>} 긴급 모달 표시 여부 */
  const isVisible = ref(false);

  /** @type {import('vue').Ref<Object|null>} 긴급 상황 데이터 (타입, 메시지 등) */
  const emergencyData = ref(null);

  /**
   * 긴급 모달 열기
   * @param {Object} [data] - 표시할 긴급 데이터 (옵션)
   */
  const open = (data = null) => {
    emergencyData.value = data;
    isVisible.value = true;
  };

  /** 긴급 모달 닫기 및 데이터 초기화 */
  const close = () => {
    isVisible.value = false;
    emergencyData.value = null;
  };

  return {
    isVisible,
    emergencyData,
    open,
    close,
  };
});
