import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';

export const useFamilyStore = defineStore('family', () => {
  const families = ref([]);
  const selectedFamily = ref(null);
  const isLoading = ref(false);

  async function fetchFamilies(force = false) {
    // [Cache Check] 강제 갱신이 아니고 이미 데이터가 있다면 skip
    if (!force && families.value.length > 0) {
      // [IMPORTANT] Even if not fetching, ensure a selection exists
      if (!selectedFamily.value && families.value.length > 0) {
        const savedId = localStorage.getItem('selectedFamilyId');
        if (savedId) {
          const savedFamily = families.value.find(f => String(f.id) === String(savedId));
          if (savedFamily) selectedFamily.value = savedFamily;
        }
        if (!selectedFamily.value) selectedFamily.value = families.value[0];
      }
      return;
    }

    isLoading.value = true;
    try {
      // 배경에서 조용히 업데이트 (silent header 사용)
      const response = await api.get('/families', { headers: { silent: true } });
      families.value = response.data;

      // If no family is selected, or the selected family is no longer in the list, default to the first one
      if (families.value.length > 0) {
        // 1. Try to restore from localStorage if no current selection
        if (!selectedFamily.value) {
          const savedId = localStorage.getItem('selectedFamilyId');
          if (savedId) {
            const savedFamily = families.value.find(f => String(f.id) === String(savedId));
            if (savedFamily) {
              selectedFamily.value = savedFamily;
            }
          }
        }

        const exists = selectedFamily.value && families.value.find(f => f.id === selectedFamily.value.id);
        if (!selectedFamily.value || !exists) {
          selectedFamily.value = families.value[0];
        }

        // Sync confirmed selection to localStorage
        if (selectedFamily.value) {
          localStorage.setItem('selectedFamilyId', selectedFamily.value.id);
        }
      } else {
        selectedFamily.value = null;
        localStorage.removeItem('selectedFamilyId');
      }
    } catch (error) {
      console.error('Failed to fetch families:', error);
      families.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  function selectFamily(family) {
    selectedFamily.value = family;
    if (family && family.id) {
      localStorage.setItem('selectedFamilyId', family.id);
    } else {
      localStorage.removeItem('selectedFamilyId');
    }
  }

  function selectFamilyById(familyId) {
    if (!familyId) return;
    const family = families.value.find(f => String(f.id) === String(familyId));
    if (family) {
      selectFamily(family);

    }
  }

  function clearFamily() {
    selectedFamily.value = null;
    families.value = [];
    localStorage.removeItem('selectedFamilyId');
  }

  async function createFamily(data) {
    isLoading.value = true;
    try {
      const response = await api.post('/families', data);
      await fetchFamilies(true); // 리스트 강제 갱신

      // 방금 만든 가족으로 자동 선택
      if (response.data && response.data.id) {
        selectFamilyById(response.data.id);
      } else if (families.value.length > 0) {
        selectFamily(families.value[families.value.length - 1]);
      }

      return response.data;
    } catch (error) {
      console.error('Failed to create family:', error);
      throw error;
    } finally {
      isLoading.value = false;
    }
  }

  async function joinFamily(inviteCode) {
    isLoading.value = true;
    try {
      const response = await api.post('/families/join', inviteCode, {
        headers: { 'Content-Type': 'text/plain' },
        transformRequest: [(data) => data]
      });
      await fetchFamilies(true); // 리스트 강제 갱신

      // 방금 가입한 가족으로 자동 선택
      if (response.data && response.data.id) {
        selectFamilyById(response.data.id);
      }

      return response.data;
    } catch (error) {
      console.error('Failed to join family:', error);
      throw error;
    } finally {
      isLoading.value = false;
    }
  }

  const membersCache = ref({});

  async function fetchMembers(familyId, force = false) {
    if (!familyId) return [];
    // [Cache Check] 강제 갱신이 아니고 이미 해당 가족의 멤버 정보가 있다면 skip
    if (!force && membersCache.value[familyId]) {
      return membersCache.value[familyId];
    }

    try {
      const response = await api.get(`/families/${familyId}/members`, { headers: { silent: true } });
      membersCache.value[familyId] = response.data;
      return response.data;
    } catch (error) {
      console.error(`Failed to fetch members for family ${familyId}:`, error);
      return membersCache.value[familyId] || []; // 에러 시 기존 캐시라도 반환
    }
  }

  return {
    families,
    selectedFamily,
    isLoading,
    membersCache,
    fetchFamilies,
    fetchMembers,
    selectFamily,
    selectFamilyById,
    clearFamily,
    createFamily,
    joinFamily
  };
});
