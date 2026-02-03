import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useUiStore = defineStore('ui', () => {
    const loadingCount = ref(0);

    const isLoading = computed(() => loadingCount.value > 0);

    const startLoading = () => {
        loadingCount.value++;
    };

    const finishLoading = () => {
        if (loadingCount.value > 0) {
            loadingCount.value--;
        }
    };

    const resetLoading = () => {
        loadingCount.value = 0;
    };

    return {
        isLoading,
        loadingCount,
        startLoading,
        finishLoading,
        resetLoading
    };
});
