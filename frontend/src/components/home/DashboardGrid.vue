<template>
  <div class="px-5 grid grid-cols-2 gap-4 mb-24"> <!-- mb-24 for taller bottom nav -->
    
    <div 
      v-for="(item, index) in items" 
      :key="item.id"
      class="bg-white rounded-2xl p-4 shadow-sm border border-gray-100 flex flex-col items-start transition-all duration-200 select-none relative cursor-pointer hover:shadow-md"
      :class="{'scale-105 shadow-xl z-10': draggedIndex === index}"
      @touchstart="startDrag(index)"
      @touchend="endDrag"
      @mousedown="startDrag(index)" 
      @mouseup="endDrag"
      @mouseleave="endDrag"
      @click="handleCardClick(item)"
    >
      <div :class="`p-3 rounded-full mb-3 ${item.bgClass}`">
        <component :is="item.icon" class="h-6 w-6" :class="item.iconClass" />
      </div>
      <h3 class="text-gray-900 font-bold text-base mb-1">{{ item.title }}</h3>
      <p :class="`text-xs ${item.textClass}`">{{ item.desc }}</p>
      
      <!-- Drag Indicator -->
      <div v-if="draggedIndex === index" class="absolute top-2 right-2 text-gray-300">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M5 4a2 2 0 110-4 2 2 0 010 4zm10 0a2 2 0 110-4 2 2 0 010 4zM5 10a2 2 0 110-4 2 2 0 010 4zm10 0a2 2 0 110-4 2 2 0 010 4zM5 16a2 2 0 110-4 2 2 0 010 4zm10 0a2 2 0 110-4 2 2 0 010 4z" /></svg>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useModalStore } from '@/stores/modal';
import { useFamilyStore } from '@/stores/family';


const modalStore = useModalStore();
const familyStore = useFamilyStore();


// Icons as mock components for simplicity in this snippet, 
// normally would import or use inline SVG efficiently
const MedicationIcon = { template: `<div class="w-6 h-6 rounded-full bg-[#ffe0b2]"></div>` };
const ActivityIcon = { template: `<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>` };
const VitalsIcon = { template: `<div class="w-6 h-6 rounded-full bg-[#b2dfdb]"></div>` }; // Placeholder 
const FamilyIcon = { template: `<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" /></svg>` };

const items = ref([
  { 
    id: 1, 
    title: 'Medication', 
    desc: 'Next: 2 PM', 
    bgClass: 'bg-[#fff3e0]', 
    iconClass: '', 
    textClass: 'text-gray-500', 
    icon: MedicationIcon, 
    route: 'medication' 
  },
  { id: 2, title: 'Activity', desc: '1,200 Steps', bgClass: 'bg-[#e3f2fd]', iconClass: 'text-[#1e88e5]', textClass: 'text-[#e76f51] font-semibold', icon: ActivityIcon },
  { id: 3, title: 'Vitals', desc: 'Normal', bgClass: 'bg-[#e0f2f1]', iconClass: '', textClass: 'text-gray-800 font-medium', icon: VitalsIcon },
  { id: 4, title: 'Family', desc: '3 members active', bgClass: 'bg-[#f3e5f5]', iconClass: 'text-[#8e24aa]', textClass: 'text-[#e76f51] font-semibold', icon: FamilyIcon },
]);

const draggedIndex = ref(null);
let longPressTimer = null;

const handleCardClick = (item) => {
  if (item.route === 'medication') {
    // Get familyId from store
    const familyId = familyStore.selectedFamily?.id;
    
    if (familyId) {
      router.push(`/families/${familyId}/medications`);
    } else {
      console.error('No familyId found in user profile or storage');
      modalStore.openAlert('가족 정보를 찾을 수 없습니다.');
    }
  } else if (item.route) {
    router.push(item.route);
  }
};

const startDrag = (index) => {
  // Long press logic
  longPressTimer = setTimeout(() => {
    draggedIndex.value = index;
    // Haptic feedback if available (Mobile only)
    if (navigator.vibrate) navigator.vibrate(50);
  }, 500); // 500ms long press
};

const endDrag = () => {
  clearTimeout(longPressTimer);
  if (draggedIndex.value !== null) {
      // Mock swap logic: Move to end for demo purposes or just reset
      // To implement real swap, we need drop targets. 
      // For now, let's just "shake" or reset to show interaction.
      draggedIndex.value = null;
  }
};

onUnmounted(() => {
    clearTimeout(longPressTimer);
});
</script>
