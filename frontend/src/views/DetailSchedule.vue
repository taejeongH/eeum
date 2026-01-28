<template>
  <div class="bg-background-light text-slate-800 min-h-screen flex flex-col">
    <header class="sticky top-0 z-20 bg-background-light/80 backdrop-blur-md px-6 pt-12 pb-4">
      <div class="flex items-center">
        <button @click="$router.back()" class="p-2 -ml-2 text-slate-600">
          <span class="material-symbols-outlined">arrow_back_ios</span>
        </button>
        <h1 class="flex-1 text-center text-xl font-bold text-slate-900 mr-8">일정 상세</h1>
      </div>
    </header>
    <main class="flex-1 px-6 pt-4 pb-24">
      <div class="space-y-8">
        <div class="space-y-1">
          <label class="text-sm font-semibold text-primary">제목</label>
          <h2 class="text-2xl font-bold text-slate-900">졸업식</h2>
        </div>
        <div class="grid grid-cols-1 gap-6">
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 rounded-full bg-accent-lavender flex items-center justify-center">
              <span class="material-symbols-outlined text-slate-600">person</span>
            </div>
            <div>
              <p class="text-xs text-slate-500">등록자</p>
              <p class="text-base font-semibold">아들</p>
            </div>
          </div>
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 rounded-full bg-accent-peach flex items-center justify-center">
              <span class="material-symbols-outlined text-slate-600">category</span>
            </div>
            <div>
              <p class="text-xs text-slate-500">일정 구분</p>
              <p class="text-base font-semibold">가족 행사</p>
            </div>
          </div>
          <div class="flex items-center gap-4">
            <div class="w-10 h-10 rounded-full bg-accent-sage flex items-center justify-center">
              <span class="material-symbols-outlined text-slate-600">calendar_today</span>
            </div>
            <div>
              <p class="text-xs text-slate-500">날짜 및 시간</p>
              <p class="text-base font-semibold">2025년 2월 11일 (화)</p>
              <p class="text-sm text-slate-600">오후 12:00 - 오후 1:00</p>
            </div>
          </div>
          <div class="pt-2">
            <p class="text-xs text-slate-500 mb-2">상세 내용</p>
            <div class="bg-surface-light p-4 rounded-2xl border border-slate-100 min-h-[120px]">
              <p class="text-base leading-relaxed text-slate-700">
                대학교 졸업식 참석입니다.<br/>
                가족들과 함께 점심 식사 예약이 되어 있습니다.<br/>
                꽃다발은 미리 준비했습니다.
              </p>
            </div>
          </div>
        </div>
      </div>
    </main>
    <div class="fixed bottom-0 left-0 right-0 px-6 py-4 flex gap-3 bg-gradient-to-t from-background-light via-background-light/90 to-transparent">
      <button class="flex-1 py-4 bg-slate-200 text-slate-700 font-bold rounded-2xl active:scale-[0.98] transition-all">
        삭제
      </button>
      <button class="flex-[2] py-4 bg-primary text-white font-bold rounded-2xl shadow-lg shadow-primary/20 active:scale-[0.98] transition-all">
        수정하기
      </button>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
</style>
