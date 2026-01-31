import { defineStore } from 'pinia';
import healthService from '@/services/healthService';

export const useHealthStore = defineStore('health', {
    state: () => ({
        currentReport: null,
        loading: false,
        error: null,
    }),
    actions: {
        async fetchDailyReport(groupId, date) {
            this.loading = true;
            this.error = null;
            try {
                const response = await healthService.getDailyReport(groupId, date);
                this.currentReport = response.data;
            } catch (err) {
                this.error = '건강 리포트를 불러오는데 실패했습니다.';
                console.error(err);
            } finally {
                this.loading = false;
            }
        }
    }
});
