import { createRouter, createWebHashHistory } from 'vue-router';
import MyProfileView from '../views/MyProfileView.vue';
import HomePage from '../views/HomePage.vue';
import VoiceRegistration from '../views/VoiceRegistration.vue';
import EmergencyAlert from '../views/EmergencyAlert.vue';
import MyProfileEdit from '../views/MyProfileEdit.vue';
import VoiceSample from '../views/VoiceSample.vue';
import LoginView from '../views/Login.vue'; 
import HomePage from '../views/HomePage.vue';
import MemberDetailView from '../views/MemberDetailView.vue';
import JoinGroupView from '../views/JoinGroupView.vue';
import GroupSetupLayout from '../views/group-setup/GroupSetupLayout.vue';
import GroupSetupStep1 from '../views/group-setup/Step1GroupName.vue';
import GroupSetupStep2 from '../views/group-setup/Step2HealthInfo.vue';
import GroupSetupStep3 from '../views/group-setup/Step3Medication.vue';

import { useUserStore } from '@/stores/user';

const routes = [
  {
    path: '/',
    redirect: '/login', // 처음 접속 시 로그인 페이지로 보냄
  },
  {
    path: '/login',
    name: 'login', // 소문자 login으로 통일
    component: LoginView 
  },
  {
    path: '/home',
    name: 'HomePage',
    component: HomePage,
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
    path: '/members/:familyId/:userId',
    name: 'MemberDetail',
    component: MemberDetailView,
  },
  {
    path: '/groups/:familyId/edit',
    name: 'GroupEdit',
    component: GroupSetupLayout,
    children: [
      { path: '', redirect: 'step1' },
      { path: 'step1', name: 'GroupEditStep1', component: GroupSetupStep1 },
      { path: 'step2', name: 'GroupEditStep2', component: GroupSetupStep2 },
      { path: 'step3', name: 'GroupEditStep3', component: GroupSetupStep3 },
    ],
  },
  {
    path: '/join',
    name: 'JoinGroup',
    component: JoinGroupView,
    beforeEnter: async (to, from, next) => {
      const userStore = useUserStore();
      const inviteCode = to.query.code;
      if (!inviteCode) {
        next({ name: 'HomePage' });
        return;
      }
      sessionStorage.setItem('redirectAfterLogin', to.fullPath);
      try {
        await userStore.fetchUser();
        if (userStore.isAuthenticated) { next(); } 
        else { next({ name: 'login' }); }
      } catch (e) { next({ name: 'login' }); }
    },
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

// 전역 가드 설정
router.beforeEach((to, from, next) => {
  if (to.name === 'login' && !localStorage.getItem('accessToken')) {
    next();
  } else if (to.name === 'JoinGroup' && !to.query.code) {
    next({ name: 'HomePage' });
  } else {
    next();
  }
});

export default router;