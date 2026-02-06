import { ref } from 'vue';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { uploadFile } from '@/services/albumService';

export function usePhotoUpload(onUploadSuccess) {
    const familyStore = useFamilyStore();
    const modalStore = useModalStore();

    const fileInput = ref(null);
    const selectedFile = ref(null);
    const previewUrl = ref('');
    const showPreviewModal = ref(false);
    const isUploading = ref(false);

    const triggerFileInput = () => {
        fileInput.value.click();
    };

    const handleFileUpload = (event) => {
        const file = event.target.files[0];
        if (!file) return;

        selectedFile.value = file;
        previewUrl.value = URL.createObjectURL(file);
        showPreviewModal.value = true;

        // Clear input value so same file can be selected again if needed
        event.target.value = '';
    };

    const handleUploadConfirm = async (description) => {
        if (!selectedFile.value || !familyStore.selectedFamily) return;

        // showPreviewModal.value = false; // Optional: close immediately or wait for success
        isUploading.value = true;

        try {
            await uploadFile(familyStore.selectedFamily.id, selectedFile.value, description);

            showPreviewModal.value = false; // Close on success

            if (onUploadSuccess) {
                await onUploadSuccess();
            }

            await modalStore.openAlert('사진이 업로드되었습니다.');
        } catch (error) {
            console.error(error);
            await modalStore.openAlert('사진 업로드에 실패했습니다.');
        } finally {
            isUploading.value = false;
            handleUploadCleanup();
        }
    };

    const handleUploadCancel = () => {
        showPreviewModal.value = false;
        handleUploadCleanup();
    };

    const handleUploadCleanup = () => {
        if (previewUrl.value) {
            URL.revokeObjectURL(previewUrl.value);
            previewUrl.value = '';
        }
        selectedFile.value = null;
        if (fileInput.value) {
            fileInput.value.value = '';
        }
    };

    return {
        fileInput,
        selectedFile,
        previewUrl,
        showPreviewModal,
        isUploading,
        triggerFileInput,
        handleFileUpload,
        handleUploadConfirm,
        handleUploadCancel
    };
}
