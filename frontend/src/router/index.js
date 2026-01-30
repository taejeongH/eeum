import { createRouter, createWebHashHistory } from 'vue-router';
import MyProfileView from '../views/MyProfileView.vue';
import HomePage from '../views/HomePage.vue';
import VoiceRegistration from '../views/VoiceRegistration.vue';
import MyProfileEdit from '../views/MyProfileEdit.vue';
import VoiceSample from '../views/VoiceSample.vue';
import LoginView from '../views/Login.vue';
import MemberDetailView from '../views/MemberDetailView.vue';
import JoinGroupView from '../views/JoinGroupView.vue';
import GroupSetupLayout from '../views/group-setup/GroupSetupLayout.vue';
import GroupSetupStep1 from '../views/group-setup/Step1GroupName.vue';
import GroupSetupStep2 from '../views/group-setup/Step2HealthInfo.vue';
import GroupSetupStep3 from '../views/group-setup/Step3EmergencyContact.vue';
import GroupSetupStep4 from '../views/group-setup/Step4Medication.vue';
import MedicationListView from '../views/MedicationListView.vue';
import MedicationDetailView from '../views/MedicationDetailView.vue';
import MessageListView from '../views/MessageList.vue';

import OnboardingView from '../views/Onboarding.vue';

import { useUserStore } from '@/stores/user';

const routes = [
  {
    path: '/',
    redirect: () => {
      // 토큰이 있으면 홈으로, 없으면 온보딩으로
      const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
      return token ? '/home' : '/onboarding';
    }
  },
  {
    path: '/onboarding',
    name: 'onboarding',
    component: OnboardingView
  },
  {
    path: '/login',
    name: 'login', // 소문자 login으로 통일
    component: LoginView
  },
  {
    path: '/signup',
    name: 'signup',
    component: () => import('../views/SignupView.vue')
  },
  {
    path: '/logout',
    name: 'logout',
    component: () => import('../views/LogoutView.vue')
  },
  {
    path: '/home',
    name: 'HomePage',
    component: HomePage,
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
    path: '/families/:familyId/medications',
    name: 'MedicationList',
    component: MedicationListView,
  },
  {
    path: '/families/:familyId/medications/:medicationId',
    name: 'MedicationDetail',
    component: MedicationDetailView,
  },
  {
    path: '/families/:familyId/messages',
    name: 'FamilyMessages',
    component: MessageListView,
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
      {
        path: 'step4',
        name: 'GroupEditStep4',
        component: GroupSetupStep4,
      },
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
    path: '/voice-register',
    name: 'VoiceRegistration',
    component: VoiceRegistration,
  },
  {
    path: '/families/:familyId/calendar',
    name: 'CalendarPage',
    component: () => import('../views/CalendarPage.vue'),
  },
  {
    path: '/families/:familyId/calendar/create',
    name: 'CalendarCreate',
    component: () => import('../views/CalendarCreate.vue'),
  },
  {
    path: '/families/:familyId/gallery',
    name: 'GalleryPage',
    component: () => import('../views/GalleryPage.vue'),
  },
  {
    path: '/families/:familyId/gallery/album/:id',
    name: 'AlbumPage',
    component: () => import('../views/AlbumPage.vue')
  },
  {
    path: '/families/:familyId/calendar/detail',
    name: 'DetailSchedule',
    component: () => import('../views/DetailSchedule.vue'),
  },
  {
    path: '/emergency',
    name: 'Emergency',
    redirect: '/home',
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

// 전역 가드 설정
router.beforeEach((to, from, next) => {
  const isAuthenticated = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');

  if (to.name === 'login' && isAuthenticated) {
    next({ name: 'HomePage' }); // 이미 로그인된 경우 홈으로
  } else if (to.name === 'login' && !isAuthenticated) {
    next();
  } else if (to.name === 'JoinGroup' && !to.query.code) {
    next({ name: 'HomePage' });
  } else {
    next();
  }
});

export default router;