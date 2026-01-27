<template>
  <transition name="fade">
    <div
      v-if="show"
      class="fixed inset-0 bg-black/40 z-40"
      @click.self="close"
    />
  </transition>


  <transition name="slide-up">
    <div
      v-if="show"
      ref="sheet"
      class="fixed inset-x-0 bottom-0 z-50 bg-white rounded-t-3xl px-5 pt-3 pb-6 touch-pan-y"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
    >

      <div class="w-10 h-1.5 bg-gray-200 rounded-full mx-auto mb-4" />


      <h2 class="text-lg font-bold text-center mb-6">
        그룹 추가
      </h2>


      <div class="space-y-4">

        <button
          @click="selected = 'join'"
          class="w-full flex items-center gap-4 p-4 rounded-xl border transition"
          :class="cardClass('join')"
        >
          <IconCircle :active="selected === 'join'">

            <svg
              class="w-5 h-5"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M15 3h4a2 2 0 012 2v4m-6-6l6 6m0 0l-6 6m6-6H9"
              />
            </svg>
          </IconCircle>

          <div class="flex-1 text-left">
            <p :class="titleClass('join')">그룹 참여하기</p>
            <p class="text-sm text-gray-500">
              초대 코드를 입력해 참여합니다
            </p>
          </div>

          <RadioDot :active="selected === 'join'" />
        </button>

        <button
          @click="selected = 'create'"
          class="w-full flex items-center gap-4 p-4 rounded-xl border transition"
          :class="cardClass('create')"
        >
          <IconCircle :active="selected === 'create'">
            <svg
              class="w-5 h-5"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M15 19a4 4 0 00-4-4H5a4 4 0 00-4 4m10-9a4 4 0 110-8 4 4 0 010 8zm6 3v4m2-2h-4"
              />
            </svg>
          </IconCircle>

          <div class="flex-1 text-left">
            <p :class="titleClass('create')">새 그룹 생성하기</p>
            <p class="text-sm text-gray-500">
              그룹 이름과 관계를 설정합니다
            </p>
          </div>

          <RadioDot :active="selected === 'create'" />
        </button>
      </div>

      <div class="mt-6">
        <button
          class="eeum-btn-primary w-full"
          :disabled="!selected"
          @click="goNext"
        >
          다음 단계로
        </button>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

defineProps({ show: Boolean })
const emit = defineEmits(['close'])

const router = useRouter()
const selected = ref(null)

let startY = 0
let currentY = 0
const sheet = ref(null)

const onTouchStart = (e) => {
  startY = e.touches[0].clientY
}

const onTouchMove = (e) => {
  currentY = e.touches[0].clientY
  const diff = currentY - startY
  if (diff > 0) {
    sheet.value.style.transform = `translateY(${diff}px)`
  }
}

const onTouchEnd = () => {
  const diff = currentY - startY
  if (diff > 120) {
    close()
  } else {
    sheet.value.style.transform = ''
  }
}


const close = () => emit('close')

const goNext = () => {
  if (selected.value === 'join') {
    router.push('/join')
  }
  if (selected.value === 'create') {
    router.push('/groups/setup/step1')
  }
  close()
}


const cardClass = (type) =>
  selected.value === type
    ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)]'
    : 'border-gray-200'

const titleClass = (type) =>
  selected.value === type
    ? 'font-semibold text-[var(--color-primary)]'
    : 'font-semibold text-gray-800'
</script>


<script>
export default {
  components: {
    IconCircle: {
      props: ['active'],
      template: `
        <div
          class="w-10 h-10 rounded-full flex items-center justify-center"
          :class="active
            ? 'bg-[var(--color-primary)] text-white'
            : 'bg-gray-100 text-[var(--color-primary)]'"
        >
          <slot />
        </div>
      `,
    },
    RadioDot: {
      props: ['active'],
      template: `
        <div
          class="w-5 h-5 rounded-full border flex items-center justify-center"
          :class="active
            ? 'border-[var(--color-primary)]'
            : 'border-gray-300'"
        >
          <div
            v-if="active"
            class="w-3 h-3 rounded-full bg-[var(--color-primary)]"
          />
        </div>
      `,
    },
  },
}
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active {
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.slide-up-leave-active {
  transition: transform 0.25s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
