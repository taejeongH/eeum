<template>
  <div class="relative">
    <transition name="dropdown">
      <div v-if="isOpen" class="absolute left-0 mt-2 w-48 bg-white rounded-md shadow-lg z-20 origin-top-left">
        <ul class="py-1">
          <li v-for="group in groups" :key="group.id"
              class="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 cursor-pointer truncate"
              @click="selectGroup(group)">
            {{ group.name }}
          </li>
          <li class="border-t mt-1 pt-1">
            <div @click="requestAddGroup" class="px-4 py-2 text-sm text-orange-500 font-semibold hover:bg-orange-50 cursor-pointer">
              + 그룹 추가
            </div>
          </li>
        </ul>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, defineEmits, defineExpose } from 'vue';
import api from '@/services/api';

const groups = ref([]);
const currentSelectedGroup = ref(null);
const isOpen = ref(false);
const emit = defineEmits(['group-selected', 'add-group-request']);

const fetchGroups = async () => {
  try {
    const response = await api.get('/families');
    groups.value = response.data;
    if (groups.value.length > 0 && !currentSelectedGroup.value) {
      selectGroup(groups.value[0]);
    }
  } catch (error) {
    console.error('Failed to fetch groups:', error);
  }
};

const selectGroup = (group) => {
  currentSelectedGroup.value = group;
  emit('group-selected', group);
  close();
};

const requestAddGroup = () => {
  close();
  emit('add-group-request');
};

const open = () => {
  isOpen.value = true;
};

const close = () => {
  isOpen.value = false;
};

const toggle = () => {
  isOpen.value = !isOpen.value;
}


defineExpose({
  open,
  close,
  toggle,
  fetchGroups
});

onMounted(() => {
  fetchGroups();
});
</script>

<style scoped>
.dropdown-enter-active, .dropdown-leave-active {
  transition: all 0.2s ease-out;
  transform-origin: top left;
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
