import api from './api';

export const healthService = {
    getDailyReport: async (groupId, date) => {
        try {
            const response = await api.get('/health/report', {
                params: { groupId, date }
            });
            return response.data.data;
        } catch (error) {
            console.error('Failed to fetch health report:', error);
            throw error;
        }
    },
    getLatestMetrics: async (groupId) => {
        try {
            const response = await api.get('/health/latest', {
                params: { groupId }
            });
            return response.data.data;
        } catch (error) {
            console.error('Failed to fetch latest health metrics:', error);
            throw error;
        }
    },
    saveHealthMetrics: async (groupId, payload) => {
        try {
            const response = await api.post(`/health/data?groupId=${groupId}`, payload);
            return response.data.data;
        } catch (error) {
            console.error('Failed to save health metrics:', error);
            throw error;
        }
    },
    analyzeDailyReport: async (groupId, date) => {
        try {
            const response = await api.post('/health/analyze', null, {
                params: { groupId, date },
                skipLoading: true
            });
            return response.data.data;
        } catch (error) {
            console.error('Failed to analyze health report:', error);
            throw error;
        }
    },
};

export default healthService;
