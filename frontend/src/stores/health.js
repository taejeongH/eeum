import { defineStore } from 'pinia';
import healthService from '@/services/healthService';
import { Logger } from '@/services/logger';

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
                Logger.error('최신 건강 지표 조회 실패:', err);
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
                Logger.error('건강 리포트 조회 오류:', err);
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
                Logger.error('리포트 재분석 실패:', err);
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
