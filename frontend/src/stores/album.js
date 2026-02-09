import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAlbumStore = defineStore('album', () => {
    const cachedPhotos = ref({})
    const lastFetchTime = ref({})
    const CACHE_DURATION = 5 * 60 * 1000 

    const setCachedPhotos = (familyId, photos) => {
        cachedPhotos.value[familyId] = photos
        lastFetchTime.value[familyId] = Date.now()
    }

    const getCachedPhotos = (familyId) => {
        const cached = cachedPhotos.value[familyId]
        const fetchTime = lastFetchTime.value[familyId]

        if (!cached || !fetchTime) return null

        
        if (Date.now() - fetchTime > CACHE_DURATION) {
            return null
        }

        return cached
    }

    const clearCache = (familyId) => {
        if (familyId) {
            delete cachedPhotos.value[familyId]
            delete lastFetchTime.value[familyId]
        } else {
            cachedPhotos.value = {}
            lastFetchTime.value = {}
        }
    }

    const isFresh = (familyId, duration = 10000) => {
        const fetchTime = lastFetchTime.value[familyId]
        if (!fetchTime) return false
        return (Date.now() - fetchTime) < duration
    }

    return {
        cachedPhotos,
        setCachedPhotos,
        getCachedPhotos,
        clearCache,
        isFresh
    }
})
