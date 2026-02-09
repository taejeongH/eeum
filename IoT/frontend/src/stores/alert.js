import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAlertStore = defineStore('alert', () => {
    const alerts = ref([]);
    const history = ref(JSON.parse(localStorage.getItem('alertHistory') || '[]')); 
    const chatHistory = ref([]); 
    const isConnected = ref(false);
    let eventSource = null;
    let reconnectTimer = null;

    const connect = () => {
        if (eventSource) return;

        
        if (reconnectTimer) clearTimeout(reconnectTimer);

        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
        console.log(`SSE 연결 시도: ${apiUrl}/api/alerts/stream`);

        try {
            eventSource = new EventSource(`${apiUrl}/api/alerts/stream`);

            eventSource.onopen = () => {
                console.log('SSE 연결 성공');
                isConnected.value = true;
            };

            
            eventSource.addEventListener('alert', (event) => {
                try {
                    const data = JSON.parse(event.data);
                    console.log('알림 수신:', data);
                    addAlert(data);
                } catch (e) {
                    console.error('알림 데이터 파싱 오류:', e);
                }
            });

            
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
                    connect(); 
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

        
        if (alert.kind === 'voice' || alert.type === 'VOICE') {
            
            

            const speakMessage = () => {
                const text = `${alert.title || '가족'}님이 메시지를 보냈습니다. "${alert.content}"`;
                const utterance = new SpeechSynthesisUtterance(text);
                utterance.lang = 'ko-KR';
                utterance.rate = 0.9; 
                window.speechSynthesis.speak(utterance);
            };

            
            setTimeout(speakMessage, 500);
        }

        
        alerts.value.push(newAlert);

        
        const isVoice = alert.kind === 'voice' || alert.type === 'VOICE';
        if (!isVoice) {
            history.value.unshift(newAlert);
            if (history.value.length > 100) history.value.pop(); 
            saveHistory();
        }

        
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
        
        chatHistory.value = chatHistory.value.filter(c => c.id !== id);

        
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
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
