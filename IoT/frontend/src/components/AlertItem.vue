<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  alert: {
    type: Object,
    required: true
  }
});

const emit = defineEmits(['close', 'dismiss']);

// --- Helpers ---
const formatTime = (timestamp) => {
  if (!timestamp) return '';
  const date = new Date(timestamp * 1000); 
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
};

const getAlertStyle = (kind) => {
  switch (kind) {
    case 'medication':
      return {
        bg: 'bg-white',
        iconBg: 'bg-gradient-to-br from-[#FF4081] to-[#C2185B]',
        textTitle: 'text-[#C2185B]',
        icon: 'medication',
        title: '복약 시간 알림'
      };
    case 'schedule':
      return {
        bg: 'bg-white',
        iconBg: 'bg-gradient-to-br from-[#2196F3] to-[#1976D2]',
        textTitle: 'text-[#1976D2]',
        icon: 'calendar_today',
        title: '오늘의 일정'
      };
    case 'voice':
      return {
        bg: 'bg-white',
        iconBg: 'bg-gradient-to-br from-[#FF9800] to-[#F57C00]',
        textTitle: 'text-[#E65100]',
        icon: 'record_voice_over',
        title: '가족 메시지'
      };
    case 'message':
      return {
        bg: 'bg-white',
        iconBg: 'bg-gradient-to-br from-[#4CAF50] to-[#388E3C]',
        textTitle: 'text-[#388E3C]',
        icon: 'chat',
        title: '새로운 메시지'
      };
     case 'photo':
      return {
        bg: 'bg-white',
        iconBg: 'bg-gradient-to-br from-[#AB47BC] to-[#7B1FA2]',
        textTitle: 'text-[#7B1FA2]',
        icon: 'image',
        title: '새 사진'
      };
    default:
      return {
        bg: 'bg-white',
        iconBg: 'bg-gradient-to-br from-gray-500 to-gray-700',
        textTitle: 'text-gray-700',
        icon: 'notifications',
        title: '알림'
      };
  }
};

const styles = computed(() => getAlertStyle(props.alert.kind));

// --- Swipe Logic ---
const touchStartX = ref(0);
const touchCurrentX = ref(0);
const isSwiping = ref(false);

const startSwipe = (clientX) => {
  touchStartX.value = clientX;
  isSwiping.value = true;
};

const moveSwipe = (clientX) => {
  if (!isSwiping.value) return;
  const diff = clientX - touchStartX.value;
  if (diff > 0) {
    touchCurrentX.value = diff;
  }
};

const endSwipe = () => {
  if (!isSwiping.value) return;
  isSwiping.value = false;
  
  if (touchCurrentX.value > 150) {
    touchCurrentX.value = 1000; 
    setTimeout(() => {
      emit('dismiss'); // Swipe to dismiss (Delete from history)
    }, 200);
  } else {
    touchCurrentX.value = 0;
  }
};

const onTouchStart = (e) => startSwipe(e.touches[0].clientX);
const onTouchMove = (e) => moveSwipe(e.touches[0].clientX);
const onTouchEnd = () => endSwipe();

const onMouseDown = (e) => startSwipe(e.clientX);
const onMouseMove = (e) => moveSwipe(e.clientX);
const onMouseUp = () => endSwipe();
const onMouseLeave = () => endSwipe();

</script>

<template>
  <div 
    class="relative rounded-[2rem] shadow-2xl p-8 bg-white/95 backdrop-blur-md border border-white/20 overflow-hidden cursor-grab active:cursor-grabbing touch-pan-y select-none"
    :style="{ transform: `translateX(${touchCurrentX}px)`, transition: isSwiping ? 'none' : 'transform 0.3s ease-out' }"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @mousedown="onMouseDown"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @mouseleave="onMouseLeave"
  >
    <div class="flex items-start gap-8 pointer-events-none">
      <div class="flex-shrink-0 w-28 h-28 rounded-full flex items-center justify-center text-white shadow-lg mt-1"
           :class="styles.iconBg">
        <span class="material-symbols-outlined text-6xl">{{ styles.icon }}</span>
      </div>

      <div class="flex-1 min-w-0 py-2">
        <div class="flex justify-between items-baseline mb-4">
           <h3 class="font-extrabold text-4xl leading-tight tracking-tight"
               :class="styles.textTitle">
            {{ styles.title }}
          </h3>
          <span class="text-2xl text-gray-400 font-medium">
             {{ formatTime(alert.sent_at) || '방금 전' }}
          </span>
        </div>
       
        <div class="prose prose-xl max-w-none">
          <div v-if="alert.kind === 'medication'">
             <p class="text-gray-800 text-3xl font-bold leading-normal break-keep">{{ alert.content }}</p>
          </div>
          <div v-else-if="alert.kind === 'schedule'">
            <p class="text-gray-500 text-2xl mb-4 font-bold">오늘의 주요 일정</p>
             <div class="space-y-4">
                <div v-for="(event, idx) in alert.data?.events_for_today" :key="idx" 
                     class="flex items-center gap-5 bg-gray-50 p-4 rounded-2xl border border-gray-100">
                    <div class="w-3 h-3 rounded-full bg-blue-500 shrink-0"></div>
                    <span class="font-black text-3xl text-gray-900 min-w-[100px]">{{ event.time }}</span>
                    <span class="font-bold text-3xl text-gray-700 truncate">{{ event.title }}</span>
                </div>
             </div>
          </div>
          <div v-else>
            <p class="text-gray-800 text-3xl font-medium leading-normal">{{ alert.content }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="absolute bottom-2 left-1/2 transform -translate-x-1/2 w-16 h-1.5 bg-gray-200 rounded-full opacity-50"></div>

    <div class="absolute bottom-0 left-0 h-1.5 bg-gray-100 w-full pointer-events-none">
      <div class="h-full w-full animate-shrink origin-left"
           :class="styles.textTitle.replace('text-', 'bg-')"></div>
    </div>
  </div>
</template>

<style scoped>
.shadow-2xl {
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.15);
}

@keyframes shrink {
  from { transform: scaleX(1); }
  to { transform: scaleX(0); }
}

.animate-shrink {
  animation: shrink 10s linear forwards;
}
</style>
