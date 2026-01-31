import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';

export const useNotificationStore = defineStore('notification', () => {
    const isInitialized = ref(false);
    const isLoading = ref(false);
    const notifications = ref([]);
    const currentFamilyId = ref(null);

    // [NEW] 전역 알림 모달 상태
    const modalVisible = ref(false);
    const modalData = ref(null);

    const latestNotification = computed(() => notifications.value[0] || null);
    const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length); // 프론트엔드 계산

    function openModal(data) {
        modalData.value = data;
        modalVisible.value = true;
    }

    function closeModal() {
        modalVisible.value = false;
        modalData.value = null;
    }

    async function fetchHistory(familyId, expectedId = null) {
        if (!familyId) {
            isInitialized.value = true;
            return;
        }

        // 가족이 변경되면 즉시 목록 초기화 및 로딩 표시
        if (currentFamilyId.value !== familyId) {
            currentFamilyId.value = familyId;
            notifications.value = [];
            isLoading.value = true;
        } else {
            // 같은 가족이면 백그라운드 갱신 (로딩 표시 선택적, 여기선 유지)
            isLoading.value = true;
        }

        try {
            let retries = 0;
            const maxRetries = 3;

            while (retries <= maxRetries) {
                try {
                    // [Fix] Android WebView 캐싱 방지를 위해 timestamp 추가
                    const timestamp = new Date().getTime();
                    const response = await api.get(`/notifications/families/${familyId}/history?_t=${timestamp}`);
                    notifications.value = response.data;

                    // [NEW] expectedId가 있는데 목록에 없다면 (레이스 컨디션), 1초 뒤 한 번 더 시도
                    if (expectedId && !notifications.value.some(n => String(n.id) === String(expectedId))) {
                        console.log(`FCM Store: Expected ID ${expectedId} not found, retrying in 1s...`);
                        await new Promise(resolve => setTimeout(resolve, 1000));

                        const retryTimestamp = new Date().getTime();
                        const retryResponse = await api.get(`/notifications/families/${familyId}/history?retry=true&_t=${retryTimestamp}`);
                        notifications.value = retryResponse.data;
                    }

                    // 성공하면 루프 탈출
                    break;

                } catch (error) {
                    // 네트워크 에러인 경우 재시도
                    if (error.message === 'Network Error' && retries < maxRetries) {
                        retries++;
                        const delay = 500 * Math.pow(2, retries); // 1s, 2s, 4s...
                        console.warn(`Network Error fetching history. Retrying in ${delay}ms... (${retries}/${maxRetries})`);
                        await new Promise(resolve => setTimeout(resolve, delay));
                        continue;
                    }

                    // 재시도 횟수 초과 혹은 다른 에러
                    const detail = error.response?.data ? JSON.stringify(error.response.data) : (error.message || error);
                    console.error('Failed to fetch notification history:', detail);
                    // 에러 발생 시, 기존 데이터가 있다면 유지 (빈 배열로 초기화하지 않음)
                    break;
                }
            }
        } finally {
            isLoading.value = false;
            isInitialized.value = true;
        }
    }

    function clearNotifications() {
        notifications.value = [];
        currentFamilyId.value = null;
        isInitialized.value = false;
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
        clearNotifications,
        openModal,
        closeModal
    };
});
