// src/services/messageService.js
import api from './api'

export const messageService = {

    /**
     * 가족에게 메시지 전송
     * @param {number} familyId - 가족 ID
     * @param {Object} messageData - 메시지 데이터
     * @returns {Promise}
     */
    async sendFamilyMessage(familyId, messageData) {
        const response = await api.post(`/families/${familyId}/message`, messageData)
        return response.data
    },

    /**
     * 보낸 메시지 목록 조회
     * @param {number} familyId - 가족 ID
     * @param {Object} params - 페이지네이션 파라미터
     * @returns {Promise}
     */
    async getFamilyMessages(familyId, params = {}) {
        const response = await api.get(`/families/${familyId}/message`, { params })
        return response.data
    },

    /**
     * 가족 정보 조회
     * @param {number} familyId - 가족 ID
     * @returns {Promise}
     */
    async getFamilyInfo(familyId) {
        const response = await api.get(`/families/${familyId}`)
        return response.data
    },

    /**
     * 그룹(=familyId) 메시지 전송 (messages 테이블 기반)
     * POST /api/groups/{groupId}/messages
     */
    async sendGroupMessage(groupId, content) {
        const response = await api.post(`/groups/${groupId}/messages`, { content })
        return response.data
    },

    /**
     * 그룹(=familyId) 메시지 목록 조회 (messages 테이블 기반)
     * GET /api/groups/{groupId}/messages
     */
    async getGroupMessages(groupId) {
        const response = await api.get(`/groups/${groupId}/messages`)
        return response.data
    },

    /**
     * TTS 설정 저장
     * @param {Object} settings - TTS 설정
     * @returns {Promise}
     */
    async saveTTSSettings(settings) {
        const response = await api.put('/settings/tts', settings)
        return response.data
    },

    /**
     * TTS 설정 조회
     * @returns {Promise}
     */
    async getTTSSettings() {
        const response = await api.get('/settings/tts')
        return response.data
    },

    /**
     * 그룹 메시지 삭제
     * @param {number} groupId - 그룹 ID
     * @param {number} messageId - 메시지 ID
     * @returns {Promise}
     */
    async deleteGroupMessage(groupId, messageId) {
        const response = await api.delete(`/groups/${groupId}/messages/${messageId}`)
        return response.data
    }
}