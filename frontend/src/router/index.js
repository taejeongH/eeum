// src/router/index.js

import { createRouter, createWebHashHistory } from 'vue-router';
import MyProfileView from '../views/MyProfileView.vue';
import MyProfileEdit from '../views/MyProfileEdit.vue';
import VoiceSample from '../views/VoiceSample.vue';
import LoginView from '../views/Login.vue'; // 1. 여기서 'LoginView'로 가져옴
import HomePage from '../views/HomePage.vue';
import MemberDetailView from '../views/MemberDetailView.vue';
import LoginPage from '../views/LoginPage.vue';
import JoinGroupView from '../views/JoinGroupView.vue';
import GroupSetupLayout from '../views/group-setup/GroupSetupLayout.vue';
import GroupSetupStep1 from '../views/group-setup/Step1GroupName.vue';
import GroupSetupStep2 from '../views/group-setup/Step2HealthInfo.vue';
import GroupSetupStep3 from '../views/group-setup/Step3Medication.vue';


import { useUserStore } from '@/stores/user';
import api from '@/services/api';

const routes = [
  {
    path: '/login',
    name: 'login',
    component: LoginView // 2. [수정] 위에서 가져온 이름인 'LoginView'로 변경! (Login -> LoginView)
  },
  {
    path: '/',
    redirect: '/login', // [추천] 바로 테스트할 수 있게 일단 로그인으로 보내버리세요
    redirect: '/home',
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
    component: GroupSetupLayout,
    children: [
      { path: '', redirect: 'step1' },
      {
        path: 'step1',
        name: 'GroupEditStep1',
        component: GroupSetupStep1,
      },
      {
        path: 'step2',
        name: 'GroupEditStep2',
        component: GroupSetupStep2,
      },
      {
        path: 'step3',
        name: 'GroupEditStep3',
        component: GroupSetupStep3,
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginPage,
  },
  {
    path: '/join',
    name: 'JoinGroup',
    component: JoinGroupView,
    beforeEnter: async (to, from, next) => {
      const userStore = useUserStore();
      const inviteCode = to.query.code;

      if (!inviteCode) {
        console.warn('No invite code provided for join.');
        next({ name: 'HomePage' });
        return;
      }

      sessionStorage.setItem('redirectAfterLogin', to.fullPath);

      try {
        await userStore.fetchUser();

        if (userStore.isAuthenticated) { 
          next(); 
        } else {
          next({ name: 'Login' });
        }
      } catch (fetchUserError) {
          console.error("Error fetching user during route guard:", fetchUserError);
          next({ name: 'Login' });
      }
    },
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
  // ... 생략
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});


router.beforeEach((to, from, next) => {
  if (to.name === 'Login') {
    const redirectPath = sessionStorage.getItem('redirectAfterLogin');
    if (redirectPath) {
      console.log("Redirect path stored for login completion:", redirectPath);
    }
  } else if (to.name === 'JoinGroup' && !to.query.code) {
    next({ name: 'HomePage' });
    return;
  }
  next();
});

export default router;