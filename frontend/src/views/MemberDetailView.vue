<template>
  <div class="bg-gray-50 min-h-screen pb-10" @click="showMenu = false">
    
    <!-- Premium Header Area -->
    <div class="relative w-full h-52 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0">
      <!-- Gradient Overlay -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>
      
      <!-- ID Pattern (Decorative) -->
      <div class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10" 
           style="background-image: radial-gradient(#fff 1px, transparent 1px); background-size: 24px 24px;"></div>

      <!-- Navigation Bar -->
      <div class="relative z-30 flex justify-between items-start p-5 pt-6">
        <button @click="goBack" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
          </svg>
        </button>

        <!-- Menu Button & Dropdown -->
        <div v-if="canShowMenu" class="relative">
          <button 
            @click.stop="showMenu = !showMenu" 
            class="p-2 -mr-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path>
            </svg>
          </button>

          <transition name="fade-slide">
             <div 
              v-if="showMenu"
              class="absolute right-0 mt-3 w-48 bg-white/90 backdrop-blur-xl rounded-2xl shadow-xl shadow-gray-200/50 border border-white/50 overflow-hidden py-1.5 z-50 origin-top-right ring-1 ring-black/5"
            >
              <template v-if="isViewingSelf">
                <button 
                  @click="goToEditProfile"
                  class="w-full text-left px-5 py-3 text-sm font-semibold text-gray-700 hover:bg-orange-50 active:bg-orange-100 transition flex items-center gap-3"
                >
                  <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path></svg>
                  프로필 수정
                </button>
                <div class="h-px bg-gray-100 mx-4 my-0.5"></div>
                <button 
                  @click="leaveGroup"
                  class="w-full text-left px-5 py-3 text-sm font-semibold text-red-500 hover:bg-red-50 active:bg-red-100 transition flex items-center gap-3"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path></svg>
                  탈퇴하기
                </button>
              </template>

              <template v-else-if="member && member.currentUserOwner">
                <button 
                  @click="kickMember"
                  class="w-full text-left px-5 py-3 text-sm font-semibold text-red-500 hover:bg-red-50 active:bg-red-100 transition flex items-center gap-3"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7a4 4 0 11-8 0 4 4 0 018 0zM9 14a6 6 0 00-6 6v1h12v-1a6 6 0 00-6-6zM21 12h-6"></path></svg>
                  내보내기
                </button>
              </template>
            </div>
          </transition>
         
        </div>
      </div>
    </div>

    <!-- Content Area -->
    <div class="px-5 -mt-24 relative z-20">
      
      <!-- Loading / Error States -->
      <div v-if="loading" class="bg-white rounded-3xl shadow-lg p-10 text-center min-h-[300px] flex items-center justify-center">
         <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-[var(--color-primary)]"></div>
      </div>
      <div v-else-if="error" class="bg-white rounded-3xl shadow-lg p-10 text-center min-h-[300px] flex flex-col items-center justify-center gap-4">
        <p class="text-gray-500 font-medium">{{ error }}</p>
        <button @click="router.go(-1)" class="text-[var(--color-primary)] underline text-sm">돌아가기</button>
      </div>

      <template v-else-if="member">
        <!-- New Profile Card Design -->
        <div class="bg-white/80 backdrop-blur-xl rounded-3xl shadow-xl shadow-gray-200/60 p-6 flex flex-col items-center relative overflow-hidden mb-6 border border-white/50">
          <!-- Profile Image -->
          <div class="w-28 h-28 rounded-full p-1 bg-white shadow-xl mb-3 ring-4 ring-orange-100/50">
            <img class="w-full h-full object-cover rounded-full" :src="member.profileImage || '/default-profile.png'" alt="Profile">
          </div>
          
          <div class="text-center w-full">
            <!-- Badge -->
            <div class="inline-flex items-center gap-1.5 px-3 py-1 bg-orange-50 text-[var(--color-primary)] rounded-full mb-2">
                <span class="w-1.5 h-1.5 rounded-full bg-[var(--color-primary)]"></span>
                <span class="text-xs font-bold tracking-wide">{{ member.dependent ? '피부양자' : (member.relationship || '부양자') }}</span>
            </div>
            
            <!-- Name -->
            <h1 class="text-2xl font-bold text-gray-900 mb-1 leading-tight">{{ member.name }}</h1>
            <p class="text-sm text-gray-400 font-medium">가족 멤버</p>
          </div>
        </div>

        <!-- Info Card: Basic -->
        <div class="bg-white rounded-3xl shadow-sm border border-gray-100/50 p-6 mb-4">
          <h2 class="text-lg font-bold text-gray-800 mb-5 flex items-center gap-2">
            <span class="w-1 h-5 rounded-full bg-[var(--color-primary)]"></span>
            기본 정보
          </h2>
          
          <div class="grid gap-1">
            <!-- Phone -->
            <div class="flex items-center gap-4 p-3 rounded-2xl hover:bg-gray-50 transition group">
              <div class="w-10 h-10 rounded-xl bg-orange-50 flex items-center justify-center flex-shrink-0 text-[var(--color-primary)] group-hover:scale-110 transition-transform">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-[11px] text-gray-400 font-medium uppercase tracking-wider">Mobile</span>
                <span class="text-[15px] font-semibold text-gray-800">{{ member.phone }}</span>
              </div>
            </div>
            
            <!-- Birth -->
            <div class="flex items-center gap-4 p-3 rounded-2xl hover:bg-gray-50 transition group">
              <div class="w-10 h-10 rounded-xl bg-orange-50 flex items-center justify-center flex-shrink-0 text-[var(--color-primary)] group-hover:scale-110 transition-transform">
               <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"></path></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-[11px] text-gray-400 font-medium uppercase tracking-wider">Birthday</span>
                <span class="text-[15px] font-semibold text-gray-800 flex items-baseline gap-1">
                  {{ member.birthDate }}
                  <span class="text-[var(--color-primary)] text-xs font-bold bg-orange-50 px-1.5 py-0.5 rounded-md">
                    {{ calculateAge(member.birthDate) }}세
                  </span>
                </span>
              </div>
            </div>

            <!-- Gender -->
            <div class="flex items-center gap-4 p-3 rounded-2xl hover:bg-gray-50 transition group">
              <div class="w-10 h-10 rounded-xl bg-orange-50 flex items-center justify-center flex-shrink-0 text-[var(--color-primary)] group-hover:scale-110 transition-transform">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-[11px] text-gray-400 font-medium uppercase tracking-wider">Gender</span>
                <span class="text-[15px] font-semibold text-gray-800">{{ member.gender === 'M' ? '남성' : '여성' }}</span>
              </div>
            </div>

             <!-- Address -->
            <div class="flex items-center gap-4 p-3 rounded-2xl hover:bg-gray-50 transition group">
              <div class="w-10 h-10 rounded-xl bg-orange-50 flex items-center justify-center flex-shrink-0 text-[var(--color-primary)] group-hover:scale-110 transition-transform">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-[11px] text-gray-400 font-medium uppercase tracking-wider">Address</span>
                <span class="text-[15px] font-semibold text-gray-800 leading-snug">{{ member.address }}</span>
              </div>
            </div>
          </div>
        </div>

      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import api from '@/services/api';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const member = ref(null);
const loading = ref(false);
const error = ref(null);
const showMenu = ref(false);

const goBack = () => {
    router.go(-1);
};

const isViewingSelf = computed(() => {
  const profileId = userStore.profile?.id;
  const memberId = member.value?.userId;
  if (!profileId || !memberId) return false;
  return Number(profileId) === Number(memberId);
});

const canShowMenu = computed(() => {
  // if (loading.value || error.value) return false; // Optional logic
  if (!member.value) return false;
  if (isViewingSelf.value) return true;
  if (member.value.currentUserOwner) return true;
  return false;
});

// ✅ 나이 계산 함수
const calculateAge = (birthDateString) => {
  if (!birthDateString) return 0;
  
  const today = new Date();
  const birthDate = new Date(birthDateString);
  
  let age = today.getFullYear() - birthDate.getFullYear();
  const m = today.getMonth() - birthDate.getMonth();
  
  if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
    age--;
  }
  return age;
};

const goToEditProfile = () => {
  router.push('/my-profile-edit'); 
};

const leaveGroup = async () => {
  if (confirm('정말로 그룹을 탈퇴하시겠습니까?')) {
    const { familyId } = route.params;
    try {
      await api.delete(`/families/${familyId}/members/me`); 
      alert('그룹에서 탈퇴했습니다.');
      router.push('/home');
    } catch (err) {
      console.error('Failed to leave group:', err);
      alert('그룹 탈퇴에 실패했습니다.');
    }
  }
};

const kickMember = async () => {
  if (confirm(`정말로 '${member.value.name}'님을 그룹에서 강퇴하시겠습니까?`)) {
    const { familyId, userId } = route.params;
    try {
      await api.delete(`/families/${familyId}/members/${userId}`);
      alert('멤버를 성공적으로 강퇴했습니다.');
      router.go(-1);
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

<style scoped>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.2s ease-out;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}
</style>