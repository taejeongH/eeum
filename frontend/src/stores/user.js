import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { getUserProfile } from '../services/api';

export const useUserStore = defineStore('user', () => {
  const profile = ref(null);

  const isAuthenticated = computed(() => !!profile.value);

  async function fetchUser() {
    try {
      const response = await getUserProfile();
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
