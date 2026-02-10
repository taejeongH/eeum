import api from './api';

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

  /**
   * 보낸 메시지 목록을 조회합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @param {Object} [params={}] - 페이지네이션 및 필터 정보
   * @returns {Promise<Object>} API 응답 데이터 (data: Array)
   */
  async getFamilyMessages(familyId, params = {}) {
    const response = await api.get(`/families/${familyId}/message`, { params });
    return response.data;
  },

  /**
   * 가족의 기본 정보를 조회합니다.
   * @param {string|number} familyId - 가족 그룹의 고유 ID
   * @returns {Promise<Object>} API 응답 데이터
   */
  async getFamilyInfo(familyId) {
    const response = await api.get(`/families/${familyId}`);
    return response.data;
  },

  /**
   * 그룹 메시지를 전송합니다. (v2/messages 테이블 기반)
   * @param {string|number} groupId - 그룹(가족) ID
   * @param {string} content - 메시지 내용
   * @returns {Promise<Object>} API 응답 데이터
   */
  async sendGroupMessage(groupId, content) {
    const response = await api.post(`/groups/${groupId}/messages`, { content });
    return response.data;
  },

  /**
   * 그룹 메시지 목록을 조회합니다. (v2/messages 테이블 기반)
   * @param {string|number} groupId - 그룹(가족) ID
   * @returns {Promise<Object>} API 응답 데이터 (data: Array)
   */
  async getGroupMessages(groupId) {
    const response = await api.get(`/groups/${groupId}/messages`);
    return response.data;
  },

  /**
   * 사용자의 TTS(Voice) 설정을 저장합니다.
   * @param {Object} settings - TTS 설정 정보
   * @returns {Promise<Object>} API 응답 데이터
   */
  async saveTTSSettings(settings) {
    const response = await api.put('/settings/tts', settings);
    return response.data;
  },

  /**
   * 사용자의 TTS 설정을 조회합니다.
   * @returns {Promise<Object>} API 응답 데이터
   */
  async getTTSSettings() {
    const response = await api.get('/settings/tts');
    return response.data;
  },

  /**
   * 그룹 메시지를 삭제합니다.
   * @param {string|number} groupId - 그룹(가족) ID
   * @param {string|number} messageId - 삭제할 메시지 ID
   * @returns {Promise<Object>} API 응답 데이터
   */
  async deleteGroupMessage(groupId, messageId) {
    const response = await api.delete(`/groups/${groupId}/messages/${messageId}`);
    return response.data;
  },
};
