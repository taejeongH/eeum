import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';
import { Logger } from '@/services/logger';

export const useFamilyStore = defineStore('family', () => {
  const families = ref([]);
  const selectedFamily = ref(null);
  const isLoading = ref(false);

  async function fetchFamilies(force = false) {
    
    if (!force && families.value.length > 0) {
      
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
      
      const response = await api.get('/families', { headers: { silent: true } });
      families.value = response.data;

      
      if (families.value.length > 0) {
        
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
        } else {
          
          selectedFamily.value = exists;
        }

        
        if (selectedFamily.value) {
          localStorage.setItem('selectedFamilyId', selectedFamily.value.id);
        }
      } else {
        selectedFamily.value = null;
        localStorage.removeItem('selectedFamilyId');
      }
    } catch (error) {
      Logger.error('가족 목록 조회 실패:', error);
      families.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  function selectFamily(family) {
    selectedFamily.value = family;
    if (family && family.id) {
      localStorage.setItem('selectedFamilyId', family.id);
      
      if (window.AndroidBridge && window.AndroidBridge.saveSelectedFamilyId) {
        window.AndroidBridge.saveSelectedFamilyId(String(family.id));
      }
    } else {
      localStorage.removeItem('selectedFamilyId');
      if (window.AndroidBridge && window.AndroidBridge.saveSelectedFamilyId) {
        window.AndroidBridge.saveSelectedFamilyId("");
      }
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
    membersCache.value = {}; 
    localStorage.removeItem('selectedFamilyId');
  }

  async function createFamily(data) {
    isLoading.value = true;
    try {
      const response = await api.post('/families', data);
      await fetchFamilies(true); 

      
      if (response.data && response.data.id) {
        selectFamilyById(response.data.id);
      } else if (families.value.length > 0) {
        selectFamily(families.value[families.value.length - 1]);
      }

      return response.data;
    } catch (error) {
      Logger.error('가족 생성 실패:', error);
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
      await fetchFamilies(true); 

      
      if (response.data && response.data.id) {
        selectFamilyById(response.data.id);
      }

      return response.data;
    } catch (error) {
      Logger.error('가족 참여 실패:', error);
      throw error;
    } finally {
      isLoading.value = false;
    }
  }

  const membersCache = ref({});

  async function fetchMembers(familyId, force = false) {
    if (!familyId) return [];
    
    if (!force && membersCache.value[familyId]) {
      return membersCache.value[familyId];
    }

    try {
      const response = await api.get(`/families/${familyId}/members`, { headers: { silent: true } });
      membersCache.value[familyId] = response.data;
      return response.data;
    } catch (error) {
      Logger.error(`가족 구성원 조회 실패 (ID: ${familyId}):`, error);
      return membersCache.value[familyId] || []; 
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
