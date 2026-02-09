
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

export const useSlideshowStore = defineStore('slideshow', () => {
    
    const currentSlide = ref(null)
    const nextSlide = ref(null) 
    const playlist = ref([])
    const isConnected = ref(false)
    const wifiStatus = ref(false) 
    const isPlaying = ref(true)

    let eventSource = null
    let lastSeq = -1
    let reconnectTimer = null
    let slideshowTimer = null
    const SLIDE_INTERVAL = 10000 

    
    const startStream = () => {
        if (eventSource) {
            eventSource.close()
        }
        if (reconnectTimer) clearTimeout(reconnectTimer)

        
        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
        const url = `${apiUrl}/api/slideshow/stream`

        console.log(`[SlideshowStore] Connecting to SSE: ${url}`)
        eventSource = new EventSource(url)

        eventSource.onopen = () => {
            console.log('[SlideshowStore] SSE Connected')
            isConnected.value = true
        }

        eventSource.addEventListener('slide', (event) => {
            try {
                const data = JSON.parse(event.data)
                console.log('[SlideshowStore] Slide event received:', data)

                if (data.seq <= lastSeq) {
                    console.warn(`[SlideshowStore] Ignoring stale/duplicate seq: ${data.seq} (last: ${lastSeq})`)
                    return
                }

                lastSeq = data.seq

                let rawMessage = data.item.message || data.item.description

                
                if (rawMessage && /\.(jpg|jpeg|png|gif|bmp|webp|heic)$/i.test(rawMessage)) {
                    rawMessage = ''
                }

                const newSlide = {
                    ...data.item,
                    message: rawMessage,
                    id: data.item.id || Date.now() 
                }

                
                const exists = playlist.value.find(p => p.id === newSlide.id)
                if (!exists) {
                    playlist.value.push(newSlide)

                    
                    if (playlist.value.length > 20) {
                        playlist.value.shift()
                    }
                }

                
                currentSlide.value = newSlide

                
                startSlideshowTimer()

            } catch (e) {
                console.error('[SlideshowStore] Error parsing event data:', e)
            }
        })

        eventSource.onerror = (err) => {
            console.error('[SlideshowStore] SSE Error:', err)
            isConnected.value = false
            eventSource.close()
            eventSource = null

            console.log('[SlideshowStore] Reconnecting in 5s...')
            reconnectTimer = setTimeout(startStream, 5000)
        }

        
        startSlideshowTimer()
    }


    const stopStream = () => {
        if (eventSource) {
            console.log('[SlideshowStore] Closing SSE connection')
            eventSource.close()
            eventSource = null
        }
        if (reconnectTimer) clearTimeout(reconnectTimer)
        if (slideshowTimer) clearInterval(slideshowTimer)
        isConnected.value = false
    }

    const startSlideshowTimer = () => {
        if (slideshowTimer) clearInterval(slideshowTimer)
        slideshowTimer = setInterval(() => {
            if (playlist.value.length > 1) {
                const currentIndex = playlist.value.findIndex(p => p.id === currentSlide.value?.id)
                const nextIndex = (currentIndex + 1) % playlist.value.length

                
                const nextItem = playlist.value[nextIndex]
                const img = new Image()
                img.src = nextItem.url
                img.onload = () => {
                    currentSlide.value = nextItem
                }
            }
        }, SLIDE_INTERVAL)
    }

    const updateWifiStatus = async () => {
        try {
            
            const response = await axios.get('http://localhost:8080/api/wifi/active')
            if (response.status === 200 && response.data && response.data.ssid) {
                wifiStatus.value = true
            } else {
                wifiStatus.value = false
            }
        } catch (error) {
            
            wifiStatus.value = false
        }
    }

    
    const controlNext = async () => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
            await axios.post(`${apiUrl}/api/slideshow/next`)
        } catch (e) {
            console.error('[SlideshowStore] Next failed:', e)
        }
    }

    const controlPrev = async () => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
            await axios.post(`${apiUrl}/api/slideshow/prev`)
        } catch (e) {
            console.error('[SlideshowStore] Prev failed:', e)
        }
    }

    const controlPlay = async (interval = 60) => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
            await axios.post(`${apiUrl}/api/slideshow/play`, { interval_sec: interval })
            startSlideshowTimer() 
            isPlaying.value = true 
        } catch (e) {
            console.error('[SlideshowStore] Play failed:', e)
        }
    }

    const controlPause = async () => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
            await axios.post(`${apiUrl}/api/slideshow/pause`)
            if (slideshowTimer) clearInterval(slideshowTimer) 
            isPlaying.value = false 
        } catch (e) {
            console.error('[SlideshowStore] Pause failed:', e)
        }
    }

    
    const advanceSlide = () => {
        
        if (playlist.value.length > 0) {
            const currentIndex = playlist.value.findIndex(p => p.id === currentSlide.value?.id)
            const nextIndex = (currentIndex + 1) % playlist.value.length
            currentSlide.value = playlist.value[nextIndex]
        }
    }

    return {
        currentSlide,
        nextSlide,
        playlist,
        isConnected,
        wifiStatus,
        isPlaying, 
        startStream,
        stopStream,
        updateWifiStatus,
        advanceSlide,
        controlNext,
        controlPrev,
        controlPlay,
        controlPause 
    }
})
