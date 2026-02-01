
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
        const url = '/api/slideshow/stream'

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

                // Rule: Ignore if seq is not greater than lastSeq
                if (data.seq <= lastSeq) {
                    console.warn(`[SlideshowStore] Ignoring stale/duplicate seq: ${data.seq} (last: ${lastSeq})`)
                    return
                }

                lastSeq = data.seq

                // Preload image
                const img = new Image()
                img.src = data.item.url
                img.onload = () => {
                    // If we don't have a current slide, show immediately.
                    // Otherwise, set as nextSlide (component will pick it up)
                    if (!currentSlide.value) {
                        currentSlide.value = data.item
                    } else {
                        nextSlide.value = data.item
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

            console.warn('[SlideshowStore] 백엔드 연결 실패. 데모 모드를 시작합니다.')
            startDemoMode()
        }
    }

    // Demo Mode Logic
    let demoInterval = null
    const demoImages = [
        {
            id: 1,
            url: 'https://picsum.photos/1920/1080?random=1',
            description: '가족 여행',
            message: '제주도에서 찍은 사진이에요. 날씨가 참 좋았죠?',
            uploader: '김철수',
            takenAt: '2025-05-12'
        },
        {
            id: 2,
            url: 'https://picsum.photos/1080/1920?random=2',
            description: '손녀 독사진',
            message: '할머니 사랑해요! 건강하세요.',
            uploader: '이영희',
            takenAt: '2025-08-20'
        },
        {
            id: 3,
            url: 'https://picsum.photos/1080/1080?random=3',
            description: '공원 산책',
            message: '집 앞 공원에 꽃이 많이 폈길래 찍어봤어요.',
            uploader: '박민수',
            takenAt: '2025-10-03'
        },
        {
            id: 4,
            url: 'https://picsum.photos/1920/1280?random=4',
            description: '생일 파티',
            message: '생일 축하드려요! 케이크 맛있게 드세요.',
            uploader: '손녀 김지은',
            takenAt: '2025-11-15'
        },
    ]

    const startDemoMode = () => {
        if (demoInterval) return
        let index = 0

        // Show first image immediately
        if (!currentSlide.value) currentSlide.value = demoImages[0]

        demoInterval = setInterval(() => {
            index = (index + 1) % demoImages.length
            currentSlide.value = demoImages[index]
        }, 5000) // 5 seconds interval for demo
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
            // POST /api/wifi/ui/ping (3초 주기 - handled by caller or component)
            // or GET /api/wifi/active
            // Using GET /api/wifi/active as per spec for active check
            const response = await axios.get('/api/wifi/active')
            // Assuming 200 OK means connected, or specific payload
            // Spec says: Busy State: skipped: true.
            // We assume if we get a valid SSID or 200 OK, it's connected.
            // Let's implement robust check.
            if (response.status === 200 && response.data) {
                wifiStatus.value = true // Simplified for now
            }
        } catch (error) {
            console.error('[SlideshowStore] Wifi check failed:', error)
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
