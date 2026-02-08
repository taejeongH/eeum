<template>
  <div class="bg-[#fcfcfc] min-h-screen pb-20">
    <!-- <MainHeader @modal-state-change="handleModalStateChange" :show-profiles="false" /> -->
    
    <div class="bg-white/90 backdrop-blur-md sticky top-0 z-[90] border-b border-gray-100 shadow-sm px-4 py-4 flex items-center gap-3 transition-all duration-300">
       <button @click="router.back()" class="p-2 -ml-1 rounded-full hover:bg-gray-100 transition-colors text-gray-600">
         <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
           <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
         </svg>
       </button>
       <h1 class="text-xl font-bold text-gray-900 tracking-tight">기기 관리</h1>
    </div>

    <main class="px-5 py-6 space-y-8">
      <!-- QR Code Section -->
      <section v-if="isRepresentative" class="space-y-4">
        <div class="flex items-center gap-2 mb-2">
          <div class="w-1.5 h-6 bg-[var(--color-primary)] rounded-full"></div>
          <h2 class="text-lg font-black text-gray-900">새 기기 등록</h2>
        </div>

        <div class="bg-white rounded-3xl p-6 shadow-sm border border-gray-100 transition-all">
          <div v-if="!qrCode" class="flex flex-col items-center py-6">
            <div class="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <p class="text-gray-500 text-sm text-center mb-6 leading-relaxed">
              IoT 기기에 있는 카메라로<br>아래 QR 코드를 스캔하여 등록하세요.
            </p>
            <button 
              @click="generateQR" 
              :disabled="isGenerating" 
              class="eeum-btn-primary flex items-center justify-center gap-2"
            >
              <div v-if="isGenerating" class="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
              <span>{{ isGenerating ? 'QR 코드 생성 중...' : 'QR 코드 생성하기' }}</span>
            </button>
          </div>

          <div v-else class="flex flex-col items-center">
            <div class="qr-container bg-white p-4 rounded-2xl border border-gray-100 shadow-sm mb-6">
              <canvas ref="qrCanvas" class="w-56 h-56 mx-auto"></canvas>
            </div>
            
            <div class="text-center mb-6">
              <div class="inline-block px-3 py-1 bg-gray-100 rounded-lg mb-2">
                <span class="text-[11px] font-bold text-gray-500 uppercase tracking-widest">페어링 코드</span>
              </div>
              <p class="text-2xl font-black text-gray-900 tracking-tight">{{ qrCode.pairingCode }}</p>
              <p class="text-[13px] font-bold mt-1" :class="remainingTime <= 60 ? 'text-red-500' : 'text-gray-400'">
                {{ expiryText }}
              </p>
            </div>

            <button 
              @click="refreshQR" 
              class="w-full py-3 rounded-xl bg-gray-50 text-gray-600 font-bold text-sm hover:bg-gray-100 active:scale-95 transition-all flex items-center justify-center gap-2"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              QR 코드 새로고침
            </button>
          </div>
        </div>
      </section>

      <!-- Device List Section -->
      <section class="space-y-4">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <div class="w-1.5 h-6 bg-[var(--color-primary)] rounded-full"></div>
            <h2 class="text-lg font-black text-gray-900">등록된 기기</h2>
          </div>
          <span class="text-xs font-bold text-gray-400">{{ devices.length }}개</span>
        </div>

        <div v-if="isLoadingDevices" class="flex flex-col items-center justify-center py-10">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]"></div>
          <p class="mt-4 text-gray-400 text-xs font-bold uppercase tracking-widest">데이터를 불러오고 있습니다</p>
        </div>

        <div v-else-if="devices.length === 0" class="bg-white rounded-3xl p-10 border border-dashed border-gray-200 flex flex-col items-center text-center">
          <div class="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
            </svg>
          </div>
          <p class="text-gray-900 font-bold mb-1">등록된 기기가 없어요</p>
          <p class="text-gray-400 text-xs">상단의 QR 코드를 사용하여 첫 기기를 등록해보세요.</p>
        </div>

        <div v-else class="space-y-4">
          <div v-for="device in devices" :key="device.id" class="bg-white rounded-3xl p-5 shadow-sm border border-gray-100 flex items-center gap-4 group transition-all relative overflow-hidden">
            <!-- Icon -->
            <div class="w-14 h-14 rounded-2xl shrink-0 flex items-center justify-center transition-colors" :class="device.isActive ? 'bg-[var(--color-primary-soft)] text-[var(--color-primary-light)]' : 'bg-gray-50 text-gray-300'">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
              </svg>
            </div>

            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-0.5">
                <span class="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{{ getLocationName(device.locationType) }}</span>
              </div>
              <h3 class="text-lg font-bold text-gray-900 truncate tracking-tight">{{ device.deviceName }}</h3>
              <p class="text-[11px] font-bold text-gray-400 mt-0.5 uppercase tracking-widest">S/N: {{ device.serialNumber }}</p>
            </div>

            <!-- Simple Actions -->
            <div v-if="isRepresentative" class="flex flex-col gap-2">
              <button @click="editDevice(device)" class="w-8 h-8 rounded-full bg-gray-50 flex items-center justify-center text-gray-400 hover:bg-orange-50 hover:text-[var(--color-primary)] transition-all">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                </svg>
              </button>
              <button @click="confirmDelete(device)" class="w-8 h-8 rounded-full bg-gray-50 flex items-center justify-center text-gray-300 hover:bg-red-50 hover:text-red-500 transition-all">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- Generic Modal Wrapper (Matches project style) -->
    <transition name="modal">
      <div v-if="showEditModal || showDeleteModal" class="fixed inset-0 z-[200] flex items-end sm:items-center justify-center p-0 sm:p-6">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity" @click="handleModalClose"></div>
        
        <!-- Modal Content -->
        <div 
          class="relative w-full sm:max-w-md bg-white rounded-t-[2.5rem] sm:rounded-[2.5rem] p-8 shadow-2xl transition-all animate-slide-up sm:animate-scale-up"
          :style="modalStyle"
        >
          <!-- Handle for Mobile -->
          <div 
            class="w-12 h-1 bg-gray-200 rounded-full mx-auto mb-6 sm:hidden cursor-grab active:cursor-grabbing"
            @touchstart="onTouchStart"
            @touchmove="onTouchMove"
            @touchend="onTouchEnd"
          ></div>

          <div v-if="showEditModal">
            <h3 class="text-2xl font-black text-gray-900 mb-2 tracking-tight">기기 정보 수정</h3>
            <p class="text-sm text-gray-500 mb-8">기기의 이름과 설치 위치를 변경할 수 있습니다.</p>

            <div class="space-y-6 mb-10">
              <div class="space-y-2">
                <label class="text-[11px] font-bold text-gray-400 uppercase tracking-widest ml-1">기기 이름</label>
                <input 
                  v-model="editForm.deviceName" 
                  type="text" 
                  class="w-full h-14 px-5 rounded-2xl bg-gray-50 border border-gray-100 font-bold focus:bg-white focus:ring-2 focus:ring-[var(--color-primary)] transition-all outline-none text-gray-900" 
                  placeholder="예: 거실 카메라" 
                />
              </div>

              <div class="space-y-2">
                <label class="text-[11px] font-bold text-gray-400 uppercase tracking-widest ml-1">설치 위치</label>
                <div class="grid grid-cols-2 gap-2">
                  <button 
                    v-for="loc in locations" 
                    :key="loc.value"
                    @click="editForm.locationType = loc.value"
                    class="h-12 rounded-[var(--radius-xl)] text-xs font-bold transition-all border"
                    :class="editForm.locationType === loc.value ? 'bg-gray-800 text-white border-gray-800 shadow-md' : 'bg-gray-50 text-gray-400 border-gray-100 hover:bg-white'"
                  >
                    {{ loc.label }}
                  </button>
                </div>
              </div>
            </div>

            <div class="flex gap-3">
              <button @click="closeEditModal" class="flex-1 h-14 rounded-[var(--radius-xl)] bg-gray-50 text-gray-400 font-bold hover:bg-gray-100 transition-all active:scale-95">취소</button>
              <button @click="saveDevice" :disabled="isSaving" class="flex-1 eeum-btn-primary flex items-center justify-center gap-2">
                <div v-if="isSaving" class="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                저장하기
              </button>
            </div>
          </div>

          <div v-if="showDeleteModal">
            <div class="w-20 h-20 bg-red-50 rounded-3xl flex items-center justify-center mb-6 mx-auto">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </div>
            <h3 class="text-2xl font-black text-gray-900 mb-2 tracking-tight text-center">기기 삭제</h3>
            <p class="text-sm text-gray-500 mb-8 text-center leading-relaxed">
              <span class="text-gray-900 font-black">'{{ deviceToDelete?.deviceName }}'</span><br>기기를 정말로 삭제하시겠습니까?
            </p>

            <div class="flex gap-3">
              <button @click="closeDeleteModal" class="flex-1 h-14 rounded-[var(--radius-xl)] bg-gray-50 text-gray-400 font-bold transition-all active:scale-95">취소</button>
              <button @click="deleteDevice" :disabled="isDeleting" class="flex-1 h-14 rounded-[var(--radius-xl)] bg-red-500 text-white font-bold active:scale-95 transition-all flex items-center justify-center gap-2">
                <div v-if="isDeleting" class="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                삭제하기
              </button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
// // 창민 추가
// import MainHeader from '@/components/MainHeader.vue';
// // 창민 추가
import QRCode from 'qrcode';
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import { storeToRefs } from 'pinia';
import { generatePairingCode, getIotDevices, updateIotDevice, deleteIotDevice } from '@/services/api';
import { Logger } from '@/services/logger';

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();
// // 창민추가
// const isModalOpen = ref(false);
// const handleModalStateChange = (isOpen) => {
//   isModalOpen.value = isOpen;
// };
// // 창민추가
const { selectedFamily } = storeToRefs(familyStore);
const familyId = ref(parseInt(route.params.familyId));



const locations = [
  { label: '거실', value: 'LIVING_ROOM' },
  { label: '침실', value: 'BEDROOM' },
  { label: '부엌', value: 'KITCHEN' },
  { label: '욕실', value: 'BATHROOM' },
  { label: '현관', value: 'ENTRANCE' },
  { label: '기타', value: 'OTHER' }
];

// QR Code state
const qrCode = ref(null);
const qrCanvas = ref(null);
const isGenerating = ref(false);
const expiryTimer = ref(null);
const remainingTime = ref(0);

// Device list state
const devices = ref([]);
const isLoadingDevices = ref(false);

// Edit modal state
const showEditModal = ref(false);
const editingDevice = ref(null);
const editForm = ref({
  deviceName: '',
  locationType: ''
});
const isSaving = ref(false);

// Delete modal state
const showDeleteModal = ref(false);
const deviceToDelete = ref(null);
const isDeleting = ref(false);

// Drag to close state
const touchStartY = ref(0);
const modalTranslateY = ref(0);
const isDragging = ref(false);

const modalStyle = computed(() => {
  if (!isDragging.value) return {};
  return {
    transform: `translateY(${modalTranslateY.value}px)`,
    transition: 'none'
  };
});

const onTouchStart = (e) => {
  touchStartY.value = e.touches[0].clientY;
  isDragging.value = true;
};

const onTouchMove = (e) => {
  const currentY = e.touches[0].clientY;
  const deltaY = currentY - touchStartY.value;
  if (deltaY > 0) {
    modalTranslateY.value = deltaY;
  }
};

const onTouchEnd = () => {
  isDragging.value = false;
  if (modalTranslateY.value > 100) {
    handleModalClose();
  }
  modalTranslateY.value = 0;
};

// Role check
const isRepresentative = computed(() => {
  return selectedFamily.value?.owner === true;
});

// Computed
const expiryText = computed(() => {
  if (remainingTime.value <= 0) return 'QR 코드가 만료되었습니다';
  const minutes = Math.floor(remainingTime.value / 60);
  const seconds = remainingTime.value % 60;
  return `${minutes}분 ${seconds}초 후 만료`;
});

// Methods
const generateQR = async () => {
  
  // 토큰 확인 로그 추가
  const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
  
  // AndroidBridge 확인
  if (window.AndroidBridge?.getAccessToken) {
    const nativeToken = window.AndroidBridge.getAccessToken();
  }
  
  if (!familyId.value || isNaN(familyId.value)) {
    alert('가족 ID가 유효하지 않습니다. 페이지를 새로고침해 주세요.');
    return;
  }
  isGenerating.value = true;
  try {
    const response = await generatePairingCode(familyId.value);
    
    // 서버 응답 구조가 { data: { ... } } 형태일 경우와 아닐 경우 모두 대응
    const qrData = response.data || response;
    
    
    qrCode.value = qrData;
    remainingTime.value = qrCode.value.expiresIn;
    
    // Wait for DOM update
    await nextTick();
    
    if (qrCanvas.value && qrCode.value.qrContent) {
      await QRCode.toCanvas(qrCanvas.value, qrCode.value.qrContent, {
        width: 224, // 56 * 4 (Tailwind w-56 is 14rem = 224px)
        margin: 2,
        color: {
          dark: '#111827', // Gray-900 (Black)
          light: '#ffffff'
        },
        errorCorrectionLevel: 'H'
      });
    } else {
      throw new Error('QR 데이터(qrContent)가 응답에 포함되어 있지 않습니다.');
    }

    startExpiryTimer();
  } catch (error) {
    Logger.error('[QR] Failed to generate QR code:', error);
    Logger.error('[QR] Error details:', {
      message: error.message,
      response: error.response?.data,
      status: error.response?.status,
      headers: error.response?.headers
    });
    alert(`QR 코드 생성 실패: ${error.response?.data?.message || error.message || JSON.stringify(error)}`);
  } finally {
    isGenerating.value = false;
  }
};

const refreshQR = () => {
  qrCode.value = null;
  clearExpiryTimer();
  generateQR();
};

const startExpiryTimer = () => {
  clearExpiryTimer();
  expiryTimer.value = setInterval(() => {
    remainingTime.value--;
    if (remainingTime.value <= 0) {
      clearExpiryTimer();
    }
  }, 1000);
};

const clearExpiryTimer = () => {
  if (expiryTimer.value) {
    clearInterval(expiryTimer.value);
    expiryTimer.value = null;
  }
};

const loadDevices = async () => {
  if (!familyId.value || isNaN(familyId.value)) {
    Logger.warn('[loadDevices] 유효하지 않은 familyId로 인해 건너뜀:', familyId.value);
    return;
  }
  isLoadingDevices.value = true;
  try {
    const response = await getIotDevices(familyId.value);
    devices.value = response.data || [];
  } catch (error) {
    Logger.error('기기 목록 로드 실패:', error);
  } finally {
    isLoadingDevices.value = false;
  }
};

const editDevice = (device) => {
  editingDevice.value = device;
  editForm.value = {
    deviceName: device.deviceName,
    locationType: device.locationType
  };
  showEditModal.value = true;
};

const closeEditModal = () => {
  showEditModal.value = false;
  editingDevice.value = null;
};

const saveDevice = async () => {
  if (!editForm.value.deviceName.trim()) return;
  isSaving.value = true;
  try {
    await updateIotDevice(familyId.value, editingDevice.value.id, editForm.value);
    await loadDevices();
    closeEditModal();
  } catch (error) {
    Logger.error('기기 정보 업데이트 실패:', error);
  } finally {
    isSaving.value = false;
  }
};

const confirmDelete = (device) => {
  deviceToDelete.value = device;
  showDeleteModal.value = true;
};

const closeDeleteModal = () => {
  showDeleteModal.value = false;
  deviceToDelete.value = null;
};

const deleteDevice = async () => {
  isDeleting.value = true;
  try {
    await deleteIotDevice(familyId.value, deviceToDelete.value.id);
    await loadDevices();
    closeDeleteModal();
  } catch (error) {
    Logger.error('기기 삭제 실패:', error);
  } finally {
    isDeleting.value = false;
  }
};

const handleModalClose = () => {
  if (showEditModal.value) closeEditModal();
  if (showDeleteModal.value) closeDeleteModal();
};


// Body scroll lock
watch([showEditModal, showDeleteModal], ([edit, del]) => {
  if (edit || del) {
    document.body.style.overflow = 'hidden';
  } else {
    document.body.style.overflow = '';
  }
});

const getLocationName = (type) => {
  return locations.find(l => l.value === type)?.label || type;
};

// React to route changes
watch(() => route.params.familyId, (newId) => {
    if (newId && newId !== String(familyId.value)) {
        familyId.value = parseInt(newId);
        loadDevices();
    }
});

// React to store changes (Header dropdown)
watch(() => familyStore.selectedFamily, (newFamily) => {
    if (newFamily && newFamily.id) {
        if (String(newFamily.id) !== String(route.params.familyId)) {
             router.replace({ name: 'DeviceManagement', params: { familyId: newFamily.id } });
        }
    }
});

// Lifecycle
onMounted(() => {
  loadDevices();
});

onUnmounted(() => {
  clearExpiryTimer();
});
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Pretendard:wght@400;600;800;900&display=swap');

h1, h2, h3, p, span, button, input {
  font-family: 'Pretendard', sans-serif;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* Modal Transitions */
.modal-enter-active, .modal-leave-active {
  transition: opacity 0.3s ease;
}
.modal-enter-from, .modal-leave-to {
  opacity: 0;
}

@keyframes slide-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes scale-up {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.animate-slide-up {
  animation: slide-up 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

@media (min-width: 640px) {
  .animate-scale-up {
    animation: scale-up 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  }
}

.flex-2 {
  flex: 2;
}

/* QR Canvas Fix */
canvas {
  image-rendering: pixelated;
}
</style>
