
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

export const useSlideshowStore = defineStore('slideshow', () => {
    // State
    const currentSlide = ref(null)
    const nextSlide = ref(null)
    const isConnected = ref(false)
    const wifiStatus = ref(false) // true: Connected, false: Disconnected

    let eventSource = null
    let lastSeq = -1

    // Actions
    const startStream = () => {
        if (eventSource) {
            eventSource.close()
        }

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

                // Preload image for smooth transition
                const img = new Image()
                img.src = data.item.url
                img.onload = () => {
                    // Directly update currentSlide to trigger Vue transition
                    currentSlide.value = {
                        ...data.item,
                        message: data.item.message || data.item.description // Fallback to description
                    }
                }
            } catch (e) {
                console.error('[SlideshowStore] Error parsing event data:', e)
            }
        })

        eventSource.onerror = (err) => {
            console.error('[SlideshowStore] SSE Error:', err)
            isConnected.value = false
            eventSource.close()
            eventSource = null
        }
    }


    const stopStream = () => {
        if (eventSource) {
            console.log('[SlideshowStore] Closing SSE connection')
            eventSource.close()
            eventSource = null
        }
        isConnected.value = false
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

    // Transition logic helper: Promotes nextSlide to currentSlide
    const advanceSlide = () => {
        if (nextSlide.value) {
            currentSlide.value = nextSlide.value
            nextSlide.value = null
        }
    }

    return {
        currentSlide,
        nextSlide,
        isConnected,
        wifiStatus,
        startStream,
        stopStream,
        updateWifiStatus,
        advanceSlide
    }
})
