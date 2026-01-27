<template>
  <header class="flex justify-between items-center px-7 py-6 bg-white sticky top-0 z-10 transition-all duration-300 shadow-sm" style="height: 5rem;">
    <!-- Family Selector -->
    <div class="relative">
      <button class="flex items-center space-x-2 border border-gray-200 rounded-full py-2.5 px-5 bg-white shadow-sm active:bg-gray-50 transition-colors">
        <span class="text-base font-bold text-gray-800">우리 가족</span>
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-[#e76f51]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
    </div>

    <!-- Logo -->
    <h1 class="text-3xl font-black text-gray-900 tracking-tighter absolute left-1/2 transform -translate-x-1/2">
      이음
    </h1>

    <!-- Actions -->
    <div class="flex items-center space-x-5">
      <!-- Notification -->
      <button class="relative text-gray-800 p-2 rounded-full hover:bg-gray-100 transition">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="black" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
        <span class="absolute top-2 right-2 block h-3 w-3 rounded-full bg-red-500 ring-2 ring-white"></span>
      </button>

      <!-- Settings Dropdown -->
      <div class="relative">
        <button 
          @click="isSettingsOpen = !isSettingsOpen"
          class="text-gray-800 p-2 rounded-full hover:bg-gray-100 transition"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="black" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        </button>

        <!-- Dropdown Menu -->
        <div v-if="isSettingsOpen" class="eeum-dropdown right-0 origin-top-right">
          <div class="py-1">
             <div class="eeum-dropdown-item font-medium">Edit Profile</div>
             <div class="eeum-dropdown-item font-medium">App Settings</div>
             <div class="eeum-dropdown-item font-medium text-red-600" @click="$router.push('/emergency')">Simulate Emergency</div>
             <div class="eeum-dropdown-divider"></div>
             <div class="eeum-dropdown-action text-red-500 hover:bg-red-50">Log Out</div>
          </div>
        </div>
      </div>

    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue';

const isSettingsOpen = ref(false);
</script>
