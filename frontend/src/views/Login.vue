<template>
  <div style="display: flex; justify-content: center; align-items: center; height: 100vh; flex-direction: column; gap: 20px;">
    <h1>이음 서비스 로그인</h1>
    
    <button 
      @click="handleKakaoLogin" 
      style="padding: 15px 30px; background-color: #FEE500; border: none; border-radius: 5px; font-weight: bold; cursor: pointer;"
    >
      카카오로 시작하기
    </button>
  </div>
</template>

<script setup>
const handleKakaoLogin = () => {
  // 백엔드 클라우드 서버의 소셜 로그인 시작 주소
  const BACKEND_URL = 'https://i14a105.p.ssafy.io/api/auth/login/social/kakao';
  window.location.href = BACKEND_URL;
};
</script>