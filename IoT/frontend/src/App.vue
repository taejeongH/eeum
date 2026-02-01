<script setup>
</script>

<template>
  <div class="h-full bg-bg-page flex flex-col items-center justify-center gap-8 p-4">
    <div class="text-center space-y-4">
      <h1 class="eeum-title text-4xl">IoT Device Setup</h1>
      <p class="eeum-sub text-lg">Checking Fonts & Colors</p>
    </div>
    
    <div class="w-full max-w-md space-y-6">
      <!-- Color Checks -->
      <div class="p-6 rounded-2xl bg-primary shadow-lg text-white">
        <h2 class="text-xl font-bold">Primary Color</h2>
        <p class="opacity-90">#f3532b</p>
      </div>

      <div class="p-6 rounded-2xl bg-emergency shadow-lg text-white">
        <h2 class="text-xl font-bold">Emergency Color</h2>
        <p class="opacity-90">#dc2626</p>
      </div>

      <!-- Surface/Text Checks -->
      <div class="p-6 rounded-2xl bg-white border border-border-default shadow-sm">
        <h2 class="text-xl font-bold text-text-title">Text Title</h2>
        <p class="text-text-body mt-2">Text Body content should be legible and use Pretendard.</p>
        <p class="text-text-sub mt-1 text-sm">Subtext color check.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
