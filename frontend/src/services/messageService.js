
import api from './api'

/**
 * 메시지 및 알림 관련 API 호출을 담당하는 서비스 객체입니다.
 */
export const messageService = {
  /**
   * 가족에게 메시지를 전송합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {Object} messageData - 메시지 데이터 (content 등)
   * @returns {Promise<Object>} API 응답 데이터
   */
  async sendFamilyMessage(familyId, messageData) {
    const response = await api.post(`/families/${familyId}/message`, messageData);
    return response.data;
  },

    
    async sendFamilyMessage(familyId, messageData) {
        const response = await api.post(`/families/${familyId}/message`, messageData)
        return response.data
    },

    
    async getFamilyMessages(familyId, params = {}) {
        const response = await api.get(`/families/${familyId}/message`, { params })
        return response.data
    },

    
    async getFamilyInfo(familyId) {
        const response = await api.get(`/families/${familyId}`)
        return response.data
    },

    
    async sendGroupMessage(groupId, content) {
        const response = await api.post(`/groups/${groupId}/messages`, { content })
        return response.data
    },

    
    async getGroupMessages(groupId) {
        const response = await api.get(`/groups/${groupId}/messages`)
        return response.data
    },

    
    async saveTTSSettings(settings) {
        const response = await api.put('/settings/tts', settings)
        return response.data
    },

    
    async getTTSSettings() {
        const response = await api.get('/settings/tts')
        return response.data
    },

    
    async deleteGroupMessage(groupId, messageId) {
        const response = await api.delete(`/groups/${groupId}/messages/${messageId}`)
        return response.data
    }
}