<template>
  <div class="bg-gray-50 min-h-screen pb-10">
    
    <!-- Premium Header Area -->
    <div class="relative w-full h-52 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0">
      <!-- Gradient Overlay -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      
      <!-- ID Pattern (Decorative) -->
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>

      <!-- Navigation Bar -->
      <div class="relative z-30 flex justify-between items-start p-5 pt-6">
        <button @click="goBack" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        <!-- Edit Button -->
        <button 
           @click="openEditModal"
           class="px-4 py-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm font-semibold text-sm flex items-center gap-1.5"
        >
           <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg>
           수정
        </button>
      </div>
    </div>

    <!-- Content Area -->
    <div class="px-5 -mt-24 relative z-20">
      
      <div v-if="isLoading" class="bg-white rounded-3xl shadow-lg p-10 flex justify-center min-h-[300px] items-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]"></div>
      </div>

      <template v-else-if="medication">
        <!-- Hero Card -->
        <div class="bg-white/80 backdrop-blur-xl rounded-3xl shadow-xl shadow-gray-200/60 p-8 flex flex-col items-center relative overflow-hidden mb-6 border border-white/50">
           
           <!-- Decorative Icon -->
           <div class="w-24 h-24 rounded-full bg-gradient-to-br from-orange-50 to-orange-100 flex items-center justify-center text-[var(--color-primary)] shadow-inner mb-4 ring-4 ring-white">
              <svg class="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.384-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path></svg>
           </div>

           <div class="text-center w-full">
             <div class="inline-flex items-center gap-1.5 px-3 py-1 bg-gray-100 text-gray-600 rounded-full mb-3">
                <span class="w-1.5 h-1.5 rounded-full"
                    :class="{
                        'bg-blue-500': medication.cycleType === 'DAILY',
                        'bg-green-500': medication.cycleType === 'WEEKLY'
                    }"
                ></span>
                <span class="text-xs font-bold tracking-wide">{{ getCycleLabel(medication.cycleType) }}</span>
             </div>
             
             <h1 class="text-2xl font-extrabold text-gray-900 mb-2 leading-tight">{{ medication.medicineName }}</h1>
             <p class="text-sm text-gray-400 font-medium">
               {{ formatDate(medication.startDate) }} ~ {{ formatDate(medication.endDate) }}
             </p>
           </div>
        </div>

        <!-- Detail Grid -->
        <div class="bg-white rounded-3xl shadow-sm border border-gray-100/50 p-6 mb-6">
          <h2 class="text-lg font-bold text-gray-800 mb-5 flex items-center gap-2">
            <span class="w-1 h-5 rounded-full bg-[var(--color-primary)]"></span>
            복약 상세
          </h2>

          <div class="grid gap-1">
             <!-- Cycle -->
            <div class="flex items-center gap-4 p-3 rounded-2xl hover:bg-gray-50 transition group">
              <div class="w-12 h-12 rounded-xl bg-orange-50 flex items-center justify-center flex-shrink-0 text-[var(--color-primary)] group-hover:scale-110 transition-transform">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-[11px] text-gray-400 font-medium uppercase tracking-wider">Schedule</span>
                <span class="text-[15px] font-semibold text-gray-800">
                    <span v-if="medication.cycleType === 'DAILY'">매일 복용</span>
                    <span v-else-if="medication.cycleType === 'WEEKLY'">매주 <span class="text-[var(--color-primary)]">{{ formatDays(medication.daysOfWeek) }}</span> 요일</span>
                </span>
              </div>
            </div>

            <!-- Time -->
            <div class="flex items-start gap-4 p-3 rounded-2xl hover:bg-gray-50 transition group">
              <div class="w-12 h-12 rounded-xl bg-orange-50 flex items-center justify-center flex-shrink-0 text-[var(--color-primary)] group-hover:scale-110 transition-transform mt-1">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
              </div>
              <div class="flex flex-col gap-2 w-full">
                <div class="flex items-center justify-between">
                    <span class="text-[11px] text-gray-400 font-medium uppercase tracking-wider">Times</span>
                    <span class="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full font-bold">{{ medication.totalDosesDay }}회</span>
                </div>
                <div class="flex flex-wrap gap-2">
                    <span 
                      v-for="time in medication.notificationTimes" 
                      :key="time"
                      class="px-3 py-1.5 bg-white border border-gray-200 rounded-lg text-sm font-semibold text-gray-700 shadow-sm"
                    >
                      {{ time.substring(0, 5) }}
                    </span>
                 </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Delete Button -->
        <button 
           @click="handleDelete"
           class="w-full py-4 rounded-2xl bg-red-50 border border-red-100 text-red-500 font-bold hover:bg-red-100 hover:text-red-600 transition-all shadow-sm active:scale-[0.98] mt-4"
        >
           이 복약 정보 삭제하기
        </button>

      </template>

      <div v-else class="bg-white rounded-3xl shadow-lg p-10 flex flex-col items-center justify-center gap-4 text-center min-h-[300px]">
         <p class="text-gray-500">정보가 존재하지 않습니다.</p>
         <button @click="goBack" class="text-[var(--color-primary)] font-bold">돌아가기</button>
      </div>

    </div>

    <!-- Edit Modal -->
    <MedicationAddModal 
      :show="isModalOpen" 
      :initial-data="medication"
      @close="closeModal"
      @add-medication="handleUpdateMedication"
    />

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import api from '@/services/api';
import MedicationAddModal from './group-setup/MedicationAddModal.vue';
import { useModalStore } from '@/stores/modal';

const modalStore = useModalStore();

const route = useRoute();
const router = useRouter();
const familyId = route.params.familyId;
const medicationId = route.params.medicationId;

const medication = ref(null);
const isLoading = ref(true);
const isModalOpen = ref(false);

const fetchDetail = async () => {
    isLoading.value = true;
  try {
    const response = await api.get(`/families/${familyId}/medications/${medicationId}`);
    medication.value = response.data;
  } catch (error) {
    console.error('Failed to fetch medication detail:', error);
    await modalStore.openAlert('상세 정보를 불러오는데 실패했습니다.');
    router.replace({ name: 'MedicationList', params: { familyId } });
  } finally {
    isLoading.value = false;
  }
};

const openEditModal = () => {
    isModalOpen.value = true;
};

const closeModal = () => {
    isModalOpen.value = false;
};

const handleUpdateMedication = async (medData) => {
    try {
        const payload = {
            medicineName: medData.medicineName,
            cycleType: medData.cycleType,
            totalDosesDay: medData.notificationTimes.length,
            cycleValue: medData.cycleValue,
            daysOfWeek: medData.daysOfWeek,
            startDate: medData.startDate,
            endDate: medData.endDate,
            notificationTimes: medData.notificationTimes
        };

        await api.put(`/families/${familyId}/medications/${medicationId}`, payload);
        await modalStore.openAlert('수정되었습니다.');
        closeModal();
        fetchDetail(); // Refresh data
    } catch (error) {
        console.error('Failed to update medication:', error);
        await modalStore.openAlert('수정에 실패했습니다.');
    }
};

const handleDelete = async () => {
    if (!await modalStore.openConfirm('정말로 삭제하시겠습니까?')) return;
    
    try {
        await api.delete(`/families/${familyId}/medications/${medicationId}`);
        await modalStore.openAlert('삭제되었습니다.');
        router.replace({ name: 'MedicationList', params: { familyId } });
    } catch (error) {
        console.error('Failed to delete medication:', error);
        await modalStore.openAlert('삭제에 실패했습니다.');
    }
};

const goBack = () => {
    router.go(-1);
};

onMounted(() => {
    fetchDetail();
});

// Utilities
const getCycleLabel = (type) => {
    const map = { 'DAILY': '매일', 'WEEKLY': '매주' };
    return map[type] || type;
};

const formatDate = (dateStr) => {
    if (!dateStr) return '';
    return dateStr.replace(/-/g, '.');
};

const formatDays = (bitmask) => {
    const days = ['월', '화', '수', '목', '금', '토', '일'];
    const result = [];
    const masks = [1, 2, 4, 8, 16, 32, 64];
    
    masks.forEach((mask, index) => {
        if ((bitmask & mask) !== 0) {
            result.push(days[index]);
        }
    });
    
    return result.join(', ');
};
</script>
