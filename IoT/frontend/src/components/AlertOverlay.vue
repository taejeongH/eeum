<script setup>
import { useAlertStore } from '../stores/alert';
import { storeToRefs } from 'pinia';
import AlertItem from './AlertItem.vue';

const alertStore = useAlertStore();
const { alerts } = storeToRefs(alertStore);

const handleDismiss = (id) => {
    
    alertStore.removeAlert(id);
    
    alertStore.removeHistory(id);
};
</script>

<template>
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
      v-for="alert in alerts" 
      :key="alert.id"
      :alert="alert"
      class="pointer-events-auto"
      @close="alertStore.removeAlert(alert.id)"
      @dismiss="handleDismiss(alert.id)"
    />
  </TransitionGroup>
</template>

<style scoped>
</style>
