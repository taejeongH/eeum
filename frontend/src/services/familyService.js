import apiClient from './api';

export const familyService = {
    
    getFamilyMembers: (familyId) => {
        return apiClient.get(`/families/${familyId}/members`);
    },

    
    getFamilyDetails: (familyId) => {
        return apiClient.get(`/families/${familyId}/details`);
    }
};
