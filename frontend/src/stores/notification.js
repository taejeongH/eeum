import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';
import { Logger } from '@/services/logger';

/**
 * 실시간 알림 로그 및 전역 알림 모달 상태를 관리하는 Pinia 스토어입니다.
 * @summary 알림 관리 스토어
 */
export const useNotificationStore = defineStore('notification', () => {
  /** @type {import('vue').Ref<boolean>} 초기화 여부 */
  const isInitialized = ref(false);

  /** @type {import('vue').Ref<boolean>} 로딩 상태 */
  const isLoading = ref(false);

  /** @type {import('vue').Ref<Array>} 알림 목록 */
  const notifications = ref([]);

  /** @type {import('vue').Ref<number|null>} 현재 보고있는 가족 ID */
  const currentFamilyId = ref(null);

  /** @type {import('vue').Ref<boolean>} 전역 알림 모달 노출 여부 */
  const modalVisible = ref(false);

  /** @type {import('vue').Ref<Object|null>} 모달에 표시할 데이터 */
  const modalData = ref(null);

  /** @type {import('vue').ComputedRef<Object|null>} 가장 최근 알림 반환 */
  const latestNotification = computed(() => notifications.value[0] || null);

  /** @type {import('vue').ComputedRef<number>} 읽지 않은 알림 개수 */
  const unreadCount = computed(() => notifications.value.filter((n) => !n.isRead).length);

  /**
   * 전역 알림 모달을 엽니다.
   * @param {Object} data 알림 상세 데이터
   */
  function openModal(data) {
    modalData.value = data;
    modalVisible.value = true;
  }

  /**
   * 전역 알림 모달을 닫습니다.
   */
  function closeModal() {
    modalVisible.value = false;
    modalData.value = null;
  }

  /**
   * 특정 가족의 알림 기록을 가져옵니다. (재시도 로직 포함)
   * @param {number|string} familyId 가족 ID
   * @param {number|string} [expectedId=null] 포함 여부를 확인할 특정 알림 ID
   */
  async function fetchHistory(familyId, expectedId = null) {
    if (!familyId) {
      isInitialized.value = true;
      return;
    }

    prepareForNewFetch(familyId);

    try {
      await executeFetchWithRetry(familyId, expectedId);
    } finally {
      isLoading.value = false;
      isInitialized.value = true;
    }
  }

  /**
   * 새로운 데이터를 가져오기 전 상태를 정리합니다. (Internal)
   * @param {number|string} familyId
   */
  function prepareForNewFetch(familyId) {
    if (currentFamilyId.value !== familyId) {
      currentFamilyId.value = familyId;
      notifications.value = [];
    }
  }

  /**
   * 재시도 로직을 포함하여 데이터를 가져옵니다. (Internal)
   * @param {number|string} familyId
   * @param {number|string} expectedId
   */
  async function executeFetchWithRetry(familyId, expectedId) {
    const MAX_RETRIES = 3;
    let retries = 0;

    while (retries <= MAX_RETRIES) {
      try {
        const response = await fetchHistoryOnce(familyId);
        notifications.value = response.data;

        if (shouldRetryForMissingId(expectedId)) {
          await waitForRetry();
          const retryResponse = await fetchHistoryOnce(familyId, true);
          notifications.value = retryResponse.data;
        }
        break;
      } catch (error) {
        if (isNetworkError(error) && retries < MAX_RETRIES) {
          retries++;
          await handleRetryDelay(retries);
          continue;
        }
        logFetchError(error);
        break;
      }
    }
  }

  /**
   * 단일 히스토리 요청을 수행합니다. (Internal)
   * @param {number|string} familyId
   * @param {boolean} isRetry
   */
  function fetchHistoryOnce(familyId, isRetry = false) {
    const timestamp = Date.now();
    const url = `/notifications/families/${familyId}/history?_t=${timestamp}${isRetry ? '&retry=true' : ''}`;
    return api.get(url, { headers: { silent: true } });
  }

  /**
   * 특정 ID가 목록에 없는 경우 재시도 여부를 결정합니다. (Internal)
   * @param {number|string} expectedId
   * @returns {boolean}
   */
  function shouldRetryForMissingId(expectedId) {
    return !!(expectedId && !notifications.value.some((n) => String(n.id) === String(expectedId)));
  }

  const waitForRetry = () => new Promise((resolve) => setTimeout(resolve, 1000));
  const isNetworkError = (error) => error.message === 'Network Error';
  const logFetchError = (error) => {
    const detail = error.response?.data
      ? JSON.stringify(error.response.data)
      : error.message || error;
    Logger.error('알림 기록 조회 실패:', detail);
  };

  /**
   * 재시도 대시 시간을 처리합니다. (Internal)
   * @param {number} retries
   */
  async function handleRetryDelay(retries) {
    const delay = 500 * Math.pow(2, retries);
    Logger.warn(`네트워크 오류로 재시도합니다. ${delay}ms 후 재시도... (${retries}/3)`);
    await new Promise((resolve) => setTimeout(resolve, delay));
  }

  /**
   * 특정 알림을 읽음 처리합니다.
   * @param {number|string} notificationId 알림 ID
   */
  async function markAsRead(notificationId) {
    try {
      await api.post(`/notifications/${notificationId}/read`, null, { headers: { silent: true } });
      const noti = notifications.value.find((n) => n.id === notificationId);
      if (noti) noti.isRead = true;
    } catch (error) {
      Logger.error('알림 읽음 처리 실패:', error);
    }
  }

  /**
   * 알림 데이터 및 상태를 초기화합니다.
   */
  function clearNotifications() {
    notifications.value = [];
    currentFamilyId.value = null;
    isInitialized.value = false;
    modalVisible.value = false;
    modalData.value = null;
  }

  return {
    notifications,
    isLoading,
    isInitialized,
    latestNotification,
    unreadCount,
    currentFamilyId,
    modalVisible,
    modalData,
    fetchHistory,
    markAsRead,
    clearNotifications,
    openModal,
    closeModal,
  };
});
