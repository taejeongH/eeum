import apiClient from './api';

/**
 * 가족 정보 관련 API 호출을 담당하는 서비스 객체입니다.
 */
export const familyService = {
  /**
   * 특정 가족의 멤버 목록을 조회합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @returns {Promise<Object>} API 응답 객체
   */
  getFamilyMembers: (familyId) => {
    return apiClient.get(`/families/${familyId}/members`);
  },

  /**
   * 가족의 상세 정보(피부양자, 그룹 이름 등)를 조회합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @returns {Promise<Object>} API 응답 객체
   */
  getFamilyDetails: (familyId) => {
    return apiClient.get(`/families/${familyId}/details`);
  },
};
