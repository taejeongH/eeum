import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import axios from 'axios';

/**
 * 동적 API URL 반환 함수
 * 환경 변수 또는 기본 로컬 주소를 사용하여 API 베이스 URL을 반환합니다.
 * @returns {string} API Base URL
 */
const getApiUrl = () => import.meta.env.VITE_API_URL || 'http://localhost:8080';

/**
 * 음성 메시지 스토어
 * 음성 메시지의 수신(SSE), 재생, 상태 관리 및 서버와의 동기화를 담당합니다.
 * @module useVoiceStore
 */
export const useVoiceStore = defineStore('voice', () => {
  // --- 상태 (State) ---
  /** @type {import('vue').Ref<Array<object>>} 음성 메시지 목록 */
  const voiceMessages = ref([]);

  /** @type {import('vue').Ref<boolean>} SSE 연결 상태 */
  const isConnected = ref(false);

  /** @type {import('vue').Ref<EventSource|null>} SSE 이벤트 소스 객체 */
  const eventSource = ref(null);

  // --- 액션 (Actions) ---

  /**
   * 보류 중인(미확인) 음성 메시지를 서버에서 가져옵니다.
   * 초기 로딩 시 또는 연결 복구 시 호출됩니다.
   */
  const fetchPendingMessages = async () => {
    try {
      const apiUrl = getApiUrl();
      const token = localStorage.getItem('iotAccessToken');
      // 'pending' 엔드포인트가 있다고 가정 (실제로는 history 조회일 수 있음)
      const response = await axios.get(`${apiUrl}/api/voice/pending`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      if (response.data.ok && response.data.data?.items) {
        const pendingItems = response.data.data.items;
        console.log(`[VoiceStore] 보류 중인 메시지 ${pendingItems.length}개 발견`);

        // 기존 목록과 중복 검사 후 병합
        pendingItems.forEach((item) => {
          const exists = voiceMessages.value.some((msg) => msg.id === item.id);
          if (!exists) {
            voiceMessages.value.push({
              id: item.id,
              sender: item.sender?.name || '알 수 없음',
              content: item.description || '',
              profile_image: item.sender?.profile_image_url,
              created_at: item.created_at || Date.now() / 1000,
              type: 'VOICE',
              status: 'pending',
              isPlayed: false,
              download: item.download,
            });
          }
        });

        // 최신순 정렬 (생성 시간 내림차순)
        voiceMessages.value.sort((a, b) => b.created_at - a.created_at);
      }
    } catch (e) {
      console.error('[VoiceStore] 보류 메시지 조회 실패:', e);
    }
  };

  /**
   * 음성 메시지 SSE 스트림에 연결합니다.
   */
  const connect = () => {
    if (eventSource.value) return;

    const apiUrl = getApiUrl();
    console.log(`[VoiceStore] SSE 연결: ${apiUrl}/api/voice/stream`);
    eventSource.value = new EventSource(`${apiUrl}/api/voice/stream`);

    eventSource.value.onopen = async () => {
      console.log('[VoiceStore] SSE 연결 성공');
      isConnected.value = true;
      await fetchPendingMessages();
    };

    eventSource.value.onerror = (err) => {
      console.error('[VoiceStore] SSE 오류:', err);
      isConnected.value = false;

      if (eventSource.value) {
        eventSource.value.close();
        eventSource.value = null;
      }

      // 3초 후 재연결 시도
      setTimeout(connect, 3000);
    };

    // 이벤트: 새로운 음성 메시지 수신 (voice)
    eventSource.value.addEventListener('voice', (event) => {
      try {
        const data = JSON.parse(event.data);
        console.log('[VoiceStore] 새 메시지 수신:', data);

        // 중복 확인
        if (!voiceMessages.value.some((msg) => msg.id === data.id)) {
          voiceMessages.value.push({
            ...data,
            sender: data.sender?.name || data.title || '알 수 없음',
            content: data.description || data.content || '',
            profile_image: data.sender?.profile_image_url,
            created_at: data.created_at || Date.now() / 1000,
            type: 'VOICE',
            status: 'pending',
            isPlayed: false,
            download: data.download,
          });

          // 최대 50개까지만 유지
          if (voiceMessages.value.length > 50) voiceMessages.value.shift();

          // 최신순 정렬
          voiceMessages.value.sort((a, b) => b.created_at - a.created_at);
        }
      } catch (e) {
        console.error('[VoiceStore] 메시지 파싱 오류:', e);
      }
    });

    // 이벤트: 재생 완료 (voice_done)
    eventSource.value.addEventListener('voice_done', (event) => {
      try {
        const data = JSON.parse(event.data);
        console.log('[VoiceStore] 재생 완료:', data);

        // 재생 완료된 메시지는 목록에서 제거하거나 상태 업데이트
        // 여기서는 목록에서 제거하는 로직으로 구현됨
        const index = voiceMessages.value.findIndex((msg) => msg.id === data.id);
        if (index !== -1) {
          voiceMessages.value.splice(index, 1);
        }
      } catch (e) {
        console.error('[VoiceStore] voice_done 이벤트 파싱 오류:', e);
      }
    });
  };

  /**
   * SSE 연결을 종료합니다.
   */
  const disconnect = () => {
    if (eventSource.value) {
      eventSource.value.close();
      eventSource.value = null;
      isConnected.value = false;
    }
  };

  /**
   * 특정 메시지를 재생 요청합니다.
   * @param {string} id - 메시지 ID
   */
  const playMessage = async (id) => {
    const msg = voiceMessages.value.find((m) => m.id === id);
    if (!msg) return;

    // 다운로드 완료 상태 확인 (있는 경우)
    if (msg.download && !msg.download.ready) {
      console.warn('[VoiceStore] 재생 준비 안 됨 (다운로드 중)');
      return;
    }

    // 로컬 상태 즉시 업데이트 (읽음 처리)
    msg.isPlayed = true;

    try {
      msg.status = 'playing'; // 낙관적 업데이트
      const apiUrl = getApiUrl();

      // 재생 요청 (단일 ACK)
      const response = await axios.post(`${apiUrl}/api/ack`, {
        target: { type: 'voice', id: id },
        action: 'play',
      });

      if (response.data.ok) {
        console.log(`[VoiceStore] 재생 시작. 길이: ${response.data.data.duration_sec}초`);
      } else {
        console.warn('[VoiceStore] 재생 거부:', response.data.reason);
        msg.status = 'pending';
      }
    } catch (e) {
      console.error('[VoiceStore] 재생 요청 실패:', e);
      msg.status = 'pending';
    }
  };

  /**
   * 특정 메시지를 건너뜁니다 (스킵).
   * @param {string} id - 메시지 ID
   */
  const skipMessage = async (id) => {
    // 낙관적 제거
    const index = voiceMessages.value.findIndex((m) => m.id === id);
    if (index !== -1) voiceMessages.value.splice(index, 1);

    try {
      const apiUrl = getApiUrl();
      await axios.post(`${apiUrl}/api/ack`, {
        target: { type: 'voice', id: id },
        action: 'skip',
      });
    } catch (e) {
      console.error('[VoiceStore] 건너뛰기 요청 실패:', e);
    }
  };

  /**
   * 여러 메시지에 대해 일괄 ACK를 보냅니다.
   * @param {Array<object>} items - 메시지 객체 배열
   * @param {string} [defaultAction='skip'] - 기본 동작
   */
  const batchAck = async (items, defaultAction = 'skip') => {
    try {
      const apiUrl = getApiUrl();
      const payload = {
        mode: 'sequential',
        default_action: defaultAction,
        items: items.map((item) => ({
          target: { type: 'voice', id: item.id },
          action: item.action, // 개별 오버라이드 가능
        })),
      };

      await axios.post(`${apiUrl}/api/ack/batch`, payload);
    } catch (e) {
      console.error('[VoiceStore] 일괄 ACK 실패:', e);
    }
  };

  /**
   * 현재 재생 중인 항목을 건너뜁니다 (글로벌).
   */
  const skipCurrentPlayback = async () => {
    try {
      const apiUrl = getApiUrl();
      await axios.post(`${apiUrl}/api/playback/skip_current`);
    } catch (e) {
      console.error('[VoiceStore] 현재 항목 건너뛰기 실패:', e);
    }
  };

  /**
   * 메시지를 서버에서 완전히 삭제합니다.
   * @param {string} id - 메시지 ID
   */
  const removeMessage = async (id) => {
    // 낙관적 제거
    const index = voiceMessages.value.findIndex((m) => m.id === id);
    if (index !== -1) voiceMessages.value.splice(index, 1);

    try {
      const apiUrl = getApiUrl();
      const token = localStorage.getItem('iotAccessToken');
      await axios.delete(`${apiUrl}/api/iot/device/sync/voice/${id}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      console.log(`[VoiceStore] 메시지 삭제됨: ${id}`);
    } catch (e) {
      console.error('[VoiceStore] 메시지 삭제 실패:', e);
    }
  };

  // --- 계산된 속성 (Computed) ---

  /**
   * 읽지 않은(재생되지 않은) 메시지 수를 반환합니다.
   */
  const unreadCount = computed(() => voiceMessages.value.filter((m) => !m.isPlayed).length);

  return {
    voiceMessages,
    isConnected,
    connect,
    disconnect,
    playMessage,
    skipMessage,
    batchAck,
    skipCurrentPlayback,
    removeMessage,
    unreadCount,
    fetchPendingMessages,
  };
});
