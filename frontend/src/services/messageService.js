
import api from './api'

export const messageService = {

    
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