import { ref } from 'vue';
import { Logger } from '@/services/logger';

/**
 * Pull-to-Refresh 로직을 처리하는 Composable
 * @param {Function} onRefresh - 새로고침 시 실행할 비동기 함수
 * @returns {Object} State and Handlers
 */
export function usePullToRefresh(onRefresh) {
  const isRefreshing = ref(false);
  const startY = ref(0);
  const refreshPullDistance = ref(0);
  const canRefresh = ref(true);

  const handleTouchStart = (e) => {
    if (window.scrollY > 5 || isRefreshing.value) {
      canRefresh.value = false;
      return;
    }
    canRefresh.value = true;
    startY.value = e.touches[0].clientY;
  };

  const handleTouchMove = (e) => {
    if (!canRefresh.value || isRefreshing.value) return;

    const currentY = e.touches[0].clientY;
    const distance = currentY - startY.value;

    if (distance > 0) {
      if (e.cancelable) e.preventDefault();
      refreshPullDistance.value = Math.min(distance * 0.6, 150);
    }
  };

  const handleTouchEnd = async () => {
    if (!canRefresh.value || isRefreshing.value) return;

    if (refreshPullDistance.value > 100) {
      await executeRefresh();
    } else {
      refreshPullDistance.value = 0;
    }
  };

  const executeRefresh = async () => {
    isRefreshing.value = true;
    try {
      if (onRefresh) {
        await onRefresh();
      }
    } catch (error) {
      Logger.error('새로고침 실패:', error);
    } finally {
      setTimeout(() => {
        isRefreshing.value = false;
        refreshPullDistance.value = 0;
      }, 800);
    }
  };

  return {
    isRefreshing,
    refreshPullDistance,
    handleTouchStart,
    handleTouchMove,
    handleTouchEnd,
  };
}
