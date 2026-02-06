import apiClient from './api';

export const familyService = {
    // 가족 멤버 목록 조회
    getFamilyMembers: (familyId) => {
        return apiClient.get(`/families/${familyId}/members`);
    },

    // 가족 상세 정보 조회
    getFamilyDetails: (familyId) => {
        return apiClient.get(`/families/${familyId}/details`);
    }
};
