
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

export const useSlideshowStore = defineStore('slideshow', () => {
    // State
    const currentSlide = ref(null)
    const nextSlide = ref(null) // Not strictly used if we just rotate from playlist, but keeping for compatibility
    const playlist = ref([])
    const isConnected = ref(false)
    const wifiStatus = ref(false) // true: Connected, false: Disconnected
    const isPlaying = ref(true)

    let eventSource = null
    let lastSeq = -1
    let reconnectTimer = null
    let slideshowTimer = null
    const SLIDE_INTERVAL = 10000 // 10 seconds per slide

    // Actions
    const startStream = () => {
        if (eventSource) {
            eventSource.close()
        }
        if (reconnectTimer) clearTimeout(reconnectTimer)

        // SSE Endpoint
        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081'
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

                const newSlide = {
                    ...data.item,
                    message: data.item.message || data.item.description,
                    id: data.item.id || Date.now() // Ensure ID
                }

                // Add to playlist if not exists
                const exists = playlist.value.find(p => p.id === newSlide.id)
                if (!exists) {
                    playlist.value.push(newSlide)

                    // Keep playlist size manageable
                    if (playlist.value.length > 20) {
                        playlist.value.shift()
                    }
                }

                // Immediately show the new slide from SSE
                currentSlide.value = newSlide

                // Reset timer so we view this slide for the full duration
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

        // Start local slideshow rotation
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

                // Preload next image
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
            // Using correct mock server URL (port 8080)
            const response = await axios.get('http://localhost:8080/api/wifi/active')
            if (response.status === 200 && response.data && response.data.ssid) {
                wifiStatus.value = true
            } else {
                wifiStatus.value = false
            }
        } catch (error) {
            // console.error('[SlideshowStore] Wifi check failed:', error)
            wifiStatus.value = false
        }
    }

    // API Control Navigation
    const controlNext = async () => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081'
            await axios.post(`${apiUrl}/api/slideshow/next`)
        } catch (e) {
            console.error('[SlideshowStore] Next failed:', e)
        }
    }

    const controlPrev = async () => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081'
            await axios.post(`${apiUrl}/api/slideshow/prev`)
        } catch (e) {
            console.error('[SlideshowStore] Prev failed:', e)
        }
    }

    const controlPlay = async (interval = 60) => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081'
            await axios.post(`${apiUrl}/api/slideshow/play`, { interval_sec: interval })
            startSlideshowTimer() // Resume local timer
            isPlaying.value = true // Update playing state
        } catch (e) {
            console.error('[SlideshowStore] Play failed:', e)
        }
    }

    const controlPause = async () => {
        try {
            const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081'
            await axios.post(`${apiUrl}/api/slideshow/pause`)
            if (slideshowTimer) clearInterval(slideshowTimer) // Pause local timer
            isPlaying.value = false // Update playing state
        } catch (e) {
            console.error('[SlideshowStore] Pause failed:', e)
        }
    }

    // Transition logic helper: Promotes nextSlide to currentSlide
    const advanceSlide = () => {
        // Now handled by interval, but kept for manual overrides if needed
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
        isPlaying, // Export new state
        startStream,
        stopStream,
        updateWifiStatus,
        advanceSlide,
        controlNext,
        controlPrev,
        controlPlay,
        controlPause // Export new controls
    }
})
