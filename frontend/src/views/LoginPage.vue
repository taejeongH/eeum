<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

onMounted(() => {
  // 이미 로그인 상태일 때만, 그리고 딱 한 번
  if (userStore.isAuthenticated) {
    router.replace('/home');
  }
});
</script>
