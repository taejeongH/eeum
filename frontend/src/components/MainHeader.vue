<template>
  <header
    class="bg-white px-6 pt-[calc(0.75rem+var(--sat))] pb-2 shadow-sm sticky top-0 z-[100] transition-all duration-300"
  >
    <!-- 상단 네비게이션 영역 -->
    <div class="flex items-center justify-between mb-1 min-h-[44px]">
      <!-- 로고 및 서비스 명칭 영역 (왼쪽 고정) -->
      <div class="flex-shrink-0 flex items-center z-50">
        <router-link
          to="/home"
          class="flex items-center gap-1 active:scale-95 transition-transform duration-200 group"
          title="메인 홈으로 이동"
        >
          <img src="@/assets/eeum_logo2.png" alt="로고" class="h-6 w-auto object-contain" />
          <h1
            class="text-[1.125rem] font-[950] text-[#1E293B] tracking-[-0.05em]"
            style="font-family: 'NanumSquareNeo', sans-serif"
          >
            이음
          </h1>
        </router-link>
      </div>

      <!-- 중간 여백 공간 (배치 조절용) -->
      <div class="flex-1"></div>

      <!-- 액션 버튼, 그룹 선택 및 설정 메뉴 영역 (오른쪽 고정) -->
      <div class="flex-shrink-0 flex justify-end items-center relative z-50 gap-2">
        <!-- 그룹 선택 드롭다운 버튼 -->
        <div class="relative" ref="groupSelectorWrapper">
          <div
            @click="toggleGroupSelector"
            class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-gray-100/80 hover:bg-gray-200/80 transition cursor-pointer group whitespace-nowrap"
            title="그룹 선택 및 변경"
          >
            <span class="truncate text-[13px] font-bold text-gray-800 max-w-[70px]">
              {{ selectedGroup ? selectedGroup.name : '그룹 선택' }}
            </span>
            <svg
              class="w-3 h-3 text-gray-400 group-hover:text-gray-600 transition"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M19 9l-7 7-7-7"
              />
            </svg>
          </div>
          <GroupSelector ref="groupSelectorRef" @add-group-request="openAddGroupModal" />
        </div>

        <!-- 커스텀 액션 버튼들을 위한 슬롯 -->
        <slot name="actions"></slot>

        <!-- 설정 메뉴 버튼 및 드롭다운 -->
        <div v-if="showSettings" class="relative" ref="settingsMenu">
          <button
            @click="toggleSettings"
            class="p-1.5 rounded-full hover:bg-gray-50/80 active:bg-gray-100 transition text-[var(--text-sub)] hover:text-[var(--text-body)]"
          >
            <!-- 점 3개 (더보기) 아이콘 -->
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"
              />
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

    <!-- 펼침/접힘 가능한 섹터 래퍼 영역 -->
    <div
      v-if="showProfiles"
      class="overflow-hidden transition-[max-height,opacity] duration-300 ease-in-out"
      :class="isCollapsed ? 'max-h-0 opacity-0' : 'max-h-48 opacity-100'"
    >
      <!-- 가족 구성원 프로필 가로 리스트 영역 -->
      <div class="flex items-end justify-between pt-1 pb-1 gap-2 -mr-6">
        <!-- 왼쪽 영역: 고정된 피부양자/본인 아이콘 및 스크롤 가능 리스트 -->
        <div class="flex items-end gap-2 flex-grow min-w-0">
          <!-- 고정된 주 대상자(피부양자) 또는 초대 플레이스홀더 -->
          <div class="flex-shrink-0 text-center min-w-[72px] ml-0.5">
            <!-- 플레이스홀더 (피부양자 추가) -->
            <router-link
              v-if="dependentOrPlaceholder?.isPlaceholder && selectedGroup"
              :to="{
                name: 'GroupEditStep1',
                params: { familyId: selectedGroup.id },
                query: { groupName: selectedGroup.name },
              }"
              class="flex flex-col items-center gap-1.5 group"
            >
              <div
                class="w-[68px] h-[68px] rounded-full flex items-center justify-center bg-[var(--color-primary-soft)] text-[var(--color-primary)] ring-2 ring-white shadow-sm group-hover:bg-orange-100 transition"
              >
                <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 4v16m8-8H4"
                  ></path>
                </svg>
              </div>
              <span
                class="text-xs font-semibold text-[var(--text-sub)] truncate w-full group-hover:text-[var(--color-primary)] transition"
              >
                {{ dependentOrPlaceholder.name }}
              </span>
            </router-link>

            <!-- 실제 피부양자 -->
            <router-link
              v-else-if="dependentOrPlaceholder"
              :to="{
                name: 'MemberDetail',
                params: { familyId: selectedGroup.id, userId: dependentOrPlaceholder.userId },
              }"
              class="flex flex-col items-center gap-1.5 group"
            >
              <div
                class="relative w-[68px] h-[68px] rounded-full p-0.5 bg-white transition-all duration-300"
                :class="[
                  selectedId === dependentOrPlaceholder.userId
                    ? 'ring-2 ring-[var(--color-primary)] ring-offset-2 shadow-md'
                    : 'ring-1 ring-gray-100 shadow-sm',
                ]"
              >
                <img
                  :src="dependentOrPlaceholder.profileImage || '/default-profile.png'"
                  alt="Profile"
                  class="w-full h-full rounded-full object-cover"
                />
                <!-- 그룹 관리자(대표자) 표시 왕관 아이콘 -->
                <div
                  v-if="dependentOrPlaceholder.representative"
                  class="absolute -bottom-1 -right-1 w-6 h-6 z-10 filter drop-shadow-md"
                >
                  <IconCrown class="text-amber-400 w-full h-full" />
                </div>
                <div
                  v-if="selectedId === dependentOrPlaceholder.userId"
                  class="absolute inset-0 rounded-full border-2 border-white/20"
                ></div>
              </div>
              <span
                class="text-xs font-semibold truncate w-full transition-colors"
                :class="
                  selectedId === dependentOrPlaceholder.userId
                    ? 'text-[var(--text-title)]'
                    : 'text-[var(--text-sub)]'
                "
              >
                {{ dependentOrPlaceholder.name }}
              </span>
            </router-link>
          </div>

          <!-- 구분선 (선택사항, 멤버가 있을 때만 표시) -->
          <div v-if="otherMembers.length > 0" class="w-px h-8 bg-gray-100 mb-6 mx-0.5"></div>

          <!-- 스크롤 가능한 나머지 멤버 -->
          <div
            class="flex-1 flex items-end gap-2 overflow-x-auto hide-scrollbar pb-1 px-0.5 pt-2 min-w-0 pr-6"
          >
            <template v-for="member in otherMembers" :key="member.userId">
              <router-link
                :to="{
                  name: 'MemberDetail',
                  params: { familyId: selectedGroup.id, userId: member.userId },
                }"
                class="flex flex-col items-center flex-shrink-0 gap-1.5 min-w-[52px] group"
              >
                <div
                  class="relative w-[48px] h-[48px] rounded-full p-0.5 bg-white ring-1 ring-gray-100 shadow-sm group-hover:ring-gray-200 transition-all duration-300"
                >
                  <img
                    :src="member.profileImage || '/default-profile.png'"
                    alt="프로필"
                    class="w-full h-full rounded-full object-cover"
                  />
                  <!-- 그룹 관리자(대표자) 표시 왕관 아이콘 -->
                  <div
                    v-if="member.representative"
                    class="absolute -bottom-1 -right-1 w-5 h-5 z-10 filter drop-shadow-md"
                  >
                    <IconCrown class="text-amber-400 w-full h-full" />
                  </div>
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
              <button
                class="w-[48px] h-[48px] rounded-full border border-dashed border-gray-300 flex items-center justify-center text-gray-400 group-hover:text-[var(--color-primary)] group-hover:border-[var(--color-primary)] group-hover:bg-[var(--color-primary-soft)] transition-all bg-white"
              >
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M12 4v16m8-8H4"
                  ></path>
                </svg>
              </button>
              <span
                class="text-[11px] font-medium text-[var(--text-sub)] group-hover:text-[var(--color-primary)] transition-colors text-center w-[52px] truncate"
                >초대</span
              >
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 토글 버튼 컨테이너 (헤더 통합형) -->
    <div v-if="showProfiles" class="flex justify-center -mb-2.5 relative z-20">
      <!-- 접기/펴기 토글 버튼 -->
      <div class="flex justify-center">
        <button
          @click="isCollapsed = !isCollapsed"
          class="flex w-14 cursor-pointer items-center justify-center overflow-hidden rounded-b-lg h-5 bg-white shadow-sm border-b border-x border-gray-100 text-[#1c140d] hover:bg-gray-50 transition gap-2"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            class="h-4 w-4 transform transition-transform duration-300"
            :class="isCollapsed ? 'rotate-180' : ''"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2.5"
              d="M5 15l7-7 7 7"
            />
          </svg>
        </button>
      </div>
    </div>
  </header>

  <GroupCreateModal
    :show="isGroupCreateModalOpen"
    @close="closeGroupCreateModal"
    @create-group="handleCreateGroup"
  />
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
import { Logger } from '@/services/logger';
import GroupSelector from './GroupSelector.vue';
import GroupCreateModal from './GroupCreateModal.vue';
import SettingsDropdown from './SettingsDropdown.vue';
import AddGroupModal from './AddGroupModal.vue';
import InviteCodeModal from './InviteCodeModal.vue';

const props = defineProps({
  showProfiles: {
    type: Boolean,
    default: true,
  },
  showSettings: {
    type: Boolean,
    default: false,
  },
});

const router = useRouter();

const route = useRoute();
const userStore = useUserStore();
const familyStore = useFamilyStore();
const modalStore = useModalStore();
const { selectedFamily: selectedGroup } = storeToRefs(familyStore);

/** 부모 컴포넌트(HomePage 등)로 모달 상태 변경 이벤트를 전달합니다. */
const emit = defineEmits(['modal-state-change']);

/** 왕관 아이콘 임포트 */
import IconCrown from '@/components/icons/IconCrown.vue';

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

/**
 * 피부양자 또는 추가 유도 플레이스홀더 항목을 반환합니다.
 */
const dependentOrPlaceholder = computed(() =>
  members.value.find((m) => m.dependent || m.isPlaceholder),
);

/**
 * 피부양자를 제외한 나머지 가족 멤버 목록을 반환합니다.
 * 본인인 경우 목록의 가장 앞으로 정렬합니다.
 */
const otherMembers = computed(() => {
  const others = members.value.filter((m) => !m.dependent && !m.isPlaceholder);
  return sortMembersByMe(others);
});

/**
 * 멤버 목록에서 나 자신을 가장 앞으로 정렬합니다. (내부 사용)
 * @param {Array} list
 * @returns {Array}
 */
const sortMembersByMe = (list) => {
  const myId = userStore.profile?.id;
  if (!myId) return list;

  return [...list].sort((a, b) => {
    if (String(a.userId) === String(myId)) return -1;
    if (String(b.userId) === String(myId)) return 1;
    return 0;
  });
};

/**
 * 그룹 생성 모달을 엽니다.
 */
const openGroupCreateModal = () => (isGroupCreateModalOpen.value = true);

/**
 * 그룹 생성 모달을 닫습니다.
 */
const closeGroupCreateModal = () => (isGroupCreateModalOpen.value = false);

/**
 * 그룹 참여/생성 선택 모달을 엽니다.
 */
const openAddGroupModal = () => {
  isAddGroupModalOpen.value = true;
};

/**
 * 그룹 참여/생성 선택 모달을 닫습니다.
 */
const closeAddGroupModal = () => {
  isAddGroupModalOpen.value = false;
};

/**
 * 현재 그룹으로 가족을 초대하기 위한 코드 생성 모달을 엽니다.
 */
const openInviteModal = async () => {
  if (!selectedGroup.value?.id) {
    await modalStore.openAlert('그룹을 먼저 선택해주세요.');
    return;
  }
  isInviteModalOpen.value = true;
};

/**
 * 초대 코드 모달을 닫습니다.
 */
const closeInviteModal = () => (isInviteModalOpen.value = false);

/**
 * 설정 드롭다운 메뉴를 토글합니다.
 */
const toggleSettings = () => {
  isSettingsOpen.value = !isSettingsOpen.value;
};

/**
 * 영역 외부 클릭 시 드롭다운들을 닫습니다. (내부 사용)
 * @param {MouseEvent} event
 */
const handleGlobalClick = (event) => {
  // 설정 메뉴 닫기
  if (settingsMenu.value && !settingsMenu.value.contains(event.target)) {
    isSettingsOpen.value = false;
  }
  // 그룹 선택기 닫기
  if (groupSelectorWrapper.value && !groupSelectorWrapper.value.contains(event.target)) {
    groupSelectorRef.value?.close();
  }
};

/** 모달 상태 변화를 감시하고 이벤트를 발생시킵니다. */
watch(
  [isAddGroupModalOpen, isInviteModalOpen, isGroupCreateModalOpen],
  ([addGroup, invite, groupCreate]) => {
    emit('modal-state-change', addGroup || invite || groupCreate);
  },
);

/** 헤더 접힘 상태를 로컬 스토리지에 저장하여 유지합니다. */
watch(isCollapsed, (newVal) => {
  localStorage.setItem('headerCollapsed', JSON.stringify(newVal));
});

onMounted(async () => {
  document.addEventListener('click', handleGlobalClick);

  /** 저장된 헤더 접힘 상태 불러오기 */
  const savedCollapsed = localStorage.getItem('headerCollapsed');
  if (savedCollapsed !== null) {
    isCollapsed.value = JSON.parse(savedCollapsed);
  }

  /** 최신 데이터(DTO) 동기화를 위해 가족 목록 강제 새로고침 */
  await familyStore.fetchFamilies();

  if (!userStore.profile) {
    await userStore.fetchUser();
  }
});

onUnmounted(() => {
  document.removeEventListener('click', handleGlobalClick);
});

/**
 * 그룹 정보를 수정하는 페이지로 이동합니다.
 */
const goToGroupEdit = () => {
  if (!selectedGroup.value) return;
  router.push({
    name: 'GroupEdit',
    params: { familyId: selectedGroup.value.id },
    query: { groupName: selectedGroup.value.name },
  });
  isSettingsOpen.value = false;
};

/**
 * 현재 선택된 그룹에서 탈퇴하거나 (대표자일 경우) 그룹을 해체합니다.
 */
const leaveGroup = async () => {
  if (!selectedGroup.value) return;

  const confirmed = await modalStore.openConfirm(
    `'${selectedGroup.value.name}' 그룹을 정말로 탈퇴하시겠습니까? 대표자일 경우 그룹이 삭제됩니다.`,
  );

  if (confirmed) {
    try {
      await api.delete(`/families/${selectedGroup.value.id}/leave`);
      await modalStore.openAlert('그룹에서 성공적으로 탈퇴/삭제되었습니다.');
      window.location.reload();
    } catch (error) {
      Logger.error('그룹 탈퇴 실패:', error);
      await modalStore.openAlert('그룹 탈퇴/삭제에 실패했습니다.');
    }
  }
  isSettingsOpen.value = false;
};

/**
 * 새로운 그룹을 생성하고 해당 그룹을 선택 상태로 설정합니다.
 * @param {Object} groupData
 */
const handleCreateGroup = async (groupData) => {
  try {
    const payload = {
      name: groupData.groupName,
      relation: groupData.relation,
    };
    const response = await api.post('/families', payload);
    await familyStore.fetchFamilies();
    familyStore.selectFamily(response.data);
    closeAddGroupModal();
    await modalStore.openAlert('새로운 그룹이 생성되었습니다.');
  } catch (error) {
    Logger.error('그룹 생성 실패:', error);
    await modalStore.openAlert('그룹 생성에 실패했습니다.');
  }
};

/**
 * 초대 코드를 사용하여 신규 그룹에 참여합니다.
 * @param {string} inviteCode
 */
const joinGroup = async (inviteCode) => {
  try {
    const response = await joinFamilyWithCode(inviteCode);
    await familyStore.fetchFamilies(true);
    familyStore.selectFamily(response.data);
    await modalStore.openAlert('그룹에 성공적으로 참여했습니다!');
  } catch (error) {
    Logger.error('그룹 참여 실패:', error);
    let errorMessage = error.response?.data?.message || '그룹 참여 중 오류가 발생했습니다.';
    await modalStore.openAlert(errorMessage);
  }
};

/**
 * 그룹 선택 드롭다운을 토글합니다.
 */
const toggleGroupSelector = () => groupSelectorRef.value?.toggle();
/** 참고: 그룹 선택 처리는 이제 스토어 상태를 통해 관리되므로 별도의 handleGroupSelected는 삭제함 */

/**
 * 현재 선택된 그룹의 멤버 목록을 불러와 정렬 처리합니다.
 */
const fetchMembers = async () => {
  if (!selectedGroup.value) {
    members.value = [];
    return;
  }
  try {
    const fetchedMembers = await familyStore.fetchMembers(selectedGroup.value.id);
    members.value = prepareMembersList(fetchedMembers);
    syncSelectedIdWithRoute();
  } catch (error) {
    Logger.error(`구성원 데이터 처리 실패 (ID: ${selectedGroup.value.id}):`, error);
    members.value = [];
  }
};

/**
 * 멤버 목록을 복사하고 필요한 경우 플레이스홀더를 추가하여 반환합니다. (내부 사용)
 * @param {Array} fetchedMembers
 * @returns {Array}
 */
const prepareMembersList = (fetchedMembers) => {
  const list = JSON.parse(JSON.stringify(fetchedMembers));
  const dependent = list.find((m) => m.dependent);

  if (dependent) {
    list.sort((a, b) => b.dependent - a.dependent);
  } else {
    list.unshift({ userId: 'add-dependent', name: '피부양자 설정', isPlaceholder: true });
  }
  return list;
};

/**
 * 현재 URL의 userId 파라미터와 헤더의 선택 상태를 동기화합니다. (내부 사용)
 */
const syncSelectedIdWithRoute = () => {
  /** 1. URL 파라미터가 현재 멤버 목록에 있는 경우 사용 */
  if (route.params.userId) {
    const isValid = members.value.some((m) => String(m.userId) === String(route.params.userId));
    if (isValid) {
      selectedId.value = route.params.userId;
      return;
    }
  }

  /** 2. 폴백: 멤버 목록의 첫 번째 실제 멤버 선택 */
  const firstActualMember = members.value.find((m) => !m.isPlaceholder);
  selectedId.value = firstActualMember?.userId || null;
};

watch(
  () => route.params.userId,
  (newId) => {
    if (newId) {
      selectedId.value = newId;
    }
  },
);

watch(
  selectedGroup,
  (newGroup) => {
    if (newGroup) {
      /** fetchMembers 로직은 아래에서 통합 호출됨 */
    }
    fetchMembers();
  },
  { immediate: true },
);
</script>

<style scoped>
/* 가로 스크롤바 숨기기 처리 (필요한 경우 Tailwind 또는 전역 스타일에서 처리됨) */
.hide-scrollbar::-webkit-scrollbar {
  display: none;
}
.hide-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
