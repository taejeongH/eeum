import { defineStore } from 'pinia';
import healthService from '@/services/healthService';
import { Logger } from '@/services/logger';

/**
 * @typedef {Object} HealthMetrics
 * @property {number} [steps] - 걸음 수
 * @property {number} [heartRate] - 심박수 (bpm)
 * @property {number} [sleepMinutes] - 수면 시간 (분)
 * @property {number} [calories] - 소모 칼로리 (kcal)
 * @property {string} [lastUpdated] - 마지막 업데이트 시간 (ISO string)
 */

/**
 * @typedef {Object} DailyReport
 * @property {string} date - 리포트 날짜 (YYYY-MM-DD)
 * @property {number} score - 건강 점수 (0-100)
 * @property {string} summary - 일일 요약 메시지
 * @property {Array<string>} advice - 건강 조언 목록
 * @property {string} [analysisResult] - AI 분석 결과 텍스트
 */

export const useHealthStore = defineStore('health', {
  state: () => ({
    /** @type {HealthMetrics} 최신 건강 지표 (걸음수, 심박수 등) */
    latestMetrics: {},
    /** @type {DailyReport|null} 일일 건강 리포트 데이터 */
    currentReport: null,
    /** @type {Object|null} 최근 심박수 정밀 측정 결과 */
    heartRateResult: null,
    /** @type {boolean} 데이터 로딩 상태 */
    loading: false,
    /** @type {string|null} 에러 메시지 */
    error: null,
  }),
  actions: {
    /**
     * 최신 건강 지표 조회
     * @param {string} groupId - 그룹 ID
     */
    async fetchLatestMetrics(groupId) {
      this.loading = true;
      this.error = null; // 에러 초기화
      try {
        const data = await healthService.getLatestMetrics(groupId);
        if (data) {
          this.latestMetrics = data;
        }
      } catch (err) {
        this.error = '최신 건강 데이터를 불러오는데 실패했습니다.';
        Logger.error('최신 건강 지표 조회 실패:', err);
      } finally {
        this.loading = false;
      }
    },

    /**
     * 일일 건강 리포트 조회
     * @param {string} groupId - 그룹 ID
     * @param {string} date - 조회 날짜 (YYYY-MM-DD)
     */
    async fetchDailyReport(groupId, date) {
      this.loading = true;
      this.error = null;
      try {
        const response = await healthService.getDailyReport(groupId, date);

        // [Fix] AI 분석 결과(description)가 서버에서 저장되지 않는 경우, 로컬에 남아있는 분석 결과를 유지
        if (this.currentReport) {
          // 로컬 데이터 날짜 확인 (reportDate 사용, 없으면 date)
          const oldDate = this.currentReport.reportDate
            ? this.currentReport.reportDate.split('T')[0]
            : this.currentReport.date
              ? this.currentReport.date.split('T')[0]
              : '';

          if (oldDate === date) {
            if (!response) {
              // 서버 데이터가 없으면(null), 로컬 데이터를 유지 (덮어쓰기 방지)
              return;
            }

            // 서버 데이터가 있지만 description이 없는 경우 병합
            // 서버 응답 날짜 확인 (reportDate 우선, 없으면 date)
            const newDate = response.reportDate
              ? response.reportDate.split('T')[0]
              : response.date
                ? response.date.split('T')[0]
                : '';

            if (newDate === oldDate || !newDate) {
              // description이 없거나 빈 배열인 경우
              const isDescriptionEmpty =
                !response.description ||
                (Array.isArray(response.description) && response.description.length === 0);

              if (isDescriptionEmpty && this.currentReport.description) {
                response.description = this.currentReport.description;
              }

              // summary가 없거나 기본 문구인 경우
              const isSummaryEmpty =
                !response.summary ||
                response.summary === '리포트 생성 전' ||
                response.summary === '오늘 수집된 건강 데이터가 없습니다.';

              if (
                isSummaryEmpty &&
                this.currentReport.summary &&
                typeof this.currentReport.summary === 'object'
              ) {
                response.summary = this.currentReport.summary;
              }
            }
          }
        }

        this.currentReport = response;
      } catch (err) {
        this.error = '건강 리포트를 불러오는데 실패했습니다.';
        Logger.error('건강 리포트 조회 오류:', err);
      } finally {
        this.loading = false;
      }
    },

    /**
     * 리포트 재분석 요청 (AI 분석 다시 실행)
     * @param {string} groupId - 그룹 ID
     * @param {string} date - 대상 날짜
     */
    async reanalyzeReport(groupId, date) {
      this.loading = true;
      this.error = null; // 에러 초기화
      try {
        const response = await healthService.analyzeDailyReport(groupId, date);
        this.currentReport = response;
      } catch (err) {
        this.error = '리포트 재분석 중 오류가 발생했습니다.';
        Logger.error('리포트 재분석 실패:', err);
        // throw err; // UI에서 별도 처리가 필요 없다면 throw 제거하고 state 에러로 처리
      } finally {
        this.loading = false;
      }
    },

    /**
     * 건강 데이터 저장 (모바일 기기에서 업로드)
     * @param {string} groupId - 그룹 ID
     * @param {Object} metrics - 저장할 건강 데이터
     */
    async saveHealthMetrics(groupId, metrics) {
      try {
        await healthService.saveHealthMetrics(groupId, [metrics]);
        // 저장 후 최신 데이터 갱신
        this.latestMetrics = metrics;
      } catch (err) {
        Logger.error('건강 데이터 저장 실패:', err);
        throw err;
      }
    },

    /**
     * 심박수 측정 요청 (워치 트리거)
     * @param {string} groupId
     */
    async requestMeasurement(groupId) {
      try {
        await healthService.requestMeasurement(groupId);
      } catch (err) {
        Logger.error('심박수 측정 요청 실패:', err);
        throw err;
      }
    },

    /**
     * 최신 심박수 측정 결과 조회
     * @param {string} groupId
     */
    async fetchLatestHeartRate(groupId) {
      this.loading = true;
      try {
        const data = await healthService.getLatestHeartRate(groupId);
        this.heartRateResult = data;
        return data;
      } catch (err) {
        Logger.error('최신 심박수 조회 오류:', err);
        throw err;
      } finally {
        this.loading = false;
      }
    },

    /** 스토어 상태 초기화 (로그아웃 시 사용) */
    reset() {
      this.latestMetrics = {};
      this.currentReport = null;
      this.loading = false;
      this.error = null;
    },
  },
  persist: {
    key: 'health-store',
    storage: localStorage,
    paths: ['latestMetrics', 'currentReport'],
  },
});
