import axios from 'axios';
import { MOCK_USER } from '../mocks/data';
import { useUiStore } from '../stores/ui';
import { useModalStore } from '../stores/modal';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
  timeout: 5000, // 5초 타임아웃 (서버 응답 없을 시 무한 로딩 방지)
});

// [중요] 모든 요청에 토큰을 자동으로 붙여주는 인터셉터입니다.
apiClient.interceptors.request.use(
  (config) => {
    // [NEW] 전역 로딩 시작 (silent 옵션이 있으면 건너뜀)
    const uiStore = useUiStore();
    if (!config.silent && !config.headers?.silent) {
      uiStore.startLoading();
    } else {
      // 헤더에 silent가 있으면 실제 요청 선에서 제거 (서버 전송 방지)
      if (config.headers?.silent) delete config.headers.silent;
      config.isSilent = true; // 응답에서도 참조하기 위함
    }

    // 1. localStorage 또는 sessionStorage에서 토큰 확인
    let token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');

    // ... (rest of the code follows)

    // 2. [NEW] 모바일 앱: AndroidBridge에서 토큰 가져오기 (fallback)
    if (!token && window.AndroidBridge?.getAccessToken) {
      const nativeToken = window.AndroidBridge.getAccessToken();
      if (nativeToken && nativeToken !== 'null' && nativeToken.length > 0) {
        token = nativeToken;
        // 다음 요청을 위해 localStorage에 동기화
        localStorage.setItem('accessToken', nativeToken);
      }
    }

    if (token) {
      // 반드시 Bearer 뒤에 한 칸 공백이 있어야 합니다.
      config.headers.Authorization = `Bearer ${token}`;
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
    // [NEW] 전역 로딩 종료
    const uiStore = useUiStore();
    if (!response.config?.isSilent) {
      uiStore.finishLoading();
    }
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

        if (window.AndroidBridge) {
          if (window.AndroidBridge.logout) window.AndroidBridge.logout();
          if (window.AndroidBridge.saveAccessToken) window.AndroidBridge.saveAccessToken(""); // Explicitly clear token
        }
        window.location.href = '#/login';
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    // [NEW] 전역 에러 핸들링 (404 등)
    if (error.response?.status === 404) {
      const modalStore = useModalStore();
      modalStore.openAlert("요청하신 페이지나 정보를 찾을 수 없습니다. (404 Not Found)", "오류");
    }

    // [NEW] 전역 로딩 종료
    const uiStore = useUiStore();
    if (!error.config?.isSilent) {
      uiStore.finishLoading();
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

export const getNotificationHistory = async (familyId) => {
  try {
    const response = await apiClient.get(`/notifications/families/${familyId}/history`);
    return response.data;
  } catch (error) {
    console.error(`Failed to fetch notification history for family ${familyId}:`, error);
    throw error;
  }
};

export const getFallVideo = async (eventId) => {
  try {
    const response = await apiClient.get(`/falls/${eventId}/video`);
    return response.data; // Expected: { videoUrl: "..." }
  } catch (error) {
    console.error(`Failed to fetch fall video for event ${eventId}:`, error);
    throw error;
  }
};

export const getFamilyDetails = async (familyId) => {
  try {
    const response = await apiClient.get(`/families/${familyId}/details`);
    return response.data;
  } catch (error) {
    console.error(`Failed to fetch family details for ${familyId}:`, error);
    throw error;
  }
};

// IoT Device Management APIs
export const generatePairingCode = async (familyId) => {
  try {
    const response = await apiClient.post(`/families/${familyId}/iot/pair/code`);
    return response.data;
  } catch (error) {
    console.error(`Failed to generate pairing code for family ${familyId}:`, error);
    throw error;
  }
};

export const getIotDevices = async (familyId) => {
  try {
    const response = await apiClient.get(`/families/${familyId}/devices`);
    return response.data;
  } catch (error) {
    console.error(`Failed to fetch IoT devices for family ${familyId}:`, error);
    throw error;
  }
};

export const registerIotDevice = async (familyId, deviceData) => {
  try {
    const response = await apiClient.post(`/families/${familyId}/devices`, deviceData);
    return response.data;
  } catch (error) {
    console.error(`Failed to register IoT device for family ${familyId}:`, error);
    throw error;
  }
};

export const updateIotDevice = async (familyId, deviceId, updateData) => {
  try {
    const response = await apiClient.patch(`/families/${familyId}/devices/${deviceId}`, updateData);
    return response.data;
  } catch (error) {
    console.error(`Failed to update IoT device ${deviceId}:`, error);
    throw error;
  }
};

export const deleteIotDevice = async (familyId, deviceId) => {
  try {
    const response = await apiClient.delete(`/families/${familyId}/devices/${deviceId}`);
    return response.data;
  } catch (error) {
    console.error(`Failed to delete IoT device ${deviceId}:`, error);
    throw error;
  }
};
