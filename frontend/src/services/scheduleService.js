import apiClient from './api';
import { Logger } from '@/services/logger';

export const scheduleService = {
    // 월간 일정 조회
    // 월간 일정 조회
    async getMonthlySchedules(familyId, year, month) {
        try {
            const response = await apiClient.get(`/families/${familyId}/schedules`, {
                params: { year, month },
                headers: { silent: true }
            });

            // API 응답 구조가 { statusCode: ..., data: [...] } 형태임
            const scheduleList = response.data.data || [];

            // EXCLUDED 타이틀을 가진 일정은 필터링하여 반환
            return scheduleList.filter(item => item.title !== 'EXCLUDED');
        } catch (error) {
            Logger.error("일정 로드 실패:", error);
            Logger.error("에러 응답 데이터:", error.response?.data);
            throw error;
        }
    },

    // 일정 상세 조회
    async getSchedule(familyId, scheduleId) {
        try {
            const response = await apiClient.get(`/families/${familyId}/schedules/${scheduleId}`);
            // 상세 조회도 동일한 구조라고 가정 (확인 필요)
            return response.data.data;
        } catch (error) {
            Logger.error("일정 상세 조회 실패:", error);
            throw error;
        }
    },

    // 일정 등록
    async createSchedule(familyId, formData) {
        const requestBody = {
            title: formData.title,
            description: formData.description,
            categoryType: formData.categoryType || 'VISIT',
            startAt: formData.startAt,
            endAt: formData.endAt,
            isLunar: formData.isLunar ? 1 : 0, // 0 또는 1로 전송 (DB 호환성 고려)
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

    // 일정 수정
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

    // 일정 삭제
    async deleteSchedule(familyId, scheduleId, deleteAll = false) {
        return await apiClient.delete(`/families/${familyId}/schedules/${scheduleId}`, {
            params: { delete_all: deleteAll }
        });
    },

    // 방문 상태 변경
    async patchVisitStatus(familyId, scheduleId, isVisited) {
        return await apiClient.patch(
            `/families/${familyId}/schedules/${scheduleId}/visit`,
            null,
            { params: { visited: isVisited } }
        );
    }
};
