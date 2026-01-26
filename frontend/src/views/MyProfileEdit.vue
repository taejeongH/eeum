<template>
  <div class="w-full min-h-screen bg-white font-sans">
    <div class="w-full max-w-lg mx-auto p-6">
      
      <div class="pt-12 pb-8 text-center sticky top-0 bg-white z-10">
        <h2 class="text-3xl font-extrabold text-gray-900">내 프로필 설정</h2>
        <p class="text-lg text-gray-600 mt-2">{{ isInitialSetup ? '사용자님의 정보를 입력해주세요.' : '회원님의 프로필 정보를 수정합니다.' }}</p>
      </div>

      <form @submit.prevent="submitProfile" class="space-y-6 px-6 pb-8">
        
        <div class="flex justify-center py-4">
          <label for="profileImage" class="cursor-pointer">
            <div class="w-40 h-40 rounded-full border-4 border-dashed border-gray-200 flex items-center justify-center text-gray-400 hover:border-primary hover:text-primary transition-colors bg-gray-50">
              <img v-if="imageUrl" :src="imageUrl" alt="Profile Preview" class="w-full h-full rounded-full object-cover" />
              <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-16 h-16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1"><path d="M5 12h14"/><path d="M12 5v14"/></svg>
            </div>
          </label>
          <input type="file" @change="handleFileChange" id="profileImage" accept="image/*" class="hidden" />
        </div>

        <div>
          <label for="name" class="block text-sm font-medium text-gray-700 mb-1">이름</label>
          <input ref="nameInput" type="text" v-model="profile.name" id="name" required placeholder="이름을 입력하세요" @keydown.enter.prevent="phoneInput.focus()" class="input-base" />
        </div>

        <div>
          <label for="phone" class="block text-sm font-medium text-gray-700 mb-1">전화번호</label>
          <input ref="phoneInput" type="tel" v-model="profile.phone" id="phone" placeholder="01012345678" maxlength="13" @keydown.enter.prevent="birthYearInput.focus()" class="input-base" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">생년월일</label>
          <div class="flex items-center gap-2">
            <input ref="birthYearInput" type="text" inputmode="numeric" v-model="birthYear" placeholder="YYYY" maxlength="4" @keydown.enter.prevent="birthMonthInput.focus()" class="input-base text-center" />
            <span class="text-gray-400">/</span>
            <input ref="birthMonthInput" type="text" inputmode="numeric" v-model="birthMonth" placeholder="MM" maxlength="2" @keydown.enter.prevent="birthDayInput.focus()" class="input-base text-center" />
            <span class="text-gray-400">/</span>
            <input ref="birthDayInput" type="text" inputmode="numeric" v-model="birthDay" placeholder="DD" maxlength="2" @keydown.enter.prevent="genderRadioGroupFocus()" class="input-base text-center" />
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">성별</label>
          <div class="grid grid-cols-2 gap-4" ref="genderRadioGroup">
            <label class="relative">
              <input type="radio" v-model="profile.gender" value="M" name="gender" class="sr-only peer" @change="addressInput.focus()" />
              <div class="p-2 text-center rounded-lg border border-gray-200 cursor-pointer peer-checked:border-primary peer-checked:bg-primaryBg peer-checked:text-primary peer-checked:font-semibold transition-all">남성</div>
            </label>
            <label class="relative">
              <input type="radio" v-model="profile.gender" value="F" name="gender" class="sr-only peer" @change="addressInput.focus()" />
              <div class="p-2 text-center rounded-lg border border-gray-200 cursor-pointer peer-checked:border-primary peer-checked:bg-primaryBg peer-checked:text-primary peer-checked:font-semibold transition-all">여성</div>
            </label>
          </div>
        </div>

        <div>
          <label for="address" class="block text-sm font-medium text-gray-700 mb-1">주소</label>
          <div class="flex gap-2">
            <input ref="addressInput" type="text" v-model="profile.address" id="address" placeholder="주소 검색" readonly @click="openAddressSearch" class="input-base cursor-pointer bg-gray-100" />
            <button @click.prevent="openAddressSearch" type="button" class="w-28 bg-primaryBg text-primary font-semibold py-2 px-4 rounded-lg hover:bg-primary/90 flex-shrink-0">검색</button>
          </div>
          <input ref="detailAddressInput" type="text" v-model="detailAddress" placeholder="상세주소를 입력하세요" @keydown.enter.prevent="submitProfile" class="input-base mt-2" />
        </div>

        <div class="pt-4">
          <button type="submit" :disabled="isLoading" class="btn-primary py-3">
            {{ isLoading ? '저장 중...' : '저장하기' }}
          </button>
        </div>
        
        <div v-if="errorMessage" class="flex items-center p-4 mt-4 text-sm text-red-800 rounded-lg bg-red-50" role="alert">
            <svg class="flex-shrink-0 inline w-4 h-4 mr-3" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" fill="currentColor" viewBox="0 0 20 20"><path d="M10 .5a9.5 9.5 0 1 0 9.5 9.5A9.51 9.51 0 0 0 10 .5ZM9.5 4a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3ZM12 15H8a1 1 0 0 1 0-2h1v-3H8a1 1 0 0 1 0-2h2a1 1 0 0 1 1 1v4h1a1 1 0 0 1 0 2Z"/></svg>
            <span class="font-medium">{{ errorMessage }}</span>
        </div>
      </form>
    </div>

    <div v-if="showAddressModal" class="fixed inset-0 bg-black bg-opacity-50 z-40 flex justify-center items-center">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-lg">
        <div class="p-4 border-b flex justify-between items-center">
            <h3 class="text-lg font-semibold">주소 검색</h3>
            <button @click="showAddressModal = false" class="text-2xl font-light">&times;</button>
        </div>
        <div ref="addressApiWrapper" class="h-[500px] overflow-y-auto">
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick, computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useUserStore } from '../stores/user';
import { updateUserProfile } from '../services/api';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
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
    nextTick(() => {
      if (window.daum && window.daum.Postcode && addressApiWrapper.value) {
        new window.daum.Postcode({
          oncomplete: (data) => {
            profile.value.address = data.address;
            showAddressModal.value = false;
            detailAddressInput.value.focus();
          },
          width: '100%',
          height: '100%',
        }).embed(addressApiWrapper.value);
      } else {
        alert('주소 검색 API를 불러오지 못했습니다. 페이지를 새로고침해주세요.');
        showAddressModal.value = false;
      }
    });
  }
});


const submitProfile = async () => {
  if (isLoading.value) return;
  isLoading.value = true;
  errorMessage.value = '';

  const formData = new FormData();
  let birthDate = null;
  if (birthYear.value && birthMonth.value && birthDay.value) {
    const month = String(birthMonth.value).padStart(2, '0');
    const day = String(birthDay.value).padStart(2, '0');
    birthDate = `${birthYear.value}-${month}-${day}`;
  }

  const fullAddress = profile.value.address + (detailAddress.value ? ` ${detailAddress.value}` : '');

  const requestDto = {
      name: profile.value.name,
      phone: profile.value.phone,
      birthDate: birthDate,
      gender: profile.value.gender,
      address: fullAddress,
  };
  
  formData.append('request', new Blob([JSON.stringify(requestDto)], { type: 'application/json' }));
  if (profileFile.value) formData.append('file', profileFile.value);

  try {
    await updateUserProfile(formData);
    await userStore.fetchUser();
    
    if (isInitialSetup.value) {
      router.push('/voice-sample');
    } else {
      router.push('/my-profile-view');
    }

  } catch (error) {
    errorMessage.value = error.response?.data?.message || '프로필 업데이트에 실패했습니다.';
  } finally {
    isLoading.value = false;
  }
};
</script>
