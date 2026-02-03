import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAlertStore = defineStore('alert', () => {
    const alerts = ref([]);
    const history = ref(JSON.parse(localStorage.getItem('alertHistory') || '[]')); // 알림 보관함 (영구 보관)
    const chatHistory = ref([]); // 가족 메시지함 (실시간 동기화)
    const isConnected = ref(false);
    let eventSource = null;
    let reconnectTimer = null;

    const connect = () => {
        if (eventSource) return;

        // Clear any pending reconnect
        if (reconnectTimer) clearTimeout(reconnectTimer);

        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081';
        console.log(`SSE 연결 시도: ${apiUrl}/api/alerts/stream`);

        try {
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
                isConnected.value = false;
                if (eventSource) {
                    eventSource.close();
                    eventSource = null;
                }
                console.warn('[AlertStore] SSE 연결 끊김. 5초 후 재연결 시도...');

                reconnectTimer = setTimeout(() => {
                    console.log('[AlertStore] 재연결 시도 중...');
                    connect(); // Recursive call safe due to eventSource check at top
                }, 5000);
            };
        } catch (e) {
            console.warn('[AlertStore] SSE 연결 초기화 실패:', e);
            reconnectTimer = setTimeout(connect, 5000);
        }
    };

    const disconnect = () => {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
            isConnected.value = false;
        }
        if (reconnectTimer) {
            clearTimeout(reconnectTimer);
            reconnectTimer = null;
        }
    };

    const addAlert = (alert) => {
        const id = Date.now() + Math.random().toString(36).substr(2, 9);
        const newAlert = {
            id,
            ...alert,
            timestamp: new Date().toISOString()
        };

        // TTS for voice messages
        if (alert.kind === 'voice' || alert.type === 'VOICE') {
            // Push to chat tab list immediately
            chatHistory.value.unshift({
                id: newAlert.id,
                sender: alert.title || '가족',
                content: alert.content,
                timestamp: newAlert.timestamp
            });
            if (chatHistory.value.length > 50) chatHistory.value.pop();

            const speakMessage = () => {
                const text = `${alert.title || '가족'}님이 메시지를 보냈습니다. "${alert.content}"`;
                const utterance = new SpeechSynthesisUtterance(text);
                utterance.lang = 'ko-KR';
                utterance.rate = 0.9; // Slightly slower for elderly
                window.speechSynthesis.speak(utterance);
            };

            // Short delay to let the UI transition finish if needed
            setTimeout(speakMessage, 500);
        }

        // 1. 오버레이용 (일시적)
        alerts.value.push(newAlert);

        // 2. 보관함용 (영구적) - 메시지는 알림함에 넣지 않음
        const isVoice = alert.kind === 'voice' || alert.type === 'VOICE';
        if (!isVoice) {
            history.value.unshift(newAlert);
            if (history.value.length > 100) history.value.pop(); // 최대 100개 유지
            saveHistory();
        }

        // 오버레이 자동 제거
        setTimeout(() => {
            removeAlert(id);
        }, 10000);
    };

    const removeAlert = (id) => {
        alerts.value = alerts.value.filter(a => a.id !== id);
    };

    const removeHistory = (id) => {
        history.value = history.value.filter(a => a.id !== id);
        saveHistory();
    };

    const removeChatHistory = async (id) => {
        // 1. Remove from local state immediately for snappy UI
        chatHistory.value = chatHistory.value.filter(c => c.id !== id);

        // 2. Tell the server to delete it so it doesn't come back on refresh
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081';
            const token = localStorage.getItem('iotAccessToken');
            await fetch(`${apiUrl}/api/iot/device/sync/voice/${id}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            console.log(`[AlertStore] Message ${id} deleted from server`);
        } catch (e) {
            console.error('[AlertStore] Failed to delete message from server:', e);
        }
    };

    const saveHistory = () => {
        localStorage.setItem('alertHistory', JSON.stringify(history.value));
    };

    const clearHistory = () => {
        history.value = [];
        localStorage.removeItem('alertHistory');
    };

    return {
        alerts,
        history,
        chatHistory,
        isConnected,
        connect,
        disconnect,
        addAlert,
        removeAlert,
        removeHistory,
        removeChatHistory,
        clearHistory
    };
});
