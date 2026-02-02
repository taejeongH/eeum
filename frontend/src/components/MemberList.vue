<template>
  <div class="flex overflow-x-auto p-4 space-x-4">
    <div v-if="members.length > 0" class="flex">
      <div v-for="(member, index) in visibleMembers" :key="member.userId"
           :class="{'flex-none flex flex-col items-center justify-end text-center rounded-lg': true,
                      'w-32': true}">
        <img :src="member.profileImage || '/default-profile.png'" alt="Profile"
             :class="{'rounded-full object-cover border-2 border-primary': true,
                        'w-24 h-24': true}" />
        <p :class="{'font-semibold truncate mt-2 text-base': true}">
          {{ member.name }}
        </p>
        <p :class="{'text-sm text-gray-500 mt-1': true}">
          {{ member.dependent ? '피부양자' : '부양자' }}
        </p>
      </div>
      <div v-if="members.length > maxVisibleMembers"
           class="flex-none flex flex-col items-center justify-center text-center rounded-lg w-32 cursor-pointer" <!-- Universal larger size -->
           @click="showAllMembers">
        <div class="rounded-full bg-gray-200 text-gray-700 flex items-center justify-center w-24 h-24 text-base font-bold border-2 border-gray-400">
          More
        </div>
        <p class="font-semibold truncate mt-2 text-base">더보기</p>
      </div>
    </div>
    <div v-else class="flex-none flex items-center justify-center w-full p-4 text-gray-500">
      <p>선택된 그룹의 멤버가 없습니다.</p>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import api from '@/services/api';
import { useModalStore } from '@/stores/modal';

const modalStore = useModalStore();

const props = defineProps({
  familyId: {
    type: Number,
    required: true,
  },
});

const members = ref([]);
const maxVisibleMembers = 5; // 최대 표시 멤버 수

// "More" 버튼을 위해 표시될 멤버 목록
const visibleMembers = computed(() => {
  if (members.value.length <= maxVisibleMembers) {
    return members.value;
  }
  return members.value.slice(0, maxVisibleMembers);
});

const fetchMembers = async (id) => {
  if (!id) {
    members.value = [];
    return;
  }
  try {
    const response = await api.get(`/families/${id}/members`);
    members.value = response.data;

  } catch (error) {
    console.error(`Failed to fetch members for familyId ${id}:`, error);
    members.value = [];
  }
};

const showAllMembers = () => {
  // TODO: 여기에 모든 멤버를 보여주는 로직 (예: 모달 열기 또는 다른 페이지로 이동) 구현
  modalStore.openAlert('더보기 버튼 클릭 - 모든 멤버 보기 기능 구현 예정');
};


watch(() => props.familyId, (newFamilyId) => {
  fetchMembers(newFamilyId);
}, { immediate: true });

</script>
