<template>
  <div class="bg-black min-h-screen text-white flex flex-col relative h-screen overflow-hidden">
    
    <!-- Top Bar -->
    <div class="absolute top-0 left-0 right-0 z-30 p-4 flex items-center justify-between bg-gradient-to-b from-black/60 to-transparent">
        <button @click="$router.back()" class="p-2 rounded-full hover:bg-white/10 transition-colors">
            <span class="material-symbols-outlined text-white">arrow_back</span>
        </button>
        
        <div class="flex items-center gap-2">
            <button v-if="!isEditing" @click="startEdit" class="hidden p-2 rounded-full hover:bg-white/10 transition-colors text-white">
                 <!-- Hidden for now, simpler UI first? No, requirements ask for edit -->
                <span class="material-symbols-outlined">edit</span>
            </button>
            <div v-if="canManage" class="relative" ref="moreMenu">
                <button @click="toggleMenu" class="p-2 rounded-full hover:bg-white/10 transition-colors">
                    <span class="material-symbols-outlined text-white">more_vert</span>
                </button>
                
                <!-- Dropdown Menu -->
                <div v-if="showMenu" class="absolute right-0 mt-2 w-32 bg-white rounded-xl shadow-xl overflow-hidden z-40 py-1">
                    <button @click="startEdit" class="w-full text-left px-4 py-3 text-[#1c140d] hover:bg-gray-50 text-sm font-medium flex items-center gap-2">
                        <span class="material-symbols-outlined text-lg">edit</span>
                        수정
                    </button>
                    <button @click="handleDelete" class="w-full text-left px-4 py-3 text-red-600 hover:bg-red-50 text-sm font-medium flex items-center gap-2">
                        <span class="material-symbols-outlined text-lg">delete</span>
                        삭제
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- Main Image Area -->
    <div class="flex-1 relative w-full h-full bg-black">
        <swiper
          v-if="isReady && allPhotos.length > 0"
          :initial-slide="currentIndex"
          @slide-change="onSlideChange"
          class="h-full w-full"
        >
            <swiper-slide v-for="p in allPhotos" :key="p.photoId || p.id">
                <div class="flex items-center justify-center w-full h-full">
                    <img 
                        :src="p.displayUrl" 
                        class="max-w-full max-h-full object-contain"
                        alt="Detail Photo"
                    />
                </div>
            </swiper-slide>
        </swiper>
        <div v-else class="flex items-center justify-center h-full text-gray-500">
            사진을 불러오는 중...
        </div>
    </div>

    <!-- Bottom Info Overlay (View Mode) -->
    <div v-if="photo && !isEditing" class="absolute bottom-0 left-0 right-0 z-30 p-6 bg-gradient-to-t from-black/90 via-black/50 to-transparent pt-12 pb-8">
        <div class="flex flex-col gap-1">
            <div class="flex items-center justify-between">
                <p class="text-white text-lg font-bold">{{ photo.description || '설명 없음' }}</p>
            </div>
            <div class="flex items-center gap-2 text-gray-300 text-sm">
                 <span class="material-symbols-outlined text-base">calendar_today</span>
                 <span>{{ formatDate(photo.takenAt || photo.createdAt) }}</span>
                 <span class="mx-1">•</span>
                 <span>{{ photo.uploaderName || '익명' }}</span>
            </div>
        </div>
    </div>

    <!-- Edit Mode Overlay -->
    <div v-if="isEditing" class="absolute inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
        <div class="bg-white rounded-3xl w-full max-w-sm overflow-hidden shadow-2xl">
            <div class="p-4 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                <h3 class="font-bold text-[#1c140d]">사진 정보 수정</h3>
                <button @click="cancelEdit" class="text-gray-400 hover:text-gray-600">
                    <span class="material-symbols-outlined">close</span>
                </button>
            </div>
            
            <div class="p-6 space-y-4">
                <div class="space-y-1">
                    <label class="text-xs font-bold text-gray-500 ml-1">설명</label>
                    <textarea 
                        v-model="editForm.description"
                        class="w-full px-4 py-3 bg-gray-50 border border-transparent rounded-xl focus:bg-white focus:border-[#9c7349] focus:ring-2 focus:ring-[#9c7349]/20 transition-all text-[#1c140d] text-sm resize-none"
                        rows="3"
                        placeholder="사진에 대한 설명을 입력하세요"
                    ></textarea>
                </div>
                
                <div class="space-y-1">
                    <label class="text-xs font-bold text-gray-500 ml-1">촬영 날짜</label>
                    <input 
                        v-model="editForm.takenAt"
                        type="date"
                        class="w-full px-4 py-3 bg-gray-50 border border-transparent rounded-xl focus:bg-white focus:border-[#9c7349] focus:ring-2 focus:ring-[#9c7349]/20 transition-all text-[#1c140d] text-sm"
                    />
                </div>
            </div>

            <div class="p-4 border-t border-gray-100 bg-gray-50 flex gap-3">
                 <button 
                  @click="cancelEdit" 
                  class="flex-1 px-4 py-3 rounded-xl text-sm font-bold text-gray-600 bg-white border border-gray-200 hover:bg-gray-50 transition-colors"
                >
                  취소
                </button>
                <button 
                  @click="saveEdit" 
                  class="flex-1 px-4 py-3 rounded-xl text-sm font-bold text-white bg-primary hover:bg-[#8a6540] shadow-lg shadow-primary/20 transition-all active:scale-[0.98]"
                >
                  저장
                </button>
            </div>
        </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { getPhotos, deletePhoto, updatePhoto } from '@/services/albumService';
import { useUserStore } from '@/stores/user';

// Swiper Imports
import { Swiper, SwiperSlide } from 'swiper/vue';
import 'swiper/css';

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();
const modalStore = useModalStore();
const userStore = useUserStore();

const photo = ref(null);
const allPhotos = ref([]);
const currentIndex = ref(-1);
const isReady = ref(false);
const showMenu = ref(false);
const isEditing = ref(false);
const moreMenu = ref(null);

const editForm = ref({
    description: '',
    takenAt: ''
});

const canManage = computed(() => {
    if (!photo.value || !userStore.profile) return false;
    
    const isUploader = Number(photo.value.uploaderUserId) === Number(userStore.profile.id);
    const isRepresentative = familyStore.families.find(f => String(f.id) === String(route.params.familyId))?.owner || false;
    
    return isUploader || isRepresentative;
});

const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/';

const formatDate = (dateString) => {
    if (!dateString) return '';
    return dateString.split('T')[0];
};

const fetchPhotoDetail = async () => {
    // URL의 familyId와 store의 selectedFamily 동기화
    if (route.params.familyId && (!familyStore.selectedFamily || String(familyStore.selectedFamily.id) !== String(route.params.familyId))) {
        familyStore.selectFamilyById(route.params.familyId);
    }

    if (!familyStore.selectedFamily) return;

    try {
        const response = await getPhotos(familyStore.selectedFamily.id);
        let rawPhotos = [];
        
        if (Array.isArray(response.data)) {
            rawPhotos = response.data;
        } else if (response.data && Array.isArray(response.data.data)) {
            rawPhotos = response.data.data;
        } else if (response.data && Array.isArray(response.data.result)) {
            rawPhotos = response.data.result;
        } else if (response.data && Array.isArray(response.data.content)) {
            rawPhotos = response.data.content;
        }

        // Process URLs for all photos to allow navigation
        allPhotos.value = rawPhotos.map(p => {
            let url = p.storageUrl || p.imageUrl;
            if (url && !url.startsWith('http')) {
                url = S3_BASE_URL + url;
            }
            return { ...p, displayUrl: url };
        });

        // Sort by takenAt descending (to match Gallery order usually)
        allPhotos.value.sort((a, b) => {
            const dateA = new Date(a.takenAt || a.createdAt || 0);
            const dateB = new Date(b.takenAt || b.createdAt || 0);
            return dateB - dateA;
        });

        const targetId = parseInt(route.params.photoId);
        currentIndex.value = allPhotos.value.findIndex(p => (p.photoId || p.id) === targetId);

        if (currentIndex.value !== -1) {
            photo.value = allPhotos.value[currentIndex.value];
            isReady.value = true;
        } else {
             await modalStore.openAlert('사진을 찾을 수 없습니다.');
             router.back();
        }
    } catch (error) {
        console.error("Error fetching photo:", error);
         await modalStore.openAlert('사진 로드 중 오류가 발생했습니다.');
         router.back();
    }
};

const onSlideChange = (swiper) => {
    currentIndex.value = swiper.activeIndex;
    photo.value = allPhotos.value[currentIndex.value];
    const newPhotoId = photo.value.photoId || photo.value.id;
    // Update URL without adding to history to avoid back-button hell
    router.replace({ name: 'PhotoDetail', params: { photoId: newPhotoId } });
};

const toggleMenu = () => {
    showMenu.value = !showMenu.value;
};

// Close menu when clicking outside
const closeMenu = (e) => {
    if (showMenu.value && moreMenu.value && !moreMenu.value.contains(e.target)) {
        showMenu.value = false;
    }
};
window.addEventListener('click', closeMenu);
onUnmounted(() => window.removeEventListener('click', closeMenu));


const handleDelete = async () => {
    showMenu.value = false;
    const confirmed = await modalStore.openConfirm('정말 이 사진을 삭제하시겠습니까?');
    if (confirmed) {
        try {
            await deletePhoto(photo.value.photoId || photo.value.id);
            await modalStore.openAlert('사진이 삭제되었습니다.');
            router.back();
        } catch (error) {
             console.error(error);
             await modalStore.openAlert('삭제 실패');
        }
    }
};

const startEdit = () => {
    showMenu.value = false;
    editForm.value = {
        description: photo.value.description || '',
        takenAt: formatDate(photo.value.takenAt || photo.value.taken_at || photo.value.createdAt)
    };
    isEditing.value = true;
};

const cancelEdit = () => {
    isEditing.value = false;
};

const saveEdit = async () => {
    if (!photo.value) return;
    
    try {
        const payload = {
            storageUrl: photo.value.storageUrl, // Keep existing
            description: editForm.value.description,
            takenAt: editForm.value.takenAt
        };
        
        const photoId = photo.value.photoId || photo.value.id;
        await updatePhoto(photoId, payload);
        
        // Update local state
        photo.value.description = editForm.value.description;
        photo.value.takenAt = editForm.value.takenAt;
        
        await modalStore.openAlert('수정되었습니다.');
        isEditing.value = false;
    } catch (error) {
        console.error(error);
        await modalStore.openAlert('수정 실패');
    }
};

onMounted(() => {
    fetchPhotoDetail();
});
</script>

<style scoped>
.material-symbols-outlined {
    font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
