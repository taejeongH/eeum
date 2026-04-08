import apiClient from './api';

/**
 * 가족 정보 관련 API 호출을 담당하는 서비스 객체입니다.
 */
export const familyService = {
    
    getFamilyMembers: (familyId) => {
        return apiClient.get(`/families/${familyId}/members`);
    },

    
    getFamilyDetails: (familyId) => {
        return apiClient.get(`/families/${familyId}/details`);
    }
};
