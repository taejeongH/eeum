console.log("🚀 [IEUM] App starting...");
import { createApp } from "vue";
import { createPinia } from 'pinia';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import App from "./App.vue";
import router from "./router";
import "./assets/main.css";

const app = createApp(App);
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

app.use(pinia);
app.use(router);
console.log("🛠️ [IEUM] Pinia/Router initialized. Mounting app...");
app.mount("#app");
console.log("✅ [IEUM] App mounted.");

// [NEW] 앱 마운트 완료 후 초기 로더 제거
const loader = document.getElementById('initial-loader');
if (loader) {
    console.log("🧹 [IEUM] Removing initial loader...");
    loader.style.opacity = '0';
    setTimeout(() => {
        loader.remove();
        console.log("✨ [IEUM] Initial loader removed.");
    }, 500);
}
