import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';
import { Logger } from '@/services/logger';

/**
 * 가족(그룹) 정보 및 구성을 관리하는 Pinia 스토어입니다.
 * @summary 가족 관리 스토어
 */
export const useFamilyStore = defineStore('family', () => {
  /** @type {import('vue').Ref<Array>} 가족 목록 */
  const families = ref([]);

  /** @type {import('vue').Ref<Object|null>} 현재 선택된 가족 */
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

  /**
   * 로컬에 이미 데이터가 있을 때 선택된 항목이 있는지 보장합니다. (Internal)
   */
  function ensureSelectionExists() {
    if (!selectedFamily.value && families.value.length > 0) {
      restoreSelectionFromStorage();
    }
  }

  /**
   * 데이터 로드 후 현재 선택된 가족 상태를 업데이트합니다. (Internal)
   */
  function updateSelectedFamily() {
    if (families.value.length === 0) {
      clearSelection();
      return;
    }

    if (!selectedFamily.value) {
      restoreSelectionFromStorage();
    }

    const matchedFamily = families.value.find((f) => f.id === selectedFamily.value?.id);
    if (!matchedFamily) {
      setDefaultSelection();
    } else {
      selectedFamily.value = matchedFamily;
    }

    persistSelection(selectedFamily.value);
  }

  /**
   * 로컬 스토리지로부터 선택된 가족 정보를 복구합니다. (Internal)
   */
  function restoreSelectionFromStorage() {
    const savedId = localStorage.getItem('selectedFamilyId');
    if (savedId) {
      const savedFamily = families.value.find((f) => String(f.id) === String(savedId));
      if (savedFamily) {
        selectedFamily.value = savedFamily;
      }
    }
  }

  /**
   * 첫 번째 가족을 기본 선택으로 설정합니다. (Internal)
   */
  function setDefaultSelection() {
    selectedFamily.value = families.value[0];
  }

  /**
   * 선택된 가족 정보를 유지합니다. (Internal)
   * @param {Object} family
   */
  function persistSelection(family) {
    if (family?.id) {
      const idStr = String(family.id);
      localStorage.setItem('selectedFamilyId', idStr);
      syncWithAndroidBridge(idStr);
    }
  }

  /**
   * 선택된 가족 정보를 지웁니다. (Internal)
   */
  function clearSelection() {
    selectedFamily.value = null;
    localStorage.removeItem('selectedFamilyId');
    syncWithAndroidBridge('');
  }

  /**
   * 안드로이드 네이티브와 선택된 가족 ID를 동기화합니다. (Internal)
   * @param {string} id
   */
  function syncWithAndroidBridge(id) {
    if (window.AndroidBridge?.saveSelectedFamilyId) {
      window.AndroidBridge.saveSelectedFamilyId(id);
    }
  }

  /**
   * 특정 가족을 선택합니다.
   * @param {Object} family
   */
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

  /**
   * 가족 목록 및 선택 정보를 초기화합니다.
   */
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
