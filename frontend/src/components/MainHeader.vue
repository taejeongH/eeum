<template>
  <header class="bg-white px-4 pt-4 pb-3 shadow-sm">
    <!-- 상단 네비 -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex-shrink-0 w-32 flex justify-start">
        <div class="relative">
          <div @click="toggleGroupSelector" class="flex items-center gap-1 text-sm font-medium text-gray-600 cursor-pointer w-32">
            <span class="truncate">{{ selectedGroup ? selectedGroup.name : 'Select Group' }}</span>
            <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </div>
          <GroupSelector ref="groupSelectorRef" @group-selected="handleGroupSelected" @add-group-request="openAddGroupModal" />
        </div>
      </div>
      <div class="flex-1 text-center">
        <h1 class="text-lg font-semibold">Eeum</h1>
      </div>
      <div class="flex-shrink-0 w-32 flex justify-end items-center">
        <!-- Settings Dropdown -->
        <div class="relative" ref="settingsMenu">
            <div @click="toggleSettings" class="cursor-pointer">
                <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path d="M12 15.5A3.5 3.5 0 1 0 12 8.5a3.5 3.5 0 0 0 0 7z" />
                    <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 0 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.2a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 0 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.2a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 0 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.2a1.7 1.7 0 0 0 1 1.5h.1a1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 0 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.2a1.7 1.7 0 0 0-1.4 1z" />
                </svg>
            </div>
            <SettingsDropdown 
                :show="isSettingsOpen" 
                :family-id="selectedGroup?.id"
                @leave-group="leaveGroup"
            />
        </div>
      </div>
    </div>

    <!-- 가족 프로필 리스트 -->
    <div class="flex items-end justify-between pt-4 pb-2">
      <!-- 왼쪽: 고정된 피부양자 + 스크롤 가능한 멤버 목록 -->
      <div class="flex items-end gap-4 flex-grow min-w-0">
        <!-- 고정된 피부양자/플레이스홀더 -->
        <div class="flex-shrink-0 text-center min-w-[80px] h-28">
            <router-link v-if="dependentOrPlaceholder?.isPlaceholder && selectedGroup" :to="{ name: 'GroupEdit', params: { familyId: selectedGroup.id }, query: { groupName: selectedGroup.name } }" class="flex flex-col items-center justify-end h-full">
                <div class="rounded-full flex items-center justify-center bg-primary/10 text-primary w-20 h-20 ring-1 ring-primary/20 hover:bg-primary/20">
                <svg class="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v3m0 0v3m0-3h3m-3 0H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                </div>
                <span class="mt-2 text-base font-bold h-8 text-gray-700">{{ dependentOrPlaceholder.name }}</span>
            </router-link>
            <router-link v-else-if="dependentOrPlaceholder" :to="{ name: 'MemberDetail', params: { familyId: selectedGroup.id, userId: dependentOrPlaceholder.userId } }" class="flex flex-col items-center justify-end h-full text-gray-700">
                <div class="rounded-full cursor-pointer" :class="[selectedId === dependentOrPlaceholder.userId ? 'ring-2 ring-orange-400' : 'ring-1 ring-gray-200', 'w-20 h-20']">
                    <img :src="dependentOrPlaceholder.profileImage || '/default-profile.png'" alt="Profile" class="w-full h-full rounded-full object-cover" />
                </div>
                <span class="mt-2 text-base font-bold h-8">{{ dependentOrPlaceholder.name }}</span>
            </router-link>
        </div>

        <!-- 스크롤 가능한 나머지 멤버 -->
        <div class="flex items-end gap-4 overflow-x-auto hide-scrollbar">
          <template v-for="member in otherMembers" :key="member.userId">
            <router-link :to="{ name: 'MemberDetail', params: { familyId: selectedGroup.id, userId: member.userId } }" class="flex flex-col items-center flex-shrink-0 text-center min-w-[40px] h-28 justify-end text-gray-700">
              <div class="rounded-full cursor-pointer w-10 h-10" :class="[selectedId === member.userId ? 'ring-2 ring-orange-400' : 'ring-1 ring-gray-200']">
                <img :src="member.profileImage || '/default-profile.png'" alt="Profile" class="w-full h-full rounded-full object-cover" />
              </div>
              <span class="mt-2 text-xs h-8">{{ member.name }}</span>
            </router-link>
          </template>
        </div>
      </div>

      <!-- Add 버튼 (오른쪽 고정) -->
      <div @click="openInviteModal" class="flex-shrink-0 pl-4 h-28 flex flex-col justify-end items-center text-center cursor-pointer">
        <div class="w-10 h-10 rounded-full border-2 border-dashed border-gray-300 flex items-center justify-center text-gray-400 cursor-pointer">
          +
        </div>
        <span class="mt-2 text-xs text-gray-500 h-8">Add</span>
      </div>
    </div>
  </header>

  <GroupCreateModal :show="isGroupCreateModalOpen" @close="closeGroupCreateModal" @create-group="handleCreateGroup" />
  <InviteCodeModal v-if="selectedGroup" :show="isInviteModalOpen" :family-id="selectedGroup.id" @close="closeInviteModal" />
  <AddGroupModal 
    :show="isAddGroupModalOpen" 
    @close="closeAddGroupModal" 
    @join-group="joinGroup"
    @create-group-request="openGroupCreateModal" 
  />
</template>

<script setup>
import { ref, watch, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import api, { joinFamilyWithCode } from '@/services/api';
import GroupSelector from './GroupSelector.vue';
import GroupCreateModal from './GroupCreateModal.vue';
import InviteCodeModal from './InviteCodeModal.vue';
import SettingsDropdown from './SettingsDropdown.vue';
import AddGroupModal from './AddGroupModal.vue';

const router = useRouter();
const groupSelectorRef = ref(null);
const selectedGroup = ref(null);
const members = ref([]);
const selectedId = ref(null);
const isGroupCreateModalOpen = ref(false);
const isInviteModalOpen = ref(false);
const isSettingsOpen = ref(false);
const isAddGroupModalOpen = ref(false);
const settingsMenu = ref(null);

const dependentOrPlaceholder = computed(() => members.value.find(m => m.dependent || m.isPlaceholder));
const otherMembers = computed(() => members.value.filter(m => !m.dependent && !m.isPlaceholder));

const openGroupCreateModal = () => isGroupCreateModalOpen.value = true;
const closeGroupCreateModal = () => isGroupCreateModalOpen.value = false;

const openAddGroupModal = () => {
  isAddGroupModalOpen.value = true;
};
const closeAddGroupModal = () => {
  isAddGroupModalOpen.value = false;
};

const openInviteModal = () => {
  if (!selectedGroup.value || !selectedGroup.value.id) {
    alert('그룹을 먼저 선택해주세요.');
    return;
  }
  isInviteModalOpen.value = true;
};
const closeInviteModal = () => isInviteModalOpen.value = false;

const toggleSettings = () => {
  isSettingsOpen.value = !isSettingsOpen.value;
};

const closeSettingsMenu = (event) => {
    if (settingsMenu.value && !settingsMenu.value.contains(event.target)) {
        isSettingsOpen.value = false;
    }
}

onMounted(() => {
    document.addEventListener('click', closeSettingsMenu);
});

onUnmounted(() => {
    document.removeEventListener('click', closeSettingsMenu);
});

const goToGroupEdit = () => {
  if (!selectedGroup.value) return;
  router.push({ name: 'GroupEdit', params: { familyId: selectedGroup.value.id }, query: { groupName: selectedGroup.value.name } });
  isSettingsOpen.value = false;
};

const leaveGroup = async () => {
  if (!selectedGroup.value) return;
  if (confirm(`'${selectedGroup.value.name}' 그룹을 정말로 탈퇴하시겠습니까? 대표자일 경우 그룹이 삭제됩니다.`)) {
    try {
      await api.delete(`/families/${selectedGroup.value.id}/leave`);
      alert('그룹에서 성공적으로 탈퇴/삭제되었습니다.');
      window.location.reload();
    } catch (error) {
      console.error('Failed to leave group:', error);
      alert('그룹 탈퇴/삭제에 실패했습니다.');
    }
  }
  isSettingsOpen.value = false;
};

const handleCreateGroup = async (groupData) => {
  try {
    const response = await api.post('/families', groupData);
    groupSelectorRef.value?.fetchGroups();
    handleGroupSelected(response.data);
    closeGroupCreateModal();
    alert('새로운 그룹이 생성되었습니다.');
  } catch (error) {
    console.error('Failed to create group:', error);
    alert('그룹 생성에 실패했습니다.');
  }
};


const joinGroup = async (inviteCode) => {
  try {
    const response = await joinFamilyWithCode(inviteCode);
    groupSelectorRef.value?.fetchGroups();
    handleGroupSelected(response.data);
    alert('그룹에 성공적으로 참여했습니다!');
  } catch (error) {
    console.error('Failed to join group:', error);
    let errorMessage = '그룹 참여 중 오류가 발생했습니다.';
    if (error.response && error.response.data && error.response.data.message) {
        errorMessage = error.response.data.message;
    }
    alert(errorMessage);
  }
};

const toggleGroupSelector = () => groupSelectorRef.value?.toggle();
const handleGroupSelected = (group) => {
  selectedGroup.value = group;
};

const fetchMembers = async () => {
  if (!selectedGroup.value) {
    members.value = [];
    return;
  }
  try {
    const response = await api.get(`/families/${selectedGroup.value.id}/members`);
    let fetchedMembers = response.data;
    const dependent = fetchedMembers.find(m => m.dependent);
    if (dependent) {
      fetchedMembers.sort((a, b) => b.dependent - a.dependent);
    } else {
      fetchedMembers.unshift({ userId: 'add-dependent', name: '피부양자 설정', isPlaceholder: true });
    }
    members.value = fetchedMembers;
    const firstActualMember = members.value.find(m => !m.isPlaceholder);
    if (firstActualMember) {
      selectedId.value = firstActualMember.userId;
    } else {
      selectedId.value = null;
    }
  } catch (error) {
    console.error(`Failed to fetch members for familyId ${selectedGroup.value.id}:`, error);
    members.value = [];
  }
};

watch(selectedGroup, fetchMembers, { immediate: true });
</script>