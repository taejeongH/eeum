import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 시스템 알림 스토어
 * 시스템 알림을 관리하고, 실시간 알림 수신을 위한 SSE 연결 및 알림 히스토리를 담당합니다.
 * @module useAlertStore
 */
export const useAlertStore = defineStore('alert', () => {
  // --- 상태 (State) ---

  /** @type {import('vue').Ref<Array<object>>} 오버레이에 표시될 일시적인 알림 목록 */
  const alerts = ref([]);

  /** @type {import('vue').Ref<Array<object>>} 로컬 스토리지에 영구 보관되는 알림 내역 */
  const history = ref(JSON.parse(localStorage.getItem('alertHistory') || '[]'));

  /** @type {import('vue').Ref<Array<object>>} 가족 채팅 메시지 내역 (VoiceStore에서 주로 관리하므로 여기서는 보조 역할) */
  const chatHistory = ref([]);

  /** @type {import('vue').Ref<boolean>} SSE 연결 상태 */
  const isConnected = ref(false);

  // 내부 변수
  let eventSource = null;
  let reconnectTimer = null;

  // --- 헬퍼 함수 ---

  /**
   * 알림 히스토리를 로컬 스토리지에 저장합니다.
   */
  const saveHistory = () => {
    try {
      localStorage.setItem('alertHistory', JSON.stringify(history.value));
    } catch (e) {
      console.error('[AlertStore] 히스토리 저장 실패:', e);
    }
  };

  /**
   * 알림 내용을 음성(TTS)으로 출력합니다.
   * @param {object} alert - 알림 객체
   */
  const speakAlert = (alert) => {
    if (!window.speechSynthesis) return;

    // 음성 메시지인 경우 "누구님이 메시지를 보냈습니다"라고 발음
    const text =
      alert.kind === 'voice' ? `${alert.title || '가족'}님이 메시지를 보냈습니다.` : alert.content;

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ko-KR';
    utterance.rate = 0.9; // 또박또박하게 발음하기 위해 속도 조절
    window.speechSynthesis.speak(utterance);
  };

  // --- 액션 (Actions) ---

  /**
   * 새로운 알림을 시스템에 추가합니다.
   * TTS 안내를 수행하고, 히스토리에 저장하며, 오버레이에 표시합니다.
   * @param {object} alert - 서버로부터 수신한 알림 객체
   */
  const addAlert = (alert) => {
    // 프론트엔드 관리를 위한 고유 ID 생성
    const id = Date.now().toString(36) + Math.random().toString(36).substr(2);

    const newAlert = {
      id,
      ...alert,
      timestamp: new Date().toISOString(),
    };

    // UI 렌더링 후 안내하기 위해 잠시 지연
    setTimeout(() => speakAlert(newAlert), 500);

    // 1. 오버레이에 추가 (잠시 보여주고 사라짐)
    alerts.value.push(newAlert);

    // 2. 히스토리에 저장 (음성 메시지는 제외, VoiceStore에서 관리)
    const isVoice = alert.kind === 'voice' || alert.type === 'VOICE';
    if (!isVoice) {
      history.value.unshift(newAlert);
      // 최대 100개까지만 유지
      if (history.value.length > 100) history.value.pop();
      saveHistory();
    }

    // 10초 후 오버레이에서 자동 제거
    setTimeout(() => {
      removeAlert(newAlert.id);
    }, 10000);
  };

  /**
   * 알림 수신을 위한 SSE(Server-Sent Events) 스트림을 연결합니다.
   */
  const connect = () => {
    if (eventSource) return;

    // 재연결 타이머 초기화
    if (reconnectTimer) clearTimeout(reconnectTimer);

    const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
    const url = `${apiUrl}/api/alerts/stream`;
    console.log(`[AlertStore] SSE 연결 시도: ${url}`);

    try {
      eventSource = new EventSource(url);

      eventSource.onopen = () => {
        console.log('[AlertStore] SSE 연결 성공');
        isConnected.value = true;
      };

      eventSource.addEventListener('alert', (event) => {
        try {
          const data = JSON.parse(event.data);
          console.log('[AlertStore] 알림 수신:', data);
          addAlert(data);
        } catch (e) {
          console.error('[AlertStore] 알림 파싱 실패:', e);
        }
      });

      // 하트비트(Ping)
      eventSource.addEventListener('ping', () => {
        // console.log('[AlertStore] 하트비트 수신');
      });

      eventSource.onerror = (error) => {
        console.warn('[AlertStore] SSE 연결 끊김:', error);
        isConnected.value = false;

        if (eventSource) {
          eventSource.close();
          eventSource = null;
        }

        console.log('[AlertStore] 5초 후 재연결 시도...');
        reconnectTimer = setTimeout(connect, 5000);
      };
    } catch (e) {
      console.error('[AlertStore] SSE 초기화 오류:', e);
      reconnectTimer = setTimeout(connect, 5000);
    }
  };

  /**
   * SSE 연결을 해제합니다.
   */
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

  /**
   * 오버레이에서 특정 알림을 제거합니다.
   * @param {string} id - 알림 ID
   */
  const removeAlert = (id) => {
    alerts.value = alerts.value.filter((a) => a.id !== id);
  };

  /**
   * 알림 히스토리에서 특정 알림을 제거하고 저장 상태를 갱신합니다.
   * @param {string} id - 알림 ID
   */
  const removeHistory = (id) => {
    history.value = history.value.filter((a) => a.id !== id);
    saveHistory();
  };

  /**
   * 모든 알림 히스토리를 삭제합니다.
   */
  const clearHistory = () => {
    history.value = [];
    localStorage.removeItem('alertHistory');
  };

  /**
   * 채팅(음성 메시지) 내역을 삭제합니다. (하위 호환성 유지를 위해 남겨둠)
   * @param {string} id - 메시지 ID
   */
  const removeChatHistory = async (id) => {
    // UI에서 즉시 제거
    chatHistory.value = chatHistory.value.filter((c) => c.id !== id);

    try {
      const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
      const token = localStorage.getItem('iotAccessToken');
      await fetch(`${apiUrl}/api/iot/device/sync/voice/${id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      console.log(`[AlertStore] 서버에서 메시지 삭제 완료: ${id}`);
    } catch (e) {
      console.error('[AlertStore] 메시지 삭제 요청 실패:', e);
    }
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
    clearHistory,
    removeChatHistory,
  };
});
