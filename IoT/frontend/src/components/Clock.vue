
<template>
  <!-- 
    Clock Component
    - Position: Fixed (will be positioned by parent or self, here using simple props or default fixed)
    - Z-Index: High to stay on top
    - Transition: smooth movement for pixel shifting
  -->
  <div 
    class="pointer-events-none fixed z-20 transition-transform duration-[3000ms] ease-in-out select-none text-right"
    :style="{ transform: `translate3d(${offsetX}px, ${offsetY}px, 0)` }"
    :class="positionClasses"
  >
    <!-- Time -->
    <div class="text-[140px] font-bold text-white leading-[0.9] drop-shadow-lg font-mono tracking-tighter">
      {{ timeDisplay }}
    </div>
    <!-- Date -->
    <div class="text-4xl text-white/90 font-medium mt-2 mr-2 drop-shadow-md">
      {{ dateDisplay }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'

const props = defineProps({
  position: {
    type: String,
    default: 'top-right' // 'top-right', 'top-left'
  }
})

const positionClasses = computed(() => {
  return props.position === 'top-left' ? 'top-10 left-10 text-left' : 'top-10 right-10 text-right'
})

const now = ref(new Date())
const offsetX = ref(0)
const offsetY = ref(0)

// Formatters
const timeDisplay = computed(() => {
  const hours = String(now.value.getHours()).padStart(2, '0')
  const minutes = String(now.value.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
})

const dateDisplay = computed(() => {
  const days = ['일', '월', '화', '수', '목', '금', '토']
  const y = now.value.getFullYear()
  const m = String(now.value.getMonth() + 1).padStart(2, '0')
  const d = String(now.value.getDate()).padStart(2, '0')
  const dayName = days[now.value.getDay()]
  return `${y}.${m}.${d} (${dayName})`
})

// Timer ID
let timer = null
let burnInTimer = null

const updateTime = () => {
  now.value = new Date()
}

// Pixel Shifting Logic
// Move randomly within a small range (e.g., +/- 20px) every minute
const shiftPixels = () => {
  const range = 20
  offsetX.value = Math.floor(Math.random() * (range * 2 + 1)) - range
  offsetY.value = Math.floor(Math.random() * (range * 2 + 1)) - range
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
  
  // Burn-in protection interval: every 1 minute
  shiftPixels() // Initial shift
  burnInTimer = setInterval(shiftPixels, 60000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (burnInTimer) clearInterval(burnInTimer)
})
</script>

<style scoped>
/* Optional: Add custom font if needed, but Tailwind sans/mono usually works */
</style>
