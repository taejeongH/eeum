import { ref, onUnmounted } from 'vue';
import { Logger } from '@/services/logger';

export function useAudioRecorder() {
  const isRecording = ref(false);
  const recordingDuration = ref(0);
  const recordedBlob = ref(null);
  const audioUrl = ref(null);
  const error = ref(null);

  const recorder = ref(null);
  const stream = ref(null);
  const audioChunks = ref([]);
  const recordingTimer = ref(null);
  let stopResolver = null;

  const startRecording = async () => {
    try {
      cleanup(); // Clear previous session

      stream.value = await navigator.mediaDevices.getUserMedia({ audio: true });
      audioChunks.value = [];
      const mimeType = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '';
      const mediaRecorder = new MediaRecorder(stream.value, { mimeType });

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) audioChunks.value.push(event.data);
      };

      mediaRecorder.onstop = () => {
        const blob = new Blob(audioChunks.value, { type: mimeType || 'audio/webm' });
        recordedBlob.value = blob;
        audioUrl.value = URL.createObjectURL(blob);

        if (stopResolver) {
          stopResolver(blob);
          stopResolver = null;
        }
      };

      mediaRecorder.start();
      recorder.value = mediaRecorder;
      isRecording.value = true;
      recordingDuration.value = 0;

      // 0.1s interval for smoother progress bar
      recordingTimer.value = setInterval(() => {
        recordingDuration.value += 0.1;
      }, 100);
    } catch (err) {
      Logger.error('마이크 접근 실패:', err);
      error.value = err;
      if (err.name === 'NotReadableError') {
        alert('마이크를 시작할 수 없습니다. (다른 앱이 마이크 사용 중일 수 있음)');
      } else if (err.name === 'NotAllowedError') {
        alert('마이크 권한이 거부되었습니다.');
      } else {
        alert(`마이크 오류: ${err.name}`);
      }
    }
  };

  const stopRecording = () => {
    return new Promise((resolve) => {
      if (recorder.value && recorder.value.state !== 'inactive') {
        stopResolver = resolve;
        recorder.value.stop();
      } else {
        resolve(null);
      }
      stopStream();
      isRecording.value = false;
    });
  };

  const stopStream = () => {
    if (stream.value) {
      stream.value.getTracks().forEach((track) => track.stop());
      stream.value = null;
    }
    if (recordingTimer.value) {
      clearInterval(recordingTimer.value);
      recordingTimer.value = null;
    }
  };

  const cleanup = () => {
    stopStream();
    recordedBlob.value = null;
    if (audioUrl.value) {
      URL.revokeObjectURL(audioUrl.value);
      audioUrl.value = null;
    }
    error.value = null;
    recordingDuration.value = 0;
    stopResolver = null;
  };

  onUnmounted(() => {
    cleanup();
  });

  return {
    isRecording,
    recordingDuration,
    recordedBlob,
    audioUrl,
    error,
    startRecording,
    stopRecording,
    cleanup,
  };
}
