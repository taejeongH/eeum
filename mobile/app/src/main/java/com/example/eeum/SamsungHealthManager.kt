package com.example.eeum

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
// 와일드카드 대신 명시적으로 임포트 [cite: 40]
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import java.time.Duration

//import com.samsung.android.sdk.health.data.*
//import com.samsung.android.sdk.health.data.permission.AccessType
//import com.samsung.android.sdk.health.data.permission.Permission
//import com.samsung.android.sdk.health.data.request.Ordering
//import com.samsung.android.sdk.health.data.error.*
//import java.time.LocalDateTime

class SamsungHealthManager(private val context: Context) {
    // 삼성 헬스 데이터 스토어 인스턴스 가져오기
    private val healthDataStore = HealthDataService.getStore(context)

    // 1. 권한 체크 (요청 없이 현재 상태만 확인)
    suspend fun hasAllPermissions(): Boolean {
        val permSet = setOf(
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.STEPS, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
            Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ),
            Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ)
        )

        return try {
            val granted = healthDataStore.getGrantedPermissions(permSet)
            val hasAll = granted.containsAll(permSet)
            android.util.Log.d("SHD_DEBUG", "권한 확인: ${granted.size}/${permSet.size} 승인됨")
            hasAll
        } catch (e: Exception) {
            android.util.Log.e("SHD_DEBUG", "권한 확인 실패: ${e.message}")
            false
        }
    }

    // 2. 권한 요청 (UI 표시)
    suspend fun requestPermissions(activity: Activity) {
        val permSet = setOf(
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.STEPS, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
            Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ),
            Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ)
        )

        try {
            android.util.Log.d("SHD_DEBUG", "삼성 헬스 권한 요청 시작")
            healthDataStore.requestPermissions(permSet, activity)
        } catch (e: HealthDataException) {
            android.util.Log.e("SHD_DEBUG", "권한 요청 실패: ${e.message}")
            // 삼성 헬스 미설치 등 해결 가능한 에러 처리
            if (e is ResolvablePlatformException && e.hasResolution) {
                e.resolve(activity)
            }
        }
    }

    // 2. 통합 건강 데이터 조회
    suspend fun getAllHealthMetrics(): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("SHD_DEBUG", "통합 건강 데이터 조회 시작")
        val result = mutableMapOf<String, Any?>()
        val now = LocalDateTime.now()
        val todayStart = now.toLocalDate().atStartOfDay()
        val weekAgo = now.minusDays(7)

        try {
            // 2.1 심박수 (오늘의 평균 및 최대치 - 수동 집계)
            try {
                val hrRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                    .build()
                val hrList = healthDataStore.readData(hrRequest).dataList
                
                var avgHr = 0
                var maxHr = 0
                var minHr = 0
                
                if (hrList.isNotEmpty()) {
                    val values = hrList.mapNotNull { it.getValue(DataType.HeartRateType.HEART_RATE)?.toInt() }
                    if (values.isNotEmpty()) {
                        avgHr = values.average().toInt()
                        var maxV = values[0]
                        var minV = values[0]
                        for (v in values) {
                            if (v > maxV) maxV = v
                            if (v < minV) minV = v
                        }
                        maxHr = maxV
                        minHr = minV
                    }
                }

                // 만약 오늘 데이터가 없다면 최근 7일 중 최신 1건이라도 가져옴 (Fallback)
                if (avgHr == 0) {
                    val fallbackRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                        .setOrdering(Ordering.DESC).setLimit(1).build()
                    healthDataStore.readData(fallbackRequest).dataList.firstOrNull()?.let {
                        val hr = it.getValue(DataType.HeartRateType.HEART_RATE)?.toInt() ?: 0
                        avgHr = hr
                        maxHr = hr
                        minHr = hr
                    }
                }

                result["resting_heart_rate"] = minHr
                result["average_heart_rate"] = avgHr
                result["max_heart_rate"] = maxHr
                
                android.util.Log.d("SHD_DEBUG", "심박수 수동 집계: Avg=$avgHr, Max=$maxHr, Min=$minHr (${hrList.size} 샘플)")
            } catch (e: Exception) {
                android.util.Log.e("SHD_DEBUG", "심박수 수동 집계 실패: ${e.message}")
                result["resting_heart_rate"] = 0
                result["average_heart_rate"] = 0
                result["max_heart_rate"] = 0
            }

            // 2.2 걸음 수 (오늘 총계)
            val stepsRequest = DataType.StepsType.TOTAL.requestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                .build()
            val stepsResponse = healthDataStore.aggregateData(stepsRequest)
            val totalSteps = stepsResponse.dataList.sumOf { (it.value as? Number)?.toLong() ?: 0L }.toInt()
            result["steps"] = totalSteps
            android.util.Log.d("SHD_DEBUG", "걸음 수 집계: $totalSteps")

            // 2.3 수면 (최근 48시간 내 최신 세션)
            try {
                val sleepRequest = DataTypes.SLEEP.readDataRequestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(now.minusDays(2), now))
                    .setOrdering(Ordering.DESC).build()
                val sleepSessions = healthDataStore.readData(sleepRequest).dataList
                
                android.util.Log.d("SHD_DEBUG", "수면 세션 발견: ${sleepSessions.size}개")
                
                sleepSessions.firstOrNull()?.let { session ->
                    val duration = java.time.Duration.between(session.startTime, session.endTime).toMinutes().toInt()
                    result["sleep_total_minutes"] = duration
                    result["sleep_deep_minutes"] = (duration * 0.2).toInt()
                    result["sleep_light_minutes"] = (duration * 0.5).toInt()
                    result["sleep_rem_minutes"] = (duration * 0.3).toInt()
                    
                    android.util.Log.i("SHD_DEBUG", "수면 데이터 확정: ${duration}분 (기간: ${session.startTime} ~ ${session.endTime})")
                } ?: run {
                    android.util.Log.w("SHD_DEBUG", "수면 데이터를 찾을 수 없음 (48시간 범위)")
                }
            } catch (e: Exception) {
                android.util.Log.e("SHD_DEBUG", "수면 데이터 조회 실패: ${e.message}")
            }

            // 2.4 혈압 (최신 1건)
            val bpRequest = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(bpRequest).dataList.firstOrNull()?.let {
                val sys = it.getValue(DataType.BloodPressureType.SYSTOLIC)?.toInt() ?: 0
                val dia = it.getValue(DataType.BloodPressureType.DIASTOLIC)?.toInt() ?: 0
                result["systolic_pressure"] = sys
                result["diastolic_pressure"] = dia
                android.util.Log.d("SHD_DEBUG", "혈압 확인: $sys/$dia")
            }

            // 2.5 혈중 산소 (최신 1건)
            val o2Request = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(o2Request).dataList.firstOrNull()?.let {
                result["blood_oxygen"] = it.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)?.toInt() ?: 0
            }

            // 2.6 혈당 (최신 1건)
            val glucoseRequest = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(glucoseRequest).dataList.firstOrNull()?.let {
                result["blood_glucose"] = it.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)?.toInt() ?: 0
            }



            // 2.8 활동 요약 (오늘 총계 - 활동 칼로리 & 활동 시간)
            try {
                // 1) 활동 칼로리 조회
                val calsRequest = DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                    .build()
                val calsResponse = healthDataStore.aggregateData(calsRequest)
                val activeCals = calsResponse.dataList.sumOf { (it.value as? Number)?.toDouble() ?: 0.0 }.toInt()
                result["active_calories"] = activeCals

                // 2) 활동 시간 조회

                val timeRequest = DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME.requestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                    .build()
                val timeResponse = healthDataStore.aggregateData(timeRequest)
                val totalMs = timeResponse.dataList.sumOf { (it.value as? java.time.Duration)?.toMillis() ?: 0L }
                val activeMins = (totalMs / 1000 / 60).toInt()
                result["active_minutes"] = activeMins
                
                android.util.Log.d("SHD_DEBUG", "활동 요약 집계: ${activeCals}kcal, ${activeMins}분")
            } catch (e: Exception) {
                android.util.Log.w("SHD_DEBUG", "활동 요약 조회 실패: ${e.message}")
                result["active_calories"] = 0
                result["active_minutes"] = 0
            }

            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            result["record_date"] = now.format(formatter)
            android.util.Log.i("SHD_DEBUG", "✅ 모든 건강 데이터 수집 완료 (${result.size} 항목)")
            return@withContext if (result.isNotEmpty()) Gson().toJson(result) else null

        } catch (e: Throwable) {
            android.util.Log.e("SHD_DEBUG", "건강 데이터 통합 조회 에러: ${e.message}")
            return@withContext null
        }
    }




    // 하위 호환을 위한 기존 메소드 유지 (필요시)
    suspend fun getLatestHeartRate(): String? = getAllHealthMetrics()
    suspend fun getTodaySteps(): String? = getAllHealthMetrics()
    suspend fun getSleepData(): String? = getAllHealthMetrics()
}
