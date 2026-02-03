<template>
  <Transition name="fade">
    <div v-if="uiStore.isLoading" class="loading-overlay">
      <div class="loader-content">
        <div class="logo-container">
          <img src="@/assets/eeum_logo2.png" alt="IEUM Logo" class="loader-logo" />
          <div class="logo-glow"></div>
        </div>
        <div class="status-indicator">
          <div class="shimmer-bar"></div>
          <p class="mt-4 text-gray-800 font-semibold text-lg tracking-tight">
            이음을 준비하고 있어요
          </p>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { useUiStore } from '@/stores/ui';

const uiStore = useUiStore();
</script>

<style scoped>
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.loader-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
}

.logo-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
}

.loader-logo {
  width: 100px;
  height: auto;
  z-index: 2;
  animation: logo-float 2s ease-in-out infinite;
}

.logo-glow {
  position: absolute;
  width: 80px;
  height: 80px;
  background: var(--color-primary, #FF9D00);
  filter: blur(30px);
  opacity: 0.2;
  border-radius: 50%;
  animation: pulse 2s ease-in-out infinite;
}

.status-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.shimmer-bar {
  width: 120px;
  height: 3px;
  background: #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
  position: relative;
}

.shimmer-bar::after {
  content: '';
  position: absolute;
  width: 40%;
  height: 100%;
  background: var(--color-primary, #FF9D00);
  left: -40%;
  animation: shimmer 1.5s infinite ease-in-out;
}

@keyframes logo-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

@keyframes shimmer {
  0% { left: -40%; }
  100% { left: 100%; }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.15; }
  50% { transform: scale(1.4); opacity: 0.3; }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.4s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
