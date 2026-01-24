<template>
  <div class="flex items-center justify-center min-h-screen">
    <p class="text-lg">로그인 중...</p>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();

onMounted(async () => {
  const loggedIn = await userStore.fetchUser();

  if (loggedIn) {
    if (!userStore.profile.phone) {
      router.push({ path: '/my-profile-edit', query: { flow: 'initial' } });
    } else {
      router.push('/my-profile-view');
    }
  } else {
    router.push('/');
  }
});
</script>
