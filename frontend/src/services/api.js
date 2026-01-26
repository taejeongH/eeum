import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

export const getUserProfile = () => {
    return apiClient.get('/users/profile/me');
};

export const updateUserProfile = (formData) => {
    return apiClient.put('/users/profile', formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
        }
    });
};
