<template>
  <div class="px-6 mb-10">
    <!-- Compact Health Summary Card -->
    <div 
      @click="goToReport"
      class="relative bg-gradient-to-br from-[#e76f51] to-[#ff7e5f] rounded-3xl p-5 text-white shadow-lg overflow-hidden active:scale-[0.98] transition-all cursor-pointer group"
    >
      <!-- Subtle Pattern Background -->
      <div class="absolute -top-12 -right-12 w-24 h-24 bg-white/10 rounded-full blur-2xl"></div>
      
      <!-- Card Header -->
      <div class="flex items-center gap-2 mb-3 opacity-90">
        <span class="material-symbols-outlined text-[14px]">analytics</span>
        <span class="text-[10px] font-bold tracking-wider uppercase">오늘의 건강 요약</span>
      </div>
 
      <!-- Main Status -->
      <div class="mb-5">
        <p class="text-white/80 text-[11px] font-medium mb-1">{{ dependentName || '할머니' }}님의 상태</p>
        <h2 class="text-xl font-black leading-tight tracking-tight">
          "{{ healthStore.currentReport?.summary || '데이터 분석 중...' }}"
        </h2>
      </div>
 
      <!-- Card Footer -->
      <div class="flex items-center justify-between border-t border-white/20 pt-4">
        <div class="flex items-center gap-2 text-[10px] font-bold">
          <div class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></div>
          {{ lastSyncTime }}
        </div>
        <div class="flex items-center bg-white/10 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/10 group-hover:bg-white/20 transition-all duration-300 leading-none -mr-1">
          <span class="text-[11px] font-black tracking-tight mt-[0.5px]">GMS 상세 분석</span>
          <span class="material-symbols-outlined text-[16px] ml-1 -mr-1 opacity-80" style="font-variation-settings: 'wght' 500">chevron_right</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useFamilyStore } from '@/stores/family';
import { useHealthStore } from '@/stores/health';
import api from '@/services/api';

const router = useRouter();
const familyStore = useFamilyStore();
const healthStore = useHealthStore();

const dependentName = ref('');

const lastSyncTime = computed(() => {
    const metric = healthStore.latestMetrics;
    const time = metric?.updatedAt || metric?.createdAt || metric?.recordDate;
    if (!time) return '최근 데이터 없음';
    
    try {
        const date = new Date(time);
        return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) + ' 업데이트';
    } catch (e) {
        return '방금 전 업데이트';
    }
});

const fetchData = async () => {
    try {
        await familyStore.fetchFamilies(); // Already cached
        const familyId = familyStore.selectedFamily?.id;
        if (!familyId) return;

        // Use cached members from store
        const members = await familyStore.fetchMembers(familyId);
        
        // 1. Try to find dependent from family list first (if DTO has it)
        const currentFamily = familyStore.families.find(f => f.id === familyId);
        if (currentFamily?.dependentName) {
            dependentName.value = currentFamily.dependentName;
        } else {
            // 2. Fallback: Find dependent from member list
            const dependent = members.find(m => m.dependent || m.isDependent);
            if (dependent) {
                dependentName.value = dependent.name;
            }
        }
    } catch (e) {
        console.error("Failed to fetch data:", e);
    }
};

onMounted(() => {
    fetchData();
});

const goToReport = () => {
    router.push({ path: '/health-detail', query: { scrollTo: 'analysis' } });
};
</script>

<style scoped>
</style>
