<template>
  <div class="relative transition-all duration-300 ease-in-out" :class="isCollapsed ? 'mb-2' : 'mb-6'">
    
    <!-- Collapsible Wrapper -->
    <div 
      class="overflow-hidden transition-[max-height,opacity] duration-300 ease-in-out"
      :class="isCollapsed ? 'max-h-0 opacity-0' : 'max-h-48 opacity-100'"
    >
      <div class="px-7 pt-4 pb-2 flex items-center space-x-3">
        
        <!-- Fixed: Grandma (Sticky effect simulation) -->
        <div class="flex flex-col items-center flex-shrink-0 z-10 bg-[#fcfcfc] pr-2">
           <div class="relative p-1.5 rounded-full bg-[#e76f51] shadow-md">
             <img 
               :src="members[0].image" 
               alt="Grandma" 
               class="w-16 h-16 rounded-full border-[3px] border-white object-cover"
             />
           </div>
           <span class="mt-2 text-sm font-bold text-[#e76f51]">Grandma</span>
        </div>

        <!-- Scrollable List -->
        <div class="flex-1 overflow-x-auto scrollbar-hide flex space-x-6 items-center pl-2 py-2">
           <div v-for="(member, index) in members.slice(1)" :key="index" class="flex flex-col items-center flex-shrink-0">
            <div 
              class="relative p-1.5 rounded-full transition-transform active:scale-95" 
              :class="member.active ? 'bg-[#e76f51]' : 'bg-transparent'"
              @click="selectMember(index + 1)"
            >
              <img 
                :src="member.image" 
                alt="Profile" 
                class="w-16 h-16 rounded-full border-[3px] border-white object-cover opacity-90"
              />
            </div>
            <span class="mt-2 text-sm font-medium text-gray-700">{{ member.name }}</span>
          </div>

          <!-- Invite Button -->
          <div class="flex flex-col items-center flex-shrink-0">
             <button class="w-16 h-16 rounded-full border-[3px] border-dashed border-gray-300 flex items-center justify-center bg-gray-50 hover:bg-gray-100 transition">
               <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                 <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
               </svg>
             </button>
             <span class="mt-2 text-sm font-medium text-gray-500">Invite</span>
          </div>
        </div>

      </div>
    </div>

    <!-- Toggle Button -->
    <div class="flex justify-center -mt-2 relative z-0">
      <button 
        @click="isCollapsed = !isCollapsed"
        class="bg-white border border-gray-200 rounded-b-xl px-4 py-0.5 shadow-sm text-gray-400 hover:text-[#e76f51] transition"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 transform transition-transform duration-300" :class="isCollapsed ? 'rotate-180' : ''" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7" />
        </svg>
      </button>
    </div>

  </div>
</template>

<script setup>
import { ref } from 'vue';

const isCollapsed = ref(false);

const members = ref([
  { name: 'Grandma', image: 'https://i.pravatar.cc/150?u=grandma', active: true },
  { name: 'Me', image: 'https://i.pravatar.cc/150?u=me', active: false },
  { name: 'Dad', image: 'https://i.pravatar.cc/150?u=dad', active: false },
  { name: 'Mom', image: 'https://i.pravatar.cc/150?u=mom', active: false },
]);

const selectMember = (index) => {
    members.value.forEach((m, i) => m.active = i === index);
};
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
    display: none;
}
.scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
}
</style>
