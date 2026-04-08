import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as voiceService from '@/services/voiceService';
import { Logger } from '@/services/logger';

export const useVoiceStore = defineStore('voice', () => {
  // State
  /** @type {import('vue').Ref<Array>} 녹음용 대본 목록 */
  const scripts = ref([]);

  /** @type {import('vue').Ref<Object|null>} 목소리 모델 상태 (샘플 수 등) */
  const voiceStatus = ref(null);

  /** @type {import('vue').Ref<boolean>} 로딩 상태 */
  const loading = ref(false);

  /** @type {import('vue').Ref<string|null>} 에러 메시지 */
  const error = ref(null);

  // Getters
  const sampleCount = () => voiceStatus.value?.sampleCount || 0;

  // Actions
  /**
   * 대본 목록 조회
   */
  async function fetchScripts() {
    loading.value = true;
    try {
      const data = await voiceService.getScripts();
      scripts.value = data.map((s) => ({
        id: s.id,
        text: s.content,
        isRecorded: false, // UI 상태용: 초기화는 fetchStatus 후 매칭 필요
      }));
      return scripts.value;
    } catch (err) {
      Logger.error('스크립트 조회 실패:', err);
      error.value = '스크립트를 불러오는데 실패했습니다.';
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 목소리 모델 상태 조회 (녹음 완료 여부 확인용)
   */
  async function fetchStatus() {
    try {
      const status = await voiceService.getVoiceStatus();
      voiceStatus.value = status;

      // scripts 상태 업데이트 (녹음 완료 표시)
      if (status && status.samples && Array.isArray(status.samples)) {
        status.samples.forEach((sample) => {
          const matchedScript = scripts.value.find((s) => String(s.id) === String(sample.scriptId));
          if (matchedScript) matchedScript.isRecorded = true;
        });
      }
      return status;
    } catch (err) {
      Logger.error('상태 조회 실패:', err);
      // 상태 조회 실패는 치명적이지 않으므로 throw하지 않음 (선택사항)
    }
  }

  /**
   * 목소리 샘플 업로드
   * @param {Blob} blob - 오디오 파일
   * @param {number|null} scriptId - 대본 ID (자유 대본이면 null)
   * @param {number} duration - 녹음 길이
   * @param {string} [transcription] - STT 텍스트 (자유 대본일 경우)
   */
  async function uploadSample(blob, scriptId, duration, transcription) {
    loading.value = true;
    try {
      await voiceService.uploadVoiceSample(blob, scriptId, duration, transcription);
      // 업로드 성공 시 상태 갱신
      await fetchStatus();
    } catch (err) {
      Logger.error('샘플 업로드 실패:', err);
      error.value = '저장에 실패했습니다.';
      throw err; // UI에서 알림 처리하도록 throw
    } finally {
      loading.value = false;
    }
  }

  /**
   * 오디오 -> 텍스트 변환 (STT)
   * @param {Blob} blob
   */
  async function transcribeAudio(blob) {
    loading.value = true;
    try {
      const text = await voiceService.transcribeAudio(blob);
      return text;
    } catch (err) {
      Logger.error('STT 변환 실패:', err);
      throw err;
    } finally {
      loading.value = false;
    }
  }

  return {
    scripts,
    voiceStatus,
    loading,
    error,
    sampleCount,
    fetchScripts,
    fetchStatus,
    uploadSample,
    transcribeAudio,
  };
});
