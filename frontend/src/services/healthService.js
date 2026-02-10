import api from './api';
import { Logger } from '@/services/logger';

export const healthService = {
  /**
   * 일일 건강 리포트 조회
   * @param {string} groupId - 가족 그룹 ID
   * @param {string} date - 조회할 날짜 (YYYY-MM-DD)
   * @returns {Promise<Object>} 건강 리포트 데이터
   */
  getDailyReport: async (groupId, date) => {
    try {
      const response = await api.get('/health/report', {
        params: { groupId, date },
      });
      return response.data.data;
    } catch (error) {
      Logger.error('건강 리포트 조회 실패:', error);
      throw error;
    }
  },

  /**
   * 최신 건강 지표 조회
   * @param {string} groupId - 가족 그룹 ID
   * @returns {Promise<Object>} 최신 건강 지표 데이터
   */
  getLatestMetrics: async (groupId) => {
    try {
      const response = await api.get('/health/latest', {
        params: { groupId },
        headers: { silent: true },
      });
      return response.data.data;
    } catch (error) {
      Logger.error('최신 건강 지표 조회 실패:', error);
      throw error;
    }
  },

  /**
   * 건강 지표 데이터 저장 (수동/워치 연동)
   * @param {string} groupId - 가족 그룹 ID
   * @param {Object} payload - 저장할 건강 데이터
   * @returns {Promise<Object>} 저장된 데이터
   */
  saveHealthMetrics: async (groupId, payload) => {
    try {
      const response = await api.post(`/health/data?groupId=${groupId}`, payload);
      return response.data.data;
    } catch (error) {
      Logger.error('건강 지표 저장 실패:', error);
      throw error;
    }
  },

  /**
   * 일일 건강 리포트 분석 요청 (AI)
   * @param {string} groupId - 가족 그룹 ID
   * @param {string} date - 분석할 날짜 (YYYY-MM-DD)
   * @returns {Promise<Object>} 분석 결과
   */
  analyzeDailyReport: async (groupId, date) => {
    try {
      const response = await api.post('/health/analyze', null, {
        params: { groupId, date },
        headers: { silent: true },
        timeout: 60000, // 60 seconds timeout for AI analysis
      });
      return response.data.data;
    } catch (error) {
      Logger.error('건강 리포트 분석 실패:', error);
      throw error;
    }
  },

  /**
   * 워치 심박수 측정 요청
   * @param {string} groupId - 가족 그룹 ID
   * @returns {Promise<void>}
   */
  requestMeasurement: async (groupId) => {
    try {
      await api.post('/health/request-measurement', null, {
        params: { groupId },
      });
      // No return value (void)
    } catch (error) {
      Logger.error('심박수 측정 요청 실패:', error);
      throw error;
    }
  },

  /**
   * 최신 심박수 데이터 조회
   * @param {string} groupId - 가족 그룹 ID
   * @returns {Promise<Object>} 심박수 데이터
   */
  getLatestHeartRate: async (groupId) => {
    try {
      const response = await api.get('/health/heart-rate/latest', {
        params: { groupId },
      });
      return response.data.data;
    } catch (error) {
      Logger.error('최신 심박수 조회 실패:', error);
      throw error;
    }
  },

  /**
   * 특정 이벤트 ID에 대한 심박수 결과 조회
   * @param {string} eventId - 측정 이벤트 ID
   * @returns {Promise<Object>} 심박수 결과 데이터
   */
  getHeartRateResult: async (eventId) => {
    try {
      const response = await api.get(`/health/heart-rate/${eventId}`);
      return response.data.data;
    } catch (error) {
      Logger.error('심박수 결과 조회 실패:', error);
      throw error;
    }
  },
};

export default healthService;
