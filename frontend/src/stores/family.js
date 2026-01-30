import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import api from '@/services/api';

export const useFamilyStore = defineStore('family', () => {
  const families = ref([]);
  const selectedFamily = ref(null);

  async function fetchFamilies() {
    try {
      const response = await api.get('/families');
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

  function clearFamily() {
    selectedFamily.value = null;
    families.value = [];
    localStorage.removeItem('selectedFamilyId');
  }

  return {
    families,
    selectedFamily,
    fetchFamilies,
    selectFamily,
    clearFamily
  };
});
