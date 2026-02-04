import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { getUserProfile } from '../services/api';

export const useUserStore = defineStore('user', () => {
  const profile = ref(null);

  const isAuthenticated = computed(() => !!profile.value);

  async function fetchUser(force = false) {
    // [Cache Check] 강제 갱신이 아니고 이미 프로필이 있다면 skip
    if (!force && profile.value) return true;

    try {
      // 배경에서 조용히 업데이트 (silent header 사용)
      const response = await getUserProfile({ headers: { silent: true } });
      profile.value = response.data;
      return true;
    } catch (error) {
      profile.value = null;
      console.error("Failed to fetch user profile:", error);
      return false;
    }
  }

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
