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
          <button @click="router.back()" class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 class="text-white text-lg font-bold tracking-tight opacity-90">프로필 수정</h1>
          <div class="w-9 h-9"></div> <!-- Spacer -->
        </div>

         <!-- Title Section inside Header -->
         <div class="relative z-10 px-6 mt-2 text-center text-white">
            <h2 class="text-2xl font-extrabold mb-1 drop-shadow-sm">내 정보 설정</h2>
            <p class="text-sm text-orange-100 opacity-90 font-medium">
               {{ isInitialSetup ? '서비스 이용을 위해 정보를 입력해주세요.' : '회원님의 프로필 정보를 수정합니다.' }}
            </p>
         </div>
      </div>

      <div class="px-6 -mt-16 relative z-20">
         <form @submit.prevent="submitProfile" class="space-y-6">
           
           <!-- Profile Image Card -->
           <div class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-6 flex justify-center mb-2">
             <div class="relative group">
                <label for="profileImage" class="cursor-pointer">
                  <div class="w-32 h-32 rounded-full border-4 border-orange-50 flex items-center justify-center bg-gray-50 overflow-hidden shadow-inner group-hover:border-[var(--color-primary)] transition-colors duration-300">
                    <img v-if="imageUrl" :src="imageUrl" alt="Profile Preview" class="w-full h-full object-cover" />
                    <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 text-gray-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M5 12h14"/><path d="M12 5v14"/></svg>
                  </div>
                  <!-- Camera Icon Badge -->
                  <div class="absolute bottom-1 right-1 bg-[var(--color-primary)] text-white p-2 rounded-full shadow-md hover:bg-orange-500 transition-transform hover:scale-110">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"></path><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
                  </div>
                </label>
                <input type="file" @change="handleFileChange" id="profileImage" accept="image/*" class="hidden" />
             </div>
           </div>

           <!-- Form Fields Container -->
           <div class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-6 space-y-5">
              
              <!-- Name -->
              <div>
                <label for="name" class="eeum-label">이름</label>
                <input ref="nameInput" type="text" v-model="profile.name" id="name" required placeholder="이름을 입력하세요" @keydown.enter.prevent="phoneInput.focus()" class="eeum-input" />
              </div>

              <!-- Phone -->
              <div>
                <label for="phone" class="eeum-label">전화번호</label>
                <input ref="phoneInput" type="tel" v-model="profile.phone" id="phone" placeholder="01012345678" maxlength="13" @keydown.enter.prevent="birthYearInput.focus()" class="eeum-input" />
              </div>

               <!-- Birth Date -->
               <div>
                  <label class="eeum-label">생년월일</label>
                  <div class="flex items-center gap-2">
                    <input ref="birthYearInput" type="text" inputmode="numeric" v-model="birthYear" placeholder="YYYY" maxlength="4" @keydown.enter.prevent="birthMonthInput.focus()" class="eeum-input text-center px-1" />
                    <span class="text-gray-300 font-bold self-center">/</span>
                    <input ref="birthMonthInput" type="text" inputmode="numeric" v-model="birthMonth" placeholder="MM" maxlength="2" @keydown.enter.prevent="birthDayInput.focus()" class="eeum-input text-center px-1" />
                    <span class="text-gray-300 font-bold self-center">/</span>
                    <input ref="birthDayInput" type="text" inputmode="numeric" v-model="birthDay" placeholder="DD" maxlength="2" @keydown.enter.prevent="genderRadioGroupFocus()" class="eeum-input text-center px-1" />
                  </div>
               </div>

                <!-- Gender -->
                <div>
                   <label class="eeum-label">성별</label>
                   <div class="grid grid-cols-2 gap-3" ref="genderRadioGroup">
                     <label class="relative cursor-pointer group">
                       <input type="radio" v-model="profile.gender" value="M" name="gender" class="sr-only peer" @change="addressInput.focus()" />
                       <div class="py-3 text-center rounded-2xl border border-gray-200 bg-gray-50 text-gray-400 font-medium peer-checked:border-[var(--color-primary)] peer-checked:bg-orange-50 peer-checked:text-[var(--color-primary)] peer-checked:font-bold transition-all shadow-sm">
                           남성
                       </div>
                     </label>
                     <label class="relative cursor-pointer group">
                       <input type="radio" v-model="profile.gender" value="F" name="gender" class="sr-only peer" @change="addressInput.focus()" />
                       <div class="py-3 text-center rounded-2xl border border-gray-200 bg-gray-50 text-gray-400 font-medium peer-checked:border-[var(--color-primary)] peer-checked:bg-orange-50 peer-checked:text-[var(--color-primary)] peer-checked:font-bold transition-all shadow-sm">
                           여성
                       </div>
                     </label>
                   </div>
                </div>

                <!-- Address -->
                <div>
                  <label for="address" class="eeum-label">주소</label>
                  <div class="flex gap-2 mb-2">
                    <input ref="addressInput" type="text" v-model="profile.address" id="address" placeholder="주소 검색" readonly @click="openAddressSearch" class="eeum-input cursor-pointer bg-gray-50 text-gray-600" />
                    <button @click.prevent="openAddressSearch" type="button" class="w-20 bg-gray-800 text-white font-medium rounded-2xl hover:bg-gray-700 transition flex-shrink-0 text-sm shadow-md">
                        검색
                    </button>
                  </div>
                  <input ref="detailAddressInput" type="text" v-model="detailAddress" placeholder="상세주소를 입력하세요" @keydown.enter.prevent="submitProfile" class="eeum-input" />
                </div>
           </div>

           <!-- Submit Button -->
           <div class="pt-2">
             <button type="submit" :disabled="isLoading" class="w-full py-4 rounded-2xl bg-[var(--color-primary)] text-white font-bold text-lg shadow-xl shadow-orange-200 hover:bg-orange-600 hover:shadow-orange-300 active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed">
               {{ isLoading ? '저장 중...' : '저장하기' }}
             </button>
           </div>
           
           <!-- Error Message -->
           <div v-if="errorMessage" class="flex items-center p-4 text-sm text-red-800 rounded-2xl bg-red-50 border border-red-100 shadow-sm animate-pulse" role="alert">
               <svg class="flex-shrink-0 inline w-4 h-4 mr-3" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" fill="currentColor" viewBox="0 0 20 20"><path d="M10 .5a9.5 9.5 0 1 0 9.5 9.5A9.51 9.51 0 0 0 10 .5ZM9.5 4a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3ZM12 15H8a1 1 0 0 1 0-2h1v-3H8a1 1 0 0 1 0-2h2a1 1 0 0 1 1 1v4h1a1 1 0 0 1 0 2Z"/></svg>
               <span class="font-medium">{{ errorMessage }}</span>
           </div>
         </form>
      </div>

      <!-- Address Modal -->
      <Teleport to="body">
        <div v-if="showAddressModal" class="fixed inset-0 z-[9999] overflow-y-auto bg-black/60 backdrop-blur-sm" @click="showAddressModal = false">
          
          <div class="flex min-h-full items-center justify-center p-4">
          <!-- Modal Panel -->
          <div 
            class="relative z-10 bg-white rounded-3xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col min-h-[500px] pointer-events-auto" 
            role="dialog" 
            aria-modal="true"
            @click.stop
          >
            <!-- Header -->
            <div 
              class="p-5 border-b border-gray-100 flex justify-between items-center bg-gray-50 flex-shrink-0"
            >
                <h3 class="text-lg font-bold text-gray-800">주소 검색</h3>
                <button 
                  @click="showAddressModal = false" 
                  class="w-8 h-8 rounded-full bg-white text-gray-400 hover:text-gray-600 hover:bg-gray-100 flex items-center justify-center transition shadow-sm border border-gray-100"
                >
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                </button>
            </div>
            <!-- Content -->
            <div id="postcode-layer" ref="addressApiWrapper" class="w-full bg-white overflow-y-auto h-[500px]">
              <!-- Daum Postcode will be embedded here -->
            </div>
          </div>
          </div>
        </div>
      </Teleport>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick, computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useUserStore } from '../stores/user';
import { useFamilyStore } from '../stores/family';
import { updateUserProfile } from '../services/api';
import { useRouter, useRoute } from 'vue-router';
import { useModalStore } from '@/stores/modal';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const familyStore = useFamilyStore(); // Initialize familyStore
const modalStore = useModalStore();
const { profile: userProfile } = storeToRefs(userStore);

const profile = ref({ name: '', phone: '', gender: 'M', address: '' });
const detailAddress = ref('');
const birthYear = ref('');
const birthMonth = ref('');
const birthDay = ref('');
const profileFile = ref(null);
const imageUrl = ref(null);
const isLoading = ref(false);
const errorMessage = ref('');
const showAddressModal = ref(false);

const isInitialSetup = computed(() => route.query.flow === 'initial');

const nameInput = ref(null);
const phoneInput = ref(null);
const birthYearInput = ref(null);
const birthMonthInput = ref(null);
const birthDayInput = ref(null);
const addressInput = ref(null);
const detailAddressInput = ref(null);
const genderRadioGroup = ref(null);
const addressApiWrapper = ref(null);


watch(userProfile, (newUserProfile) => {
  if (newUserProfile) {
    profile.value = {
      name: newUserProfile.name || '',
      phone: newUserProfile.phone || '',
      gender: newUserProfile.gender || 'M',
      address: newUserProfile.address || '',
    };
    if (newUserProfile.birthDate) {
      const [year, month, day] = newUserProfile.birthDate.split('-');
      birthYear.value = year;
      birthMonth.value = month;
      birthDay.value = day;
    }
    if (newUserProfile.profileImage) {
      imageUrl.value = newUserProfile.profileImage;
    }
    if (newUserProfile.address) {
        const parts = newUserProfile.address.split(' ');
        if (parts.length > 2) {
             const mainAddress = parts.slice(0, -1).join(' ');
             const detail = parts.slice(-1).join(' ');
             profile.value.address = mainAddress;
             detailAddress.value = detail;
        } else {
            profile.value.address = newUserProfile.address;
            detailAddress.value = '';
        }
    }
  }
}, { immediate: true });

onMounted(async () => {
  if (!userStore.isAuthenticated) {
    await userStore.fetchUser();
  }
});


watch(() => profile.value.phone, (newPhone) => {
  const digits = newPhone.replace(/\D/g, '');
  let formatted = '';
  if (digits.length <= 3) formatted = digits;
  else if (digits.length <= 7) formatted = `${digits.slice(0, 3)}-${digits.slice(3)}`;
  else formatted = `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`;
  profile.value.phone = formatted;
});

const genderRadioGroupFocus = () => {
  genderRadioGroup.value?.querySelector('input[type="radio"]')?.focus();
};

const handleFileChange = (event) => {
  const file = event.target.files[0];
  if (file) {
    profileFile.value = file;
    imageUrl.value = URL.createObjectURL(file);
  }
};

const openAddressSearch = () => {
  showAddressModal.value = true;
};

watch(showAddressModal, (isShown) => {
  if (isShown) {
    document.body.style.overflow = 'hidden';
    // Wait for render
    setTimeout(() => {
      const container = document.getElementById('postcode-layer');
      
      if (!window.daum || !window.daum.Postcode) {
          modalStore.openAlert('주소 검색 서비스를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.');
          showAddressModal.value = false;
          return;
      }

      if (container) {
        container.innerHTML = ''; // Clear
        container.style.display = 'block';
        
        new window.daum.Postcode({
          oncomplete: (data) => {
            profile.value.address = data.address;
            showAddressModal.value = false;
            nextTick(() => detailAddressInput.value?.focus());
          },
          width: '100%',
          height: '100%',
        }).embed(container);
      } else {
         console.error('Postcode container not found');
      }
    }, 100);
  } else {
    document.body.style.overflow = '';
  }
});


const submitProfile = async () => {
  // 1. 저장 중 중복 클릭 방지
  if (isLoading.value) return;
  isLoading.value = true;
  errorMessage.value = '';

  // 2. 생년월일 데이터 포맷팅
  let birthDate = null;
  if (birthYear.value && birthMonth.value && birthDay.value) {
    const month = String(birthMonth.value).padStart(2, '0');
    const day = String(birthDay.value).padStart(2, '0');
    birthDate = `${birthYear.value}-${month}-${day}`;
  }

  // 3. 전체 주소 문자열 생성
  const fullAddress = profile.value.address + (detailAddress.value ? ` ${detailAddress.value}` : '');

  // 4. API 전송용 객체 생성
  const requestDto = {
    name: profile.value.name,
    phone: profile.value.phone,
    birthDate: birthDate,
    gender: profile.value.gender,
    address: fullAddress,
  };

  // 5. FormData 생성 (이미지 포함 전송을 위함)
  const formData = new FormData();
  formData.append('request', new Blob([JSON.stringify(requestDto)], { type: 'application/json' }));
  if (profileFile.value) formData.append('file', profileFile.value);

  try {
    // 6. 서버에 프로필 업데이트 요청
    await updateUserProfile(formData);
    
    // 7. Pinia 스토어의 사용자 정보를 최신화 (홈 화면에서 바뀐 이름/사진을 바로 보여주기 위해)
    await userStore.fetchUser(true);
    
    // [Fix] 현재 선택된 가족의 멤버 리스트도 강제 갱신하여 헤더에 즉시 반영
    if (familyStore.selectedFamily && familyStore.selectedFamily.id) {
        await familyStore.fetchMembers(familyStore.selectedFamily.id, true);
    }
    
    if (isInitialSetup.value) {
      router.push({ name: 'VoiceRegistration', query: { flow: 'initial' } });
    } else {
      // Redirect to Member Detail Page (e.g., /members/4/7)
      const familyId = familyStore.selectedFamily?.id;
      const userId = userStore.profile?.id;
      
      if (familyId && userId) {
          router.push({ name: 'MemberDetail', params: { familyId, userId } });
      } else {
          router.push('/');
      }
    }

  } catch (error) {
    // 에러 발생 시 처리
    console.error(error);
    errorMessage.value = error.response?.data?.message || '프로필 업데이트에 실패했습니다.';
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped>
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-up {
  animation: fadeInUp 0.3s ease-out forwards;
}

.eeum-label {
    @apply block text-sm font-bold text-gray-700 mb-2 ml-1;
}

.eeum-input {
    @apply w-full px-4 py-3.5 rounded-2xl border border-gray-200 bg-gray-50 focus:bg-white focus:border-[var(--color-primary)] focus:ring-4 focus:ring-orange-100/50 transition-all outline-none text-gray-800 placeholder-gray-400 font-medium;
}
</style>
