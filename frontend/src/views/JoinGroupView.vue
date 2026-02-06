<template>
  <div class="min-h-screen bg-gray-50 font-sans pb-10">
    <div class="w-full max-w-lg mx-auto">
      
      <!-- Premium Header Area -->
      <div class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0 mb-6 z-0">
        <!-- Gradient Overlay -->
        <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
        
        <!-- ID Pattern (Decorative) -->
        <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
             style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>

        <!-- Navigation Bar -->
        <div class="relative z-10 flex justify-between items-center p-5 pt-6">
           <!-- Empty for balance or home button if needed -->
           <div class="w-9 h-9"></div>
           <h1 class="text-white text-lg font-bold tracking-tight opacity-90">초대장 도착</h1>
           <div class="w-9 h-9"></div>
        </div>

         <!-- Title Section inside Header -->
         <div class="relative z-10 px-6 mt-2 text-center text-white">
            <h2 class="text-2xl font-extrabold mb-1 drop-shadow-sm">가족 그룹 참여</h2>
            <p class="text-sm text-orange-100 opacity-90 font-medium">
               소중한 가족과 함께 일상을 공유해보세요.
            </p>
         </div>
      </div>

      <div class="px-6 -mt-16 relative z-10">
        <!-- Loading State -->
        <div v-if="loading" class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-8 text-center space-y-4 min-h-[300px] flex flex-col items-center justify-center">
             <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-[var(--color-primary)]"></div>
             <p class="text-gray-500 font-medium">초대 정보를 확인하고 있습니다...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-8 text-center space-y-6">
            <div class="w-16 h-16 bg-red-50 rounded-full flex items-center justify-center mx-auto mb-4">
                 <svg class="w-8 h-8 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
            </div>
            <h3 class="text-xl font-bold text-gray-800">초대장 오류</h3>
            <p class="text-gray-600 word-break-keep">{{ error }}</p>
            <button @click="router.push('/home')" class="w-full py-4 rounded-2xl bg-gray-100 text-gray-700 font-bold hover:bg-gray-200 transition">
                홈으로 돌아가기
            </button>
        </div>

        <!-- Invite Info Card -->
        <div v-else-if="inviteInfo" class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-8 text-center space-y-8 animate-fade-in-up">
            
            <!-- Envelope Decoration -->
            <div class="w-20 h-20 bg-orange-50 rounded-full flex items-center justify-center mx-auto shadow-inner">
                 <svg class="w-10 h-10 text-[var(--color-primary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"></path></svg>
            </div>

            <div class="space-y-2">
                 <p class="text-gray-500 font-medium">
                    <span class="text-gray-900 font-bold text-lg">{{ inviteInfo.inviterName }}</span> 님으로부터<br>초대가 도착했습니다.
                 </p>
            </div>

            <div class="bg-gray-50 rounded-2xl p-6 border border-gray-100">
                <p class="text-sm text-gray-400 mb-1">초대된 그룹</p>
                <h3 class="text-2xl font-extrabold text-[var(--color-primary)]">{{ inviteInfo.familyName }}</h3>
            </div>

            <div class="space-y-3 pt-4">
                <button
                  class="w-full py-4 rounded-2xl bg-[var(--color-primary)] text-white font-bold text-lg shadow-xl shadow-orange-200 hover:bg-orange-600 hover:shadow-orange-300 active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                  :disabled="joining"
                  @click="joinGroup"
                >
                  {{ joining ? '참여하는 중...' : '그룹 참여하기' }}
                </button>
                
                <button
                  class="w-full py-4 rounded-2xl bg-white text-gray-400 font-medium hover:bg-gray-50 hover:text-gray-600 transition"
                  @click="router.push('/home')"
                >
                  나중에 하기
                </button>
            </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import api, { joinFamilyWithCode } from '@/services/api';
import { useUserStore } from '@/stores/user';
import { useModalStore } from '@/stores/modal';
import { useFamilyStore } from '@/stores/family';
import { useGroupSetupStore } from '@/stores/groupSetup';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const modalStore = useModalStore();

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
    const response = await joinFamilyWithCode(inviteCode);
    
    // Refresh families and select the new one
    const familyStore = useFamilyStore();
    const setupStore = useGroupSetupStore();
    
    await familyStore.fetchFamilies();
    
    // Select the newly joined family if possible
    if (response && response.data) {
        familyStore.selectFamily(response.data);
    }
    
    setupStore.reset();
    
    await modalStore.openAlert('그룹에 성공적으로 참여했습니다!');
    router.replace('/home');
  } catch (e) {
    error.value = '그룹 참여에 실패했습니다.';
  } finally {
    joining.value = false;
  }
};

onMounted(fetchInvitePreview);
</script>
