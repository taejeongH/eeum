<template>
  <div v-if="show" @click.self="close" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white rounded-lg shadow-xl w-full max-w-md m-4">
      <div class="flex justify-between items-center border-b p-4">
        <h2 class="text-lg font-semibold">전체 멤버</h2>
        <button @click="close" class="text-gray-500 hover:text-gray-800">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </button>
      </div>
      <div class="p-4 max-h-[60vh] overflow-y-auto">
        <div class="grid grid-cols-3 sm:grid-cols-4 gap-4">
          <div v-for="member in members.filter(m => !m.isPlaceholder)" :key="member.userId" class="flex flex-col items-center text-center">
            <img :src="member.profileImage || '/default-profile.png'" alt="Profile" class="w-20 h-20 rounded-full object-cover mb-2 ring-1 ring-gray-200" />
            <span class="text-sm font-medium truncate w-full">{{ member.name }}</span>
            <span v-if="member.dependent" class="text-xs text-orange-500">피부양자</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue';

defineProps({
  show: {
    type: Boolean,
    required: true,
  },
  members: {
    type: Array,
    required: true,
  }
});

const emit = defineEmits(['close']);

const close = () => {
  emit('close');
};
</script>
