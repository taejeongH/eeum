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

export const getUserProfile = () => {
  if (USE_MOCK) {
    console.log("⚠️ Using MOCK Data for getUserProfile");
    return Promise.resolve({ data: MOCK_USER });
  }
  return apiClient.get('/users/profile/me');
};

export const updateUserProfile = (formData) => {
  return apiClient.put('/users/profile', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    }
  });
};
