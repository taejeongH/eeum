<template>
  <div class="bg-gray-50 min-h-screen pb-32">
    
    <!-- Premium Header Area -->
    <div class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0">
      <!-- Gradient Overlay -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      
      <!-- ID Pattern (Decorative) -->
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>

      <!-- Navigation Bar -->
      <div class="relative z-30 flex items-center justify-between p-5 pt-6">
        <div class="flex items-center gap-4">
            <button @click="goBack" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
              <IconBack />
            </button>
            <h1 class="text-xl font-bold text-white tracking-wide">복약 정보</h1>
        </div>
      </div>
    </div>

    <!-- Content Area -->
    <div class="px-5 -mt-20 relative z-20">
       
        <!-- Summary Card / Introduction -->
        <div class="bg-white/90 backdrop-blur-xl rounded-3xl shadow-lg border border-white/50 p-6 mb-6">
           <h2 class="text-xl font-bold text-gray-900 mb-1">
              등록된 복약 일정 💊
           </h2>
           <p class="text-gray-500 text-sm">
              가족이 복용 중인 약품 목록입니다.<br>
              눌러서 상세 정보를 확인하거나 수정하세요.
           </p>
        </div>
      
      <!-- Loading State -->
      <div v-if="isLoading" class="bg-white rounded-3xl shadow-sm p-10 flex justify-center">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]"></div>
      </div>

      <!-- Content List -->
      <!-- Content List -->
      <div v-else-if="medications.length > 0" class="space-y-4">
          <!-- View Mode Toggle -->
          <div class="px-6 flex justify-end mb-4">
              <div class="bg-gray-100 p-1 rounded-xl flex gap-1">
                  <button 
                      @click="viewMode = 'LIST'"
                      class="px-3 py-1.5 text-xs font-bold rounded-lg transition-all"
                      :class="viewMode === 'LIST' ? 'bg-white text-[var(--text-title)] shadow-sm' : 'text-gray-400 hover:text-gray-600'"
                  >
                      리스트
                  </button>
                  <button 
                      @click="viewMode = 'SCHEDULE'"
                      class="px-3 py-1.5 text-xs font-bold rounded-lg transition-all"
                      :class="viewMode === 'SCHEDULE' ? 'bg-white text-[var(--text-title)] shadow-sm' : 'text-gray-400 hover:text-gray-600'"
                  >
                      시간표
                  </button>
              </div>
          </div>
  
          <!-- LIST MODE -->
          <div v-if="viewMode === 'LIST'" class="space-y-4">
              <div
                v-for="med in medications"
                :key="med.id"
                @click="goToDetail(med.id)"
                class="bg-white rounded-3xl p-5 shadow-sm border border-gray-100 flex items-center justify-between group hover:shadow-md transition-all cursor-pointer relative overflow-hidden"
              >
                 <!-- Left: Icon & Info -->
                 <div class="flex items-center gap-4">
                    <!-- Icon Box -->
                    <div class="w-14 h-14 rounded-2xl flex items-center justify-center flex-shrink-0 bg-gray-50 text-gray-400 group-hover:scale-110 transition-transform duration-300"
                       :class="[getMedicationColor(med.id).bg, getMedicationColor(med.id).text]"
                    >
                       <!-- Pill Icon -->
                       <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.384-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path></svg>
                    </div>
       
                    <div class="flex flex-col">
                       <div class="flex items-center gap-2 mb-0.5">
                           <span class="text-[10px] font-bold px-2 py-0.5 rounded-md uppercase tracking-wide"
                              :class="{
                                 'bg-blue-100/50 text-blue-600': med.cycleType === 'DAILY',
                                 'bg-green-100/50 text-green-600': med.cycleType === 'WEEKLY'
                              }"
                           >
                             {{ getCycleLabel(med.cycleType) }}
                           </span>
                           <span class="text-xs text-gray-400 font-medium tracking-tight">
                               {{ med.totalDosesDay }}회 복용
                           </span>
                       </div>
                       <h3 class="font-bold text-gray-800 text-lg group-hover:text-[var(--color-primary)] transition-colors line-clamp-1">
                           {{ med.medicineName }}
                       </h3>
                    </div>
                 </div>
       
                 <!-- Right: Arrow -->
                 <div class="w-8 h-8 rounded-full bg-gray-50 flex items-center justify-center text-gray-300 group-hover:bg-[var(--color-primary)] group-hover:text-white transition-all">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
                 </div>
              </div>
      
              <!-- Add Button (Below List) -->
              <button
                class="w-full py-4 rounded-3xl border-2 border-dashed border-gray-300 text-sm font-medium text-gray-400 hover:text-[var(--color-primary)] hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-soft)] transition-all flex items-center justify-center gap-2 group mt-2"
                @click="openAddModal"
              >
                <div class="w-6 h-6 rounded-full bg-gray-200 text-white flex items-center justify-center group-hover:bg-[var(--color-primary)] transition-colors">
                   <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
                </div>
                약 추가하기
              </button>
          </div>
  
          <!-- SCHEDULE MODE (Visualization) -->
          <div v-else class="bg-white rounded-3xl border border-gray-200 overflow-hidden shadow-sm flex flex-col min-h-[500px]">
              <div class="overflow-y-auto p-5 space-y-6">
                
                <div v-for="day in weekDays" :key="day" class="flex flex-col gap-1">
                    <!-- Day Header -->
                    <div class="flex items-center gap-3 border-b border-gray-100 pb-2 mb-1">
                        <div class="w-8 h-8 rounded-full bg-[var(--color-primary)] text-white font-bold flex items-center justify-center shadow-md">
                            {{ day }}
                        </div>
                        <div class="h-px flex-1 bg-gray-50"></div>
                    </div>

                    <!-- Time Slots List -->
                    <div class="flex flex-col gap-3 pl-2">
                        <template v-for="hour in activeHours" :key="hour">
                             <div v-if="weeklySchedule[day][hour] && weeklySchedule[day][hour].length > 0" class="flex items-start gap-4">
                                <!-- Time Column -->
                                <div class="w-14 shrink-0 flex flex-col items-center pt-1.5">
                                    <div class="text-xs font-black text-gray-800 tracking-tight">
                                        {{ String(hour).padStart(2, '0') }}:00
                                    </div>
                                    <div class="text-[9px] font-bold text-gray-400 mt-[-2px]">
                                        {{ hour < 12 ? '오전' : '오후' }}
                                    </div>
                                </div>

                                <!-- Meds Group -->
                                <div class="flex-1 flex flex-wrap gap-2 py-0.5">
                                    <div 
                                        v-for="med in weeklySchedule[day][hour]" 
                                        :key="med.id"
                                        class="group flex items-center gap-2 pl-2 pr-3 py-1.5 bg-white border border-gray-200 rounded-lg cursor-pointer hover:border-[var(--color-primary)] hover:shadow-sm transition-all shadow-sm"
                                        @click.stop="goToDetail(med.id)"
                                    >
                                        <div class="w-2 h-2 rounded-full" :class="med.color.bg.replace('bg-', 'bg-')"></div>
                                        <span class="text-sm font-bold text-gray-700 group-hover:text-[var(--color-primary)] leading-none">{{ med.medicineName }}</span>
                                    </div>
                                </div>
                             </div>
                        </template>

                        <!-- Empty State for Day -->
                        <div v-if="!activeHours.some(h => weeklySchedule[day][h])" class="text-xs text-gray-300 font-medium py-4 text-center italic">
                            복약 일정 없음
                        </div>
                    </div>
                </div>

              </div>
          </div>
      </div>

      <!-- Empty State -->
      <div v-else class="bg-white rounded-3xl border border-dashed border-gray-200 p-10 flex flex-col items-center justify-center gap-4 text-center">
        <div class="w-16 h-16 rounded-full bg-gray-50 flex items-center justify-center text-gray-300">
             <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path></svg>
        </div>
        <div>
            <p class="text-gray-900 font-bold mb-1">복약 정보가 없어요</p>
            <p class="text-gray-400 text-sm">추가 버튼을 눌러 등록해주세요.</p>
        </div>
        <button @click="openAddModal" class="px-5 py-2.5 rounded-xl bg-[var(--color-primary)] text-white font-bold text-sm shadow-lg hover:shadow-xl transition">
            지금 추가하기
        </button>
      </div>

    </div>

    <!-- Add Modal -->
    <MedicationAddModal 
      :show="isModalOpen" 
      @close="closeModal"
      @add-medication="handleAddMedication"
    />

  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import { storeToRefs } from 'pinia';
import api from '@/services/api';
import MedicationAddModal from './group-setup/MedicationAddModal.vue';
import { useModalStore } from '@/stores/modal';
import IconBack from '@/components/icons/IconBack.vue';

const modalStore = useModalStore();

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();
const { selectedFamily } = storeToRefs(familyStore);

const familyId = ref(route.params.familyId);

const medications = ref([]);
const isLoading = ref(true);
const isModalOpen = ref(false);
const viewMode = ref('LIST'); // 'LIST' or 'SCHEDULE'

const weekDays = ['월', '화', '수', '목', '금', '토', '일'];

// Color Palette for deterministic assignment
const colorPalette = [
  { bg: 'bg-red-100', text: 'text-red-700', border: 'border-red-200' },
  { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-200' },
  { bg: 'bg-amber-100', text: 'text-amber-700', border: 'border-amber-200' },
  { bg: 'bg-green-100', text: 'text-green-700', border: 'border-green-200' },
  { bg: 'bg-emerald-100', text: 'text-emerald-700', border: 'border-emerald-200' },
  { bg: 'bg-teal-100', text: 'text-teal-700', border: 'border-teal-200' },
  { bg: 'bg-cyan-100', text: 'text-cyan-700', border: 'border-cyan-200' },
  { bg: 'bg-blue-100', text: 'text-blue-700', border: 'border-blue-200' },
  { bg: 'bg-indigo-100', text: 'text-indigo-700', border: 'border-indigo-200' },
  { bg: 'bg-violet-100', text: 'text-violet-700', border: 'border-violet-200' },
  { bg: 'bg-purple-100', text: 'text-purple-700', border: 'border-purple-200' },
  { bg: 'bg-fuchsia-100', text: 'text-fuchsia-700', border: 'border-fuchsia-200' },
  { bg: 'bg-pink-100', text: 'text-pink-700', border: 'border-pink-200' },
  { bg: 'bg-rose-100', text: 'text-rose-700', border: 'border-rose-200' },
];

const getMedicationColor = (id) => {
    // Deterministic color based on ID
    if (!id) return colorPalette[0];
    const index = id % colorPalette.length;
    return colorPalette[index];
};

// Generate Weekly Schedule Data
const weeklySchedule = computed(() => {
    const schedule = {};
    // Initialize empty slots for each day
    weekDays.forEach(day => {
        schedule[day] = {}; // Key: time (hour), Value: Array of meds
    });

    medications.value.forEach(med => {
        // Find applicable days
        let applicableDays = [];
        if (med.cycleType === 'DAILY') {
            applicableDays = weekDays;
        } else if (med.cycleType === 'WEEKLY') {
            // Bitmask decode
            const mask = med.daysOfWeek;
            const masks = [1, 2, 4, 8, 16, 32, 64]; // Mon-Sun
            applicableDays = weekDays.filter((_, idx) => (mask & masks[idx]) !== 0);
        }

        // Add to schedule for each notification time
        if (med.notificationTimes) {
            med.notificationTimes.forEach(time => {
                const hour = parseInt(time.split(':')[0], 10); // Simple grouping by hour for visualization
                
                applicableDays.forEach(day => {
                    if (!schedule[day][hour]) {
                        schedule[day][hour] = [];
                    }
                    schedule[day][hour].push({
                        ...med,
                        exactTime: time,
                        color: getMedicationColor(med.id)
                    });
                });
            });
        }
    });
    return schedule;
});

// Compute Active Hours (sorted unique hours that have meds)
const activeHours = computed(() => {
    const hours = new Set();
    const schedule = weeklySchedule.value;
    weekDays.forEach(day => {
        Object.keys(schedule[day] || {}).forEach(h => {
             // ensure it has items
             if (schedule[day][h] && schedule[day][h].length > 0) {
                 hours.add(parseInt(h, 10));
             }
        });
    });
    return Array.from(hours).sort((a, b) => a - b);
});

const fetchMedications = async () => {
    if (!familyId.value) return;
    try {
        const response = await api.get(`/families/${familyId.value}/medications`);
        medications.value = response.data;
    } catch (error) {
        console.error('Failed to fetch medications:', error);
        await modalStore.openAlert('복약 정보를 불러오는데 실패했습니다.');
    } finally {
        isLoading.value = false;
    }
};

const goBack = () => {
    router.go(-1);
};

const goToDetail = (id) => {
    router.push({ name: 'MedicationDetail', params: { familyId: familyId.value, medicationId: id } });
};

const openAddModal = () => {
    isModalOpen.value = true;
};

const closeModal = () => {
    isModalOpen.value = false;
};

const handleAddMedication = async (medData) => {
    try {
        const payload = [{
            medicineName: medData.medicineName,
            cycleType: medData.cycleType,
            totalDosesDay: medData.notificationTimes.length,
            cycleValue: medData.cycleValue,
            daysOfWeek: medData.daysOfWeek,
            startDate: medData.startDate,
            endDate: medData.endDate,
            notificationTimes: medData.notificationTimes
        }];

        await api.post(`/families/${familyId.value}/medications`, payload);
        await modalStore.openAlert('추가되었습니다.');

        closeModal();
        fetchMedications(); // Refresh list
    } catch (error) {
        console.error('Failed to add medication:', error);
        await modalStore.openAlert('추가에 실패했습니다.');
    }
};

onMounted(() => {
    // Sync if needed
    if (!familyId.value && selectedFamily.value) {
        familyId.value = selectedFamily.value.id;
        router.replace({ name: 'MedicationList', params: { familyId: familyId.value } });
    }

    if (familyId.value) {
        fetchMedications();
    }
});

// Watch Route Changes
watch(() => route.params.familyId, (newId) => {
    if (newId && newId !== familyId.value) {
        familyId.value = newId;
        fetchMedications();
    }
});

// Watch Store Changes (Group Selector)
watch(selectedFamily, (newFamily) => {
    if (newFamily && newFamily.id) {
        if (String(newFamily.id) !== String(route.params.familyId)) {
            router.replace({ name: 'MedicationList', params: { familyId: newFamily.id } });
        }
    }
});

// Utilities
const getCycleLabel = (type) => {
    const map = { 'DAILY': '매일', 'WEEKLY': '매주' };
    return map[type] || type;
};
</script>
