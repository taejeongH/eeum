import { defineStore } from 'pinia';
import healthService from '@/services/healthService';

export const useHealthStore = defineStore('health', {
    state: () => ({
        latestMetrics: {},
        currentReport: null,
        loading: false,
        error: null,
    }),
    actions: {
        async fetchLatestMetrics(groupId) {
            this.loading = true;
            try {
                const data = await healthService.getLatestMetrics(groupId);
                if (data) {
                    this.latestMetrics = data;
                }
            } catch (err) {
                console.error('Failed to fetch latest metrics:', err);
            } finally {
                this.loading = false;
            }
        },
        async fetchDailyReport(groupId, date) {
            this.loading = true;
            this.error = null;
            try {
                const response = await healthService.getDailyReport(groupId, date);
                this.currentReport = response;
            } catch (err) {
                this.error = '건강 리포트를 불러오는데 실패했습니다.';
                console.error(err);
            } finally {
                this.loading = false;
            }
        },
        async reanalyzeReport(groupId, date) {
            this.loading = true;
            try {
                const response = await healthService.analyzeDailyReport(groupId, date);
                this.currentReport = response;
            } catch (err) {
                console.error('Failed to reanalyze report:', err);
                throw err;
            } finally {
                this.loading = false;
            }
        },
        reset() {
            this.latestMetrics = {};
            this.currentReport = null;
            this.loading = false;
            this.error = null;
        }
    },
    persist: {
        key: 'health-store',
        storage: localStorage,
        paths: ['latestMetrics', 'currentReport']
    }
});
