import apiClient from './api';
import { Logger } from '@/services/logger';

/**
 * 학습용 대본 목록 조회
 * @returns {Promise<Array>} 대본 목록
 */
export const getScripts = async () => {
    const response = await apiClient.get('/voice/scripts');
    return response.data.data;
};

/**
 * 음성 샘플 업로드용 Presigned URL 발급
 * @param {number} scriptId - 대본 ID
 * @returns {Promise<string>} Presigned URL
 */
/**
 * 음성 샘플 업로드용 Presigned URL 발급
 * @param {number} scriptId - 대본 ID
 * @param {string} extension - 파일 확장자 (예: wav, webm, mp4 ... default: wav)
 * @returns {Promise<string>} Presigned URL
 */
export const getPresignedUrl = async (extension = 'wav') => {
    // New spec: only needs extension
    const response = await apiClient.get('/voice/presigned-url', {
        params: { extension },
    });
    return response.data;
};

/**
 * 음성 샘플 메타데이터 저장
 * @param {Object} data
 * @param {number} data.scriptId - 대본 ID
 * @param {string} data.samplePath - S3 경로 (URL 아님)
 * @param {number} data.durationSec - 녹음 길이 (3~10초)
 * @param {string} [data.transcript] - 프리토킹 대본 (선택)
 * @param {string} [data.nickname] - 샘플 별명 (선택)
 */
export const saveSample = async (data) => {
    return apiClient.post('/voice/samples', data);
};

/**
 * 음성 모델 학습 상태 및 샘플 목록 조회
 */
export const getVoiceStatus = async () => {
    const response = await apiClient.get('/voice/status');
    return response.data.data;
};

/**
 * 대표 음성 샘플 설정
 * @param {number} sampleId - 대표로 설정할 샘플 ID
 */
export const setRepresentativeSample = async (sampleId) => {
    if (!sampleId) throw new Error("Sample ID is required");
    return apiClient.post('/voice/representative', {}, {
        params: { sampleId }
    });
};

/**
 * TTS 생성 및 전송 요청 (IoT용)
 * @param {Object} data
 * @param {string} data.text - 변환할 텍스트
 * @param {number} data.groupId - 전송 대상 기기 그룹 ID
 */
export const generateTts = async (data) => {
    return apiClient.post('/voice/tts', data);
};

/**
 * 테스트 오디오 생성 요청 (웹 미리듣기용 일괄 생성)
 */
export const generateTestAudio = async () => {
    return apiClient.post('/voice/samples/test-generate');
};

/**
 * 테스트 오디오 목록 조회
 * @returns {Promise<Array>}
 */
export const getTestAudioList = async () => {
    const response = await apiClient.get('/voice/samples/test-audio');
    return response.data.data;
};

/**
 * 음성 샘플 삭제
 * @param {number} sampleId 
 */
export const deleteSample = async (sampleId) => {
    return apiClient.delete(`/voice/samples/${sampleId}`);
};

/**
 * 음성 샘플 별명 수정
 * @param {number} sampleId
 * @param {string} nickname
 */
export const updateNickname = async (sampleId, nickname) => {
    return apiClient.patch(`/voice/samples/${sampleId}/nickname`, null, {
        params: { nickname }
    });
};

// ... (skipping to uploadVoiceSample) ...

/**
 * Helper: 전체 업로드 프로세스 진행
 * 1. Presigned URL 발급
 * 2. S3 업로드
 * 3. 메타데이터 저장
 */
/**
 * Helper: 전체 업로드 프로세스 진행 (대본/프리토킹 통합)
 * 1. Presigned URL 발급
 * 2. S3 업로드
 * 3. 메타데이터 저장
 * 
 * @param {Blob} file - 녹음 파일
 * @param {number|null} scriptId - 대본 ID (프리토킹인 경우 null)
 * @param {number} durationSec - 녹음 길이 (초 단위, 3.0 ~ 10.0 필수)
 * @param {string|null} transcript - 프리토킹인 경우 필수, 대본이면 생략 가능
 */
export const uploadVoiceSample = async (file, scriptId, durationSec, transcript = null) => {
    try {

        // Duration Validation
        if (durationSec < 3.0 || durationSec > 10.0) {
            throw new Error(`녹음 길이는 3초 이상 10초 이하여야 합니다. (현재: ${durationSec.toFixed(1)}초)`);
        }

        // Determine extension and matching MIME type (Must match Backend's VoiceService.java)
        let extension = 'wav';
        let contentType = 'audio/wav';

        if (file.type.includes('webm')) {
            extension = 'webm';
            contentType = 'audio/webm';
        } else if (file.type.includes('mp4') || file.type.includes('m4a')) {
            extension = 'm4a';
            contentType = 'audio/x-m4a';
        } else if (file.type.includes('mpeg') || file.type.includes('mp3')) {
            extension = 'mp3';
            contentType = 'audio/mpeg';
        } else if (file.type.includes('ogg')) {
            extension = 'ogg';
            contentType = 'audio/ogg';
        }


        // 1. Get Presigned URL
        // Script mode: pass scriptId, Free Talk: pass null? No, API spec implies we need extension always.
        // Spec says /api/voice/presigned-url takes extension.
        const presignedResponse = await getPresignedUrl(extension);
        // Wait, spec for presigned says: param extension (query). No scriptId mentioned in new spec text for presigned.
        // "Name Description extension string (query) Default value : wav" - NO scriptId param listed in snippet.
        // So I will remove scriptId from getPresignedUrl call or keep if legacy.

        let fullPresignedUrl = "";
        if (typeof presignedResponse === 'string') fullPresignedUrl = presignedResponse;
        else if (presignedResponse?.data) fullPresignedUrl = presignedResponse.data;
        else if (presignedResponse?.message && presignedResponse.message.startsWith('http')) fullPresignedUrl = presignedResponse.message;

        if (!fullPresignedUrl) throw new Error("유효한 업로드 URL을 받지 못했습니다.");

        // Extract key for DB saving
        let samplePath = "";
        try {
            const urlObj = new URL(fullPresignedUrl);
            const pathname = urlObj.pathname;
            samplePath = pathname.startsWith('/') ? pathname.substring(1) : pathname;
        } catch (e) {
            throw new Error(`잘못된 URL 형식: ${fullPresignedUrl}`);
        }


        // 2. Upload to S3 (MUST use the exact content-type used for Presigned URL)
        const uploadResponse = await fetch(fullPresignedUrl, {
            method: 'PUT',
            headers: { 'Content-Type': contentType },
            body: file
        });

        if (!uploadResponse.ok) {
            throw new Error(`S3 업로드 실패: ${uploadResponse.status}`);
        }

        // 3. Save Metadata
        const payload = {
            scriptId: scriptId, // can be null
            samplePath: samplePath,
            durationSec: parseFloat(durationSec.toFixed(1)),
            transcript: transcript, // Required for free talk
            nickname: scriptId ? `Script ${scriptId}` : `Free Talk`
        };

        await saveSample(payload);

        return true;
    } catch (error) {
        Logger.error("음성 업로드 처리 실패:", error);
        throw error;
    }
};

/**
 * GMS Whisper API를 통한 음성 받아쓰기 (STT)
 * @param {Blob} file - 오디오 파일 (mp3, wav 등)
 * @returns {Promise<string>} 변환된 텍스트
 */
export const transcribeAudio = async (file) => {
    try {

        const formData = new FormData();
        const ext = file.type.includes('webm') ? 'webm' :
            file.type.includes('mp4') || file.type.includes('m4a') ? 'm4a' :
                file.type.includes('mpeg') || file.type.includes('mp3') ? 'mp3' : 'wav';

        formData.append('file', file, `recording.${ext}`);
        formData.append('model', 'whisper-1');
        formData.append('language', 'ko');

        const gmsKey = import.meta.env.VITE_GMS_KEY;
        if (!gmsKey) {
            throw new Error("GMS Key is missing in environment variables.");
        }

        // Use relative URL handled by Nginx proxy to avoid CORS and 405 errors
        const response = await fetch('/gmsapi/api.openai.com/v1/audio/transcriptions', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${gmsKey}`
            },
            body: formData
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Transcription failed (${response.status}): ${errorText}`);
        }

        const data = await response.json();
        return data.text;
    } catch (error) {
        Logger.error("음성 변환(STT) 오류:", error);
        throw error;
    }
};

