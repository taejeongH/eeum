import axios from 'axios';

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
    // localStorage의 키값이 'accessToken'인지 반드시 확인해야 합니다.
    const token = localStorage.getItem('accessToken');

    if (token) {
      // 반드시 Bearer 뒤에 한 칸 공백이 있어야 합니다.
      // 반드시 Bearer 뒤에 한 칸 공백이 있어야 합니다.
      config.headers.Authorization = `Bearer ${token}`;
    } else {
      console.log("localStorage에 토큰이 없습니다.");
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

export const updateFcmToken = (fcmToken) => {
  return apiClient.put('/users/fcm-token', { fcmToken });
};

export default apiClient;