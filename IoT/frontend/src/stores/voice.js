import { defineStore } from 'pinia';
import { ref } from 'vue';
import axios from 'axios';

// Dynamic API URL resolution
const getApiUrl = () => import.meta.env.VITE_API_URL || 'http://localhost:8081';

export const useVoiceStore = defineStore('voice', () => {
    const voiceMessages = ref([]);
    const isConnected = ref(false);
    const eventSource = ref(null);

    // 1. SSE Connection
    const connect = () => {
        if (eventSource.value) return;

        const apiUrl = getApiUrl();
        console.log(`[VoiceStore] Connecting to SSE: ${apiUrl}/api/voice/stream`);
        eventSource.value = new EventSource(`${apiUrl}/api/voice/stream`);

        eventSource.value.onopen = () => {
            console.log('[VoiceStore] SSE Connected');
            isConnected.value = true;
        };

        eventSource.value.onerror = (err) => {
            console.error('[VoiceStore] SSE Error:', err);
            isConnected.value = false;
            eventSource.value?.close();
            eventSource.value = null;
            setTimeout(connect, 3000);
        };

        // Event: voice (New Message)
        eventSource.value.addEventListener('voice', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('[VoiceStore] New Voice:', data);

                // Check duplicates (req 1.3)
                if (!voiceMessages.value.some(msg => msg.id === data.id)) {
                    voiceMessages.value.push({
                        ...data,
                        status: 'pending' // pending -> playing -> done/skipped
                    });
                    // Keep recent N (e.g. 50)
                    if (voiceMessages.value.length > 50) voiceMessages.value.shift();
                }
            } catch (e) {
                console.error('Failed to parse voice event:', e);
            }
        });

        // Event: voice_done (Completion)
        eventSource.value.addEventListener('voice_done', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('[VoiceStore] Voice Done:', data);
                // req: data.id, data.result="done|skipped"

                const index = voiceMessages.value.findIndex(msg => msg.id === data.id);
                if (index !== -1) {
                    // Remove from UI when done
                    voiceMessages.value.splice(index, 1);
                }
            } catch (e) {
                console.error('Failed to parse voice_done event:', e);
            }
        });
    };

    const disconnect = () => {
        if (eventSource.value) {
            eventSource.value.close();
            eventSource.value = null;
            isConnected.value = false;
        }
    };

    // 2. Single ACK
    const playMessage = async (id) => {
        const msg = voiceMessages.value.find(m => m.id === id);
        if (!msg) return;

        try {
            msg.status = 'playing'; // Optimistic update
            const apiUrl = getApiUrl();
            const response = await axios.post(`${apiUrl}/api/ack`, {
                target: { type: 'voice', id: id },
                action: 'play'
            });

            // Response compliance check
            if (response.data.ok) {
                console.log(`[VoiceStore] Play accepted. Duration: ${response.data.data.duration_sec}s`);
            } else {
                console.warn('[VoiceStore] Play rejected:', response.data.reason);
                msg.status = 'pending';
            }
        } catch (e) {
            console.error('[VoiceStore] Play request failed:', e);
            msg.status = 'pending';
        }
    };

    const skipMessage = async (id) => {
        const index = voiceMessages.value.findIndex(m => m.id === id);
        if (index !== -1) voiceMessages.value.splice(index, 1); // Optimistic remove

        try {
            const apiUrl = getApiUrl();
            await axios.post(`${apiUrl}/api/ack`, {
                target: { type: 'voice', id: id },
                action: 'skip'
            });
        } catch (e) {
            console.error('[VoiceStore] Skip request failed:', e);
        }
    };

    // 3. Batch ACK
    const batchAck = async (items, defaultAction = 'skip') => {
        try {
            const apiUrl = getApiUrl();
            const payload = {
                mode: 'sequential',
                default_action: defaultAction,
                items: items.map(item => ({
                    target: { type: 'voice', id: item.id },
                    action: item.action // optional override
                }))
            };

            await axios.post(`${apiUrl}/api/ack/batch`, payload);
        } catch (e) {
            console.error('[VoiceStore] Batch ACK failed:', e);
        }
    }

    // 4. Skip Current Global
    const skipCurrentPlayback = async () => {
        try {
            const apiUrl = getApiUrl();
            await axios.post(`${apiUrl}/api/playback/skip_current`);
        } catch (e) {
            console.error('[VoiceStore] Skip Current failed:', e);
        }
    };

    return {
        voiceMessages,
        isConnected,
        connect,
        disconnect,
        playMessage,
        skipMessage,
        batchAck,
        skipCurrentPlayback
    };
});
