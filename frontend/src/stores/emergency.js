import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useEmergencyStore = defineStore('emergency', () => {
    const isVisible = ref(false);
    const emergencyData = ref(null);

    const open = (data = null) => {
        emergencyData.value = data;
        isVisible.value = true;
    };

    const close = () => {
        isVisible.value = false;
        emergencyData.value = null;
    };

    return {
        isVisible,
        emergencyData,
        open,
        close
    };
});
