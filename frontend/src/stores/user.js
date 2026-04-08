import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { getUserProfile } from '../services/api';
import { Logger } from '@/services/logger';

/**
 * 사용자 정보 및 인증 상태를 관리하는 Pinia 스토어입니다.
 * @summary 사용자 관리 스토어
 */
export const useUserStore = defineStore('user', () => {
  /** @type {import('vue').Ref<Object|null>} 사용자 프로필 정보 */
  const profile = ref(null);

  /** @type {import('vue').ComputedRef<boolean>} 인증 여부 반환 */
  const isAuthenticated = computed(() => !!profile.value);

  async function fetchUser(force = false) {
    
    if (!force && profile.value) return true;

    try {
      
      const response = await getUserProfile({ headers: { silent: true } });
      profile.value = response.data;
      return true;
    } catch (error) {
      profile.value = null;
      Logger.error("사용자 프로필 조회 실패:", error);
      return false;
    }
  }

  /**
   * 프로필 요청이 필요한 상태인지 확인합니다. (Internal)
   * @param {boolean} force
   * @returns {boolean}
   */
  function shouldFetch(force) {
    return force || !profile.value;
  }

  /**
   * 프로필 조회 실패 시 처리를 수행합니다. (Internal)
   * @param {Error} error
   */
  function handleFetchError(error) {
    profile.value = null;
    Logger.error('사용자 프로필 조회 실패:', error);
  }

  /**
   * 사용자 정보를 초기화(로그아웃 등)합니다.
   */
  function clearUser() {
    profile.value = null;
  }

  return {
    profile,
    isAuthenticated,
    fetchUser,
    clearUser,
  };
});
