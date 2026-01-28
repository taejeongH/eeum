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
        const exists = selectedFamily.value && families.value.find(f => f.id === selectedFamily.value.id);
        if (!selectedFamily.value || !exists) {
          selectedFamily.value = families.value[0];
        }
      } else {
        selectedFamily.value = null;
      }
    } catch (error) {
      console.error('Failed to fetch families:', error);
      families.value = [];
    }
  }

  function selectFamily(family) {
    selectedFamily.value = family;
  }

  function clearFamily() {
    selectedFamily.value = null;
    families.value = [];
  }

  return {
    families,
    selectedFamily,
    fetchFamilies,
    selectFamily,
    clearFamily
  };
});
