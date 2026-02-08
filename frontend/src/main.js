
import { createApp } from "vue";
import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import App from "./App.vue";
import router from "./router";
import "./assets/main.css";
import { Logger } from '@/services/logger';

const app = createApp(App);
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

app.use(pinia);
app.use(router);

app.mount("#app");

// [NEW] Global Error Handler
app.config.errorHandler = (err, instance, info) => {
    Logger.error(`Global Error: ${info}`, err);
};

// [NEW] Promise Rejection Handler
window.addEventListener('unhandledrejection', (event) => {
    Logger.warn(`Unhandled Promise Rejection:`, event.reason);
});



// [NEW] 앱 마운트 완료 후 초기 로더 제거
const loader = document.getElementById('initial-loader');
if (loader) {

    loader.style.opacity = '0';
    setTimeout(() => {
        loader.remove();

    }, 500);
}
