<template>
  <div class="bg-gray-50 min-h-screen py-8 px-4">
    <div class="relative flex items-center justify-center mb-6">
        <button @click="goBack" class="absolute left-0">
            <svg class="w-6 h-6 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path></svg>
        </button>
        <h1 class="text-xl font-bold text-center">멤버 상세 정보</h1>
    </div>

    <div v-if="loading" class="text-center">
      <p>Loading member details...</p>
    </div>
    <div v-else-if="error" class="text-center text-red-500">
      <p>{{ error }}</p>
    </div>
    <div v-else-if="member" class="max-w-lg mx-auto">
      <div class="bg-white rounded-xl shadow-md p-6 mb-6">
        <div class="flex flex-col items-center">
          <img class="h-32 w-32 rounded-full object-cover ring-4 ring-orange-200" :src="member.profileImage || '/default-profile.png'" alt="Member profile image">
          <h1 class="mt-4 text-2xl font-bold text-gray-900">{{ member.name }}</h1>
          <p class="mt-1 text-md text-orange-600 font-semibold">{{ member.dependent ? '피부양자' : '부양자' }}</p>
        </div>
      </div>

      <div class="space-y-4">
        <div class="bg-white rounded-xl shadow-md p-6">
          <h2 class="text-lg font-semibold border-b pb-2 mb-4">기본 정보</h2>
          <div class="space-y-3">
            <div class="flex justify-between">
              <span class="text-gray-500">전화번호</span>
              <span class="font-medium text-gray-800">{{ member.phone }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">생년월일</span>
              <span class="font-medium text-gray-800">{{ member.birthDate }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">성별</span>
              <span class="font-medium text-gray-800">{{ member.gender === 'M' ? '남성' : '여성' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">주소</span>
              <span class="font-medium text-gray-800">{{ member.address }}</span>
            </div>
          </div>
        </div>

        <div v-if="member.dependent" class="bg-white rounded-xl shadow-md p-6">
          <h2 class="text-lg font-semibold border-b pb-2 mb-4">건강 정보</h2>
          <div class="space-y-3">
            <div class="flex justify-between">
              <span class="text-gray-500">혈액형</span>
              <span class="font-medium text-gray-800">{{ member.bloodType }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">기저질환</span>
              <span class="font-medium text-gray-800">{{ member.chronicDiseases && member.chronicDiseases.length > 0 ? member.chronicDiseases.join(', ') : '없음' }}</span>
            </div>
          </div>
        </div>
        <div v-else class="bg-white rounded-xl shadow-md p-6">
          <h2 class="text-lg font-semibold border-b pb-2 mb-4">그룹 내 정보</h2>
           <div class="space-y-3">
            <div class="flex justify-between">
              <span class="text-gray-500">응급 우선순위</span>
              <span class="font-medium text-gray-800">{{ member.emergencyPriority }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-gray-500">대표자와의 관계</span>
              <span class="font-medium text-gray-800">{{ member.relationship }}</span>
            </div>
          </div>
        </div>
      </div>


      <div class="mt-8 px-4">
        <button v-if="member.currentUserOwner && !isViewingSelf" @click="kickMember" class="w-full px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700">
          그룹에서 강퇴하기
        </button>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import api from '@/services/api';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const member = ref(null);
const loading = ref(false);
const error = ref(null);

const goBack = () => {
    router.go(-1);
};

const isViewingSelf = computed(() => {
  const profileId = userStore.profile?.id;
  const memberId = member.value?.userId;
  if (profileId === undefined || memberId === undefined || profileId === null || memberId === null) {
      return false;
  }
  return Number(profileId) === Number(memberId);
});

const kickMember = async () => {
  if (confirm(`정말로 '${member.value.name}'님을 그룹에서 강퇴하시겠습니까?`)) {
    const { familyId, userId } = route.params;
    try {
      await api.delete(`/families/${familyId}/members/${userId}`);
      alert('멤버를 성공적으로 강퇴했습니다.');
      router.push('/home');
    } catch (err) {
      console.error('Failed to kick member:', err);
      alert('멤버 강퇴에 실패했습니다.');
    }
  }
};

const fetchMemberDetails = async () => {
  const { familyId, userId } = route.params;
  if (!familyId || !userId) {
    error.value = '잘못된 접근입니다.';
    return;
  }

  loading.value = true;
  error.value = null;
  try {
    if (!userStore.profile) {
      await userStore.fetchUser();
    }
    const response = await api.get(`/families/${familyId}/members/${userId}`);
    member.value = response.data;
  } catch (err) {
    console.error('Failed to fetch member details:', err);
    error.value = '멤버 정보를 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchMemberDetails();
});
</script>