<template>
  <div class="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 flex justify-around items-end z-50 transition-all duration-300"
       style="height: calc(5rem + var(--sab)); padding-bottom: calc(0.75rem + var(--sab));">
    <!-- Message -->
    <button @click="setActive('message')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'message' ? 'text-[#f3532b]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
      </svg>
      <span class="text-xs font-semibold">메시지</span>
    </button>
    
    <!-- Gallery -->
    <button @click="setActive('gallery')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'gallery' ? 'text-[#f3532b]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      <span class="text-xs font-semibold">갤러리</span>
    </button>
  
    <!-- Center Home Button (Floating Effect) -->
    <div class="relative w-1/5 flex justify-center">
      <button 
        @click="setActive('home')" 
        class="absolute -top-12 rounded-full p-5 shadow-xl flex items-center justify-center border-[5px] border-white active:scale-95 transition-transform duration-200"
        :class="activeTab === 'home' ? 'bg-[#f3532b] text-white' : 'bg-white text-[#8d6e63]'"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-9 w-9" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
        </svg>
      </button>
      <span class="text-xs font-bold mt-9 invisible select-none" aria-hidden="true">홈</span>
    </div>

    <!-- Calendar -->
    <button @click="setActive('calendar')" class="flex flex-col items-center justify-end w-1/5 transition-colors" :class="activeTab === 'calendar' ? 'text-[#f3532b]' : 'text-[#8d6e63]'">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      <span class="text-xs font-semibold">일정</span>
    </button>
    
    <!-- Menu Dropdown -->
    <div class="relative w-1/5 flex flex-col items-center">
      <transition name="fade">
        <div v-if="showMenu" class="absolute bottom-full left-1/2 -translate-x-1/2 mb-6 w-24 bg-white shadow-[0_-10px_25px_-5px_rgba(0,0,0,0.1),0_10px_10px_-5px_rgba(0,0,0,0.04)] rounded-3xl border border-gray-100 overflow-hidden z-[60] flex flex-col items-center p-2 py-4 gap-4">
          <button 
            @click="navigateTo('medication')" 
            class="flex flex-col items-center justify-center gap-1.5 transition-colors text-[#8d6e63] hover:text-[#f3532b] w-full"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z" />
            </svg>
            <span class="text-[10px] font-bold uppercase tracking-wider">복약</span>
          </button>
          
          <div class="w-10 h-[1px] bg-orange-50 rounded-full"></div>
          
          <button 
            @click="navigateTo('health')" 
            class="flex flex-col items-center justify-center gap-1.5 transition-colors text-[#8d6e63] hover:text-[#f3532b] w-full"
          >
            <span class="material-symbols-outlined text-3xl">monitor_heart</span>
            <span class="text-[10px] font-bold uppercase tracking-wider">건강</span>
          </button>

          <div class="w-10 h-[1px] bg-orange-50 rounded-full"></div>

          <button 
            @click="navigateTo('realtime-hr')" 
            class="flex flex-col items-center justify-center gap-1.5 transition-colors text-[#8d6e63] hover:text-[#e76f51] w-full"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
            </svg>
            <span class="text-[10px] font-bold uppercase tracking-wider">심박수</span>
          </button>

          <div class="w-10 h-[1px] bg-orange-50 rounded-full"></div>
          
          <button 
            @click="navigateTo('voice')" 
            class="flex flex-col items-center justify-center gap-1.5 transition-colors text-[#8d6e63] hover:text-[#f3532b] w-full"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
            </svg>
            <span class="text-[10px] font-bold uppercase tracking-wider">목소리</span>
          </button>
          
          <div class="w-10 h-[1px] bg-orange-50 rounded-full"></div>
          
          <button 
            @click="handleLogout" 
            class="flex flex-col items-center justify-center gap-1.5 transition-colors text-[#8d6e63] hover:text-[#f3532b] w-full"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
            <span class="text-[10px] font-bold uppercase tracking-wider">로그아웃</span>
          </button>
        </div>
      </transition>

      <button @click="toggleMenu" class="flex flex-col items-center justify-end w-full transition-colors" :class="activeTab === 'menu' || activeTab === 'voice' || activeTab === 'medication' ? 'text-[#f3532b]' : 'text-[#8d6e63]'">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 mb-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
        </svg>
        <span class="text-xs font-semibold">메뉴</span>
      </button>
    </div>

    <!-- Custom Logout Confirmation Modal -->
    <ConfirmModal 
      :show="showLogoutModal"
      title="로그아웃"
      message="정말로 로그아웃하시겠습니까?"
      @confirm="performLogout"
      @cancel="showLogoutModal = false"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useFamilyStore } from '@/stores/family';
import ConfirmModal from '@/components/common/ConfirmModal.vue';
import { useModalStore } from '@/stores/modal';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const modalStore = useModalStore();
const familyStore = useFamilyStore();
const activeTab = ref('home');
const showMenu = ref(false);
const showLogoutModal = ref(false);

const updateActiveTab = () => {
    if (route.path.startsWith('/families') && route.path.includes('/calendar')) {
        activeTab.value = 'calendar';
    } else if (route.path.includes('/gallery')) {
        activeTab.value = 'gallery';
    } else if (route.path === '/home') {
        activeTab.value = 'home';
    } else if (route.path.startsWith('/families') && route.path.includes('/messages')) {
        activeTab.value = 'message';
    } else if (route.path.startsWith('/families') && route.path.includes('/medications')) {
        activeTab.value = 'menu';
    } else if (route.path === '/health-detail') {
        activeTab.value = 'health';
    } else if (route.path === '/health-detail') {
        activeTab.value = 'health';
    } else if (route.path === '/health-detail') {
        activeTab.value = 'health';
    } else if (route.path === '/voice-register') {
        activeTab.value = 'menu';
    } else {
        // default or handle other routes
    }
};

onMounted(updateActiveTab);
watch(() => route.path, updateActiveTab);

const setActive = (tab) => {
  showMenu.value = false;
  activeTab.value = tab;


  if (tab === 'calendar') {
      const familyId = familyStore.selectedFamily?.id;
      if (familyId) {
          router.push({ name: 'CalendarPage', params: { familyId: familyId } });
      } else {
          modalStore.openAlert("가족 정보가 없습니다.");
      }

  } else if (tab === 'home') {
      router.push('/home');
  } else if (tab === 'message') {
      // Navigate to family messages
      // Check if we're already on a message page, if so don't navigate
      if (!route.path.startsWith('/families') || !route.path.includes('/messages')) {
        // Get familyId from store
        const familyId = familyStore.selectedFamily?.id;
        
        if (familyId) {
          router.push(`/families/${familyId}/messages`);
        } else {
          console.error('No familyId found in user profile or storage');
          modalStore.openAlert('가족 정보를 찾을 수 없습니다.');
        }
      }
  } else if (tab === 'gallery') {
      const familyId = familyStore.selectedFamily?.id;
      if (familyId) {
          router.push(`/families/${familyId}/gallery`);
      } else {
          modalStore.openAlert("가족 정보를 찾을 수 없습니다.");
      }
  } else if (tab === 'health') {
      router.push('/health-detail');
  } else if (tab !== 'home') {
     // Mock navigation feedback
  }
};

const toggleMenu = () => {
  showMenu.value = !showMenu.value;
};

const navigateTo = (type) => {
  showMenu.value = false;
  activeTab.value = 'menu';
  
  if (type === 'voice') {
    router.push('/voice-register');
  } else if (type === 'medication') {
    const familyId = familyStore.selectedFamily?.id;
    
    if (familyId) {
      router.push(`/families/${familyId}/medications`);
    } else {
      modalStore.openAlert('가족 정보를 찾을 수 없습니다.');
    }
  } else if (type === 'health') {
    router.push('/health-detail');
  } else if (type === 'realtime-hr') {
    router.push('/realtime-heart-rate');
  }
};

const handleLogout = () => {
  showMenu.value = false;
  showLogoutModal.value = true;
};

const performLogout = () => {
  showLogoutModal.value = false;
  // Clear tokens and profile
  localStorage.removeItem('accessToken');
  localStorage.removeItem('familyId');
  localStorage.removeItem('currentFamilyId');
  sessionStorage.removeItem('accessToken');
  
  userStore.clearUser();
  
  // Redirect to login or onboarding
  router.push('/login');
};
</script>
