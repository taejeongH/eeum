import apiClient from './api';
import { Logger } from '@/services/logger';

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
};
