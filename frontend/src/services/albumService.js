import apiClient from './api';
import axios from 'axios';

// Get list of photos for a family
export const getPhotos = (familyId) => {
    return apiClient.get(`/families/${familyId}/album`);
};

// Get presigned URL for S3 upload
export const getPresignedUrl = (fileName, contentType) => {
    return apiClient.get('/album/presigned-url', {
        params: { fileName, contentType }
    });
};

// Upload photo metadata to backend
export const savePhotoMetadata = (familyId, photoData) => {
    return apiClient.post(`/families/${familyId}/album`, photoData);
};

// Delete photo
export const deletePhoto = (photoId) => {
    return apiClient.delete(`/album/${photoId}`);
};

// Update photo
export const updatePhoto = (photoId, photoData) => {
    return apiClient.patch(`/album/${photoId}`, photoData);
};

// Helper to handle full upload flow
// Helper to handle full upload flow
export const uploadFile = async (familyId, file, description) => {
    try {
        // 1. Get Presigned URL
        // Method: GET /api/album/presigned-url
        // Query: fileName, contentType
        const presignedRes = await getPresignedUrl(file.name, file.type);


        // Expected Response: { url: "...", fileName: "album/uuid..." }
        // Adjust extraction based on actual wrapper (data.data or data.result etc if exists)
        // Based on previous logs, it might be inside 'data'
        const innerData = presignedRes.data.data || presignedRes.data;
        const uploadUrl = innerData.url || innerData.presignedUrl;
        const storageFileName = innerData.fileName; // Key for backend storage

        if (!uploadUrl) {
            throw new Error("Failed to get upload URL from server");
        }

        // 2. Upload to S3
        // Use fetch to avoid axios interceptors affecting S3 PUT
        const uploadResponse = await fetch(uploadUrl, {
            method: 'PUT',
            headers: {
                'Content-Type': file.type
            },
            body: file
        });

        if (!uploadResponse.ok) {
            throw new Error(`S3 Upload failed: ${uploadResponse.status}`);
        }

        // Wait a bit for S3 eventual consistency if needed
        await new Promise(resolve => setTimeout(resolve, 500));

        // 3. Save Metadata
        // Method: POST /api/families/{familyId}/album
        // Body: { storageUrl, takenAt, description }
        const metadata = {
            storageUrl: storageFileName,
            takenAt: new Date().toISOString().split('T')[0],
            description: description || file.name // Use provided description or fallback to filename
        };



        await savePhotoMetadata(familyId, metadata);

        return true;
    } catch (error) {
        console.error("File upload failed:", error);
        throw error;
    }
};
