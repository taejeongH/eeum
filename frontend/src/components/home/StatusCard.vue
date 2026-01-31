<template>
  <div class="px-7 mb-8">
    <!-- Main Card -->
    <div class="bg-[#e76f51] rounded-2xl p-6 text-white relative shadow-lg overflow-hidden transform transition hover:scale-[1.01]">
        <!-- Floating Heart Decoration -->
        <div class="absolute top-4 right-4 animate-pulse">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-white opacity-80" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" clip-rule="evenodd" />
            </svg>
        </div>
      
      <!-- Pill Shape Tag -->
      <div class="bg-white/20 backdrop-blur-sm px-3 py-1 rounded-full inline-block mb-3">
        <span class="text-xs font-semibold text-white tracking-wide">Daily Check</span>
      </div>

      <h2 class="text-2xl font-bold mb-2">할머니는 오늘 건강해요.</h2>
      <p class="text-white/90 text-sm mb-6 leading-relaxed">
        All medications have been taken on schedule.
      </p>

      <div class="flex justify-between items-center border-t border-white/20 pt-4 mt-2">
        <div class="flex items-center text-xs text-white/90 font-medium">
          <span class="w-2 h-2 rounded-full bg-green-400 mr-2 shadow-[0_0_8px_rgba(74,222,128,0.6)]"></span>
          Last checked: 10 mins ago
        </div>
        <button @click="$router.push({ name: 'HealthDetail' })" class="bg-[#d65a3d] hover:bg-[#c34e32] active:bg-[#b0422a] text-white text-xs font-bold py-2 px-4 rounded-lg shadow-md transition-colors">
          상세 보기
        </button>
      </div>
    </div>

  </div>
</template>

<script setup>
</script>
