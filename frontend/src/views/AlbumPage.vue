<template>
  <div class="bg-background-light min-h-screen text-[#1c140d] flex flex-col relative overflow-hidden">
    <!-- Modal Like Header (Pull bar) -->
    <div class="flex flex-col items-center bg-background-light pt-2">
      <div class="h-1.5 w-10 rounded-full bg-[#e8dbce]"></div>
    </div>

    <!-- Top AppBar -->
    <div class="flex items-center bg-background-light p-4 pb-2 justify-between sticky top-0 z-10">
      <div class="flex items-center gap-2">
        <button @click="$router.back()" class="flex items-center justify-center p-2 rounded-full hover:bg-primary/10 transition-colors">
          <span class="material-symbols-outlined text-[#1c140d]">close</span>
        </button>
        <div class="flex flex-col">
          <h2 class="text-[#1c140d] text-xl font-bold leading-tight tracking-[-0.015em]">가족 앨범</h2>
          <p class="text-xs text-[#9c7349] font-medium">{{ photos.length }}개 항목 • 가족 공유</p>
        </div>
      </div>
      <div class="flex items-center gap-4">
        <button @click="openDatePicker" class="flex items-center gap-1 text-primary text-base font-bold leading-normal tracking-[0.015em]">
          <span class="material-symbols-outlined text-lg">calendar_today</span>
        </button>
        <input type="date" ref="dateInput" class="hidden" @change="handleDateChangeLocal" />
        <button 
            @click="toggleSelectionMode"
            class="text-primary text-base font-bold leading-normal tracking-[0.015em] shrink-0"
        >
            {{ isSelectionMode ? '취소' : '편집' }}
        </button>
      </div>
    </div>

    <!-- Filter Chips Section -->


    <!-- 4-Column ImageGrid -->
    <div class="flex-1 overflow-y-auto px-4 pb-24">
      <div v-if="photos.length > 0" class="grid grid-cols-4 gap-1.5">
        <div 
            v-for="photo in photos" 
            :key="photo.photoId || photo.id" 
            class="relative group aspect-square"
            @click="handlePhotoClick(photo)"
        >
          <div 
            class="w-full h-full bg-center bg-no-repeat bg-cover rounded-sm cursor-pointer border-2 transition-all duration-200" 
            :class="selectedPhotos.includes(photo.photoId || photo.id) ? 'border-primary opacity-80 scale-95' : 'border-transparent hover:border-primary'"
            :style="{ backgroundImage: `url(${photo.displayUrl})` }"
          ></div>
          
          <!-- Selection Checkmark -->
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

    <!-- Floating Action Bar (Contextual for Editing) -->
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import { getPhotos, deletePhoto } from '@/services/albumService';

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();
const allPhotos = ref([]); // Store all fetched photos
const photos = ref([]); // Store filtered photos
const dateInput = ref(null);
const S3_BASE_URL = 'https://eeum-s3-bucket.s3.ap-northeast-2.amazonaws.com/';

// Update title based on query
const albumTitle = computed(() => {
    const uploader = route.query.uploader;
    return uploader ? `${uploader}의 앨범` : '가족 앨범';
});

const fetchPhotos = async () => {
    if (!familyStore.selectedFamily) return;
    try {
        const response = await getPhotos(familyStore.selectedFamily.id);
        let data = [];
        if (response.data && Array.isArray(response.data.data)) {
            data = response.data.data;
        } else if (Array.isArray(response.data)) {
            data = response.data;
        }

        // Process URLs
        allPhotos.value = data.map(photo => {
            let url = photo.storageUrl || photo.imageUrl;
            if (url && !url.startsWith('http')) {
                url = S3_BASE_URL + url;
            }
            return {
                ...photo,
                displayUrl: url
            };
        });
        
        filterPhotos(); // Apply filter initially
    } catch (error) {
        console.error("Failed to fetch album photos:", error);
    }
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

const openDatePicker = () => {
    if (dateInput.value) {
        if (typeof dateInput.value.showPicker === 'function') {
            dateInput.value.showPicker();
        } else {
            dateInput.value.click();
        }
    }
};

const handleDateChangeLocal = (event) => {
    const date = event.target.value;
    router.replace({ 
        query: { 
            ...route.query, 
            date 
        } 
    });
    // The watch on route.query.uploader needs to be expanded or a new watch added
};

// Selection State
const isSelectionMode = ref(false);
const selectedPhotos = ref([]);

const toggleSelectionMode = () => {
    isSelectionMode.value = !isSelectionMode.value;
    selectedPhotos.value = []; // Reset selection on toggle
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
        // Future: Open lightbox or detail view
        console.log("View photo:", photo);
    }
};

const deleteSelectedPhotos = async () => {
    if (selectedPhotos.value.length === 0) return;
    
    if (!confirm(`${selectedPhotos.value.length}장의 사진을 삭제하시겠습니까?`)) return;

    try {
        // Delete all selected photos in parallel
        await Promise.all(selectedPhotos.value.map(id => deletePhoto(id)));
        
        alert("사진이 삭제되었습니다.");
        
        // Refresh list
        await fetchPhotos();
        
        // Exit selection mode
        toggleSelectionMode();
    } catch (error) {
        console.error("Delete failed:", error);
        alert("사진 삭제에 실패했습니다.");
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

// Re-filter if query changes
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
</style>
