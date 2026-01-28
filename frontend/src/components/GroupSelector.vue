<template>
  <div class="relative">
    <transition name="dropdown">
      <div
        v-if="isOpen"
        class="eeum-dropdown left-0 origin-top-left"
      >
        <ul class="py-1">
          <li
            v-for="group in families"
            :key="group.id"
            class="eeum-dropdown-item"
            @click="selectGroup(group)"
          >
            {{ group.name }}
          </li>

          <li class="eeum-dropdown-divider mt-1 pt-1">
            <div
              class="eeum-dropdown-action"
              @click="requestAddGroup"
            >
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
import { useFamilyStore } from '@/stores/family';
import { storeToRefs } from 'pinia';

const familyStore = useFamilyStore();
const { families, selectedFamily } = storeToRefs(familyStore);
const isOpen = ref(false);
const emit = defineEmits(['add-group-request']); // Removed group-selected

const selectGroup = (group) => {
  familyStore.selectFamily(group);
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

const fetchGroups = async () => {
  await familyStore.fetchFamilies();
}

defineExpose({
  open,
  close,
  toggle,
  fetchGroups
});

onMounted(() => {
  // Fetch only if empty or just simply refresh? 
  // Probably safe to fetch to update list, but store logic handles preservation of selection.
  fetchGroups();
});
</script>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease-out;
  transform-origin: top left;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.dropdown-enter-to,
.dropdown-leave-from {
  opacity: 1;
  transform: scale(1);
}
</style>

