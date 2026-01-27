import { createRouter, createWebHashHistory } from 'vue-router';
import MyProfileView from '../views/MyProfileView.vue';
import HomePage from '../views/HomePage.vue';
import VoiceRegistration from '../views/VoiceRegistration.vue';
import EmergencyAlert from '../views/EmergencyAlert.vue';
import MyProfileEdit from '../views/MyProfileEdit.vue';
import VoiceSample from '../views/VoiceSample.vue';

const routes = [
  {
    path: '/',
    redirect: '/home', // 메인 홈 화면으로 리다이렉트
  },
  {
    path: '/my-profile-view',
    name: 'MyProfileView',
    component: MyProfileView,
  },
  {
    path: '/my-profile-edit',
    name: 'MyProfileEdit',
    component: MyProfileEdit,
  },
  {
    path: '/api/auth/login/social',
    redirect: '/my-profile-edit',
  },
  {
    path: '/voice-sample',
    name: 'VoiceSample',
    component: VoiceSample,
  },
  {
    path: '/voice-sample',
    name: 'VoiceSample',
    component: VoiceSample,
  },
  {
    path: '/home',
    name: 'HomePage',
    component: HomePage,
  },
  {
    path: '/voice-register',
    name: 'VoiceRegistration',
    component: VoiceRegistration,
  },
  {
    path: '/emergency',
    name: 'EmergencyAlert',
    component: EmergencyAlert,
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

export default router;
