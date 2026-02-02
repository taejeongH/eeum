<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useAlertStore } from './stores/alert';
import AlertOverlay from './components/AlertOverlay.vue';
import MainView from './views/MainView.vue';

const alertStore = useAlertStore();

onMounted(() => {
  alertStore.connect();
});

onUnmounted(() => {
  alertStore.disconnect();
});
</script>

<template>
  <AlertOverlay />
  <MainView />
</template>

<style>
/* Global Reset or specific styles if needed */
html, body, #app {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}
</style>
