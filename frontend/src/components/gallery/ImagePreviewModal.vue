<template>
  <div v-if="isOpen" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm" @click.self="cancel">
    <div class="bg-white rounded-3xl w-full max-w-md overflow-hidden shadow-2xl transform transition-all scale-100">
      
      <!-- Header -->
      <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
        <h3 class="text-lg font-bold text-[#1c140d]">사진 업로드</h3>
        <button @click="cancel" class="p-2 -mr-2 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-50 transition-colors">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-5">
        <!-- Image Preview -->
        <div class="relative w-full aspect-square bg-gray-50 rounded-2xl overflow-hidden border border-gray-100 shadow-inner">
          <img :src="imageSrc" alt="Preview" class="w-full h-full object-contain" />
        </div>

        <!-- Description Input -->
        <div class="space-y-2">
          <label class="text-sm font-bold text-[#1c140d] ml-1">설명 (선택)</label>
          <textarea 
            v-model="description" 
            placeholder="이 사진에 대한 이야기를 남겨주세요..." 
            class="w-full px-4 py-3 bg-gray-50 border border-transparent rounded-xl focus:bg-white focus:border-[#9c7349] focus:ring-2 focus:ring-[#9c7349]/20 transition-all resize-none text-[#1c140d] placeholder-gray-400 text-sm outline-none"
            rows="3"
          ></textarea>
        </div>
      </div>

      <!-- Footer -->
      <div class="px-6 py-4 bg-gray-50 border-t border-gray-100 flex gap-3">
        <button 
          @click="cancel" 
          class="flex-1 px-4 py-3 rounded-xl text-sm font-bold text-gray-600 bg-white border border-gray-200 hover:bg-gray-50 transition-colors"
        >
          취소
        </button>
        <button 
          @click="confirm" 
          class="flex-1 px-4 py-3 rounded-xl text-sm font-bold text-white bg-primary hover:bg-[#8a6540] shadow-lg shadow-primary/20 transition-all active:scale-[0.98]"
        >
          업로드하기
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  },
  imageSrc: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['close', 'confirm']);

const description = ref('');

// Reset description when modal opens
watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    description.value = '';
  }
});

const cancel = () => {
  emit('close');
};

const confirm = () => {
  emit('confirm', description.value);
};
</script>

<style scoped>
.material-symbols-outlined {
    font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
