import api from './api';

export const healthService = {
    getDailyReport: async (groupId, date) => {
        try {
            const response = await api.get('/health/report', {
                params: { groupId, date }
            });
            return response.data;
        } catch (error) {
            console.error('Failed to fetch health report:', error);
            throw error;
        }
    },
    // We can add more health related methods here later
};

export default healthService;
