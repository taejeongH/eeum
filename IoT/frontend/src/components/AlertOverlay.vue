<script setup>
import { useAlertStore } from '../stores/alert';
import AlertItem from './AlertItem.vue';

/**
 * 알림 스토어를 호출합니다.
 * @type {import('pinia').Store}
 */
const alertStore = useAlertStore();

/**
 * 알림 제거 핸들러
 * 플로팅 오버레이에서 제거하며, 필요한 경우 알림 내역에서도 제거합니다.
 * @param {string|number} id - 제거할 알림 ID
 */
const handleDismiss = (id) => {
  // 1. 오버레이 목록에서 제거
  alertStore.removeAlert(id);

  // 2. 히스토리에서도 제거 (선택적)
  // 히스토리에서 유지하고 싶다면 이 부분은 주석 처리 가능
  if (alertStore.removeHistory) {
    alertStore.removeHistory(id);
  }
};
</script>

<template>
  <!-- 
    알림 오버레이 컨테이너
    - 위치: 우측 상단 고정 (Top-Right)
    - Z-Index: 50 (높은 우선순위)
    - 상호작용: 컨테이너 영역은 클릭 투과(pointer-events-none), 개별 알림은 클릭 가능
  -->
  <TransitionGroup
    tag="div"
    move-class="transition-all duration-500 ease-in-out"
    enter-active-class="transform ease-out duration-500 transition"
    enter-from-class="translate-y-4 opacity-0 sm:translate-y-0 sm:translate-x-4"
    enter-to-class="translate-y-0 opacity-100 sm:translate-x-0"
    leave-active-class="transition ease-in duration-300 absolute w-full"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0 translate-x-full"
    class="fixed top-0 right-0 h-full z-50 flex flex-col justify-start gap-2 p-8 w-full max-w-[800px] pointer-events-none"
  >
    <AlertItem
      v-for="alert in alertStore.alerts"
      :key="alert.id"
      :alert="alert"
      class="pointer-events-auto"
      @close="alertStore.removeAlert(alert.id)"
      @dismiss="handleDismiss(alert.id)"
    />
  </TransitionGroup>
</template>

<style scoped>
/* 필요한 경우 스코프 스타일 추가 */
</style>
