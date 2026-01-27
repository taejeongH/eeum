<template>
  <transition name="dropdown">
    <div v-if="show" class="absolute right-0 mt-2 w-36 bg-white rounded-md shadow-lg z-20 origin-top-right">
      <div class="py-1">
        <router-link
          :to="{ name: 'GroupEditStep1', params: { familyId } }"
          class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 text-center"
        >
          그룹 설정
        </router-link>

        <a href="#" @click.prevent="emit('leave-group')" class="block px-4 py-2 text-sm text-red-600 hover:bg-gray-100 text-center">그룹 탈퇴</a>
      </div>
    </div>
  </transition>
</template>

<script setup>
const props = defineProps({
  show: {
    type: Boolean,
    required: true,
  },
  familyId: {
    type: Number,
    required: false,
  },
});

const emit = defineEmits(['leave-group']);
</script>

<style scoped>
.dropdown-enter-active, .dropdown-leave-active {
  transition: all 0.2s ease-out;
  transform-origin: top right;
}

.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.dropdown-enter-to, .dropdown-leave-from {
  opacity: 1;
  transform: scale(1);
}
</style>