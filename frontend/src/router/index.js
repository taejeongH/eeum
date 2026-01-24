import { createRouter, createWebHashHistory } from 'vue-router';
import MyProfileView from '../views/MyProfileView.vue';
import MyProfileEdit from '../views/MyProfileEdit.vue';
import VoiceSample from '../views/VoiceSample.vue';

const routes = [
  {
    path: '/',
    redirect: '/my-profile-view', //로그인 페이지로 변경예정
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
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

export default router;
