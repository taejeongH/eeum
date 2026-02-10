<template>
  <div class="bg-[#fcfcfc] min-h-screen text-slate-800 pb-32 relative overflow-x-hidden">
    <!-- 프리미엄 헤더 영역 (HealthReportView에서 가져옴) -->
    <div
      class="relative w-full h-56 bg-[var(--color-primary)] rounded-b-[3rem] shadow-2xl overflow-hidden shrink-0"
    >
      <!-- 그라데이션 오버레이 -->
      <div class="absolute inset-0 bg-gradient-to-b from-black/20 to-transparent"></div>

      <!-- 장식 패턴 -->
      <div
        class="absolute top-[-50%] left-[-20%] w-[150%] h-[150%] opacity-10"
        style="
          background-image: radial-gradient(#fff 1px, transparent 1px);
          background-size: 24px 24px;
        "
      ></div>

      <!-- 네비게이션 바 -->
      <div class="relative z-30 flex justify-between items-start p-5 pt-6 pb-2">
        <button
          @click="$router.back()"
          class="p-2 -ml-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm"
        >
          <IconBack />
        </button>
        <h1 class="text-xl font-bold text-white tracking-tight pt-1.5">실시간 건강 정보</h1>
        <div class="flex items-center gap-2">
          <button
            @click="handleSync"
            class="p-2 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-md transition text-white border border-white/20 shadow-sm"
          >
            <span
              class="material-symbols-outlined text-base"
              :class="{ 'animate-spin': syncLoading }"
              >sync</span
            >
          </button>
        </div>
      </div>

      <!-- 날짜 표시 -->
      <div class="relative z-30 px-10 mt-1 flex flex-col items-center justify-center text-white">
        <p class="text-[10px] font-bold opacity-80 mb-0.5 tracking-widest">{{ formattedYear }}</p>
        <div class="flex items-center gap-2 mb-3">
          <h2 class="text-xl font-black tracking-tight">{{ formattedDate }}</h2>
        </div>

        <!-- 마지막 업데이트 배지 -->
        <div
          class="flex items-center gap-1.5 px-3 py-1 bg-white/10 backdrop-blur-md rounded-full text-white/90 border border-white/20"
        >
          <span class="material-symbols-outlined text-[12px] opacity-70">history</span>
          <span class="text-[9px] font-bold tracking-tight">{{ lastUpdateTime }}</span>
        </div>
      </div>
    </div>

    <!-- 0. 초기 로딩 상태 ("피부양자 없음" 깜빡임 방지) -->
    <div
      v-if="pageLoading"
      class="flex flex-col items-center justify-center min-h-[60vh] px-10 text-center space-y-4 animate-fade-in"
    >
      <div
        class="w-12 h-12 border-4 border-orange-500/10 border-t-orange-500 rounded-full animate-spin mb-2"
      ></div>
      <p class="text-slate-400 font-bold animate-pulse text-sm">데이터를 준비하고 있습니다...</p>
    </div>

    <div v-else-if="hasDependent" class="px-5 -mt-10 relative z-40 space-y-6">
      <!-- 동기화 상태 -->
      <div
        v-if="syncMessage"
        class="w-full p-3 bg-blue-50 text-blue-600 rounded-xl text-center text-sm font-medium animate-pulse shadow-sm"
      >
        {{ syncMessage }}
      </div>

      <!-- 1. 실제 생체 신호 섹션 -->
      <div class="space-y-4 pt-4">
        <!-- 빠른 통계 그리드 -->
        <div class="grid grid-cols-2 gap-3">
          <!-- 심박수 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50"
          >
            <span class="material-symbols-outlined text-health-heart text-2xl mb-1"
              >monitor_heart</span
            >
            <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1"
              >심박수</span
            >
            <p class="text-lg font-black text-slate-900">
              {{ healthStore.latestMetrics.averageHeartRate || '--' }}
              <span class="text-[9px] font-normal text-slate-400">bpm</span>
            </p>
            <div class="flex gap-2 mt-1 text-[8px] text-slate-400">
              <span>최저:{{ healthStore.latestMetrics.restingHeartRate || '--' }}</span>
              <span>최고:{{ healthStore.latestMetrics.maxHeartRate || '--' }}</span>
            </div>
          </div>
          <!-- 걸음 수 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50"
          >
            <span class="material-symbols-outlined text-health-steps text-2xl mb-1"
              >directions_walk</span
            >
            <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1"
              >걸음 수</span
            >
            <p class="text-lg font-black text-slate-900">
              {{ (healthStore.latestMetrics.steps || 0).toLocaleString() }}
            </p>
          </div>
        </div>

        <!-- 보조 통계 그리드 (칼로리 & 활동 시간) -->
        <div class="grid grid-cols-2 gap-3">
          <!-- 활동 칼로리 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50"
          >
            <span class="material-symbols-outlined text-health-cal text-2xl mb-1"
              >local_fire_department</span
            >
            <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1"
              >활동 칼로리</span
            >
            <p class="text-lg font-black text-slate-900">
              {{ healthStore.latestMetrics.activeCalories || 0 }}
              <span class="text-[9px] font-normal text-slate-400">kcal</span>
            </p>
          </div>
          <!-- 활동 시간 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-4 flex flex-col items-center border border-slate-50"
          >
            <span class="material-symbols-outlined text-health-time text-2xl mb-1">avg_pace</span>
            <span class="text-[9px] font-bold text-slate-400 uppercase tracking-widest mb-1"
              >활동 시간</span
            >
            <p class="text-lg font-black text-slate-900">
              {{ healthStore.latestMetrics.activeMinutes || 0 }}
              <span class="text-[9px] font-normal text-slate-400">분</span>
            </p>
          </div>
        </div>

        <!-- 혈압 & 혈중 산소 목록 -->
        <div class="space-y-3">
          <!-- 혈압 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-5 border border-slate-50 flex items-center justify-between"
          >
            <div class="flex items-center space-x-4">
              <div class="w-10 h-10 bg-red-50 rounded-2xl flex items-center justify-center">
                <span class="material-symbols-outlined text-health-pressure text-xl"
                  >blood_pressure</span
                >
              </div>
              <div>
                <p class="text-sm font-bold text-slate-900">혈압</p>
              </div>
            </div>
            <div class="text-right">
              <p
                v-if="healthStore.latestMetrics.systolicPressure"
                class="text-base font-black text-slate-900"
              >
                {{ healthStore.latestMetrics.systolicPressure }}/{{
                  healthStore.latestMetrics.diastolicPressure
                }}
                <span class="text-[9px] font-normal text-slate-400 ml-1">mmHg</span>
              </p>
              <p v-else class="text-slate-300 font-bold text-sm">데이터 없음</p>
            </div>
          </div>

          <!-- 혈중 산소 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-5 border border-slate-50 flex items-center justify-between"
          >
            <div class="flex items-center space-x-4">
              <div class="w-10 h-10 bg-blue-50 rounded-2xl flex items-center justify-center">
                <span class="material-symbols-outlined text-health-oxygen text-xl"
                  >oxygen_saturation</span
                >
              </div>
              <div>
                <p class="text-sm font-bold text-slate-900">혈중 산소</p>
              </div>
            </div>
            <div class="text-right">
              <p
                v-if="healthStore.latestMetrics.bloodOxygen"
                class="text-base font-black text-slate-900"
              >
                {{ healthStore.latestMetrics.bloodOxygen }}
                <span class="text-[9px] font-normal text-slate-400 ml-1">%</span>
              </p>
              <p v-else class="text-slate-300 font-bold text-sm">데이터 없음</p>
            </div>
          </div>

          <!-- 혈당 -->
          <div
            class="bg-white rounded-3xl shadow-sm p-5 border border-slate-50 flex items-center justify-between"
          >
            <div class="flex items-center space-x-4">
              <div class="w-10 h-10 bg-orange-50 rounded-2xl flex items-center justify-center">
                <span class="material-symbols-outlined text-health-sugar text-xl">glucose</span>
              </div>
              <div>
                <p class="text-sm font-bold text-slate-900">혈당</p>
              </div>
            </div>
            <div class="text-right">
              <p
                v-if="healthStore.latestMetrics.bloodGlucose"
                class="text-base font-black text-slate-900"
              >
                {{ healthStore.latestMetrics.bloodGlucose }}
                <span class="text-[9px] font-normal text-slate-400 ml-1">mg/dL</span>
              </p>
              <p v-else class="text-slate-300 font-bold text-sm">데이터 없음</p>
            </div>
          </div>
        </div>

        <!-- 수면 카드 -->
        <div
          class="bg-white rounded-[2rem] p-6 shadow-sm border border-slate-100 flex flex-col gap-5"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div
                class="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-500 flex items-center justify-center"
              >
                <span class="material-symbols-outlined text-xl">bedtime</span>
              </div>
              <div>
                <p class="text-xs font-bold text-slate-400">수면 분석</p>
                <p class="text-lg font-black text-slate-900">
                  {{
                    healthStore.latestMetrics.sleepTotalMinutes
                      ? Math.floor(healthStore.latestMetrics.sleepTotalMinutes / 60) +
                        '시간 ' +
                        (healthStore.latestMetrics.sleepTotalMinutes % 60) +
                        '분'
                      : '기록 없음'
                  }}
                </p>
              </div>
            </div>
          </div>

          <div v-if="healthStore.latestMetrics.sleepTotalMinutes" class="space-y-2">
            <div class="w-full h-4 bg-slate-50 rounded-lg overflow-hidden flex">
              <div
                class="h-full bg-indigo-200"
                :style="{
                  width:
                    (healthStore.latestMetrics.sleepRemMinutes /
                      healthStore.latestMetrics.sleepTotalMinutes) *
                      100 +
                    '%',
                }"
                title="REM"
              ></div>
              <div
                class="h-full bg-indigo-400"
                :style="{
                  width:
                    (healthStore.latestMetrics.sleepLightMinutes /
                      healthStore.latestMetrics.sleepTotalMinutes) *
                      100 +
                    '%',
                }"
                title="LIGHT"
              ></div>
              <div
                class="h-full bg-indigo-600"
                :style="{
                  width:
                    (healthStore.latestMetrics.sleepDeepMinutes /
                      healthStore.latestMetrics.sleepTotalMinutes) *
                      100 +
                    '%',
                }"
                title="DEEP"
              ></div>
            </div>
            <div class="flex justify-between text-[9px] font-bold text-slate-400 px-1">
              <div class="flex items-center gap-1">
                <span class="w-1.5 h-1.5 rounded-full bg-indigo-200"></span> REM
              </div>
              <div class="flex items-center gap-1">
                <span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span> 얕은 수면
              </div>
              <div class="flex items-center gap-1">
                <span class="w-1.5 h-1.5 rounded-full bg-indigo-600"></span> 깊은 수면
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. AI 건강 분석 (하단으로 이동됨) -->
      <div id="ai-analysis" class="space-y-6 pt-2">
        <div class="flex items-center justify-between px-2">
          <div class="flex items-center gap-2">
            <h3 class="text-lg font-bold text-slate-900">AI 건강 도우미 분석</h3>
            <button
              @click="showHelpModal = true"
              class="w-6 h-6 flex items-center justify-center rounded-full bg-slate-100 text-slate-400 hover:bg-slate-200 transition-colors"
            >
              <span class="material-symbols-outlined text-[16px]">help_outline</span>
            </button>
          </div>
          <button
            @click="handleAnalyze"
            :disabled="analyzeLoading"
            class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[var(--color-primary)] text-white text-[11px] font-bold shadow-lg shadow-orange-100 disabled:opacity-50 transition-all active:scale-95"
          >
            <span
              class="material-symbols-outlined text-sm"
              :class="{ 'animate-spin': analyzeLoading }"
              >auto_awesome</span
            >
            {{ analyzeLoading ? '분석 중...' : '분석하기' }}
          </button>
        </div>

        <!-- 전문가 코멘트 카드 (집중 뷰) -->
        <div
          class="bg-slate-900 rounded-[2.5rem] p-8 text-white relative overflow-hidden shadow-2xl mb-8 border border-white/5"
        >
          <div class="absolute top-0 right-0 p-10 opacity-10">
            <span class="material-symbols-outlined text-8xl">clinical_notes</span>
          </div>
          <div class="relative z-10 space-y-5">
            <div class="flex items-center gap-3">
              <div
                class="w-10 h-10 rounded-2xl bg-white/10 flex items-center justify-center backdrop-blur-md"
              >
                <span class="material-symbols-outlined text-orange-400">lightbulb</span>
              </div>
              <h3 class="text-xl font-bold">AI 어드바이저 코멘트</h3>
            </div>

            <!-- 분석 요약 (새 구조) -->
            <div
              v-if="healthStore.currentReport?.summary"
              class="bg-white/5 rounded-2xl p-5 border border-white/10 animate-fade-in relative overflow-hidden"
            >
              <div class="flex justify-between items-start mb-3">
                <div class="flex items-center gap-2">
                  <span class="text-2xl">{{
                    typeof healthStore.currentReport.summary === 'object'
                      ? healthStore.currentReport.summary.emoji
                      : '💡'
                  }}</span>
                  <span class="text-orange-200 text-sm font-bold tracking-tight"
                    >오늘의 핵심 요약</span
                  >
                </div>
                <div
                  v-if="
                    typeof healthStore.currentReport.summary === 'object' &&
                    healthStore.currentReport.summary.score
                  "
                  class="px-3 py-1 bg-orange-500/20 rounded-full border border-orange-500/30"
                >
                  <span class="text-orange-400 text-xs font-black"
                    >{{ healthStore.currentReport.summary.score }}점</span
                  >
                </div>
              </div>
              <p class="text-slate-200 text-[13.5px] leading-relaxed font-medium">
                {{
                  typeof healthStore.currentReport.summary === 'object'
                    ? healthStore.currentReport.summary.text
                    : healthStore.currentReport.summary
                }}
              </p>
            </div>

            <div class="space-y-6">
              <div class="text-slate-200 text-[14px] leading-relaxed font-medium space-y-4">
                <!-- 구조적 통찰 카드 -->
                <template v-if="formattedAdvisorComment.length > 0">
                  <div
                    v-for="(item, index) in formattedAdvisorComment"
                    :key="index"
                    class="group relative bg-[#1a1c23]/40 backdrop-blur-3xl rounded-[1.5rem] p-5 border border-white/10 shadow-[0_8px_32px_rgba(0,0,0,0.3)] transition-all duration-500 hover:translate-y-[-2px] hover:bg-[#21242e]/60 hover:border-white/20"
                  >
                    <div
                      class="absolute -inset-[1px] bg-gradient-to-br from-white/10 to-transparent rounded-[1.5rem] pointer-events-none opacity-50"
                    ></div>

                    <div class="flex gap-4 relative z-10">
                      <div class="flex flex-col items-center">
                        <div
                          :class="[
                            'w-10 h-10 rounded-2xl flex items-center justify-center transition-colors duration-500 shadow-lg',
                            item.type === 'WARNING' || item.type === 'URGENT'
                              ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                              : item.type === 'TREND'
                                ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                                : item.type === 'ADVICE' || item.type === 'ACTION'
                                  ? 'bg-green-500/10 text-green-400 border border-green-500/20'
                                  : 'bg-orange-500/10 text-orange-400 border border-orange-500/20',
                          ]"
                        >
                          <span class="material-symbols-outlined text-[20px] font-light">
                            {{ getIconForType(item.type) }}
                          </span>
                        </div>
                        <div
                          class="w-px flex-grow bg-gradient-to-b from-white/10 to-transparent mt-3 h-4"
                        ></div>
                      </div>

                      <div class="flex-grow pt-1.5">
                        <h4 class="text-white font-bold text-sm mb-1 opacity-90 tracking-tight">
                          {{ item.title || '건강 지표' }}
                        </h4>
                        <p
                          class="text-slate-300 leading-[1.6] text-[13.5px] font-medium tracking-tight"
                        >
                          {{ item.content || item.text }}
                        </p>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- 카드 내부 로컬 로더 (전역 로더 아님!) -->
                <div
                  v-else-if="analyzeLoading"
                  class="flex flex-col items-center justify-center py-20 px-6 animate-fade-in"
                >
                  <div class="relative mb-6">
                    <div
                      class="w-16 h-16 border-4 border-orange-500/10 border-t-orange-500 rounded-full animate-spin"
                    ></div>
                    <div class="absolute inset-0 flex items-center justify-center">
                      <span class="material-symbols-outlined text-orange-500 text-xl animate-pulse"
                        >auto_awesome</span
                      >
                    </div>
                  </div>
                  <p class="text-orange-200 text-sm font-bold tracking-tight mb-1">
                    AI가 어르신의 건강을 분석 중입니다
                  </p>
                  <p class="text-slate-500 text-[11px]">
                    잠시만 기다려주시면 전문적인 코멘트를 작성할게요.
                  </p>
                </div>

                <!-- 유리 디자인의 빈 상태 -->
                <div
                  v-else-if="!reportDescription && !healthStore.currentReport"
                  class="flex flex-col items-center justify-center py-16 px-6 text-center border-2 border-dashed border-white/5 rounded-[2.5rem] bg-white/[0.02]"
                >
                  <div
                    class="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center mb-4 backdrop-blur-md"
                  >
                    <span class="material-symbols-outlined text-white/20 text-3xl animate-pulse"
                      >clinical_notes</span
                    >
                  </div>
                  <p class="text-white/40 text-[13px] font-bold leading-relaxed">
                    아직 분석된 리포트가 없습니다.<br />
                    <span class="text-orange-400/60 font-black">AI 어드바이저</span>의 특별한 조언을
                    받아보세요.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 피부양자가 없는 그룹을 위한 빈 상태 -->
    <div v-else class="px-5 -mt-10 relative z-40">
      <div
        class="bg-white rounded-[2.5rem] p-10 shadow-xl border border-slate-50 flex flex-col items-center text-center space-y-6"
      >
        <div class="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center">
          <span class="material-symbols-outlined text-slate-300 text-5xl">person_off</span>
        </div>
        <div class="space-y-2">
          <h3 class="text-xl font-bold text-slate-800">피부양자가 없습니다</h3>
          <p class="text-sm text-slate-500 leading-relaxed">
            이 그룹에는 건강 정보를 확인할 피부양자(어르신)가 설정되어 있지 않습니다.<br />
            그룹 설정에서 피부양자를 추가해 주세요.
          </p>
        </div>
        <button
          @click="$router.push('/home')"
          class="px-8 py-3 bg-slate-900 text-white rounded-2xl font-bold text-sm hover:bg-slate-800 transition-all shadow-lg active:scale-95"
        >
          홈으로 돌아가기
        </button>
      </div>
    </div>

    <BottomNav />

    <!-- 도움말 모달 (GMS 분석) -->
    <div v-if="showHelpModal" class="fixed inset-0 z-[60] flex items-center justify-center p-6">
      <div
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="showHelpModal = false"
      ></div>
      <div
        class="relative w-full max-w-sm bg-white rounded-[2.5rem] p-8 shadow-2xl animate-fade-in"
      >
        <div class="flex items-center gap-3 mb-6">
          <div class="w-10 h-10 rounded-2xl bg-orange-50 flex items-center justify-center">
            <span class="material-symbols-outlined text-orange-500">live_help</span>
          </div>
          <h3 class="text-xl font-bold text-slate-900">도움말</h3>
        </div>

        <div class="space-y-6 mb-8 text-slate-600">
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-orange-100 flex items-center justify-center text-xs font-bold text-orange-600"
              >1</span
            >
            <p class="text-sm leading-relaxed">
              <strong>GMS 종합 분석</strong><br />기기에서 측정된 심박수, 혈압, 활동량 등 다양한
              생체 데이터를 통합하여 분석합니다.
            </p>
          </div>
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-orange-100 flex items-center justify-center text-xs font-bold text-orange-600"
              >2</span
            >
            <p class="text-sm leading-relaxed">
              <strong>AI 어드바이저 코멘트</strong><br />의학적 지식을 학습한 AI가 어르신의 일일
              건강 패턴을 분석하여 맞춤형 조언을 드립니다.
            </p>
          </div>
          <div class="flex gap-4">
            <span
              class="flex-shrink-0 w-6 h-6 rounded-full bg-orange-100 flex items-center justify-center text-xs font-bold text-orange-600"
              >3</span
            >
            <p class="text-sm leading-relaxed">
              <strong>주의 및 상담</strong><br />제공된 정보는 단순 참고용이며, 건강에 이상이 느껴질
              경우 반드시 전문 의료진과 상담하시기 바랍니다.
            </p>
          </div>
        </div>

        <button
          @click="showHelpModal = false"
          class="w-full py-4 rounded-2xl bg-slate-900 text-white font-bold text-base hover:bg-slate-800 transition-all"
        >
          확인했습니다
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * @component HealthDetailView
 * @description 하루의 건강 데이터(걸음 수, 심박수 등) 상세 조회 및 AI 분석 결과를 제공하는 화면입니다.
 *
 * [주요 기능]
 * - 일간 건강 리포트 조회 및 표시 (`healthStore`)
 * - AI 기반 건강 상태 분석 및 조언 제공
 * - 웨어러블 디바이스와의 데이터 동기화 (`useHealthSync`)
 * - 날짜별 데이터 탐색
 *
 * @dependency healthStore - 건강 데이터 및 리포트 관리
 * @dependency familyStore - 가족 멤버 선택 상태 공유
 * @dependency useHealthSync - 모바일 건강 데이터 동기화 로직
 */
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useFamilyStore } from '@/stores/family';
import { useHealthStore } from '@/stores/health';
import { useHealthSync } from '@/composables/useHealthSync';
import api from '@/services/api';
import BottomNav from '@/components/layout/BottomNav.vue';
import IconBack from '@/components/icons/IconBack.vue';
import { Logger } from '@/services/logger';

const route = useRoute();
const userStore = useUserStore();
const familyStore = useFamilyStore();
const healthStore = useHealthStore();

// Composable 사용
/**
 * @type {Object} 동기화 관련 상태 및 함수
 * @property {Ref<boolean>} syncLoading - 동기화 로딩 상태
 * @property {Ref<string>} syncMessage - 동기화 상태 메시지
 * @property {Function} handleSync - 동기화 실행 함수 (executeSync로 별칭 사용)
 */
const { syncLoading, syncMessage, handleSync: executeSync } = useHealthSync();

/**
 * @type {Ref<boolean>} AI 분석 로딩 상태
 */
const analyzeLoading = ref(false);

/**
 * @type {Ref<boolean>} 페이지 초기 로딩 상태
 */
const pageLoading = ref(true); // 페이지 로딩 상태

/**
 * @type {Ref<boolean>} 도움말 모달 표시 여부
 */
const showHelpModal = ref(false);

/**
 * @type {Ref<Array>} 가족 구성원 목록
 */
const members = ref([]);

/**
 * @type {ComputedRef<boolean>} 현재 사용자가 피부양자(데이터 제공자)인지 여부
 */
const isUserDependent = computed(() => {
  const myId = userStore.profile?.id;
  if (!myId || !members.value.length) return false;
  const dependent = members.value.find((m) => m.dependent);
  return dependent && String(dependent.userId) === String(myId);
});

/**
 * @type {ComputedRef<boolean>} 그룹 내 피부양자 존재 여부
 */
const hasDependent = computed(() => {
  return members.value.some((m) => m.dependent);
});

// 동기화 버튼 래퍼
/**
 * 동기화 버튼 클릭 핸들러
 * 사용자가 피부양자인지 여부에 따라 적절한 동기화(업로드/다운로드)를 수행합니다.
 */
const handleSync = () => {
  executeSync(isUserDependent.value);
};

/**
 * @type {ComputedRef<Object>} 최신 건강 데이터 지표
 */
const healthMetrics = computed(() => healthStore.latestMetrics);

/**
 * @type {ComputedRef<string|Array>} AI 분석 결과 상세 내용
 */
const reportDescription = computed(() => healthStore.currentReport?.description || '');

/**
 * @type {Ref<Date>} 현재 조회 중인 날짜
 */
const currentDate = ref(new Date());
const formattedYear = computed(() => currentDate.value.getFullYear() + '년');
const formattedDate = computed(() => {
  const options = { month: 'long', day: 'numeric', weekday: 'short' };
  return currentDate.value.toLocaleDateString('ko-KR', options);
});

/**
 * @type {ComputedRef<string>} 마지막 데이터 업데이트 시간 표시 문자열
 */
const lastUpdateTime = computed(() => {
  const time = healthStore.latestMetrics.recordDate;
  if (!time) return '데이터 없음';

  try {
    const date = new Date(time);
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) + ' 업데이트';
  } catch (e) {
    return '최근 업데이트';
  }
});

/**
 * @type {ComputedRef<Array>} 포맷팅된 AI 어드바이저 코멘트 목록
 * 서버에서 받은 문자열이나 객체 형태의 description을 일관된 배열 형태로 변환합니다.
 */
const formattedAdvisorComment = computed(() => {
  const desc = healthStore.currentReport?.description;
  if (!desc) return [];

  // 1. 이미 배열인 경우 (새 백엔드 구조)
  if (Array.isArray(desc)) return desc;

  // 2. 문자열인 경우, 파싱 또는 정제 시도
  if (typeof desc === 'string') {
    try {
      const parsed = JSON.parse(desc);
      if (Array.isArray(parsed)) return parsed;
      return parsed.description || [parsed];
    } catch (e) {
      // 대체: '1) ', '(1) ', '• ' 같은 마커 제거를 위한 정규식
      const lines = desc
        .split(/\n+/)
        .map((p) => p.trim())
        .filter((p) => p.length > 0);
      return lines.map((line) => {
        let cleanedText = line.replace(/^[•\-\*\d\.\(\)]+\s*/, '').trim();
        const isTitle =
          cleanedText.endsWith(':') || (cleanedText.length < 25 && !cleanedText.endsWith('.'));
        if (isTitle && cleanedText.endsWith(':')) cleanedText = cleanedText.slice(0, -1);

        return {
          title: isTitle ? cleanedText : 'AI 분석 결과',
          content: cleanedText,
          type: cleanedText.includes('주의') || cleanedText.includes('위험') ? 'WARNING' : 'ADVICE',
        };
      });
    }
  }
  return [];
});

/**
 * 조언 유형에 따른 아이콘 반환
 * @param {string} type - 조언 유형 (TREND, WARNING, ADVICE 등)
 * @returns {string} Material Symbols 아이콘 이름
 */
const getIconForType = (type) => {
  switch (type) {
    case 'TREND':
      return 'trending_up';
    case 'METRIC':
      return 'analytics';
    case 'WARNING':
    case 'URGENT':
      return 'warning';
    case 'ADVICE':
      return 'tips_and_updates';
    case 'SOCIAL':
      return 'chat';
    case 'ACTION':
      return 'task_alt';
    default:
      return 'auto_awesome';
  }
};

/**
 * 최신 건강 데이터 조회
 * 1. 최신 측정 지표(Metrics)를 먼저 조회하여 측정 날짜를 확인합니다.
 * 2. 해당 측정 날짜를 기준으로 일일 리포트를 조회하여 UI와 데이터의 싱크를 맞춥니다.
 */
const fetchLatestData = async () => {
  const familyId = familyStore.selectedFamily?.id;
  if (!familyId) return;

  try {
    // 1. 최신 측정 지표 우선 조회
    await healthStore.fetchLatestMetrics(familyId);

    // 2. 지표 기반 대상 날짜 결정
    let targetDate = currentDate.value.toISOString().split('T')[0];

    if (healthStore.latestMetrics.recordDate) {
      const recordDate = new Date(healthStore.latestMetrics.recordDate);
      currentDate.value = recordDate; // UI 날짜 업데이트
      targetDate = recordDate.toISOString().split('T')[0];
    }

    // 3. 올바른 날짜의 일일 리포트 조회
    await healthStore.fetchDailyReport(familyId, targetDate);
  } catch (error) {
    Logger.error('건강 데이터 조회 실패:', error);
  }
};

/**
 * AI 분석 요청 핸들러
 * 현재 날짜의 건강 데이터에 대한 재분석을 요청합니다.
 */
const handleAnalyze = async () => {
  const familyId = familyStore.selectedFamily?.id;
  if (!familyId) return;

  try {
    analyzeLoading.value = true;
    const today = currentDate.value.toISOString().split('T')[0];
    await healthStore.reanalyzeReport(familyId, today);
    syncMessage.value = 'AI 건강 분석이 완료되었습니다.'; // 피드백 표시를 위해 composable의 syncMessage 사용
    setTimeout(() => (syncMessage.value = ''), 3000);
  } catch (error) {
    Logger.error('분석 실패:', error);
    syncMessage.value = 'AI 분석 중 오류가 발생했습니다.';
    setTimeout(() => (syncMessage.value = ''), 3000);
  } finally {
    analyzeLoading.value = false;
  }
};

// fetchAllData, onReceiveAllHealthData, uploadToBackend 제거됨 (composable로 이동)

onMounted(async () => {
  pageLoading.value = true;

  if (route.query.scrollTo === 'analysis') {
    const checkElement = setInterval(() => {
      const el = document.getElementById('ai-analysis');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
        clearInterval(checkElement);
      }
    }, 100);
    setTimeout(() => clearInterval(checkElement), 3000); // 안전하게 제거
  } else {
    window.scrollTo(0, 0);
  }

  // 1. 가족 목록 로드 확인
  if (familyStore.families.length === 0) {
    await familyStore.fetchFamilies();
  }

  // 2. 선택된 그룹의 멤버 조회
  if (familyStore.selectedFamily?.id) {
    try {
      await Promise.all([
        (async () => {
          const response = await api.get(`/families/${familyStore.selectedFamily.id}/members`);
          members.value = response.data;
        })(),
        fetchLatestData(),
      ]);
    } catch (e) {
      Logger.error('초기 건강 데이터 조회 실패:', e);
    }
  }

  pageLoading.value = false;
});

// 선택된 가족 그룹 변경 시 데이터 다시 조회
watch(
  () => familyStore.selectedFamily?.id,
  async (newFamilyId) => {
    if (!newFamilyId) {
      members.value = [];
      return;
    }

    try {
      // 새 그룹 멤버 조회 및 권한 갱신
      const response = await api.get(`/families/${newFamilyId}/members`);
      members.value = response.data;
    } catch (e) {
      Logger.error('새 그룹 멤버 조회 실패:', e);
      members.value = [];
    }

    // 새 그룹의 최신 건강 데이터 조회
    fetchLatestData();
  },
  { immediate: false },
);
</script>

<style scoped>
.material-symbols-outlined {
  font-variation-settings:
    'FILL' 0,
    'wght' 400,
    'GRAD' 0,
    'opsz' 24;
}
.animate-spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
@keyframes fade-in {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
.animate-fade-in {
  animation: fade-in 0.2s ease-out;
}
</style>
