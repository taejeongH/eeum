<template>
  <div
    class="w-11 h-11 rounded-full flex items-center justify-center transition-colors"
    :class="active
      ? 'bg-[var(--color-primary)] text-white'
      : 'bg-gray-100 text-[var(--color-primary)]'"
  >
    <slot />
  </div>
</template>

<script setup>
defineProps({
  active: Boolean,
})
</script>
