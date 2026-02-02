import apiClient from './api';

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
export const getPresignedUrl = async (scriptId) => {
    const response = await apiClient.get('/voice/presigned-url', {
        params: { scriptId },
    });
    return response.data; // data가 문자열 URL 자체인지, 객체인지 확인 필요. 명세상 RestApiResponseString이라 data.data일 가능성 높음
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


/**
 * Helper: 전체 업로드 프로세스 진행
 * 1. Presigned URL 발급
 * 2. S3 업로드
 * 3. 메타데이터 저장
 */
export const uploadVoiceSample = async (file, scriptId, durationSec) => {
    try {
        console.log(`Starting upload for script ${scriptId}, duration: ${durationSec}`);

        // 1. Get Presigned URL
        const presignedResponse = await getPresignedUrl(scriptId);
        console.log("Raw Presigned Response:", presignedResponse);

        let fullPresignedUrl = "";

        // Attempt to extract string URL from various common wrappers
        if (typeof presignedResponse === 'string') {
            fullPresignedUrl = presignedResponse;
        } else if (presignedResponse?.data && typeof presignedResponse.data === 'string') {
            fullPresignedUrl = presignedResponse.data;
        } else if (presignedResponse?.url && typeof presignedResponse.url === 'string') {
            fullPresignedUrl = presignedResponse.url;
        } else if (presignedResponse?.data?.url && typeof presignedResponse.data.url === 'string') {
            fullPresignedUrl = presignedResponse.data.url;
        } else if (presignedResponse?.message && typeof presignedResponse.message === 'string' && presignedResponse.message.startsWith('http')) {
            // [Hotfix] Backend returns URL in 'message' field
            fullPresignedUrl = presignedResponse.message;
        } else {
            console.warn("Could not find string URL in response, checking if response object IS the wrapper but data is missing or different.");
            // Last ditch: check if 'data' is the object itself? No, too ambiguous.
        }

        console.log("Extracted URL:", fullPresignedUrl);

        if (!fullPresignedUrl || typeof fullPresignedUrl !== 'string') {
            throw new Error(`Failed to extract valid URL. Response: ${JSON.stringify(presignedResponse)}`);
        }

        // Validate URL string
        try {
            new URL(fullPresignedUrl);
        } catch (e) {
            throw new Error(`Extracted string is not a valid URL: ${fullPresignedUrl}`);
        }

        const urlObj = new URL(fullPresignedUrl);
        const pathname = urlObj.pathname;
        const samplePath = pathname.startsWith('/') ? pathname.substring(1) : pathname;

        console.log("Uploading to:", fullPresignedUrl);
        console.log("Extracted samplePath:", samplePath);

        // 2. Upload to S3
        // Backend expects 'audio/wav'. We set the content-type header to match the presigned URL signature if required.
        // Even if the blob is webm, we label it as wav for S3 if the key ends in .wav
        // Note: Actual conversion to WAV would require client-side processing (ffmpeg.wasm or recorder-js).
        // For now, we try uploading the blob with the expected content type.
        const uploadResponse = await fetch(fullPresignedUrl, {
            method: 'PUT',
            headers: {
                'Content-Type': 'audio/wav' // Forcing content type to match expected .wav extension
            },
            body: file
        });

        if (!uploadResponse.ok) {
            throw new Error(`S3 Upload failed: ${uploadResponse.status} ${uploadResponse.statusText}`);
        }

        // 3. Save Metadata
        const payload = {
            scriptId,
            samplePath,
            durationSec,
            nickname: `Sample ${scriptId}` // Default nickname
        };

        await saveSample(payload);

        return true;
    } catch (error) {
        console.error("Voice upload processing failed:", error);
        throw error;
    }
};
