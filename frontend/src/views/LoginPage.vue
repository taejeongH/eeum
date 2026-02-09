<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

onMounted(() => {
  
  if (userStore.isAuthenticated) {
    router.replace('/home');
  }
});
</script>
