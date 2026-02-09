import api from './api';
import { Logger } from '@/services/logger';

export const healthService = {
    getDailyReport: async (groupId, date) => {
        try {
            const response = await api.get('/health/report', {
                params: { groupId, date }
            });
            return response.data.data;
        } catch (error) {
            Logger.error('건강 리포트 조회 실패:', error);
            throw error;
        }
    },
    getLatestMetrics: async (groupId) => {
        try {
            const response = await api.get('/health/latest', {
                params: { groupId },
                headers: { silent: true }
            });
            return response.data.data;
        } catch (error) {
            Logger.error('최신 건강 지표 조회 실패:', error);
            throw error;
        }
    },
    saveHealthMetrics: async (groupId, payload) => {
        try {
            const response = await api.post(`/health/data?groupId=${groupId}`, payload);
            return response.data.data;
        } catch (error) {
            Logger.error('건강 지표 저장 실패:', error);
            throw error;
        }
    },
    analyzeDailyReport: async (groupId, date) => {
        try {
            const response = await api.post('/health/analyze', null, {
                params: { groupId, date },
                headers: { silent: true }
            });
            return response.data.data;
        } catch (error) {
            Logger.error('건강 리포트 분석 실패:', error);
            throw error;
        }
    },
    requestMeasurement: async (groupId) => {
        try {
            await api.post('/health/request-measurement', null, {
                params: { groupId }
            });
            // No return value (void)
        } catch (error) {
            Logger.error('심박수 측정 요청 실패:', error);
            throw error;
        }
    },
    getLatestHeartRate: async (groupId) => {
        try {
            const response = await api.get('/health/heart-rate/latest', {
                params: { groupId }
            });
            return response.data.data;
        } catch (error) {
            Logger.error('최신 심박수 조회 실패:', error);
            throw error;
        }
    },
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
