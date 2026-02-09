import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useModalStore = defineStore('modal', () => {
    const isVisible = ref(false);
    const type = ref('alert'); 
    const title = ref('');
    const message = ref('');

    
    let resolvePromise = null;

    const openAlert = (msg, ttl = '알림') => {
        type.value = 'alert';
        title.value = ttl;
        message.value = msg;
        isVisible.value = true;

        return new Promise((resolve) => {
            resolvePromise = resolve;
        });
    };

    const openConfirm = (msg, ttl = '확인') => {
        type.value = 'confirm';
        title.value = ttl;
        message.value = msg;
        isVisible.value = true;

        return new Promise((resolve) => {
            resolvePromise = resolve;
        });
    };

    const close = (result = false) => {
        isVisible.value = false;
        if (resolvePromise) {
            resolvePromise(result);
            resolvePromise = null;
        }
    };

    return {
        isVisible,
        type,
        title,
        message,
        openAlert,
        openConfirm,
        close
    };
});
