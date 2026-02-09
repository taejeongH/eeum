<template>
  <div class="relative">
    <!-- Selected -->
    <button
      @click="toggle"
      class="eeum-input flex items-center justify-between"
    >
      <div class="flex items-center gap-3">
        <img
          v-if="selected"
          :src="selected.profileImage || '/default-profile.png'"
          class="w-8 h-8 rounded-full object-cover"
        />
        <span class="text-sm">
          {{ selected ? selected.name : '피부양자를 선택해주세요' }}
        </span>
      </div>
      <span class="text-gray-400">▾</span>
    </button>

    <!-- Dropdown -->
    <transition name="fade-slide">
      <ul
        v-if="open"
        class="absolute z-10 mt-2 w-full bg-white rounded-xl shadow-lg border overflow-hidden"
      >
        <li
          v-for="member in members"
          :key="member.userId"
          @click="select(member)"
          class="flex items-center gap-3 px-4 py-3 text-sm cursor-pointer hover:bg-gray-50"
        >
          <img
            :src="member.profileImage || '/default-profile.png'"
            class="w-8 h-8 rounded-full object-cover"
          />
          <span class="flex-1">{{ member.name }}</span>
          <span v-if="member.userId === modelValue" class="text-[var(--color-primary)]">✔</span>
        </li>
      </ul>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  members: Array,          
  modelValue: [String, Number],
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)

const selected = computed(() =>
  props.members.find(m => m.userId === props.modelValue)
)

const toggle = () => (open.value = !open.value)

const select = (member) => {
  emit('update:modelValue', member.userId)
  open.value = false
}
</script>

<style scoped>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.2s ease;
}
.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
