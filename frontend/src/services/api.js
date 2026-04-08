import axios from 'axios';
import { Logger } from '@/services/logger';
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
  timeout: 5000, 
});


apiClient.interceptors.request.use(
  (config) => {
    
    const uiStore = useUiStore();
    if (!config.silent && !config.headers?.silent) {
      uiStore.startLoading();
    } else {
      
      if (config.headers?.silent) delete config.headers.silent;
      config.isSilent = true; 
    }

    
    let token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');

    

    
    if (!token && window.AndroidBridge?.getAccessToken) {
      const nativeToken = window.AndroidBridge.getAccessToken();
      if (nativeToken && nativeToken !== 'null' && nativeToken.length > 0) {
        token = nativeToken;
        
      }
    }

    if (token) {
      
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 토큰 만료 처리 및 재발급 로직
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
    // 전역 로딩 종료
    const uiStore = useUiStore();
    if (!response.config?.isSilent) {
      uiStore.finishLoading();
    }
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // 에러 로깅
    Logger.error(
      `🌐 [API 오류] ${error.config?.method?.toUpperCase()} ${error.config?.url}`,
      error,
    );
    if (error.response) {
      Logger.error(`   상태 코드: ${error.response.status}`, error.response.data);
    } else if (error.request) {
      Logger.error(`   응답 없음. 네트워크 또는 CORS 문제일 수 있습니다.`);
    } else {
      Logger.error(`   메시지: ${error.message}`);
    }

    // 401 에러이고, 재시도 플래그가 없으며, 재발급 요청 자체가 아닌 경우
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url.includes('/auth/reissue')
    ) {
      if (isRefreshing) {
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
        const refreshToken =
          localStorage.getItem('refreshToken') || sessionStorage.getItem('refreshToken');
        if (!refreshToken) {
          throw new Error('No refresh token available');
        }

        const { data } = await apiClient.post('/auth/reissue', {
          refreshToken: refreshToken,
        });

        const newAccessToken = data.accessToken;
        const newRefreshToken = data.refreshToken;

        if (newAccessToken) {
          localStorage.setItem('accessToken', newAccessToken);
          if (newRefreshToken) {
            localStorage.setItem('refreshToken', newRefreshToken);
          }

          if (window.AndroidBridge && window.AndroidBridge.saveAccessToken) {
            window.AndroidBridge.saveAccessToken(newAccessToken);
          }

          apiClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

          processQueue(null, newAccessToken);
          return apiClient(originalRequest);
        } else {
          throw new Error('No token returned');
        }
      } catch (err) {
        Logger.error('❌ 토큰 갱신 실패:', err);
        processQueue(err, null);

        // 로그아웃 처리
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        sessionStorage.removeItem('accessToken');
        sessionStorage.removeItem('refreshToken');

        if (window.AndroidBridge) {
          if (window.AndroidBridge.logout) window.AndroidBridge.logout();
          if (window.AndroidBridge.saveAccessToken) window.AndroidBridge.saveAccessToken('');
        }

        const uiStore = useUiStore();
        uiStore.finishLoading();

        window.location.href = '#/login';
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    // 전역 에러 핸들링 (404 등)
    if (error.response?.status === 404) {
      const modalStore = useModalStore();
      modalStore.openAlert('요청하신 페이지나 정보를 찾을 수 없습니다. (404 Not Found)', '오류');
    }

    // 전역 로딩 종료
    const uiStore = useUiStore();
    if (!error.config?.isSilent) {
      uiStore.finishLoading();
    }

    return Promise.reject(error);
  },
);


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
    
    const uiStore = useUiStore();
    if (!response.config?.isSilent) {
      uiStore.finishLoading();
    }
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    
    Logger.error(`🌐 [API 오류] ${error.config?.method?.toUpperCase()} ${error.config?.url}`, error);
    if (error.response) {
      Logger.error(`   상태 코드: ${error.response.status}`, error.response.data);
    } else if (error.request) {
      Logger.error(`   응답 없음. 네트워크 또는 CORS 문제일 수 있습니다.`);
    } else {
      Logger.error(`   메시지: ${error.message}`);
    }



    
    if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.url.includes('/auth/reissue')) {
      if (isRefreshing) {
        
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

        const refreshToken = localStorage.getItem('refreshToken') || sessionStorage.getItem('refreshToken');
        if (!refreshToken) {
          throw new Error("No refresh token available");
        }

        
        const { data } = await apiClient.post('/auth/reissue', {
          refreshToken: refreshToken
        });

        const newAccessToken = data.accessToken;
        const newRefreshToken = data.refreshToken; 

        if (newAccessToken) {
          localStorage.setItem('accessToken', newAccessToken);
          if (newRefreshToken) {
            localStorage.setItem('refreshToken', newRefreshToken);
          }

          
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
        Logger.error("❌ 토큰 갱신 실패:", err);
        processQueue(err, null);

        
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        sessionStorage.removeItem('accessToken');
        sessionStorage.removeItem('refreshToken');

        if (window.AndroidBridge) {
          if (window.AndroidBridge.logout) window.AndroidBridge.logout();
          if (window.AndroidBridge.saveAccessToken) window.AndroidBridge.saveAccessToken(""); 
        }

        
        const uiStore = useUiStore();
        uiStore.finishLoading();

        window.location.href = '#/login';
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    
    if (error.response?.status === 404) {
      const modalStore = useModalStore();
      modalStore.openAlert("요청하신 페이지나 정보를 찾을 수 없습니다. (404 Not Found)", "오류");
    }

    
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


export const reissueToken = () => {
  return apiClient.post('/auth/reissue');
};


export const findEmail = (data) => {
  
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
    Logger.error(`가족 알림 기록 조회 실패 (ID: ${familyId}):`, error);
    throw error;
  }
};

export const getFallVideo = async (eventId) => {
  try {
    const response = await apiClient.get(`/falls/${eventId}/video`);
    return response.data; 
  } catch (error) {
    Logger.error(`낙상 영상 조회 실패 (Event ID: ${eventId}):`, error);
    throw error;
  }
};

export const getFamilyDetails = async (familyId) => {
  try {
    const response = await apiClient.get(`/families/${familyId}/details`);
    return response.data;
  } catch (error) {
    Logger.error(`가족 상세 정보 조회 실패 (ID: ${familyId}):`, error);
    throw error;
  }
};


export const generatePairingCode = async (familyId) => {
  try {
    const response = await apiClient.post(`/families/${familyId}/iot/pair/code`);
    return response.data;
  } catch (error) {
    Logger.error(`초대 코드 생성 실패 (ID: ${familyId}):`, error);
    throw error;
  }
};

export const getIotDevices = async (familyId) => {
  try {
    const response = await apiClient.get(`/families/${familyId}/devices`);
    return response.data;
  } catch (error) {
    Logger.error(`IoT 기기 목록 조회 실패 (ID: ${familyId}):`, error);
    throw error;
  }
};

export const registerIotDevice = async (familyId, deviceData) => {
  try {
    const response = await apiClient.post(`/families/${familyId}/devices`, deviceData);
    return response.data;
  } catch (error) {
    Logger.error(`IoT 기기 등록 실패 (ID: ${familyId}):`, error);
    throw error;
  }
};

export const updateIotDevice = async (familyId, deviceId, updateData) => {
  try {
    const response = await apiClient.patch(`/families/${familyId}/devices/${deviceId}`, updateData);
    return response.data;
  } catch (error) {
    Logger.error(`IoT 기기 수정 실패 (Device ID: ${deviceId}):`, error);
    throw error;
  }
};

export const deleteIotDevice = async (familyId, deviceId) => {
  try {
    const response = await apiClient.delete(`/families/${familyId}/devices/${deviceId}`);
    return response.data;
  } catch (error) {
    Logger.error(`IoT 기기 삭제 실패 (Device ID: ${deviceId}):`, error);
    throw error;
  }
};


export const getLatestHeartRate = async (familyId) => {
  try {
    const response = await apiClient.get('/health/heart-rate/latest', {
      params: { groupId: familyId }
    });
    return response.data; 
  } catch (error) {
    Logger.error(`최신 심박수 조회 실패 (ID: ${familyId}):`, error);
    throw error;
  }
};

export const getHeartRateResult = async (eventId) => {
  try {
    const response = await apiClient.get(`/health/heart-rate/${eventId}`);
    return response.data; 
  } catch (error) {
    Logger.error(`심박수 결과 조회 실패 (Event ID: ${eventId}):`, error);
    throw error;
  }
};
