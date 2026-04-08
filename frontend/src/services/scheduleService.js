import apiClient from './api';
import { Logger } from '@/services/logger';

/**
 * 일정 관련 API 호출을 담당하는 서비스 객체입니다.
 */
export const scheduleService = {
    
    
    async getMonthlySchedules(familyId, year, month) {
        try {
            const response = await apiClient.get(`/families/${familyId}/schedules`, {
                params: { year, month },
                headers: { silent: true }
            });

            
            const scheduleList = response.data.data || [];

            
            return scheduleList.filter(item => item.title !== 'EXCLUDED');
        } catch (error) {
            Logger.error("일정 로드 실패:", error);
            Logger.error("에러 응답 데이터:", error.response?.data);
            throw error;
        }
    },

    
    async getSchedule(familyId, scheduleId) {
        try {
            const response = await apiClient.get(`/families/${familyId}/schedules/${scheduleId}`);
            
            return response.data.data;
        } catch (error) {
            Logger.error("일정 상세 조회 실패:", error);
            throw error;
        }
    },

    
    async createSchedule(familyId, formData) {
        const requestBody = {
            title: formData.title,
            description: formData.description,
            categoryType: formData.categoryType || 'VISIT',
            startAt: formData.startAt,
            endAt: formData.endAt,
            isLunar: formData.isLunar ? 1 : 0, 
            repeatType: formData.repeatType || 'NONE',
            recurrenceEndAt: formData.recurrenceEndAt || null,
            targetPerson: formData.targetPerson || null,
            visitPurpose: formData.visitPurpose || null,
            visitorName: formData.visitorName || null
        };


        try {
            return await apiClient.post(`/families/${familyId}/schedules`, requestBody);
        } catch (error) {
            Logger.error("일정 생성 에러 상세:", error.response?.data);
            throw error;
        }
    },

    
    async updateSchedule(familyId, scheduleId, formData) {
        const requestBody = {
            title: formData.title,
            description: formData.description,
            categoryType: formData.categoryType || 'VISIT',
            startAt: formData.startAt,
            endAt: formData.endAt,
            isLunar: formData.isLunar ? 1 : 0,
            repeatType: formData.repeatType || 'NONE',
            recurrenceEndAt: formData.recurrenceEndAt,
            targetPerson: formData.targetPerson,
            visitPurpose: formData.visitPurpose,
            visitorName: formData.visitorName
        };
        return await apiClient.put(`/families/${familyId}/schedules/${scheduleId}`, requestBody);
    },

    
    async deleteSchedule(familyId, scheduleId, deleteAll = false) {
        return await apiClient.delete(`/families/${familyId}/schedules/${scheduleId}`, {
            params: { delete_all: deleteAll }
        });
    },

    
    async patchVisitStatus(familyId, scheduleId, isVisited) {
        return await apiClient.patch(
            `/families/${familyId}/schedules/${scheduleId}/visit`,
            null,
            { params: { visited: isVisited } }
        );
    }
  },

  /**
   * 일정 상세 정보를 조회합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {string|number} scheduleId - 조회할 일정의 고유 ID
   * @returns {Promise<Object>} 일정 상세 정보
   */
  async getSchedule(familyId, scheduleId) {
    try {
      const response = await apiClient.get(`/families/${familyId}/schedules/${scheduleId}`);
      return response.data.data;
    } catch (error) {
      Logger.error('일정 상세 조회 실패:', error);
      throw error;
    }
  },

  /**
   * 새로운 일정을 등록합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {Object} formData - 일정 등록 정보 (title, description, startAt, endAt 등)
   * @returns {Promise} API 응답 객체
   */
  async createSchedule(familyId, formData) {
    const requestBody = {
      title: formData.title,
      description: formData.description,
      categoryType: formData.categoryType || 'VISIT',
      startAt: formData.startAt,
      endAt: formData.endAt,
      isLunar: formData.isLunar ? 1 : 0,
      repeatType: formData.repeatType || 'NONE',
      recurrenceEndAt: formData.recurrenceEndAt || null,
      targetPerson: formData.targetPerson || null,
      visitPurpose: formData.visitPurpose || null,
      visitorName: formData.visitorName || null,
    };

    try {
      return await apiClient.post(`/families/${familyId}/schedules`, requestBody);
    } catch (error) {
      Logger.error('일정 생성 실패:', error.response?.data);
      throw error;
    }
  },

  /**
   * 기존 일정을 수정합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {string|number} scheduleId - 수정할 일정의 고유 ID
   * @param {Object} formData - 수정할 일정 정보
   * @returns {Promise} API 응답 객체
   */
  async updateSchedule(familyId, scheduleId, formData) {
    const requestBody = {
      title: formData.title,
      description: formData.description,
      categoryType: formData.categoryType || 'VISIT',
      startAt: formData.startAt,
      endAt: formData.endAt,
      isLunar: formData.isLunar ? 1 : 0,
      repeatType: formData.repeatType || 'NONE',
      recurrenceEndAt: formData.recurrenceEndAt,
      targetPerson: formData.targetPerson,
      visitPurpose: formData.visitPurpose,
      visitorName: formData.visitorName,
    };
    return await apiClient.put(`/families/${familyId}/schedules/${scheduleId}`, requestBody);
  },

  /**
   * 일정을 삭제합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {string|number} scheduleId - 삭제할 일정의 고유 ID
   * @param {boolean} [deleteAll=false] - 반복 일정의 경우 전체 삭제할지 여부
   * @returns {Promise} API 응답 객체
   */
  async deleteSchedule(familyId, scheduleId, deleteAll = false) {
    return await apiClient.delete(`/families/${familyId}/schedules/${scheduleId}`, {
      params: { delete_all: deleteAll },
    });
  },

  /**
   * 일정의 방문 완료 여부 상태를 변경합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {string|number} scheduleId - 변경할 일정의 고유 ID
   * @param {boolean} isVisited - 방문 완료 여부
   * @returns {Promise} API 응답 객체
   */
  async patchVisitStatus(familyId, scheduleId, isVisited) {
    return await apiClient.patch(`/families/${familyId}/schedules/${scheduleId}/visit`, null, {
      params: { visited: isVisited },
    });
  },
};
