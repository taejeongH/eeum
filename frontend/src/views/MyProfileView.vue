<template>
  <div class="w-full min-h-screen bg-white font-sans">
    <div class="w-full max-w-lg mx-auto p-6">
      
      <div class="pt-12 pb-8 text-center sticky top-0 bg-white z-10">
        <h2 class="text-3xl font-extrabold text-gray-900">내 프로필 조회</h2>
        <p class="text-lg text-gray-600 mt-2">회원님의 프로필 정보입니다.</p>
      </div>

      <div v-if="userStore.isAuthenticated && userProfile" class="space-y-6 px-6 pb-8">
        
        <div class="flex justify-center py-4">
          <div class="w-40 h-40 rounded-full bg-gray-50">
            <img v-if="userProfile.profileImage" :src="userProfile.profileImage" alt="Profile" class="w-full h-full rounded-full object-cover" />
            <div v-else class="w-full h-full rounded-full border-4 border-dashed border-gray-200 flex items-center justify-center text-gray-400">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-16 h-16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </div>
          </div>
        </div>

        <div class="info-group">
          <label>이름</label>
          <p>{{ userProfile.name }}</p>
        </div>

        <div class="info-group">
          <label>전화번호</label>
          <p>{{ userProfile.phone }}</p>
        </div>

        <div v-if="userProfile.birthDate" class="info-group">
          <label>생년월일</label>
          <p>{{ userProfile.birthDate }}</p>
        </div>

        <div class="info-group">
          <label>성별</label>
          <p>{{ userProfile.gender === 'M' ? '남성' : '여성' }}</p>
        </div>

        <div v-if="userProfile.address" class="info-group">
          <label>주소</label>
          <p>{{ userProfile.address }}</p>
        </div>
        
        <div class="pt-4">
          <router-link to="/my-profile-edit" class="edit-btn">
            프로필 설정
          </router-link>
        </div>

      </div>
      <div v-else class="text-center py-10">
        <p class="text-gray-500">프로필 정보를 불러오는 중이거나, 로그인되어 있지 않습니다.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia';
import { useUserStore } from '../stores/user';

const userStore = useUserStore();
const { profile: userProfile } = storeToRefs(userStore);
</script>

<style scoped>
.info-group {
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--border-default);
}
.info-group label {
  display: block;
  font-size: 0.875rem;
  color: var(--text-sub);
  margin-bottom: 0.25rem;
}
.info-group p {
  font-size: 1.125rem;
  color: var(--text-title);
  font-weight: 500;
}
.edit-btn {
  display: block;
  width: 100%;
  text-align: center;
  background-color: var(--color-primary);
  color: white;
  font-weight: bold;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  transition: background-color 0.2s;
}
.edit-btn:hover {
  filter: brightness(1.1);
}
</style>
