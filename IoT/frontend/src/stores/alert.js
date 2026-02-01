import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAlertStore = defineStore('alert', () => {
    const alerts = ref([]);
    const isConnected = ref(false);
    let eventSource = null;

    const connect = () => {
        if (eventSource) return;

        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
        console.log(`SSE 연결 시도: ${apiUrl}/api/alerts/stream`);
        eventSource = new EventSource(`${apiUrl}/api/alerts/stream`);

        eventSource.onopen = () => {
            console.log('SSE 연결 성공');
            isConnected.value = true;
        };

        // 핵심 알림 이벤트 처리
        eventSource.addEventListener('alert', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('알림 수신:', data);
                addAlert(data);
            } catch (e) {
                console.error('알림 데이터 파싱 오류:', e);
            }
        });

        // Heartbeat 처리
        eventSource.addEventListener('ping', () => {
            console.log('SSE heartbeat (ping)');
        });

        eventSource.onerror = (error) => {
            console.error('SSE 연결 오류:', error);
            isConnected.value = false;
            eventSource.close();
            eventSource = null;
            // 재연결 로직은 필요에 따라 추가 (보통 브라우저가 자동 재연결 시도하나 EventSource는 닫히면 수동 처리 필요할 수 있음)
            setTimeout(connect, 3000); // 3초 후 재연결 시도
        };
    };

    const disconnect = () => {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
            isConnected.value = false;
        }
    };

    const addAlert = (alert) => {
        // 알림 표시를 위한 ID 및 타임스탬프 추가
        const newAlert = {
            id: Date.now() + Math.random().toString(36).substr(2, 9),
            ...alert,
            timestamp: new Date()
        };
        alerts.value.push(newAlert);

        // 모든 알림 10초 후 자동 제거 (노인 사용자 고려하여 넉넉하게 설정)
        setTimeout(() => {
            removeAlert(newAlert.id);
        }, 10000);
    };

    const removeAlert = (id) => {
        alerts.value = alerts.value.filter(a => a.id !== id);
    };

    return {
        alerts,
        isConnected,
        connect,
        disconnect,
        addAlert,
        removeAlert
    };
});
