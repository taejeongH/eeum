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
      class="fixed inset-x-0 bottom-0 z-50 bg-white rounded-t-3xl px-5 pt-3 pb-6 touch-pan-y min-h-[300px] max-h-[90vh] overflow-y-auto"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
    >
      <div class="w-10 h-1.5 bg-gray-200 rounded-full mx-auto mb-4" />

      <template v-if="step === 0">
        <h2 class="text-lg font-bold text-center mb-6">그룹 추가</h2>

        <div class="space-y-4">
          <button
            @click="selected = 'join'"
            class="w-full flex items-center gap-4 p-4 rounded-xl border transition"
            :class="cardClass('join')"
          >
            <IconCircle 
              :active="selected === 'join'"
              :class="[
                selected === 'join' 
                  ? 'bg-[var(--color-primary)] text-white' 
                  : 'bg-gray-100 text-[var(--color-primary)]'
              ]"
            >
              <svg
                class="w-5 h-5 flex-shrink-0"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
                <polyline points="10 17 15 12 10 7" />
                <line x1="15" y1="12" x2="3" y2="12" />
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
            <IconCircle 
              :active="selected === 'create'"
              :class="[
                selected === 'create' 
                  ? 'bg-[var(--color-primary)] text-white' 
                  : 'bg-gray-100 text-[var(--color-primary)]'
              ]"
            >
              <svg
                class="w-5 h-5 flex-shrink-0"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="8.5" cy="7" r="4" />
                <line x1="20" y1="8" x2="20" y2="14" />
                <line x1="23" y1="11" x2="17" y2="11" />
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

        <button
          class="eeum-btn-primary w-full mt-6"
          :disabled="!selected"
          @click="goNext"
        >
          다음 단계로
        </button>
      </template>

      <template v-else-if="step === 1">
        <div class="relative flex items-center justify-center mb-6">
          <button 
            class="absolute left-0 p-1 text-gray-500 hover:text-gray-800 transition" 
            @click="handleBack"
          >
            <svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h2 class="text-lg font-bold">초대 코드 입력</h2>
        </div>

        <input
          v-model="inviteCode"
          class="eeum-input w-full"
          placeholder="초대 코드 혹은 초대 링크를 입력해주세요"
        />

        <button class="eeum-btn-primary w-full mt-6" @click="submitJoin">
          참여하기
        </button>
      </template>

      <template v-else-if="step === 2">
        <div class="relative flex items-center justify-center mb-6">
          <button 
            class="absolute left-0 p-1 text-gray-500 hover:text-gray-800 transition" 
            @click="handleBack"
          >
            <svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h2 class="text-lg font-bold">그룹 생성</h2>
        </div>

        <input
          v-model="groupName"
          class="eeum-input w-full mb-4"
          placeholder="그룹 이름"
        />

        <input
          v-model="relation"
          class="eeum-input w-full"
          placeholder="예) 큰아들, 딸 등"
        />

        <button
          class="eeum-btn-primary w-full mt-6"
          :disabled="!canCreate"
          @click="submitCreate"
        >
          생성하기
        </button>
      </template>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import IconCircle from './IconCircle.vue'
import RadioDot from './RadioDot.vue'
import { useModalStore } from '@/stores/modal'

const modalStore = useModalStore()

const props = defineProps({ 
  show: Boolean,
  initialStep: {
    type: Number,
    default: 0
  }
})
const emit = defineEmits(['close', 'join-group', 'create-group-request'])

const step = ref(props.initialStep)
const selected = ref(null)


watch(() => props.show, (newVal) => {
  if (newVal) {
    step.value = props.initialStep
    
    if (props.initialStep === 1) selected.value = 'join'
    else if (props.initialStep === 2) selected.value = 'create'
    else selected.value = null
  }
})


const inviteCode = ref('')


const groupName = ref('')
const relation = ref('') 
const canCreate = computed(() => groupName.value && relation.value)


let startY = 0
let currentY = 0
const sheet = ref(null)

const onTouchStart = (e) => (startY = e.touches[0].clientY)
const onTouchMove = (e) => {
  currentY = e.touches[0].clientY
  const diff = currentY - startY
  if (diff > 0) sheet.value.style.transform = `translateY(${diff}px)`
}
const onTouchEnd = () => {
  if (currentY - startY > 120) close()
  else sheet.value.style.transform = ''
}


const handleBack = () => {
  if (props.initialStep !== 0) {
    close();
  } else {
    step.value = 0;
  }
};

const goNext = () => {
  step.value = selected.value === 'join' ? 1 : 2
}

const submitJoin = () => {
  let code = inviteCode.value.trim();
  
  
  
  if (code.includes('code=')) {
     const match = code.match(/[?&]code=([^&]+)/);
     if (match && match[1]) {
        code = match[1];
     }
  }

  if (!code) {
      modalStore.openAlert('초대 코드를 입력해주세요.');
      return;
  }

  emit('join-group', code);
  close();
}

const submitCreate = () => {
  emit('create-group-request', { groupName: groupName.value, relation: relation.value });
  close();
}

const close = () => {
  step.value = 0
  selected.value = null
  inviteCode.value = ''
  groupName.value = ''
  relation.value = ''
  emit('close')
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