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

// [NEW] 토큰 만료 처리 및 재발급 로직
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config;


    // 401 에러이고, 재시도 플래그가 없으며, 재발급 요청 자체가 아닌 경우
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.url.includes('/auth/reissue')) {
      if (isRefreshing) {
        // 이미 재발급 중이면 큐에 넣어 대기
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(apiClient(originalRequest));
            },
            reject: (err) => {
              reject(err);
            },
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        console.log("♻️ 401 detected, attempting to refresh token...");

        const refreshToken = localStorage.getItem('refreshToken') || sessionStorage.getItem('refreshToken');
        if (!refreshToken) {
          throw new Error("No refresh token available");
        }

        // API 호출: 토큰 재발급
        const { data } = await apiClient.post('/auth/reissue', {
          refreshToken: refreshToken
        });

        const newAccessToken = data.accessToken;
        const newRefreshToken = data.refreshToken; // Rotate된 Refresh Token

        if (newAccessToken) {
          console.log("✅ Token refreshed successfully.");
          localStorage.setItem('accessToken', newAccessToken);
          if (newRefreshToken) {
            localStorage.setItem('refreshToken', newRefreshToken);
          }

          // Native Bridge에도 저장
          if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
            window.AndroidBridge.saveAccessToken(newAccessToken);
          }

          apiClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

          processQueue(null, newAccessToken);
          return apiClient(originalRequest);
        } else {
          throw new Error("No token returned");
        }
      } catch (err) {
        console.error("❌ Token refresh failed:", err);
        processQueue(err, null);

        // 로그아웃 처리
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        sessionStorage.removeItem('accessToken');
        sessionStorage.removeItem('refreshToken');

        if (window.AndroidBridge && window.AndroidBridge.logout) {
          window.AndroidBridge.logout();
        }
        window.location.href = '/login';
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

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

export const logout = () => {
  return apiClient.post('/auth/logout');
};

// [NEW] 토큰 재발급 API (수동 호출용)
export const reissueToken = () => {
  return apiClient.post('/auth/reissue');
};

// Account Recovery APIs
export const findEmail = (data) => {
  // data: { name, phone }
  return apiClient.post('/auth/find/email', data);
};

export const sendPasswordResetCode = (email) => {
  return apiClient.post('/auth/password/code', { email });
};

export const verifyPasswordResetCode = (email, code) => {
  return apiClient.post('/auth/password/verify', { email, code });
};

export const resetPassword = (email, newPassword) => {
  return apiClient.post('/auth/password/reset', { email, newPassword });
};

export const login = (credentials) => {
  return apiClient.post('/auth/login', credentials);
};

export default apiClient;

