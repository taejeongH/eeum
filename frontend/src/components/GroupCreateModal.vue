  <Teleport to="body">
    <div v-if="show" class="fixed inset-0 z-[60] overflow-y-auto bg-black bg-opacity-50" @click.self="close">
      <div class="flex min-h-full items-center justify-center p-4">
        <div class="relative bg-white rounded-lg shadow-xl w-full max-w-sm">
          <div class="flex justify-between items-center border-b p-4">
            <h2 class="text-lg font-semibold">새 그룹 생성</h2>
            <button @click="close" class="text-gray-500 hover:text-gray-800">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
            </button>
          </div>
          <div class="p-4 space-y-4">
            <div>
              <label for="group-name" class="block text-sm font-medium text-gray-700">그룹 이름</label>
              <input type="text" id="group-name" v-model="groupName" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-orange-500 focus:border-orange-500 sm:text-sm">
            </div>
            <div>
              <label for="relationship" class="block text-sm font-medium text-gray-700">대표자와의 관계</label>
              <input type="text" id="relationship" v-model="relationship" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-orange-500 focus:border-orange-500 sm:text-sm" placeholder="예: 아들, 딸, 배우자">
            </div>
          </div>
          <div class="flex justify-end items-center border-t p-4 space-x-2">
            <button @click="close" class="px-4 py-2 bg-gray-200 text-gray-800 rounded-md hover:bg-gray-300">취소</button>
            <button @click="handleCreate" class="px-4 py-2 bg-orange-500 text-white rounded-md hover:bg-orange-600">생성</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

<script setup>
import { ref, defineProps, defineEmits } from 'vue';

defineProps({
  show: {
    type: Boolean,
    required: true,
  }
});

const emit = defineEmits(['close', 'create-group']);

const groupName = ref('');
const relationship = ref('');

const close = () => {
  emit('close');
};

const handleCreate = () => {
  if (!groupName.value || !relationship.value) {
    alert('그룹 이름과 관계를 모두 입력해주세요.');
    return;
  }
  emit('create-group', { name: groupName.value, relationship: relationship.value });
};
</script>
