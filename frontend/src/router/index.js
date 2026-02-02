import { createRouter, createWebHashHistory } from 'vue-router';
import MyProfileView from '../views/MyProfileView.vue';
import HomePage from '../views/HomePage.vue';
import VoiceRegistration from '../views/VoiceRegistration.vue';
import MyProfileEdit from '../views/MyProfileEdit.vue';
import VoiceSample from '../views/VoiceSample.vue';
import LoginView from '../views/Login.vue';
import InitialSetupWizard from '../views/InitialSetupWizard.vue';
import MemberDetailView from '../views/MemberDetailView.vue';
import JoinGroupView from '../views/JoinGroupView.vue';
import GroupSetupLayout from '../views/group-setup/GroupSetupLayout.vue';
import GroupSetupStep1 from '../views/group-setup/Step1GroupName.vue';
import GroupSetupStep2 from '../views/group-setup/Step2HealthInfo.vue';
import GroupSetupStep3 from '../views/group-setup/Step3EmergencyContact.vue';
import GroupSetupStep4 from '../views/group-setup/Step4Medication.vue';

import MedicationListView from '../views/MedicationListView.vue';
import MedicationDetailView from '../views/MedicationDetailView.vue';
import HealthDetailView from '../views/HealthDetailView.vue';
import MessageListView from '../views/MessageList.vue';

import OnboardingView from '../views/Onboarding.vue';

import { useUserStore } from '@/stores/user';

const routes = [
  {
    path: '/',
    redirect: () => {
      const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
      if (token) return '/home';

      const hasSeenOnboarding = localStorage.getItem('hasSeenOnboarding');
      return hasSeenOnboarding ? '/login' : '/onboarding';
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
    path: '/find-account',
    name: 'FindAccount',
    component: () => import('../views/FindAccountPage.vue')
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
    path: '/families/:familyId/notifications',
    name: 'NotificationList',
    component: () => import('../views/NotificationListView.vue'),
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
    path: '/initial-setup',
    name: 'InitialSetupWizard',
    component: InitialSetupWizard,
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
    path: '/health-detail',
    name: 'HealthDetail',
    component: HealthDetailView,
  },
  {
    path: '/emergency',
    name: 'Emergency',
    redirect: '/home',
  },
  {
    path: '/realtime-heart-rate',
    name: 'RealTimeHeartRate',
    component: () => import('../views/HeartRateView.vue'),
  },
  {
    path: '/families/:familyId/devices',
    name: 'DeviceManagement',
    component: () => import('../views/DeviceManagementPage.vue'),
  },
  {
    path: '/setup-complete',
    name: 'InitialSetupComplete',
    component: () => import('../views/InitialSetupComplete.vue'),
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

// 전역 가드 설정
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
  const userStore = useUserStore();

  // 1. 로그인 상태인 경우
  if (token) {
    if (to.name === 'login') {
      return next({ name: 'HomePage' });
    }

    // 초기 설정 대상인지 확인 (프로필 이름이 비어있는 경우)
    // 데이터 로드가 안되어 있으면 시도
    if (!userStore.profile) {
      await userStore.fetchUser();
    }

    const isMissingProfile = userStore.profile && !userStore.profile.phone;
    const isSetupRoute = ['InitialSetupWizard', 'InitialSetupComplete'].includes(to.name);

    if (isMissingProfile && !isSetupRoute && to.name !== 'logout') {
      return next({ name: 'InitialSetupWizard' });
    }

    return next();
  }

  // 2. 비로그인 상태
  if (to.name !== 'login' && to.name !== 'onboarding' && to.name !== 'signup' && to.name !== 'FindAccount') {
    return next({ name: 'login' });
  }

  next();
});

export default router;