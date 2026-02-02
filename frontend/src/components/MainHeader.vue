<template>
  <header class="bg-white px-6 pt-5 pb-3 shadow-sm relative z-30">
    <!-- 상단 네비게이션 영역 -->
    <div class="flex items-center justify-between mb-2">
      
      <!-- Group Selector (Left) -->
      <div class="flex-shrink-0 w-36 flex justify-start relative z-50">
        <div class="relative" ref="groupSelectorWrapper">
          <div 
            @click="toggleGroupSelector" 
            class="flex items-center gap-2 px-3 py-1.5 rounded-full bg-gray-50 hover:bg-gray-100 transition cursor-pointer group"
          >
            <span class="truncate text-sm font-bold text-gray-800 max-w-[80px]">
              {{ selectedGroup ? selectedGroup.name : '그룹 선택' }}
            </span>
            <svg class="w-3.5 h-3.5 text-gray-400 group-hover:text-gray-600 transition" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
          <GroupSelector ref="groupSelectorRef" @add-group-request="openAddGroupModal" />
        </div>
      </div>

      <!-- Title (Center) -->
      <div class="flex-1 flex flex-col items-center justify-center">
        <router-link to="/home" class="flex flex-col items-center group">
          <h1 class="text-2xl font-extrabold text-[var(--text-title)] tracking-tighter leading-none" style="font-family: 'Pretendard', sans-serif;">
            이음
          </h1>
        </router-link>
      </div>

       <!-- Settings (Right) -->
       <div class="flex-shrink-0 w-36 flex justify-end items-center relative z-50">
         <div class="relative" ref="settingsMenu">
             <button 
               @click="toggleSettings" 
               class="p-2 rounded-full hover:bg-gray-50/80 active:bg-gray-100 transition text-[var(--text-sub)] hover:text-[var(--text-body)] -mr-2"
             >
                 <!-- 점 3개 (더보기) 아이콘 -->
                 <svg class="w-6 h-6 " fill="none" stroke="currentColor" viewBox="0 0 24 24">
                     <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                 </svg>
             </button>
             <SettingsDropdown 
                 :show="isSettingsOpen" 
                 :family-id="selectedGroup?.id"
                 :is-representative="selectedGroup?.owner"
                 @leave-group="leaveGroup"
             />
         </div>
       </div>
    </div>
 
     <!-- Collapsible Wrapper -->
     <div 
       v-if="showProfiles"
       class="overflow-hidden transition-[max-height,opacity] duration-300 ease-in-out"
       :class="isCollapsed ? 'max-h-0 opacity-0' : 'max-h-48 opacity-100'"
     >
       <!-- 가족 프로필 리스트 -->
       <div class="flex items-end justify-between pt-2 pb-1 gap-2">
         <!-- 왼쪽: 고정된 피부양자/본인 + 스크롤 가능한 멤버 목록 -->
         <div class="flex items-end gap-2 flex-grow min-w-0">
           
           <!-- 고정된 피부양자/플레이스홀더 -->
           <div class="flex-shrink-0 text-center min-w-[72px] ml-0.5">
               <!-- 플레이스홀더 (피부양자 추가) -->
               <router-link 
                 v-if="dependentOrPlaceholder?.isPlaceholder && selectedGroup" 
                 :to="{ name: 'GroupEditStep1', params: { familyId: selectedGroup.id }, query: { groupName: selectedGroup.name } }" 
                 class="flex flex-col items-center gap-1.5 group"
               >
                   <div class="w-[68px] h-[68px] rounded-full flex items-center justify-center bg-[var(--color-primary-soft)] text-[var(--color-primary)] ring-2 ring-white shadow-sm group-hover:bg-orange-100 transition">
                     <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                         <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                     </svg>
                   </div>
                   <span class="text-xs font-semibold text-[var(--text-sub)] truncate w-full group-hover:text-[var(--color-primary)] transition">
                     {{ dependentOrPlaceholder.name }}
                   </span>
               </router-link>
   
               <!-- 실제 피부양자 -->
               <router-link 
                 v-else-if="dependentOrPlaceholder" 
                 :to="{ name: 'MemberDetail', params: { familyId: selectedGroup.id, userId: dependentOrPlaceholder.userId } }" 
                 class="flex flex-col items-center gap-1.5 group"
               >
                   <div 
                     class="relative w-[68px] h-[68px] rounded-full p-0.5 bg-white transition-all duration-300"
                     :class="selectedId === dependentOrPlaceholder.userId ? 'ring-2 ring-[var(--color-primary)] ring-offset-2 shadow-md' : 'ring-1 ring-gray-100 shadow-sm'"
                   >
                       <img :src="dependentOrPlaceholder.profileImage || '/default-profile.png'" alt="Profile" class="w-full h-full rounded-full object-cover" />
                       <div v-if="selectedId === dependentOrPlaceholder.userId" class="absolute inset-0 rounded-full border-2 border-white/20"></div>
                   </div>
                  <span 
                    class="text-xs font-semibold truncate w-full transition-colors"
                    :class="selectedId === dependentOrPlaceholder.userId ? 'text-[var(--text-title)]' : 'text-[var(--text-sub)]'"
                  >
                    {{ dependentOrPlaceholder.name }}
                  </span>
              </router-link>
          </div>
  
          <!-- 구분선 (선택사항, 멤버가 있을 때만 표시) -->
          <div v-if="otherMembers.length > 0" class="w-px h-8 bg-gray-100 mb-6 mx-0.5"></div>
  
          <!-- 스크롤 가능한 나머지 멤버 -->
          <div class="flex-1 flex items-end gap-2 overflow-x-auto hide-scrollbar pb-1 px-0.5 pt-2 min-w-0">
            <template v-for="member in otherMembers" :key="member.userId">
              <router-link 
                :to="{ name: 'MemberDetail', params: { familyId: selectedGroup.id, userId: member.userId } }" 
                class="flex flex-col items-center flex-shrink-0 gap-1.5 min-w-[52px] group"
              >
                <div 
                  class="w-[48px] h-[48px] rounded-full p-0.5 bg-white ring-1 ring-gray-100 shadow-sm group-hover:ring-gray-200 transition-all duration-300"
                >
                  <img :src="member.profileImage || '/default-profile.png'" alt="Profile" class="w-full h-full rounded-full object-cover" />
                </div>
                <span 
                  class="text-[11px] font-medium truncate w-[52px] text-center transition-colors text-[var(--text-sub)]"
                >
                  {{ member.name }}
                </span>
              </router-link>
            </template>

            <!-- Add 버튼 (리스트 끝으로 이동) -->
            <div 
              @click="openInviteModal" 
              class="flex flex-col items-center flex-shrink-0 gap-1.5 min-w-[52px] cursor-pointer group"
            >
              <button class="w-[48px] h-[48px] rounded-full border border-dashed border-gray-300 flex items-center justify-center text-gray-400 group-hover:text-[var(--color-primary)] group-hover:border-[var(--color-primary)] group-hover:bg-[var(--color-primary-soft)] transition-all bg-white">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path></svg>
              </button>
              <span class="text-[11px] font-medium text-[var(--text-sub)] group-hover:text-[var(--color-primary)] transition-colors text-center w-[52px] truncate">초대</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Toggle Button Container (Integrated with Header) -->
    <div v-if="showProfiles" class="flex justify-center -mb-6 relative z-20">
      <!-- Collapse Toggle Button -->
      <div class="flex px-4 py-1 justify-center">
        <button 
          @click="isCollapsed = !isCollapsed"
          class="flex min-w-[84px] cursor-pointer items-center justify-center overflow-hidden rounded-lg h-10 px-4 bg-white shadow-sm border border-gray-100 text-[#1c140d] hover:bg-gray-50 transition gap-2"
        >
           <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 transform transition-transform duration-300" :class="isCollapsed ? 'rotate-180' : ''" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 15l7-7 7 7" />
          </svg>
        </button>
      </div>
    </div>
  </header>

  <GroupCreateModal :show="isGroupCreateModalOpen" @close="closeGroupCreateModal" @create-group="handleCreateGroup" />
  <InviteCodeModal 
    v-if="selectedGroup" 
    :show="isInviteModalOpen" 
    :family-id="selectedGroup.id" 
    @close="closeInviteModal" 
  />
  <AddGroupModal
    :show="isAddGroupModalOpen"
    @close="closeAddGroupModal"
    @join-group="joinGroup"
    @create-group-request="handleCreateGroup"
  />
</template>

<script setup>
import { ref, watch, computed, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useFamilyStore } from '@/stores/family';
import { useModalStore } from '@/stores/modal';
import { storeToRefs } from 'pinia';
import api, { joinFamilyWithCode } from '@/services/api';
import GroupSelector from './GroupSelector.vue';
import GroupCreateModal from './GroupCreateModal.vue';
import SettingsDropdown from './SettingsDropdown.vue';
import AddGroupModal from './AddGroupModal.vue';
import InviteCodeModal from './InviteCodeModal.vue'; 

const props = defineProps({
  showProfiles: {
    type: Boolean,
    default: true
  }
});

const router = useRouter();

const route = useRoute();
const userStore = useUserStore();
const familyStore = useFamilyStore();
const modalStore = useModalStore();
const { selectedFamily: selectedGroup } = storeToRefs(familyStore); // Alias for compatibility with existing template code

// Emit modal state changes to parent (HomePage)
const emit = defineEmits(['modal-state-change']);

const groupSelectorRef = ref(null);
const groupSelectorWrapper = ref(null);
const members = ref([]);
const selectedId = ref(null);
const isGroupCreateModalOpen = ref(false);
const isInviteModalOpen = ref(false);
const isSettingsOpen = ref(false);
const isAddGroupModalOpen = ref(false);
const isCollapsed = ref(false);
const settingsMenu = ref(null);

const dependentOrPlaceholder = computed(() => members.value.find(m => m.dependent || m.isPlaceholder));
const otherMembers = computed(() => {
  const others = members.value.filter(m => !m.dependent && !m.isPlaceholder);
  const myId = userStore.profile?.id;
  
  if (!myId) return others;

  return [...others].sort((a, b) => { 
    if (String(a.userId) === String(myId)) return -1;
    if (String(b.userId) === String(myId)) return 1;
    return 0;
  });
});

const openGroupCreateModal = () => isGroupCreateModalOpen.value = true;
const closeGroupCreateModal = () => isGroupCreateModalOpen.value = false;

const openAddGroupModal = () => {
  isAddGroupModalOpen.value = true;
};
const closeAddGroupModal = () => {
  isAddGroupModalOpen.value = false;
};

const openInviteModal = async () => {
  if (!selectedGroup.value || !selectedGroup.value.id) {
    await modalStore.openAlert('그룹을 먼저 선택해주세요.');
    return;
  }
  isInviteModalOpen.value = true;
};
const closeInviteModal = () => isInviteModalOpen.value = false;

const toggleSettings = () => {
  isSettingsOpen.value = !isSettingsOpen.value;
};

const handleGlobalClick = (event) => {
    // Close Settings Menu
    if (settingsMenu.value && !settingsMenu.value.contains(event.target)) {
        isSettingsOpen.value = false;
    }
    // Close Group Selector
    if (groupSelectorWrapper.value && !groupSelectorWrapper.value.contains(event.target)) {
        groupSelectorRef.value?.close();
    }
}

// Watch modal states and emit change
watch(
  [isAddGroupModalOpen, isInviteModalOpen, isGroupCreateModalOpen],
  ([addGroup, invite, groupCreate]) => {
    emit('modal-state-change', addGroup || invite || groupCreate);
  }
);

// Persistence for isCollapsed
watch(isCollapsed, (newVal) => {
  localStorage.setItem('headerCollapsed', JSON.stringify(newVal));
});

onMounted(async () => {
    document.addEventListener('click', handleGlobalClick);
    
    // Load collapsed state
    const savedCollapsed = localStorage.getItem('headerCollapsed');
    if (savedCollapsed !== null) {
      isCollapsed.value = JSON.parse(savedCollapsed);
    }

    // Force refresh families to ensure updated DTO
    await familyStore.fetchFamilies();
    

    if (!userStore.profile) {
      await userStore.fetchUser();
    }
});

onUnmounted(() => {
    document.removeEventListener('click', handleGlobalClick);
});

const goToGroupEdit = () => {
  if (!selectedGroup.value) return;
  router.push({ name: 'GroupEdit', params: { familyId: selectedGroup.value.id }, query: { groupName: selectedGroup.value.name } });
  isSettingsOpen.value = false;
};

const leaveGroup = async () => {
  if (!selectedGroup.value) return;
  if (await modalStore.openConfirm(`'${selectedGroup.value.name}' 그룹을 정말로 탈퇴하시겠습니까? 대표자일 경우 그룹이 삭제됩니다.`)) {
    try {
      await api.delete(`/families/${selectedGroup.value.id}/leave`);
      await modalStore.openAlert('그룹에서 성공적으로 탈퇴/삭제되었습니다.');
      // Refreshing might reset store state if not persisted, but usually app reloads
      window.location.reload(); 
    } catch (error) {
      console.error('Failed to leave group:', error);
      await modalStore.openAlert('그룹 탈퇴/삭제에 실패했습니다.');
    }
  }
  isSettingsOpen.value = false;
};

const handleCreateGroup = async (groupData) => {
  try {
    const payload = {
      name: groupData.groupName,
      relation: groupData.relation
    };
    const response = await api.post('/families', payload);
    // Fetch via store
    await familyStore.fetchFamilies();
    familyStore.selectFamily(response.data);
    closeAddGroupModal();
    await modalStore.openAlert('새로운 그룹이 생성되었습니다.');
  } catch (error) {
    console.error('Failed to create group:', error);
    await modalStore.openAlert('그룹 생성에 실패했습니다.');
  }
};


const joinGroup = async (inviteCode) => {
  try {
    const response = await joinFamilyWithCode(inviteCode);
     // Fetch via store
    await familyStore.fetchFamilies();
    familyStore.selectFamily(response.data);
    await modalStore.openAlert('그룹에 성공적으로 참여했습니다!');
  } catch (error) {
    console.error('Failed to join group:', error);
    let errorMessage = '그룹 참여 중 오류가 발생했습니다.';
    if (error.response && error.response.data && error.response.data.message) {
        errorMessage = error.response.data.message;
    }
    await modalStore.openAlert(errorMessage);
  }
};

const toggleGroupSelector = () => groupSelectorRef.value?.toggle();
// Remove handleGroupSelected as it's now handled by store state

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
    
    syncSelectedIdWithRoute();
    
  } catch (error) {
    console.error(`Failed to fetch members for familyId ${selectedGroup.value.id}:`, error);
    members.value = [];
  }
};

const syncSelectedIdWithRoute = () => {
    // 1. Try to use route params if valid in current members
    if (route.params.userId) {
        const isValid = members.value.some(m => String(m.userId) === String(route.params.userId));
        if (isValid) {
            selectedId.value = route.params.userId;
            return;
        }
    }

    // 2. Fallback: Default to dependent (usually first member logic handles dependent first)
    // or first actual member if dependent is placeholder? 
    // User wants dependent highlighted. Dependent is always sorted to top if exists.
    // If no dependent (placeholder), maybe we still select placeholder if logic requires?
    // But typically we select first available profile.
    
    // Existing logic prioritizes finding FIRST member in the list to select.
    // Since fetchMembers sorts dependent to front, members.value[0] is dependent or placeholder.
    
    // If dependent exists (is not placeholder):
    const firstMember = members.value[0];
    if (firstMember && !firstMember.isPlaceholder) {
        selectedId.value = firstMember.userId;
    } else {
        // If first is placeholder, find next actual member
        const firstActual = members.value.find(m => !m.isPlaceholder);
        selectedId.value = firstActual ? firstActual.userId : null;
    }
    
    // Optional: If the user is on a detail page and the group changes, 
    // strictly speaking we should probably redirect, but let's stick to just fixing the highlight for now 
    // as that's the explicit request.
};

watch(() => route.params.userId, (newId) => {
    if (newId) {
        selectedId.value = newId;
    }
});

watch(selectedGroup, (newGroup) => {

    if (newGroup) {



    }
    fetchMembers();
}, { immediate: true });
</script>

<style scoped>
/* Scrollbar hiding logic if needed, usually mostly handled by Tailwind classes or global styles */
.hide-scrollbar::-webkit-scrollbar {
  display: none;
}
.hide-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>