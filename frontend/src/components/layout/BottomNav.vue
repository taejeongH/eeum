<template>
  <div class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 h-24 flex justify-around items-end pb-4 z-50">
    <!-- Message -->
    <button @click="setActive('message')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'message' ? 'text-[#e76f51]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
      </svg>
      <span class="text-xs font-semibold">Message</span>
    </button>
    
    <!-- Gallery -->
    <button @click="setActive('gallery')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'gallery' ? 'text-[#e76f51]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      <span class="text-xs font-semibold">Gallery</span>
    </button>

   <!-- Center Home Button (Floating Effect) -->
    <div class="relative w-1/5 flex justify-center">
      <button @click="setActive('home')" class="absolute -top-12 bg-[#e76f51] rounded-full p-5 shadow-xl flex items-center justify-center border-[5px] border-white active:scale-95 transition-transform duration-200">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-9 w-9 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
        </svg>
      </button>
      <span class="text-xs font-bold text-[#e76f51] mt-10">Home</span>
    </div>

    <!-- Calendar -->
    <button @click="setActive('calendar')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'calendar' ? 'text-[#e76f51]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      <span class="text-xs font-semibold">Calendar</span>
    </button>
    
    <!-- Menu -->
    <button @click="setActive('menu')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'menu' ? 'text-[#e76f51]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
      </svg>
      <span class="text-xs font-semibold">Menu</span>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const activeTab = ref('home');

const setActive = (tab) => {
  activeTab.value = tab;
  console.log(`Navigating to ${tab}...`);
  if (tab === 'menu') {
      // Temporary Entry Point for Voice Registration
      router.push('/voice-register');
  } else if (tab !== 'home') {
     // Mock navigation feedback
     // alert(`Navigating to ${tab}...`); 
  }
};
</script>
