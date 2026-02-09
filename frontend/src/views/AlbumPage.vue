<template>
  <div class="bg-background-light min-h-screen text-[#1c140d] flex flex-col relative overflow-hidden">
    <!-- Modal Like Header (Pull bar) -->
    <div class="flex flex-col items-center bg-background-light pt-2">
      <div class="h-1.5 w-10 rounded-full bg-[#e8dbce]"></div>
    </div>

    <!-- Top AppBar -->
    <div class="flex items-center bg-background-light p-4 pb-2 justify-between sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="p-2 -ml-2 rounded-full hover:bg-primary/10 transition-colors">
          <svg class="w-6 h-6 text-[#1c140d]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div class="flex flex-col">
          <h2 class="text-[#1c140d] text-xl font-bold leading-tight tracking-[-0.015em]">
            {{ albumTitle }}
          </h2>
          <p class="text-xs text-[#9c7349] font-medium">{{ photos.length }}개 항목 • 가족 공유</p>
        </div>
      </div>
      <div class="flex items-center gap-4">
        <EeumDatePicker v-model="filterDateLocal">
          <template #trigger>
            <button class="flex items-center gap-1 text-primary text-base font-bold leading-normal tracking-[0.015em]">
              <span class="material-symbols-outlined text-lg">calendar_today</span>
            </button>
          </template>
        </EeumDatePicker>
        <button 
            v-if="canManage"
            @click="toggleSelectionMode"
            class="text-primary text-base font-bold leading-normal tracking-[0.015em] shrink-0"
        >
            {{ isSelectionMode ? '취소' : '편집' }}
        </button>
      </div>
    </div>

    <!-- Filter Chips Section -->


    <!-- 4-Column ImageGrid -->
    <div class="flex-1 overflow-y-auto px-4 pb-32">
      <div v-if="photos.length > 0" class="grid grid-cols-4 gap-1.5">
        <div 
            v-for="photo in photos" 
            :key="photo.photoId || photo.id" 
            class="relative group aspect-square"
            @click="handlePhotoClick(photo)"
        >
          <div 
            class="w-full h-full bg-[#f4ede7] rounded-sm cursor-pointer border-2 transition-all duration-200 overflow-hidden" 
            :class="selectedPhotos.includes(photo.photoId || photo.id) ? 'border-primary opacity-80 scale-95' : 'border-transparent hover:border-primary'"
          >
            <img 
              :src="photo.displayUrl" 
              :alt="photo.description || '사진'"
              loading="lazy"
              class="w-full h-full object-cover rounded-sm image-fade-in"
              @error="handleImageError"
            />
          </div>
          
          <!-- 선택 확인 표시 -->
          <div v-if="isSelectionMode" class="absolute top-1 right-1 w-5 h-5 rounded-full border border-white flex items-center justify-center"
               :class="selectedPhotos.includes(photo.photoId || photo.id) ? 'bg-primary' : 'bg-black/30'">
              <span v-if="selectedPhotos.includes(photo.photoId || photo.id)" class="material-symbols-outlined text-white text-sm">check</span>
          </div>
        </div>
      </div>
      <div v-else class="flex flex-col items-center justify-center h-64 text-gray-500">
          <span class="material-symbols-outlined text-4xl mb-2">image_not_supported</span>
          <p>사진이 없습니다.</p>
      </div>
    </div>

    <!-- 하단 부유 액션 바 (편집 모드 시) -->
    <div v-if="isSelectionMode" class="fixed bottom-0 left-0 right-0 p-4 bg-white/90 backdrop-blur-md border-t border-[#e8dbce] z-50">
      <div class="flex items-center justify-between max-w-lg mx-auto">
        <div class="flex flex-col">
          <p class="text-sm font-bold text-[#1c140d]">{{ selectedPhotos.length }}개 선택됨</p>
          <p class="text-[10px] text-[#9c7349] uppercase tracking-wider font-semibold">항목 선택</p>
        </div>
        <div class="flex gap-2">
          <button class="flex items-center justify-center p-3 rounded-full bg-[#f4ede7] text-primary">
            <span class="material-symbols-outlined">share</span>
          </button>
          <button 
            @click="deleteSelectedPhotos"
            class="flex items-center justify-center p-3 rounded-full bg-red-100 text-red-600 active:scale-95 transition-transform"
            :disabled="selectedPhotos.length === 0"
          >
            <span class="material-symbols-outlined">delete</span>
          </button>
        </div>
      </div>
    </div>
    
    <!-- 업로드 버튼 (편집 모드가 아닐 때만 표시) -->
    <button v-if="!isSelectionMode" @click="triggerFileInput" class="fixed bottom-6 right-6 w-14 h-14 bg-primary text-white rounded-full shadow-lg shadow-primary/30 flex items-center justify-center active:scale-95 transition-transform z-30">
      <span v-if="!isUploading" class="material-symbols-outlined text-3xl">add_photo_alternate</span>
      <span v-else class="material-symbols-outlined text-3xl animate-spin">progress_activity</span>
    </button>
    <input type="file" ref="fileInput" class="hidden" accept="image/*" multiple @change="handleFileUpload" />

    <ImagePreviewModal 
      :is-open="showPreviewModal"
      :preview-urls="previewUrls"
      @close="handleUploadCancel"
      @confirm="handleUploadConfirm"
    />

  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { getPhotos, deletePhoto } from '@/services/albumService';
import EeumDatePicker from '@/components/common/EeumDatePicker.vue';
import ImagePreviewModal from '@/components/gallery/ImagePreviewModal.vue';
import { usePhotoUpload } from '@/composables/usePhotoUpload';
import { useUserStore } from '@/stores/user';
import { useAlbumStore } from '@/stores/album';
import { Logger } from '@/services/logger';

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();
const modalStore = useModalStore();
const userStore = useUserStore();
const albumStore = useAlbumStore();
const allPhotos = ref([]); // 모든 불러온 사진 저장
const photos = ref([]); // 필터링된 사진 저장
const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/';

// 공통 업로드 로직 사용
const {
  fileInput,
  previewUrls,
  showPreviewModal,
  isUploading,
  triggerFileInput,
  handleFileUpload,
  handleUploadConfirm,
  handleUploadCancel
} = usePhotoUpload(async () => {
    // 성공 시 콜백
    await fetchPhotos(true);
});

const filterDateLocal = computed({
    get: () => route.query.date || '',
    set: (val) => {
        router.replace({ 
            query: { 
                ...route.query, 
                date: val 
            } 
        });
    }
});

// 쿼리에 따른 제목 업데이트
const albumTitle = computed(() => {
    const uploader = route.query.uploader;
    const groupName = familyStore.selectedFamily?.name || '우리 가족';
    return uploader ? `${uploader}의 앨범` : `${groupName} 앨범`;
});

const canManage = computed(() => {
    // 1. 대표자 확인
    const isRep = familyStore.families.find(f => String(f.id) === String(route.params.familyId))?.owner || false;
    
    // 2. 본인 앨범 확인
    const currentUploaderFilter = route.query.uploader;
    const myName = userStore.profile?.name;
    const isMyAlbum = currentUploaderFilter && myName && (currentUploaderFilter === myName);
    
    return isRep || isMyAlbum;
});

const fetchPhotos = async (forceRefresh = false) => {
    // URL의 familyId와 store의 selectedFamily 동기화
    if (route.params.familyId && (!familyStore.selectedFamily || String(familyStore.selectedFamily.id) !== String(route.params.familyId))) {
        familyStore.selectFamilyById(route.params.familyId);
    }

    if (!familyStore.selectedFamily) return;
    
    const familyId = familyStore.selectedFamily.id;
    
    // 즉시 표시를 위해 캐시된 데이터 먼저 시도
    if (!forceRefresh) {
        const cached = albumStore.getCachedPhotos(familyId);
        if (cached) {
            allPhotos.value = cached;
            filterPhotos();
            
            // 캐시가 충분히 최신인 경우(30초 미만) 백그라운드 호출 건너뜀
            if (albumStore.isFresh(familyId, 30000)) {
                return;
            }
        }
    }
    
    try {
        const response = await getPhotos(familyId);
        let data = [];
        if (response.data && Array.isArray(response.data.data)) {
            data = response.data.data;
        } else if (Array.isArray(response.data)) {
            data = response.data;
        }

        // URL 처리
        const processedPhotos = data.map(photo => {
            let url = photo.storageUrl || photo.imageUrl;
            if (url && !url.startsWith('http')) {
                url = S3_BASE_URL + url;
            }
            return {
                ...photo,
                displayUrl: url
            };
        });
        
        // 최신순 정렬
        processedPhotos.sort((a, b) => {
            const dateA = new Date(a.createdAt || a.created_at || a.takenAt || 0);
            const dateB = new Date(b.createdAt || b.created_at || b.takenAt || 0);
            return dateB - dateA;
        });

        // 처리된 사진 캐시 저장
        albumStore.setCachedPhotos(familyId, processedPhotos);
        allPhotos.value = processedPhotos;
        
        filterPhotos(); // 초기 필터 적용
    } catch (error) {
        Logger.error("앨범 사진 조회 실패:", error);
    }
};

const handleImageError = (event) => {
    // 이미지 오류 시 처리
    event.target.style.display = 'none';
};

const filterPhotos = () => {
    const uploader = route.query.uploader;
    const date = route.query.date;

    let filtered = allPhotos.value;

    if (uploader) {
        filtered = filtered.filter(p => p.uploaderName === uploader);
    }

    if (date) {
        filtered = filtered.filter(p => {
             const pDate = new Date(p.takenAt || p.taken_at || p.createdAt || p.created_at ||0).toISOString().split('T')[0];
             return pDate === date;
        });
    }

    photos.value = filtered;
};

// 선택 상태
const isSelectionMode = ref(false);
const selectedPhotos = ref([]);

const toggleSelectionMode = () => {
    isSelectionMode.value = !isSelectionMode.value;
    selectedPhotos.value = []; // 모드 전환 시 선택 초기화
};

const togglePhotoSelection = (photo) => {
    if (!isSelectionMode.value) return; 
    
    const id = photo.photoId || photo.id;
    if (selectedPhotos.value.includes(id)) {
        selectedPhotos.value = selectedPhotos.value.filter(pId => pId !== id);
    } else {
        selectedPhotos.value.push(id);
    }
};

const handlePhotoClick = (photo) => {
    if (isSelectionMode.value) {
        togglePhotoSelection(photo);
    } else {
        // 현재 필터들을 컨텍스트로 함께 전달하며 상세 페이지 이동
        router.push({
            name: 'PhotoDetail',
            params: { photoId: photo.photoId || photo.id },
            query: { 
                ...route.query // uploader, date 등 전달
            }
        });
    }
};

const deleteSelectedPhotos = async () => {
    if (selectedPhotos.value.length === 0) return;
    
    const isConfirmed = await modalStore.openConfirm(`${selectedPhotos.value.length}장의 사진을 삭제하시겠습니까?`);
    if (!isConfirmed) return;

    try {
        // 모든 선택된 사진 병렬 삭제
        await Promise.all(selectedPhotos.value.map(id => deletePhoto(id)));
        
        await modalStore.openAlert("사진이 삭제되었습니다.");
        
        // 목록 새로고침
        await fetchPhotos(true);
        
        // 편집 모드 종료
        toggleSelectionMode();
    } catch (error) {
        Logger.error("삭제 실패:", error);
        await modalStore.openAlert("사진 삭제에 실패했습니다.");
    }
};

onMounted(() => {
    if (familyStore.selectedFamily) {
        fetchPhotos();
    }
});

watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily) {
        fetchPhotos();
    }
});

// 쿼리 변경 시 필터 다시 적용
watch(() => route.query, () => {
    filterPhotos();
}, { deep: true });
</script>

<style scoped>
.hide-scrollbar::-webkit-scrollbar {
  display: none;
}
.hide-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.material-symbols-outlined {
    font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}

/* 이미지 페이드인 애니메이션 */
.image-fade-in {
    animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}
</style>
