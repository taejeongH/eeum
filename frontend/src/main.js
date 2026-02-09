
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


app.config.errorHandler = (err, instance, info) => {
    Logger.error(`Global Error: ${info}`, err);
};


window.addEventListener('unhandledrejection', (event) => {
    Logger.warn(`Unhandled Promise Rejection:`, event.reason);
});




const loader = document.getElementById('initial-loader');
if (loader) {

    loader.style.opacity = '0';
    setTimeout(() => {
        loader.remove();

    }, 500);
}
