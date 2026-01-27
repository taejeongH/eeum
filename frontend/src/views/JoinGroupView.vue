<template>
  <div class="flex flex-col items-center justify-center min-h-screen bg-gray-50">
    <!-- 로딩 -->
    <div v-if="loading" class="text-center p-8 max-w-sm mx-auto bg-white rounded-xl shadow-md space-y-6">
      <p>초대 정보를 불러오는 중입니다...</p>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center text-red-500 p-8 max-w-sm mx-auto bg-white rounded-xl shadow-md space-y-6">
      <p>{{ error }}</p>
      <router-link to="/home" class="text-blue-500 hover:underline">홈으로 이동</router-link>
    </div>

    <!-- 확인 화면 -->
    <div v-else-if="inviteInfo" class="text-center p-8 max-w-sm mx-auto bg-white rounded-xl shadow-md space-y-6">
      <p class="text-lg font-semibold">
        {{ inviteInfo.inviterName }} 님이
      </p>
      <p>
        <strong class="text-primary">{{ inviteInfo.familyName }}</strong>
        그룹에 초대했습니다.
      </p>
      <p>참여하시겠습니까?</p>

      <div class="flex justify-center gap-4 mt-6">
        <button
          class="px-4 py-2 border rounded-md"
          @click="router.push('/home')"
        >
          취소
        </button>
        <button
          class="px-4 py-2 bg-primary text-white rounded-md"
          :disabled="joining"
          @click="joinGroup"
        >
          {{ joining ? '참여 중...' : '참여하기' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import api, { joinFamilyWithCode } from '@/services/api';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(true);
const error = ref(null);
const inviteInfo = ref(null);
const joining = ref(false);

const inviteCode = route.query.code;

/**
 *  초대 정보 미리보기
 */
const fetchInvitePreview = async () => {
  if (!inviteCode) {
    error.value = '유효하지 않은 초대 링크입니다.';
    loading.value = false;
    return;
  }

  if (!userStore.isAuthenticated) {
    sessionStorage.setItem('redirectAfterLogin', `/join?code=${inviteCode}`);
    router.replace('/login');
    return;
  }

  try {
    const res = await api.get('/families/join/preview', {
      params: { code: inviteCode },
    });
    inviteInfo.value = res.data;
  } catch (e) {
    error.value = '초대 정보를 불러올 수 없습니다.';
  } finally {
    loading.value = false;
  }
};

/**
 *  실제 그룹 참여
 */
const joinGroup = async () => {
  joining.value = true;
  try {
    await joinFamilyWithCode(inviteCode);
    alert('그룹에 성공적으로 참여했습니다!');
    router.replace('/home');
  } catch (e) {
    error.value = '그룹 참여에 실패했습니다.';
  } finally {
    joining.value = false;
  }
};

onMounted(fetchInvitePreview);
</script>
