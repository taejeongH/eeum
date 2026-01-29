import axios from 'axios';
import { MOCK_USER } from '../mocks/data';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// [중요] 모든 요청에 토큰을 자동으로 붙여주는 인터셉터입니다.
apiClient.interceptors.request.use(
  (config) => {
    // localStorage 또는 sessionStorage에서 토큰 확인
    const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');

    if (token) {
      // 반드시 Bearer 뒤에 한 칸 공백이 있어야 합니다.
      config.headers.Authorization = `Bearer ${token}`;
    } else {
      console.log("토큰이 없습니다.");
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const getUserProfile = () => {
  return apiClient.get('/users/profile/me');
};

export const updateUserProfile = (data) => {
  return apiClient.put('/users/profile', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

// ... 나머지 함수들
export const joinFamilyWithCode = (inviteCode) => {
  return apiClient.post('/families/join', inviteCode, {
    headers: {
      'Content-Type': 'text/plain',
    },
    transformRequest: [(data) => data],
  });
};

// [수정] 로그인/회원가입 등 인증 관련 API는 withCredentials: false 로 설정하여 CORS 에러 방지

export const login = (credentials) => {
  return apiClient.post('/auth/login', credentials, {
    withCredentials: false
  });
};

export const signup = (data) => {
  return apiClient.post('/auth/signup', data, {
    withCredentials: false
  });
};

export const sendCode = (email) => {
  return apiClient.post('/auth/email/code', { email }, {
    withCredentials: false
  });
};

export const verifyCode = (data) => {
  return apiClient.post('/auth/email/verify', data, {
    withCredentials: false
  });
};

export const logout = () => {
  return apiClient.post('/auth/logout', {}, {
    withCredentials: false
  });
};

export default apiClient;