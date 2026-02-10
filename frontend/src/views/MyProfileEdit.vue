<template>
  <div class="min-h-screen bg-gray-50 font-sans pb-10">
    <div class="w-full max-w-lg mx-auto">
      <!-- 상단 헤더 영역 -->
      <div
        class="relative w-full h-48 bg-[var(--color-primary)] rounded-b-[2.5rem] shadow-xl overflow-hidden shrink-0 mb-6 z-0"
      >
        <!-- 그라데이션 오버레이 -->
        <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>

        <!-- 배경 패턴 (장식용) -->
        <div
          class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10"
          style="
            background-image: radial-gradient(#fff 1px, transparent 1px);
            background-size: 24px 24px;
          "
        ></div>

        <!-- 상단 네비게이션 바 -->
        <div class="relative z-10 flex justify-between items-center p-5 pt-6">
          <button
            @click="router.back()"
            class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm"
          >
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 19l-7-7 7-7"
              />
            </svg>
          </button>
          <h1 class="text-white text-lg font-bold tracking-tight opacity-90">프로필 수정</h1>
          <div class="w-9 h-9"></div>
          <!-- 좌우 밸런스를 위한 여백 -->
        </div>

        <!-- 헤더 내 타이틀 섹션 -->
        <div class="relative z-10 px-6 mt-2 text-center text-white">
          <h2 class="text-2xl font-extrabold mb-1 drop-shadow-sm">내 정보 설정</h2>
          <p class="text-sm text-orange-100 opacity-90 font-medium">
            {{
              isInitialSetup
                ? '서비스 이용을 위해 정보를 입력해주세요.'
                : '회원님의 프로필 정보를 수정합니다.'
            }}
          </p>
        </div>
      </div>

      <div class="px-6 -mt-16 relative z-20">
        <form @submit.prevent="submitProfile" class="space-y-6">
          <!-- 프로필 이미지 변경 카드 -->
          <div
            class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-6 flex justify-center mb-2"
          >
            <div class="relative group">
              <label for="profileImage" class="cursor-pointer">
                <div
                  class="w-32 h-32 rounded-full border-4 border-orange-50 flex items-center justify-center bg-gray-50 overflow-hidden shadow-inner group-hover:border-[var(--color-primary)] transition-colors duration-300"
                >
                  <img
                    v-if="imageUrl"
                    :src="imageUrl"
                    alt="Profile Preview"
                    class="w-full h-full object-cover"
                  />
                  <svg
                    v-else
                    xmlns="http://www.w3.org/2000/svg"
                    class="w-12 h-12 text-gray-300"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.5"
                  >
                    <path d="M5 12h14" />
                    <path d="M12 5v14" />
                  </svg>
                </div>
                <!-- 카메라 아이콘 배지 -->
                <div
                  class="absolute bottom-1 right-1 bg-[var(--color-primary)] text-white p-2 rounded-full shadow-md hover:bg-orange-500 transition-transform hover:scale-110"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"
                    ></path>
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M15 13a3 3 0 11-6 0 3 3 0 016 0z"
                    ></path>
                  </svg>
                </div>
              </label>
              <input
                type="file"
                @change="handleFileChange"
                id="profileImage"
                accept="image/*"
                class="hidden"
              />
            </div>
          </div>

          <!-- 입력 폼 컨테이너 -->
          <div class="bg-white rounded-3xl shadow-lg shadow-gray-200/50 p-6 space-y-5">
            <!-- 이름 입력 -->
            <div>
              <label for="name" class="eeum-label">이름</label>
              <input
                ref="nameInput"
                type="text"
                v-model="profile.name"
                id="name"
                required
                placeholder="이름을 입력하세요"
                @keydown.enter.prevent="phoneInput.focus()"
                class="eeum-input"
              />
            </div>

            <!-- 전화번호 입력 -->
            <div>
              <label for="phone" class="eeum-label">전화번호</label>
              <input
                ref="phoneInput"
                type="tel"
                v-model="profile.phone"
                id="phone"
                placeholder="01012345678"
                maxlength="13"
                @keydown.enter.prevent="birthYearInput.focus()"
                class="eeum-input"
              />
            </div>

            <!-- 생년월일 입력 -->
            <div>
              <label class="eeum-label">생년월일</label>
              <div class="flex items-center gap-2">
                <input
                  ref="birthYearInput"
                  type="text"
                  inputmode="numeric"
                  v-model="birthYear"
                  placeholder="YYYY"
                  maxlength="4"
                  @keydown.enter.prevent="birthMonthInput.focus()"
                  class="eeum-input text-center px-1"
                />
                <span class="text-gray-300 font-bold self-center">/</span>
                <input
                  ref="birthMonthInput"
                  type="text"
                  inputmode="numeric"
                  v-model="birthMonth"
                  placeholder="MM"
                  maxlength="2"
                  @keydown.enter.prevent="birthDayInput.focus()"
                  class="eeum-input text-center px-1"
                />
                <span class="text-gray-300 font-bold self-center">/</span>
                <input
                  ref="birthDayInput"
                  type="text"
                  inputmode="numeric"
                  v-model="birthDay"
                  placeholder="DD"
                  maxlength="2"
                  @keydown.enter.prevent="genderRadioGroupFocus()"
                  class="eeum-input text-center px-1"
                />
              </div>
            </div>

            <!-- 성별 선택 -->
            <div>
              <label class="eeum-label">성별</label>
              <div class="grid grid-cols-2 gap-3" ref="genderRadioGroup">
                <label class="relative cursor-pointer group">
                  <input
                    type="radio"
                    v-model="profile.gender"
                    value="M"
                    name="gender"
                    class="sr-only peer"
                    @change="addressInput.focus()"
                  />
                  <div
                    class="py-3 text-center rounded-2xl border border-gray-200 bg-gray-50 text-gray-400 font-medium peer-checked:border-[var(--color-primary)] peer-checked:bg-orange-50 peer-checked:text-[var(--color-primary)] peer-checked:font-bold transition-all shadow-sm"
                  >
                    남성
                  </div>
                </label>
                <label class="relative cursor-pointer group">
                  <input
                    type="radio"
                    v-model="profile.gender"
                    value="F"
                    name="gender"
                    class="sr-only peer"
                    @change="addressInput.focus()"
                  />
                  <div
                    class="py-3 text-center rounded-2xl border border-gray-200 bg-gray-50 text-gray-400 font-medium peer-checked:border-[var(--color-primary)] peer-checked:bg-orange-50 peer-checked:text-[var(--color-primary)] peer-checked:font-bold transition-all shadow-sm"
                  >
                    여성
                  </div>
                </label>
              </div>
            </div>

            <!-- 주소 입력 -->
            <div>
              <label for="address" class="eeum-label">주소</label>
              <div class="flex gap-2 mb-2">
                <input
                  ref="addressInput"
                  type="text"
                  v-model="profile.address"
                  id="address"
                  placeholder="주소 검색"
                  readonly
                  @click="openAddressSearch"
                  class="eeum-input cursor-pointer bg-gray-50 text-gray-600"
                />
                <button
                  @click.prevent="openAddressSearch"
                  type="button"
                  class="w-20 bg-gray-800 text-white font-medium rounded-2xl hover:bg-gray-700 transition flex-shrink-0 text-sm shadow-md"
                >
                  검색
                </button>
              </div>
              <input
                ref="detailAddressInput"
                type="text"
                v-model="detailAddress"
                placeholder="상세주소를 입력하세요"
                @keydown.enter.prevent="submitProfile"
                class="eeum-input"
              />
            </div>
          </div>

          <!-- 저장 버튼 -->
          <div class="pt-2">
            <button
              type="submit"
              :disabled="isLoading"
              class="w-full py-4 rounded-2xl bg-[var(--color-primary)] text-white font-bold text-lg shadow-xl shadow-orange-200 hover:bg-orange-600 hover:shadow-orange-300 active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ isLoading ? '저장 중...' : '저장하기' }}
            </button>
          </div>

          <!-- 에러 메시지 표시 -->
          <div
            v-if="errorMessage"
            class="flex items-center p-4 text-sm text-red-800 rounded-2xl bg-red-50 border border-red-100 shadow-sm animate-pulse"
            role="alert"
          >
            <svg
              class="flex-shrink-0 inline w-4 h-4 mr-3"
              aria-hidden="true"
              xmlns="http://www.w3.org/2000/svg"
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                d="M10 .5a9.5 9.5 0 1 0 9.5 9.5A9.51 9.51 0 0 0 10 .5ZM9.5 4a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3ZM12 15H8a1 1 0 0 1 0-2h1v-3H8a1 1 0 0 1 0-2h2a1 1 0 0 1 1 1v4h1a1 1 0 0 1 0 2Z"
              />
            </svg>
            <span class="font-medium">{{ errorMessage }}</span>
          </div>
        </form>
      </div>

      <!-- 주소 검색 모달 -->
      <Teleport to="body">
        <div
          v-if="showAddressModal"
          class="fixed inset-0 z-[9999] overflow-y-auto bg-black/60 backdrop-blur-sm"
          @click="showAddressModal = false"
        >
          <div class="flex min-h-full items-center justify-center p-4">
            <!-- 모달 패널 -->
            <div
              class="relative z-10 bg-white rounded-3xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col min-h-[500px] pointer-events-auto"
              role="dialog"
              aria-modal="true"
              @click.stop
            >
              <!-- 헤더 영역 -->
              <div
                class="p-5 border-b border-gray-100 flex justify-between items-center bg-gray-50 flex-shrink-0"
              >
                <h3 class="text-lg font-bold text-gray-800">주소 검색</h3>
                <button
                  @click="showAddressModal = false"
                  class="w-8 h-8 rounded-full bg-white text-gray-400 hover:text-gray-600 hover:bg-gray-100 flex items-center justify-center transition shadow-sm border border-gray-100"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M6 18L18 6M6 6l12 12"
                    ></path>
                  </svg>
                </button>
              </div>
              <!-- 본문 영역 -->
              <div
                id="postcode-layer"
                ref="addressApiWrapper"
                class="w-full bg-white overflow-y-auto h-[500px]"
              >
                <!-- 다음 주소 서비스가 여기에 임베드됩니다. -->
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
import { compressImage } from '@/utils/imageUtils';
import { Logger } from '@/services/logger';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const familyStore = useFamilyStore();
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

watch(
  userProfile,
  (newUserProfile) => {
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
  },
  { immediate: true },
);

onMounted(async () => {
  if (!userStore.isAuthenticated) {
    await userStore.fetchUser();
  }
});

watch(
  () => profile.value.phone,
  (newPhone) => {
    const digits = newPhone.replace(/\D/g, '');
    let formatted = '';
    if (digits.length <= 3) formatted = digits;
    else if (digits.length <= 7) formatted = `${digits.slice(0, 3)}-${digits.slice(3)}`;
    else formatted = `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`;
    profile.value.phone = formatted;
  },
);

/**
 * 성별 선택 라디오 그룹의 첫 번째 항목에 포커스를 줍니다.
 */
const genderRadioGroupFocus = () => {
  genderRadioGroup.value?.querySelector('input[type="radio"]')?.focus();
};

/**
 * 프로필 이미지 파일 선택 변경 시 이미지를 압축하고 미리보기를 생성합니다.
 * @param {Event} event
 */
const handleFileChange = async (event) => {
  const file = event.target.files[0];
  if (!file) return;

  try {
    const compressedFile = await compressImage(file, 1600, 0.7);
    if (validateFileSize(compressedFile)) {
      updateProfileImagePreview(compressedFile);
    } else {
      event.target.value = '';
    }
  } catch (e) {
    Logger.error('이미지 압축 실패:', e);
    await modalStore.openAlert('이미지 처리 중 오류가 발생했습니다.', '오류');
  }
};

/**
 * 파일 용량이 제한(3MB) 내에 있는지 확인합니다.
 * @param {Blob} file
 * @returns {boolean}
 */
const validateFileSize = (file) => {
  if (file.size > 3 * 1024 * 1024) {
    modalStore.openAlert('이미지 용량이 너무 큽니다. (최대 3MB)', '업로드 실패');
    return false;
  }
  return true;
};

/**
 * 미리보기 URL을 업데이트하고 내부 파일을 설정합니다.
 * @param {Blob} file
 */
const updateProfileImagePreview = (file) => {
  profileFile.value = file;
  imageUrl.value = URL.createObjectURL(file);
};

/**
 * 주소 검색 모달을 엽니다.
 */
const openAddressSearch = () => {
  showAddressModal.value = true;
};

watch(showAddressModal, (isShown) => {
  if (isShown) {
    document.body.style.overflow = 'hidden';
    setTimeout(() => {
      const container = document.getElementById('postcode-layer');

      if (!window.daum || !window.daum.Postcode) {
        modalStore.openAlert('주소 검색 서비스를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.');
        showAddressModal.value = false;
        return;
      }

      if (container) {
        container.innerHTML = '';
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
        Logger.error('주소 검색 컨테이너를 찾을 수 없음');
      }
    }, 100);
  } else {
    document.body.style.overflow = '';
  }
});

/**
 * 프로필 수정 양식을 서버에 제출합니다.
 */
const submitProfile = async () => {
  if (isLoading.value) return;
  isLoading.value = true;
  errorMessage.value = '';

  try {
    const formData = prepareProfilePayload();
    await updateUserProfile(formData);
    await syncProfileData();
    handleNavigationAfterSubmit();
  } catch (error) {
    handleProfileSubmitError(error);
  } finally {
    isLoading.value = false;
  }
};

/**
 * 제출을 위해 생년월일과 주소를 포함한 FormData를 생성합니다.
 * @returns {FormData}
 */
const prepareProfilePayload = () => {
  const birthDate = formatBirthDate();
  const fullAddress =
    profile.value.address + (detailAddress.value ? ` ${detailAddress.value}` : '');

  const requestDto = {
    name: profile.value.name,
    phone: profile.value.phone,
    birthDate: birthDate,
    gender: profile.value.gender,
    address: fullAddress,
  };

  const formData = new FormData();
  formData.append('request', new Blob([JSON.stringify(requestDto)], { type: 'application/json' }));
  if (profileFile.value) {
    formData.append('file', profileFile.value);
  }
  return formData;
};

/**
 * 연/월/일을 YYYY-MM-DD 형식으로 포맷팅합니다.
 * @returns {string|null}
 */
const formatBirthDate = () => {
  if (birthYear.value && birthMonth.value && birthDay.value) {
    const month = String(birthMonth.value).padStart(2, '0');
    const day = String(birthDay.value).padStart(2, '0');
    return `${birthYear.value}-${month}-${day}`;
  }
  return null;
};

/**
 * 수정 완료 후 관련 스토어 데이터를 동기화합니다.
 */
const syncProfileData = async () => {
  // Pinia 스토어의 사용자 정보를 최신화
  await userStore.fetchUser(true);

  // 현재 선택된 가족의 멤버 리스트도 강제 갱신하여 헤더 등에 즉시 반영
  if (familyStore.selectedFamily?.id) {
    await familyStore.fetchMembers(familyStore.selectedFamily.id, true);
  }
};

/**
 * 제출 성공 후 페이지 이동을 처리합니다.
 */
const handleNavigationAfterSubmit = () => {
  if (isInitialSetup.value) {
    router.push({ name: 'VoiceRegistration', query: { flow: 'initial' } });
  } else {
    router.back();
  }
};

/**
 * 프로필 제출 중 오류 발생 시 에러 메시지를 설정합니다.
 * @param {Error} error
 */
const handleProfileSubmitError = (error) => {
  Logger.error('프로필 수정 실패:', error);
  errorMessage.value = error.response?.data?.message || '프로필 업데이트에 실패했습니다.';
};
</script>

<style scoped>
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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
