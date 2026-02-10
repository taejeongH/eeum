import apiClient from './api';
import { Logger } from '@/services/logger';

/**
 * 가족별 사진 목록을 조회합니다.
 * @param {string|number} familyId - 가족 그룹의 고유 ID
 * @returns {Promise} API 응답 객체
 */
export const getPhotos = (familyId) => {
  return apiClient.get(`/families/${familyId}/album`, { headers: { silent: true } });
};

/**
 * S3 업로드를 위한 Presigned URL을 발급받습니다.
 * @param {string} fileName - 원본 파일명
 * @param {string} contentType - 파일의 MIME 타입 (e.g. 'image/jpeg')
 * @returns {Promise} Presigned URL과 저장될 파일명을 포함한 응답 객체
 */
export const getPresignedUrl = (fileName, contentType) => {
  return apiClient.get('/album/presigned-url', {
    params: { fileName, contentType },
  });
};

/**
 * 사진 메타데이터를 서버에 저장합니다.
 * @param {string|number} familyId - 가족 그룹의 고유 ID
 * @param {Object} photoData - 사진 상세 정보 (storageUrl, takenAt, description 등)
 * @returns {Promise} API 응답 객체
 */
export const savePhotoMetadata = (familyId, photoData) => {
  return apiClient.post(`/families/${familyId}/album`, photoData);
};

/**
 * 특정 사진을 삭제합니다.
 * @param {string|number} photoId - 삭제할 사진의 고유 ID
 * @returns {Promise} API 응답 객체
 */
export const deletePhoto = (photoId) => {
  return apiClient.delete(`/album/${photoId}`);
};

/**
 * 사진 정보를 수정합니다.
 * @param {string|number} photoId - 수정할 사진의 고유 ID
 * @param {Object} photoData - 수정할 사진 정보
 * @returns {Promise} API 응답 객체
 */
export const updatePhoto = (photoId, photoData) => {
  return apiClient.patch(`/album/${photoId}`, photoData);
};

/**
 * 다중 사진 메타데이터를 서버에 일괄 저장합니다.
 * @param {string|number} familyId - 가족 그룹의 고유 ID
 * @param {Array<Object>} photosData - 여러 사진의 메타데이터 배열
 * @returns {Promise} API 응답 객체
 */
export const saveBulkPhotoMetadata = (familyId, photosData) => {
  return apiClient.post(`/families/${familyId}/album/bulk`, photosData);
};

/**
 * 단일 사진 업로드 전체 프로세스를 처리하는 헬퍼 함수입니다.
 * (Presigned URL 발급 -> S3 직접 업로드 -> 메타데이터 저장)
 * @param {string|number} familyId - 가족 그룹의 고유 ID
 * @param {File} file - 업로드할 파일 객체
 * @param {string} [description] - 사진 설명
 * @returns {Promise<boolean>} 성공 여부
 */
export const uploadFile = async (familyId, file, description) => {
  try {
    // 1. Presigned URL 요청
    const presignedRes = await getPresignedUrl(file.name, file.type);
    const innerData = presignedRes.data.data || presignedRes.data;
    const uploadUrl = innerData.url || innerData.presignedUrl;
    const storageFileName = innerData.fileName;

    if (!uploadUrl) {
      throw new Error('서버로부터 업로드 URL을 받지 못했습니다.');
    }

    // 2. S3에 직접 업로드
    const uploadResponse = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': file.type,
      },
      body: file,
    });

    if (!uploadResponse.ok) {
      throw new Error(`S3 업로드 실패: ${uploadResponse.status}`);
    }

    // 3. 메타데이터 저장
    const metadata = {
      storageUrl: storageFileName,
      takenAt: new Date().toISOString().split('T')[0],
      description: description || file.name,
    };

    await savePhotoMetadata(familyId, metadata);
    return true;
  } catch (error) {
    Logger.error('파일 업로드 실패:', error);
    throw error;
  }
};

/**
 * 다중 사진 병렬 업로드 및 일괄 저장을 처리하는 헬퍼 함수입니다.
 * @param {string|number} familyId - 가족 그룹의 고유 ID
 * @param {FileList|File[]} files - 업로드할 파일 목록
 * @param {string} [description] - 공통 사진 설명
 * @returns {Promise<boolean>} 성공 여부
 */
export const bulkUploadFiles = async (familyId, files, description) => {
  try {
    // 1. 모든 파일에 대한 Presigned URL 병렬 요청
    const presignedPromises = Array.from(files).map((file) =>
      getPresignedUrl(file.name, file.type),
    );
    const presignedResponses = await Promise.all(presignedPromises);

    const uploadTasks = Array.from(files).map(async (file, index) => {
      const innerData = presignedResponses[index].data.data || presignedResponses[index].data;
      const uploadUrl = innerData.url || innerData.presignedUrl;
      const storageFileName = innerData.fileName;

      // 2. 각 파일을 S3에 병렬 업로드
      const uploadResponse = await fetch(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type },
        body: file,
      });

      if (!uploadResponse.ok) throw new Error(`${file.name}의 S3 업로드 실패`);

      return {
        storageUrl: storageFileName,
        takenAt: new Date().toISOString().split('T')[0],
        description: description || file.name,
      };
    });

    const allMetadata = await Promise.all(uploadTasks);

    // 3. 모든 메타데이터를 일괄 저장 요청
    await saveBulkPhotoMetadata(familyId, allMetadata);
    return true;
  } catch (error) {
    Logger.error('일괄 업로드 실패:', error);
    throw error;
  }
};
