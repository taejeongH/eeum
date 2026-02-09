import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';
import { Logger } from '@/services/logger';

export const useNotificationStore = defineStore('notification', () => {
    const isInitialized = ref(false);
    const isLoading = ref(false);
    const notifications = ref([]);
    const currentFamilyId = ref(null);

    
    const modalVisible = ref(false);
    const modalData = ref(null);

    const latestNotification = computed(() => notifications.value[0] || null);
    const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length); 

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

        if (currentFamilyId.value !== familyId) {
            currentFamilyId.value = familyId;
            notifications.value = [];
        }

        try {
            let retries = 0;
            const maxRetries = 3;

            while (retries <= maxRetries) {
                try {
                    
                    const timestamp = new Date().getTime();
                    const response = await api.get(`/notifications/families/${familyId}/history?_t=${timestamp}`, { headers: { silent: true } });
                    notifications.value = response.data;

                    
                    if (expectedId && !notifications.value.some(n => String(n.id) === String(expectedId))) {

                        await new Promise(resolve => setTimeout(resolve, 1000));

                        const retryTimestamp = new Date().getTime();
                        const retryResponse = await api.get(`/notifications/families/${familyId}/history?retry=true&_t=${retryTimestamp}`, { headers: { silent: true } });
                        notifications.value = retryResponse.data;
                    }

                    
                    break;

                } catch (error) {
                    
                    if (error.message === 'Network Error' && retries < maxRetries) {
                        retries++;
                        const delay = 500 * Math.pow(2, retries); 
                        Logger.warn(`네트워크 오류로 재시도합니다. ${delay}ms 후 재시도... (${retries}/${maxRetries})`);
                        await new Promise(resolve => setTimeout(resolve, delay));
                        continue;
                    }

                    
                    const detail = error.response?.data ? JSON.stringify(error.response.data) : (error.message || error);
                    Logger.error('알림 기록 조회 실패:', detail);
                    
                    break;
                }
            }
        } finally {
            isLoading.value = false;
            isInitialized.value = true;
        }
    }

    async function markAsRead(notificationId) {
        try {
            await api.post(`/notifications/${notificationId}/read`, null, { headers: { silent: true } });
            const noti = notifications.value.find(n => n.id === notificationId);
            if (noti) noti.isRead = true;
        } catch (error) {
            Logger.error('알림 읽음 처리 실패:', error);
        }
    }

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
        closeModal
    };
});
