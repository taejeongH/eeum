import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import axios from 'axios';


const getApiUrl = () => import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const useVoiceStore = defineStore('voice', () => {
    const voiceMessages = ref([]);
    const isConnected = ref(false);
    const eventSource = ref(null);

    
    const connect = () => {
        if (eventSource.value) return;

        const apiUrl = getApiUrl();
        console.log(`[VoiceStore] Connecting to SSE: ${apiUrl}/api/voice/stream`);
        eventSource.value = new EventSource(`${apiUrl}/api/voice/stream`);

        eventSource.value.onopen = async () => {
            console.log('[VoiceStore] SSE Connected');
            isConnected.value = true;
            await fetchPendingMessages();
        };

        eventSource.value.onerror = (err) => {
            console.error('[VoiceStore] SSE Error:', err);
            isConnected.value = false;
            eventSource.value?.close();
            eventSource.value = null;
            setTimeout(connect, 3000);
        };

        
        eventSource.value.addEventListener('voice', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('[VoiceStore] New Voice:', data);

                
                if (!voiceMessages.value.some(msg => msg.id === data.id)) {
                    voiceMessages.value.push({
                        ...data,
                        sender: data.sender?.name || data.title || '알 수 없음',
                        content: data.description || data.content || '',
                        profile_image: data.sender?.profile_image_url,
                        created_at: data.created_at || (Date.now() / 1000),
                        type: 'VOICE',
                        status: 'pending',
                        isPlayed: false, 
                        download: data.download 
                    });
                    
                    if (voiceMessages.value.length > 50) voiceMessages.value.shift();
                }
            } catch (e) {
                console.error('Failed to parse voice event:', e);
            }
        });

        
        eventSource.value.addEventListener('voice_done', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('[VoiceStore] Voice Done:', data);
                

                const index = voiceMessages.value.findIndex(msg => msg.id === data.id);
                if (index !== -1) {
                    
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

    
    const playMessage = async (id) => {
        const msg = voiceMessages.value.find(m => m.id === id);
        if (!msg) return;

        
        if (msg.download && !msg.download.ready) {
            console.warn('[VoiceStore] Message not ready for playback (download pending/failed)');
            
            return;
        }

        
        msg.isPlayed = true;

        try {
            msg.status = 'playing'; 
            const apiUrl = getApiUrl();
            const response = await axios.post(`${apiUrl}/api/ack`, {
                target: { type: 'voice', id: id },
                action: 'play'
            });

            
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
        if (index !== -1) voiceMessages.value.splice(index, 1); 

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

    
    const batchAck = async (items, defaultAction = 'skip') => {
        try {
            const apiUrl = getApiUrl();
            const payload = {
                mode: 'sequential',
                default_action: defaultAction,
                items: items.map(item => ({
                    target: { type: 'voice', id: item.id },
                    action: item.action 
                }))
            };

            await axios.post(`${apiUrl}/api/ack/batch`, payload);
        } catch (e) {
            console.error('[VoiceStore] Batch ACK failed:', e);
        }
    }

    
    const skipCurrentPlayback = async () => {
        try {
            const apiUrl = getApiUrl();
            await axios.post(`${apiUrl}/api/playback/skip_current`);
        } catch (e) {
            console.error('[VoiceStore] Skip Current failed:', e);
        }
    };

    
    const removeMessage = async (id) => {
        
        const index = voiceMessages.value.findIndex(m => m.id === id);
        if (index !== -1) voiceMessages.value.splice(index, 1);

        try {
            const apiUrl = getApiUrl();
            const token = localStorage.getItem('iotAccessToken');
            await axios.delete(`${apiUrl}/api/iot/device/sync/voice/${id}`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            console.log(`[VoiceStore] Message ${id} deleted`);
        } catch (e) {
            console.error('[VoiceStore] Failed to delete message:', e);
        }
    };

    
    
    const unreadCount = computed(() => voiceMessages.value.filter(m => !m.isPlayed).length);

    
    const fetchPendingMessages = async () => {
        try {
            const apiUrl = getApiUrl();
            const token = localStorage.getItem('iotAccessToken');
            const response = await axios.get(`${apiUrl}/api/voice/pending`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            if (response.data.ok && response.data.data?.items) {
                const pendingItems = response.data.data.items;
                console.log(`[VoiceStore] Found ${pendingItems.length} pending messages`);

                pendingItems.forEach(item => {
                    
                    if (!voiceMessages.value.some(msg => msg.id === item.id)) {
                        voiceMessages.value.push({
                            id: item.id,
                            sender: item.sender?.name || '알 수 없음',
                            content: item.description || '',
                            profile_image: item.sender?.profile_image_url,
                            created_at: item.created_at || (Date.now() / 1000),
                            type: 'VOICE',
                            status: 'pending',
                            isPlayed: false,
                            download: item.download 
                        });
                    }
                });

                
                voiceMessages.value.sort((a, b) => b.created_at - a.created_at);
            }
        } catch (e) {
            console.error('[VoiceStore] Failed to fetch pending messages:', e);
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
        skipCurrentPlayback,
        removeMessage,
        unreadCount,
        fetchPendingMessages
    };
});
