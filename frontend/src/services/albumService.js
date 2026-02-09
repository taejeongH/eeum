import apiClient from './api';
import { Logger } from '@/services/logger';
import axios from 'axios';


export const getPhotos = (familyId) => {
    return apiClient.get(`/families/${familyId}/album`, { headers: { silent: true } });
};


export const getPresignedUrl = (fileName, contentType) => {
    return apiClient.get('/album/presigned-url', {
        params: { fileName, contentType }
    });
};


export const savePhotoMetadata = (familyId, photoData) => {
    return apiClient.post(`/families/${familyId}/album`, photoData);
};


export const deletePhoto = (photoId) => {
    return apiClient.delete(`/album/${photoId}`);
};


export const updatePhoto = (photoId, photoData) => {
    return apiClient.patch(`/album/${photoId}`, photoData);
};


export const saveBulkPhotoMetadata = (familyId, photosData) => {
    return apiClient.post(`/families/${familyId}/album/bulk`, photosData);
};


export const uploadFile = async (familyId, file, description) => {
    try {
        
        const presignedRes = await getPresignedUrl(file.name, file.type);
        const innerData = presignedRes.data.data || presignedRes.data;
        const uploadUrl = innerData.url || innerData.presignedUrl;
        const storageFileName = innerData.fileName;

        if (!uploadUrl) {
            throw new Error("서버로부터 업로드 URL을 받지 못했습니다.");
        }

        
        const uploadResponse = await fetch(uploadUrl, {
            method: 'PUT',
            headers: {
                'Content-Type': file.type
            },
            body: file
        });

        if (!uploadResponse.ok) {
            throw new Error(`S3 업로드 실패: ${uploadResponse.status}`);
        }

        
        const metadata = {
            storageUrl: storageFileName,
            takenAt: new Date().toISOString().split('T')[0],
            description: description || file.name
        };

        await savePhotoMetadata(familyId, metadata);
        return true;
    } catch (error) {
        Logger.error("파일 업로드 실패:", error);
        throw error;
    }
};


export const bulkUploadFiles = async (familyId, files, description) => {
    try {
        
        const presignedPromises = Array.from(files).map(file => getPresignedUrl(file.name, file.type));
        const presignedResponses = await Promise.all(presignedPromises);

        const uploadTasks = Array.from(files).map(async (file, index) => {
            const innerData = presignedResponses[index].data.data || presignedResponses[index].data;
            const uploadUrl = innerData.url || innerData.presignedUrl;
            const storageFileName = innerData.fileName;

            
            const uploadResponse = await fetch(uploadUrl, {
                method: 'PUT',
                headers: { 'Content-Type': file.type },
                body: file
            });

            if (!uploadResponse.ok) throw new Error(`${file.name}의 S3 업로드 실패`);

            return {
                storageUrl: storageFileName,
                takenAt: new Date().toISOString().split('T')[0],
                description: description || file.name
            };
        });

        const allMetadata = await Promise.all(uploadTasks);

        
        await saveBulkPhotoMetadata(familyId, allMetadata);
        return true;
    } catch (error) {
        Logger.error("일괄 업로드 실패:", error);
        throw error;
    }
};
