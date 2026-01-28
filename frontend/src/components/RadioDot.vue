<template>
  <div
    class="w-5 h-5 rounded-full border flex items-center justify-center transition"
    :class="active
      ? 'border-[var(--color-primary)]'
      : 'border-gray-300'"
  >
    <div
      v-if="active"
      class="w-3 h-3 rounded-full bg-[var(--color-primary)] transition-transform scale-100"
    />
  </div>
</template>

<script setup>
defineProps({
  active: {
    type: Boolean,
    default: false,
  },
})
</script>
