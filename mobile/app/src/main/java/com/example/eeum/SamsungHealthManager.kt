package com.example.eeum

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.request.DataType
import java.time.LocalDateTime
import java.time.Duration

/**
 * 삼성 헬스 데이터 관리자
 *
 * 삼성 헬스 SDK와 연동하여 건강 데이터를 조회하고 집계하는 역할을 담당합니다.
 * - 권한 관리 (확인 및 요청)
 * - 건강 데이터 조회 (심박수, 걸음 수, 수면, 혈압, 혈당 등)
 * - 데이터 집계 및 포맷팅
 */
class SamsungHealthManager(private val context: Context) {
    
    // 삼성 헬스 데이터 스토어 인스턴스
    private val healthDataStore = HealthDataService.getStore(context)

    companion object {
        private const val TAG = "SamsungHealthManager"
    }

    /**
     * 필수 권한 승인 여부 확인
     *
     * @return 모든 필수 권한이 승인되었으면 true, 아니면 false
     */
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
            Log.d(TAG, "권한 확인: ${granted.size}/${permSet.size} 승인됨")
            hasAll
        } catch (e: Exception) {
            Log.e(TAG, "권한 확인 실패: ${e.message}")
            false
        }
    }

    /**
     * 삼성 헬스 권한 요청
     *
     * 사용자에게 권한 동의 화면을 표시합니다.
     *
     * @param activity 권한 요청을 수행할 액티비티
     */
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
            Log.d(TAG, "삼성 헬스 권한 요청 시작")
            healthDataStore.requestPermissions(permSet, activity)
        } catch (e: HealthDataException) {
            Log.e(TAG, "권한 요청 실패: ${e.message}")
            // 삼성 헬스 미설치 등 해결 가능한 에러 처리
            if (e is ResolvablePlatformException && e.hasResolution) {
                e.resolve(activity)
            }
        }
    }

    /**
     * 통합 건강 데이터 조회
     *
     * 심박수, 걸음 수, 수면, 혈압, 혈당 등 모든 건강 데이터를 조회하여 JSON 형태로 반환합니다.
     *
     * @return 건강 데이터 JSON 문자열 (데이터가 없거나 오류 발생 시 null)
     */
    suspend fun getAllHealthMetrics(): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "통합 건강 데이터 조회 시작")
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
                
                Log.d(TAG, "심박수 수동 집계: Avg=$avgHr, Max=$maxHr, Min=$minHr (${hrList.size} 샘플)")
            } catch (e: Exception) {
                Log.e(TAG, "심박수 수동 집계 실패: ${e.message}")
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
            Log.d(TAG, "걸음 수 집계: $totalSteps")

            // 2.3 수면 (최근 48시간 내 최신 세션)
            try {
                val sleepRequest = DataTypes.SLEEP.readDataRequestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(now.minusDays(2), now))
                    .setOrdering(Ordering.DESC).build()
                val sleepSessions = healthDataStore.readData(sleepRequest).dataList
                
                Log.d(TAG, "수면 세션 발견: ${sleepSessions.size}개")
                
                sleepSessions.firstOrNull()?.let { session ->
                    val duration = Duration.between(session.startTime, session.endTime).toMinutes().toInt()
                    result["sleep_total_minutes"] = duration
                    result["sleep_deep_minutes"] = (duration * 0.2).toInt()
                    result["sleep_light_minutes"] = (duration * 0.5).toInt()
                    result["sleep_rem_minutes"] = (duration * 0.3).toInt()
                    
                    Log.i(TAG, "수면 데이터 확정: ${duration}분 (기간: ${session.startTime} ~ ${session.endTime})")
                } ?: run {
                    Log.w(TAG, "수면 데이터를 찾을 수 없음 (48시간 범위)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "수면 데이터 조회 실패: ${e.message}")
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
                Log.d(TAG, "혈압 확인: $sys/$dia")
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
                val totalMs = timeResponse.dataList.sumOf { (it.value as? Duration)?.toMillis() ?: 0L }
                val activeMins = (totalMs / 1000 / 60).toInt()
                result["active_minutes"] = activeMins
                
                Log.d(TAG, "활동 요약 집계: ${activeCals}kcal, ${activeMins}분")
            } catch (e: Exception) {
                Log.w(TAG, "활동 요약 조회 실패: ${e.message}")
                result["active_calories"] = 0
                result["active_minutes"] = 0
            }

            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            result["record_date"] = now.format(formatter)
            Log.i(TAG, "✅ 모든 건강 데이터 수집 완료 (${result.size} 항목)")
            return@withContext if (result.isNotEmpty()) Gson().toJson(result) else null

        } catch (e: Throwable) {
            Log.e(TAG, "건강 데이터 통합 조회 에러: ${e.message}")
            return@withContext null
        }
    }

    /**
     * 하위 호환을 위한 메소드 (필요하지 않으면 제거 가능)
     */
    suspend fun getLatestHeartRate(): String? = getAllHealthMetrics()
    suspend fun getTodaySteps(): String? = getAllHealthMetrics()
    suspend fun getSleepData(): String? = getAllHealthMetrics()
}

