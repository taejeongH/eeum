import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useModalStore = defineStore('modal', () => {
    const isVisible = ref(false);
    const type = ref('alert'); 
    const title = ref('');
    const message = ref('');

    
    let resolvePromise = null;

  /**
   * 알림(Alert) 모달을 엽니다.
   * @param {string} msg 표시할 메시지
   * @param {string} ttl 타이틀 (기본값: '알림')
   * @returns {Promise<boolean>} 확인 클릭 시 resolve
   */
  const openAlert = (msg, ttl = '알림') => {
    type.value = 'alert';
    title.value = ttl;
    message.value = msg;
    isVisible.value = true;

    return new Promise((resolve) => {
      resolvePromise = resolve;
    });
  };

  /**
   * 확인(Confirm) 모달을 엽니다.
   * @param {string} msg 표시할 메시지
   * @param {string} ttl 타이틀 (기본값: '확인')
   * @returns {Promise<boolean>} 확인 시 true, 취소 시 false로 resolve
   */
  const openConfirm = (msg, ttl = '확인') => {
    type.value = 'confirm';
    title.value = ttl;
    message.value = msg;
    isVisible.value = true;

    return new Promise((resolve) => {
      resolvePromise = resolve;
    });
  };

  /**
   * 모달을 닫고 기다리고 있던 Promise를 결과값과 함께 해결합니다.
   * @param {boolean} result 확인 여부
   */
  const close = (result = false) => {
    isVisible.value = false;
    if (resolvePromise) {
      resolvePromise(result);
      resolvePromise = null;
    }
  };

  return {
    isVisible,
    type,
    title,
    message,
    openAlert,
    openConfirm,
    close,
  };
});
