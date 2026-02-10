import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useModalStore = defineStore('modal', () => {
  /** 모달 표시 여부 */
  const isVisible = ref(false);
  /** 모달 유형 ('alert' | 'confirm') */
  const type = ref('alert');
  /** 모달 제목 */
  const title = ref('');
  /** 모달 메시지 */
  const message = ref('');

  /** 비동기 처리를 위한 Promise resolve 함수 */
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
