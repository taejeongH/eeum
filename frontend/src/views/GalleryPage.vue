<template>
  <div class="bg-background-light min-h-screen text-[#1c140d] pb-24 relative">
    
    <!-- Refined Header -->
    <MainHeader @modal-state-change="handleModalStateChange" :show-profiles="false" />
    
    <!-- Sub Header for Page Controls -->
    <div class="sticky top-0 z-20 bg-background-light/80 backdrop-blur-md px-4 pt-4 pb-4 transition-colors duration-200 flex items-center justify-between border-b border-gray-100/50">
      <div class="flex items-center gap-3">
        <h1 class="text-xl font-bold tracking-tight pl-2">가족 갤러리</h1>
      </div>
      <div class="flex items-center gap-1">
        <EeumDatePicker v-model="selectedDateProxy">
          <template #trigger>
            <button class="p-2 rounded-full hover:bg-gray-100 transition-colors text-[#1c140d]">
              <span class="material-symbols-outlined">calendar_today</span>
            </button>
          </template>
        </EeumDatePicker>
        <button class="p-2 rounded-full hover:bg-gray-100 transition-colors text-[#1c140d]">
          <span class="material-symbols-outlined">tune</span>
        </button>
      </div>
    </div>

    <main class="space-y-6">
      <!-- Recently Added (Swiper) -->
      <section class="recent-photos-section py-6 bg-[#F0EEE9]" @click="navigateToAlbum({ id: 'all' })">
        <div class="flex items-center justify-between px-6 mb-4">
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
              shadow: true,
              translate: ['-120%', 0, -500],
              rotate: [0, 0, -15],
              opacity: 0.6,
            },
            next: {
              shadow: true,
              translate: ['120%', 0, -500],
              rotate: [0, 0, 15],
              opacity: 0.6,
            },
          }"
          :modules="modules"
          class="recent-swiper"
        >
          <swiper-slide v-for="(photo, index) in recentPhotos" :key="photo.photoId || index">
            <div class="photo-card relative group overflow-hidden rounded-2xl bg-black/5">
              <!-- Blurred Background for Fill -->
              <img :src="photo.displayUrl" class="absolute inset-0 w-full h-full object-cover blur-md scale-110 opacity-30" aria-hidden="true" />
              
              <!-- Main Image (Contained) -->
              <img :src="photo.displayUrl" class="relative w-full h-full object-contain shadow-sm z-10" />
              
              <div class="absolute bottom-4 left-4 text-white drop-shadow-md z-20 group-[.swiper-slide-active]:opacity-100 transition-opacity">
                <p class="text-sm font-bold">{{ photo.takenAt || 'Unknown Date' }}</p>
                <p class="text-xs">{{ photo.uploaderName || '익명' }}님이 올림</p>
              </div>
            </div>
          </swiper-slide>
        </swiper>
        <div v-else class="h-[340px] flex items-center justify-center text-gray-400">
            <p>최근 사진이 없습니다.</p>
        </div>
      </section>

      <!-- Family Albums Grid -->
      <section class="px-4">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold leading-tight tracking-tight text-[#1c140d]">가족 앨범</h3>
        </div>
        <div class="grid grid-cols-3 gap-x-3 gap-y-6">
          <!-- Dynamic Albums (from API) -->
          <div 
            v-for="album in albums" 
            :key="album.id" 
            class="flex flex-col gap-2 group cursor-pointer" 
            @click.stop="navigateToAlbum(album)"
          >
            <!-- Stacked Effect Container -->
            <div class="relative w-full aspect-square">
                 <!-- Stack Layers -->
                 <div class="absolute top-0 left-2 right-2 bottom-2 bg-white border border-[#e8dbce] rounded-2xl transform -rotate-[8deg] translate-y-1 shadow-sm z-0"></div>
                 <div class="absolute top-0 left-1 right-1 bottom-1 bg-white border border-[#e8dbce] rounded-2xl transform rotate-[5deg] translate-y-0.5 shadow-sm z-10"></div>
                 
                 <!-- Main Cover -->
                 <div class="absolute inset-0 rounded-2xl overflow-hidden shadow-lg z-20 bg-[#f4ede7] border border-[#f0e6dd]">
                    <img 
                        class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110" 
                        :src="album.cover || 'https://via.placeholder.com/150'"
                        alt="Album Cover"
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

    <!-- Floating Action Button -->
    <button @click="triggerFileInput" class="fixed bottom-32 right-6 w-14 h-14 bg-primary text-white rounded-full shadow-lg shadow-primary/30 flex items-center justify-center active:scale-95 transition-transform z-30">
      <span v-if="!isUploading" class="material-symbols-outlined text-3xl">add_photo_alternate</span>
      <span v-else class="material-symbols-outlined text-3xl animate-spin">progress_activity</span>
    </button>
    <input type="file" ref="fileInput" class="hidden" accept="image/*" @change="handleFileUpload" />
    
    <BottomNav v-if="!isModalOpen" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import MainHeader from '@/components/MainHeader.vue';
import BottomNav from '@/components/layout/BottomNav.vue';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { getPhotos, uploadFile } from '@/services/albumService';
import EeumDatePicker from '@/components/common/EeumDatePicker.vue';

// Swiper Imports
import { Swiper, SwiperSlide } from 'swiper/vue';
import { EffectCreative } from 'swiper/modules';
import 'swiper/css';
import 'swiper/css/effect-creative';

const modules = [EffectCreative];

const router = useRouter();
const familyStore = useFamilyStore();
const modalStore = useModalStore();
const photos = ref([]);
const fileInput = ref(null);
const isUploading = ref(false);
const isModalOpen = ref(false);

const handleModalStateChange = (isOpen) => {
  isModalOpen.value = isOpen;
};

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
    
    // Sort by takenAt descending (robust check for various date field formats)
    return [...photos.value]
        .sort((a, b) => {
            const dateA = new Date(a.takenAt || a.taken_at || a.createdAt || a.created_at || 0);
            const dateB = new Date(b.takenAt || b.taken_at || b.createdAt || b.created_at || 0);
            const diff = dateB - dateA;
            if (diff !== 0) return diff;

            // Tie-breaker: createdAt
            const createdA = new Date(a.createdAt || a.created_at || 0);
            const createdB = new Date(b.createdAt || b.created_at || 0);
            const diffCreated = createdB - createdA;
            if (diffCreated !== 0) return diffCreated;

            // Tie-breaker: ID
            return (b.photoId || b.id || 0) - (a.photoId || a.id || 0);
        })
        .slice(0, 5);
});

// Computed Albums for API Data
const albums = computed(() => {
    if (photos.value.length === 0) return [];
    
    // 1. All Photos Album
    const allPhotosAlbum = { 
        id: 'all', 
        title: '전체 사진', 
        count: photos.value.length, 
        cover: photos.value[0]?.displayUrl 
    };

    // 2. Group by Uploader
    const groups = {};
    photos.value.forEach(photo => {
        const name = photo.uploaderName || '익명';
        if (!groups[name]) {
            groups[name] = [];
        }
        groups[name].push(photo);
    });

    const uploaderAlbums = Object.keys(groups).map((name, index) => {
        // Sort photos by date descending to get the latest one as cover
        const groupPhotos = groups[name].sort((a, b) => {
            const dateA = new Date(a.takenAt || a.taken_at || a.createdAt || a.created_at || 0);
            const dateB = new Date(b.takenAt || b.taken_at || b.createdAt || b.created_at || 0);
            const diff = dateB - dateA;
            if (diff !== 0) return diff;

            // Tie-breaker: createdAt
            const createdA = new Date(a.createdAt || a.created_at || 0);
            const createdB = new Date(b.createdAt || b.created_at || 0);
            const diffCreated = createdB - createdA;
            if (diffCreated !== 0) return diffCreated;

            // Tie-breaker: ID
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


const fetchAlbumPhotos = async () => {
    if (!familyStore.selectedFamily) return;
    try {
        const response = await getPhotos(familyStore.selectedFamily.id);
        console.log("getPhotos raw response:", response);
        
        let rawPhotos = [];
        // Checking for different possible structures of response
        if (Array.isArray(response.data)) {
            rawPhotos = response.data;
        } else if (response.data && Array.isArray(response.data.data)) {
            rawPhotos = response.data.data;
        } else if (response.data && Array.isArray(response.data.result)) {
            rawPhotos = response.data.result;
        } else if (response.data && Array.isArray(response.data.content)) {
            rawPhotos = response.data.content;
        } else {
            console.warn("Unexpected response structure:", response.data);
            rawPhotos = [];
        }

        // Process URLs
        photos.value = rawPhotos.map(photo => {
            let url = photo.storageUrl || photo.imageUrl;
            if (url && !url.startsWith('http')) {
                url = S3_BASE_URL + url;
            }
            return {
                ...photo,
                displayUrl: url
            };
        });
        
        console.log("Processed Photos:", photos.value);

    } catch (error) {
        console.error("Failed to fetch photos:", error);
    }
};

// ... (triggerFileInput same)

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

const triggerFileInput = () => {
    fileInput.value.click();
};

const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file || !familyStore.selectedFamily) return;

    isUploading.value = true;
    try {
        await uploadFile(familyStore.selectedFamily.id, file);
        // Refresh list
        await fetchAlbumPhotos();
        await modalStore.openAlert('사진이 업로드되었습니다.');
    } catch (error) {
        await modalStore.openAlert('사진 업로드에 실패했습니다.');
        console.error(error);
    } finally {
        isUploading.value = false;
        event.target.value = ''; // Reset input
    }
};



onMounted(() => {
    if (familyStore.selectedFamily) {
        fetchAlbumPhotos();
    }
});

watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily) {
        fetchAlbumPhotos();
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
  width: 260px; /* Card Width */
  height: 340px; /* Card Height */
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

/* Swiper Active Slide Effects */
.swiper-slide:not(.swiper-slide-active) img {
  filter: blur(4px);
  transition: filter 0.3s ease;
}

.swiper-slide-active img {
  filter: blur(0);
  transform: scale(1.05);
  transition: all 0.3s ease;
}
</style>
