<template>
  <div v-if="show" @click.self="close" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white rounded-lg shadow-xl w-full max-w-sm m-4">
      <div class="flex justify-between items-center border-b p-4">
        <h2 class="text-lg font-semibold">그룹 초대</h2>
        <button @click="close" class="text-gray-500 hover:text-gray-800">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </button>
      </div>
      <div class="p-6 text-center">
        <p class="text-sm text-gray-600 mb-4">
          <span class="font-semibold">{{ inviterName }}</span>님께서 당신을 
          <span class="font-semibold">{{ groupName }}</span> 그룹에 초대했습니다.
        </p>
        <p class="text-md font-medium text-gray-700">초대하시겠습니까?</p>
      </div>
      <div class="flex justify-center items-center border-t p-4 space-x-4">
        <button @click="confirm" class="px-4 py-2 bg-primary text-white rounded-md hover:bg-orange-600">수락</button>
        <button @click="close" class="px-4 py-2 bg-gray-200 text-gray-800 rounded-md hover:bg-gray-300">취소</button>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  show: {
    type: Boolean,
    required: true,
  },
  groupName: {
    type: String,
    required: true,
  },
  inviterName: {
    type: String,
    required: true,
  }
});

const emit = defineEmits(['close', 'confirm-join']);

const close = () => {
  emit('close');
};

const confirm = () => {
  emit('confirm-join');
  close();
};
</script>