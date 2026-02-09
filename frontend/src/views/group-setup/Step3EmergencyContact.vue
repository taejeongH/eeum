<template>
  <div @keyup.enter="goNext">
    <h2 class="eeum-title">
      위급 상황 시 연락할<br />
      <span class="text-[var(--color-primary)]">비상 연락망</span>을 설정해주세요
    </h2>

    <p class="eeum-sub mt-3">
      119 신고 후 등록된 순서대로<br />
      가족들에게 알림이 전송돼요.
    </p>

    <!-- Progress -->
    <div class="mt-6 mb-8">
      <div class="h-2 w-full rounded-full bg-[var(--color-primary-soft)]">
        <div class="h-2 w-3/4 rounded-full bg-[var(--color-primary)]"></div>
      </div>
      <p class="mt-2 text-xs text-[var(--color-primary)]">
        단계 3 / 4 · 비상 연락망 설정
      </p>
    </div>

    <!-- Priority Slots -->
    <div class="grid grid-cols-3 gap-3 mb-8">
      <div 
        v-for="(priority, index) in [1, 2, 3]" 
        :key="priority"
        @click="selectSlot(index)"
        class="relative flex flex-col items-center justify-center p-3 rounded-2xl border-2 transition-all cursor-pointer aspect-[3/4]"
        :class="[
          contactSlots[index] ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)] border-solid' : 'border-dashed border-gray-200 hover:border-gray-300 hover:bg-gray-50',
          selectedSlotIndex === index ? 'ring-2 ring-offset-2 ring-[var(--color-primary)]' : ''
        ]"
      >
        <div class="absolute top-2 left-2 w-6 h-6 rounded-full bg-white flex items-center justify-center text-xs font-bold text-gray-500 shadow-sm"
             :class="contactSlots[index] ? 'text-[var(--color-primary)]' : ''"
        >
          {{ priority }}
        </div>

        <template v-if="contactSlots[index]">
          <div class="w-12 h-12 rounded-full mb-2 bg-white p-0.5 shadow-sm">
            <img :src="contactSlots[index].profileImage || '/default-profile.png'" class="w-full h-full rounded-full object-cover">
          </div>
          <span class="text-sm font-bold text-gray-800 text-center leading-tight break-keep px-1">
            {{ contactSlots[index].name }}
          </span>
           <button 
             @click="(e) => removeContact(index, e)"
             class="absolute -top-2 -right-2 bg-white rounded-full p-1 shadow-md text-red-500 hover:bg-red-50 transition-colors z-10"
           >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
          </button>
        </template>
        
        <template v-else>
          <div class="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center text-gray-300 mb-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path></svg>
          </div>
          <span class="text-xs text-gray-400 font-medium">선택</span>
        </template>
      </div>
    </div>

    <!-- Member List -->
    <div class="bg-white rounded-3xl shadow-lg border border-gray-100 p-5 mb-8">
      <h3 class="text-sm font-bold text-gray-600 mb-4 px-1">가족 멤버 목록</h3>
      <div class="grid grid-cols-4 gap-3">
        <template v-for="member in availableMembers" :key="member.userId">
          <button 
            @click="addContact(member)"
            :disabled="isSelected(member)"
            class="flex flex-col items-center gap-1.5 group disabled:opacity-40"
          >
            <div class="relative w-14 h-14 rounded-full p-0.5 bg-white ring-1 ring-gray-200 transition-all group-hover:ring-gray-300 group-disabled:ring-gray-100">
               <img :src="member.profileImage || '/default-profile.png'" class="w-full h-full rounded-full object-cover">
               <div v-if="isSelected(member)" class="absolute inset-0 bg-white/60 rounded-full flex items-center justify-center">
                 <svg class="w-6 h-6 text-[var(--color-primary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"></path></svg>
               </div>
            </div>
            <span class="text-xs font-medium text-gray-600 truncate w-full text-center">{{ member.name }}</span>
          </button>
        </template>
        <!-- If no members -->
        <div v-if="availableMembers.length === 0" class="col-span-4 py-4 text-center text-xs text-gray-400 bg-gray-50 rounded-xl">
           선택 가능한 멤버가 없습니다.
        </div>
      </div>
    </div>

    <!-- Navigation Buttons -->
    <div class="mt-10">
      <button 
        @click="goNext" 
        class="eeum-btn-primary"
      >
        다음 단계로 →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import api from '@/services/api';
import { useFamilyStore } from '@/stores/family';
import { useUserStore } from '@/stores/user';
import { useGroupSetupStore } from '@/stores/groupSetup';
import { storeToRefs } from 'pinia';
import { Logger } from '@/services/logger';

const route = useRoute();
const router = useRouter();
const familyStore = useFamilyStore();
const userStore = useUserStore();
const setupStore = useGroupSetupStore();


const { contactSlots } = storeToRefs(setupStore);

const selectedSlotIndex = ref(0); 
const members = ref([]);


const availableMembers = computed(() => {
    return members.value;
});

const isSelected = (member) => {
    return contactSlots.value.some(contact => contact && contact.userId === member.userId);
};

const selectSlot = (index) => {
    selectedSlotIndex.value = index;
};

const addContact = (member) => {
    
    if (selectedSlotIndex.value !== null) {
        contactSlots.value[selectedSlotIndex.value] = member;
        
        
        const nextEmptyIndex = contactSlots.value.findIndex(slot => slot === null);
        if (nextEmptyIndex !== -1) {
            selectedSlotIndex.value = nextEmptyIndex;
        }
    }
};

const removeContact = (index, event) => {
    event.stopPropagation(); 
    if (contactSlots.value[index]) {
        contactSlots.value[index] = null;
        selectedSlotIndex.value = index; 
    }
};

onMounted(async () => {
    const familyId = route.params.familyId;
    if (familyId) {
        setupStore.initData(familyId);
        
        try {
            
            const membersResponse = await api.get(`/families/${familyId}/members`);
            members.value = membersResponse.data.filter(m => !m.isPlaceholder);
        } catch (error) {
            Logger.error("데이터 조회 실패:", error);
        }
    }
});

const goBack = () => {
  router.back();
};

const goNext = () => {
    
    
    router.push({
        name: 'GroupEditStep4',
        params: { familyId: route.params.familyId }
    });
};
</script>
