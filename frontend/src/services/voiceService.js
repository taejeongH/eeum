import apiClient from './api';
import { Logger } from '@/services/logger';


export const getScripts = async () => {
    const response = await apiClient.get('/voice/scripts');
    return response.data.data;
};



export const getPresignedUrl = async (extension = 'wav') => {
    
    const response = await apiClient.get('/voice/presigned-url', {
        params: { extension },
    });
    return response.data;
};


export const saveSample = async (data) => {
    return apiClient.post('/voice/samples', data);
};


export const getVoiceStatus = async () => {
    const response = await apiClient.get('/voice/status');
    return response.data.data;
};


export const setRepresentativeSample = async (sampleId) => {
    if (!sampleId) throw new Error("Sample ID is required");
    return apiClient.post('/voice/representative', {}, {
        params: { sampleId }
    });
};


export const generateTts = async (data) => {
    return apiClient.post('/voice/tts', data);
};


export const generateTestAudio = async () => {
    return apiClient.post('/voice/samples/test-generate');
};


export const getTestAudioList = async () => {
    const response = await apiClient.get('/voice/samples/test-audio');
    return response.data.data;
};


export const deleteSample = async (sampleId) => {
    return apiClient.delete(`/voice/samples/${sampleId}`);
};


export const updateNickname = async (sampleId, nickname) => {
    return apiClient.patch(`/voice/samples/${sampleId}/nickname`, null, {
        params: { nickname }
    });
};





export const uploadVoiceSample = async (file, scriptId, durationSec, transcript = null) => {
    try {

        
        if (durationSec < 3.0 || durationSec > 10.0) {
            throw new Error(`녹음 길이는 3초 이상 10초 이하여야 합니다. (현재: ${durationSec.toFixed(1)}초)`);
        }

        
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


        
        
        
        const presignedResponse = await getPresignedUrl(extension);
        
        
        

        let fullPresignedUrl = "";
        if (typeof presignedResponse === 'string') fullPresignedUrl = presignedResponse;
        else if (presignedResponse?.data) fullPresignedUrl = presignedResponse.data;
        else if (presignedResponse?.message && presignedResponse.message.startsWith('http')) fullPresignedUrl = presignedResponse.message;

        if (!fullPresignedUrl) throw new Error("유효한 업로드 URL을 받지 못했습니다.");

        
        let samplePath = "";
        try {
            const urlObj = new URL(fullPresignedUrl);
            const pathname = urlObj.pathname;
            samplePath = pathname.startsWith('/') ? pathname.substring(1) : pathname;
        } catch (e) {
            throw new Error(`잘못된 URL 형식: ${fullPresignedUrl}`);
        }


        
        const uploadResponse = await fetch(fullPresignedUrl, {
            method: 'PUT',
            headers: { 'Content-Type': contentType },
            body: file
        });

        if (!uploadResponse.ok) {
            throw new Error(`S3 업로드 실패: ${uploadResponse.status}`);
        }

        
        const payload = {
            scriptId: scriptId, 
            samplePath: samplePath,
            durationSec: parseFloat(durationSec.toFixed(1)),
            transcript: transcript, 
            nickname: scriptId ? `Script ${scriptId}` : `Free Talk`
        };

        await saveSample(payload);

        return true;
    } catch (error) {
        Logger.error("음성 업로드 처리 실패:", error);
        throw error;
    }
};


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

