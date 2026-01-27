import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});


apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const getUserProfile = () => {
  return apiClient.get('/users/profile/me');
};

export const updateUserProfile = (formData) => {
  return apiClient.put('/users/profile', formData, {
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

export default apiClient;
