import { ref } from 'vue';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { uploadFile, bulkUploadFiles } from '@/services/albumService';
import { compressImage } from '@/utils/imageUtils';
import { Logger } from '@/services/logger';

export function usePhotoUpload(onUploadSuccess) {
    const familyStore = useFamilyStore();
    const modalStore = useModalStore();

    const fileInput = ref(null);
    const selectedFiles = ref([]);
    const previewUrls = ref([]);
    const showPreviewModal = ref(false);
    const isUploading = ref(false);

    const triggerFileInput = () => {
        if (fileInput.value) fileInput.value.click();
    };

    const handleFileUpload = (event) => {
        const files = Array.from(event.target.files);
        if (files.length === 0) return;

        selectedFiles.value = files;
        previewUrls.value = files.map(file => URL.createObjectURL(file));
        showPreviewModal.value = true;

        // 동일한 파일을 다시 선택할 수 있도록 입력 값 초기화
        event.target.value = '';
    };

    const handleUploadConfirm = async (description) => {
        if (selectedFiles.value.length === 0 || !familyStore.selectedFamily) return;

        isUploading.value = true;

        try {
            const familyId = familyStore.selectedFamily.id;

            // 이미지 압축 적용 (비동기 병렬 처리)
            const compressedFiles = await Promise.all(
                selectedFiles.value.map(file => compressImage(file))
            );

            if (compressedFiles.length === 1) {
                // 단일 업로드
                await uploadFile(familyId, compressedFiles[0], description);
            } else {
                // 다중 업로드 (S3 병렬 업로드 + 메타데이터 일괄 저장)
                await bulkUploadFiles(familyId, compressedFiles, description);
            }

            showPreviewModal.value = false;

            if (onUploadSuccess) {
                await onUploadSuccess();
            }

            await modalStore.openAlert(`${selectedFiles.value.length}장의 사진이 업로드되었습니다.`);
        } catch (error) {
            Logger.error(error);
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
        previewUrls.value.forEach(url => URL.revokeObjectURL(url));
        previewUrls.value = [];
        selectedFiles.value = [];
        if (fileInput.value) {
            fileInput.value.value = '';
        }
    };

    return {
        fileInput,
        selectedFiles,
        previewUrls,
        showPreviewModal,
        isUploading,
        triggerFileInput,
        handleFileUpload,
        handleUploadConfirm,
        handleUploadCancel
    };
}
