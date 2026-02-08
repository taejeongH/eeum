<template>
  <div class="bg-background-light min-h-screen text-[#1c140d] pb-32 relative">
    
    <!-- 정제된 헤더 -->
    <MainHeader @modal-state-change="handleModalStateChange" :show-profiles="false">
      <template #actions>
        <div class="flex items-center gap-1 -mr-2">
          <EeumDatePicker v-model="selectedDateProxy">
            <template #trigger>
              <button class="p-2 rounded-full hover:bg-gray-50 text-[#1c140d] transition-colors">
                <IconCalendar />
              </button>
            </template>
          </EeumDatePicker>
        </div>
      </template>
    </MainHeader>
    
    <main class="space-y-6">
      <!-- 최근 추가된 사진 (Swiper) -->
      <section class="recent-photos-section py-6 bg-[#F0EEE9]">
        <div class="flex items-center justify-between px-6 mb-4 cursor-pointer" @click="navigateToAlbum({ id: 'all' })">
            <h2 class="text-lg font-bold text-[#1c140d]">최근 추가된 사진</h2>
            <span class="material-symbols-outlined text-[#9c7349]">chevron_right</span>
        </div>
        
        <swiper v-if="recentPhotos.length > 0"
          :effect="'creative'"
          :grabCursor="true"
          :centeredSlides="true"
          :slidesPerView="'auto'"
          :loop="true"
          :creativeEffect="{
            prev: {
              shadow: false,
              translate: ['-120%', 0, -500],
              rotate: [0, 0, -15],
              opacity: 1,
            },
            next: {
              shadow: false,
              translate: ['120%', 0, -500],
              rotate: [0, 0, 15],
              opacity: 1,
            },
          }"
          :modules="modules"
          class="recent-swiper"
        >
          <swiper-slide v-for="(photo, index) in recentPhotos" :key="photo.photoId || index">
            <div @click.stop="goToPhotoDetail(photo)" class="photo-card relative group overflow-hidden rounded-2xl bg-white cursor-pointer">
              <!-- 메인 이미지 -->
              <img :src="photo.displayUrl" class="relative w-full h-full object-cover shadow-sm z-10 image-fade-in" />
              
              <!-- 커스텀 딤 오버레이 (둥근 모서리 적용을 위해 내부 배치) -->
              <div class="absolute inset-0 bg-black/40 z-20 pointer-events-none transition-opacity duration-300 custom-overlay opacity-0"></div>

              <div class="absolute bottom-4 left-4 text-white drop-shadow-md z-30 group-[.swiper-slide-active]:opacity-100 transition-opacity">
                <p class="text-sm font-bold">{{ photo.takenAt || '날짜 미상' }}</p>
                <p class="text-xs">{{ photo.uploaderName || '익명' }}님이 올림</p>
              </div>
            </div>
          </swiper-slide>
        </swiper>
        <div v-else class="h-[340px] flex items-center justify-center text-gray-400">
            <p>최근 사진이 없습니다.</p>
        </div>
      </section>

      <!-- 가족 앨범 그리드 -->
      <section class="px-4">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold leading-tight tracking-tight text-[#1c140d]">
            {{ familyStore.selectedFamily?.name || '우리 가족' }} 앨범
          </h3>
        </div>
        <div class="grid grid-cols-3 gap-x-3 gap-y-6">
          <!-- 동적 앨범 목록 -->
          <div 
            v-for="album in albums" 
            :key="album.id" 
            class="flex flex-col gap-2 group cursor-pointer" 
            @click.stop="navigateToAlbum(album)"
          >
            <!-- 겹쳐진 사진 효과 컨테이너 -->
            <div class="relative w-full aspect-square">
                 <!-- 겹침 레이어 -->
                 <div class="absolute top-0 left-2 right-2 bottom-2 bg-white border border-[#e8dbce] rounded-2xl transform -rotate-[8deg] translate-y-1 shadow-sm z-0"></div>
                 <div class="absolute top-0 left-1 right-1 bottom-1 bg-white border border-[#e8dbce] rounded-2xl transform rotate-[5deg] translate-y-0.5 shadow-sm z-10"></div>
                 
                 <!-- 메인 커버 -->
                 <div class="absolute inset-0 rounded-2xl overflow-hidden shadow-lg z-20 bg-[#f4ede7] border border-[#f0e6dd]">
                    <img 
                        class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110 image-fade-in" 
                        :src="album.cover || 'https://via.placeholder.com/150'"
                        alt="앨범 커버"
                    />
                    <div class="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors"></div>
                 </div>
            </div>
            
            <div class="text-center">
              <p class="text-sm font-bold truncate text-[#1c140d]">{{ album.title }}</p>
              <p class="text-[11px] text-[#9c7349]">{{ album.count }}장</p>
            </div>
          </div>
        </div>
      </section>

    </main>

    <!-- 부유형 액션 버튼 -->
    <button @click="triggerFileInput" class="fixed bottom-32 right-6 w-14 h-14 bg-primary text-white rounded-full shadow-lg shadow-primary/30 flex items-center justify-center active:scale-95 transition-transform z-30">
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

    <BottomNav v-if="!isModalOpen && !showPreviewModal" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import MainHeader from '@/components/MainHeader.vue';
import BottomNav from '@/components/layout/BottomNav.vue';
import ImagePreviewModal from '@/components/gallery/ImagePreviewModal.vue';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { getPhotos } from '@/services/albumService';
import EeumDatePicker from '@/components/common/EeumDatePicker.vue';
import { usePhotoUpload } from '@/composables/usePhotoUpload';
import IconCalendar from '@/components/icons/IconCalendar.vue';

// Swiper 설정
import { Swiper, SwiperSlide } from 'swiper/vue';
import { EffectCreative } from 'swiper/modules';
import 'swiper/css';
import 'swiper/css/effect-creative';

const modules = [EffectCreative];

const router = useRouter();
const route = useRoute();
const familyStore = useFamilyStore();
const modalStore = useModalStore();
const photos = ref([]);
const isModalOpen = ref(false);

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

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
    await fetchAlbumPhotos();
});

const selectedDateProxy = computed({
    get: () => '',
    set: (date) => {
        if (date) {
            router.push({ 
                name: 'AlbumPage', 
                params: { id: 'all' },
                query: { date } 
            });
        }
    }
});

const recentPhotos = computed(() => {
    if (!photos.value || photos.value.length === 0) return [];
    
    // 최신순 정렬
    return [...photos.value]
        .sort((a, b) => {
            const dateA = new Date(a.createdAt || a.created_at || a.takenAt || a.taken_at || 0);
            const dateB = new Date(b.createdAt || b.created_at || b.takenAt || b.taken_at || 0);
            const diff = dateB - dateA;
            if (diff !== 0) return diff;

            // 동일 시간인 경우 처리
            const createdA = new Date(a.createdAt || a.created_at || 0);
            const createdB = new Date(b.createdAt || b.created_at || 0);
            const diffCreated = createdB - createdA;
            if (diffCreated !== 0) return diffCreated;

            return (b.photoId || b.id || 0) - (a.photoId || a.id || 0);
        })
        .slice(0, 5);
});

// API 데이터를 기반으로 계산된 앨범 목록
const albums = computed(() => {
    if (photos.value.length === 0) return [];
    
    // 1. 전체 사진 앨범
    const allPhotosAlbum = { 
        id: 'all', 
        title: '전체 사진', 
        count: photos.value.length, 
        cover: photos.value[0]?.displayUrl 
    };

    // 2. 업로더별 그룹핑
    const groups = {};
    photos.value.forEach(photo => {
        const name = photo.uploaderName || '익명';
        if (!groups[name]) {
            groups[name] = [];
        }
        groups[name].push(photo);
    });

    const uploaderAlbums = Object.keys(groups).map((name, index) => {
        // 최신 사진을 커버로 사용하기 위해 정렬
        const groupPhotos = groups[name].sort((a, b) => {
            const dateA = new Date(a.createdAt || a.created_at || a.takenAt || a.taken_at || 0);
            const dateB = new Date(b.createdAt || b.created_at || b.takenAt || b.taken_at || 0);
            const diff = dateB - dateA;
            if (diff !== 0) return diff;

            const createdA = new Date(a.createdAt || a.created_at || 0);
            const createdB = new Date(b.createdAt || b.created_at || 0);
            const diffCreated = createdB - createdA;
            if (diffCreated !== 0) return diffCreated;

            return (b.photoId || b.id || 0) - (a.photoId || a.id || 0);
        });
        
        return {
            id: index + 1000, 
            title: `${name}의 앨범`,
            uploaderName: name,
            count: groupPhotos.length,
            cover: groupPhotos[0]?.displayUrl
        };
    });

    return [...uploaderAlbums];
});

const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/';

// 반응형 familyId
const familyId = ref(null);

const fetchAlbumPhotos = async () => {
    if (!familyId.value) return;

    try {
        const response = await getPhotos(familyId.value);

        let rawPhotos = [];
        // 다양한 응답 구조 대응
        if (Array.isArray(response.data)) {
            rawPhotos = response.data;
        } else if (response.data && Array.isArray(response.data.data)) {
            rawPhotos = response.data.data;
        } else if (response.data && Array.isArray(response.data.result)) {
            rawPhotos = response.data.result;
        } else if (response.data && Array.isArray(response.data.content)) {
            rawPhotos = response.data.content;
        } else {
            console.warn("예상치 못한 응답 구조:", response.data);
            rawPhotos = [];
        }

        // URL 처리 및 초기 가공
        const processed = rawPhotos.map(photo => {
            let url = photo.storageUrl || photo.imageUrl;
            if (url && !url.startsWith('http')) {
                url = S3_BASE_URL + url;
            }
            return {
                ...photo,
                displayUrl: url
            };
        });

        // 전체 정렬: 최신순
        processed.sort((a, b) => {
            const dateA = new Date(a.createdAt || a.created_at || a.takenAt || a.taken_at || 0);
            const dateB = new Date(b.createdAt || b.created_at || b.takenAt || b.taken_at || 0);
            return dateB - dateA;
        });

        photos.value = processed;
        
    } catch (error) {
        console.error("사진을 불러오는데 실패했습니다:", error);
    }
};

const navigateToAlbum = (album) => {
  const query = {};
  if (album.id !== 'all' && album.uploaderName) {
      query.uploader = album.uploaderName;
  }
  
  router.push({ 
      name: 'AlbumPage', 
      params: { id: album.id },
      query: query
  });
};

const goToPhotoDetail = (photo) => {
    router.push({
        name: 'PhotoDetail',
        params: { photoId: photo.photoId || photo.id },
        query: { context: 'recent' }
    });
};

onMounted(() => {
    // 1. 라우트 파라미터 우선
    if (route.params.familyId) {
        familyId.value = route.params.familyId;
    } 
    // 2. 스토어 상태 사용
    else if (familyStore.selectedFamily?.id) {
        familyId.value = familyStore.selectedFamily.id;
        router.replace({ name: 'GalleryPage', params: { familyId: familyId.value } });
    }

    if (familyId.value) {
        fetchAlbumPhotos();
    }
});

// 라우트 파라미터 변경 감지
watch(() => route.params.familyId, (newId) => {
    if (newId && newId !== familyId.value) {
        familyId.value = newId;
        fetchAlbumPhotos();
    }
});

// 스토어 가족 선택 변경 감지 (헤더 드롭다운)
watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily && newFamily.id) {
        if (String(newFamily.id) !== String(route.params.familyId)) {
            router.replace({ name: 'GalleryPage', params: { familyId: newFamily.id } });
        }
    }
});
</script>

<style scoped>
.recent-swiper {
  width: 100%;
  padding-top: 20px;
  padding-bottom: 50px;
}

.swiper-slide {
  width: 260px;
  height: 340px;
}

.photo-card {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

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

/* Swiper 활성 슬라이드 효과 */
.swiper-slide:not(.swiper-slide-active) .custom-overlay {
  opacity: 1;
}

.swiper-slide-active img {
  transform: scale(1.05);
  transition: all 0.3s ease;
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
