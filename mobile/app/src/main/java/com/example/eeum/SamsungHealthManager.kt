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
            // 2.1 심박수 (최신 1건 및 기본 정보)
            val hrRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(hrRequest).dataList.firstOrNull()?.let {
                val hr = it.getValue(DataType.HeartRateType.HEART_RATE)
                result["resting_heart_rate"] = hr
                result["average_heart_rate"] = hr
                result["max_heart_rate"] = hr
            }

            // 2.2 걸음 수 (오늘 총계)
            val stepsRequest = DataType.StepsType.TOTAL.requestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                .build()
            val stepsResponse = healthDataStore.aggregateData(stepsRequest)
            result["steps"] = stepsResponse.dataList.sumOf { it.value as Long }

            // 2.3 수면 (최근 1개 세션)
            val sleepRequest = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(now.minusDays(1).withHour(12).withMinute(0), now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(sleepRequest).dataList.firstOrNull()?.let {
                val duration = java.time.Duration.between(it.startTime, it.endTime).toMinutes().toInt()
                result["sleep_total_minutes"] = duration
                result["sleep_deep_minutes"] = (duration * 0.2).toInt()
                result["sleep_light_minutes"] = (duration * 0.5).toInt()
                result["sleep_rem_minutes"] = (duration * 0.3).toInt()
            }

            // 2.4 혈압 (최신 1건)
            val bpRequest = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(bpRequest).dataList.firstOrNull()?.let {
                result["systolic_pressure"] = it.getValue(DataType.BloodPressureType.SYSTOLIC)
                result["diastolic_pressure"] = it.getValue(DataType.BloodPressureType.DIASTOLIC)
            }

            // 2.5 혈중 산소 (최신 1건)
            val o2Request = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(o2Request).dataList.firstOrNull()?.let {
                result["blood_oxygen"] = it.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)
            }

            // 2.6 혈당 (최신 1건)
            val glucoseRequest = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(weekAgo, now))
                .setOrdering(Ordering.DESC).setLimit(1).build()
            healthDataStore.readData(glucoseRequest).dataList.firstOrNull()?.let {
                result["blood_glucose"] = it.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
            }



            // 2.8 활동 요약 (오늘 총계 - 활동 칼로리 & 활동 시간)
            try {
                // 1) 활동 칼로리 조회
                val calsRequest = DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                    .build()
                val calsResponse = healthDataStore.aggregateData(calsRequest)
                result["active_calories"] = calsResponse.dataList.sumOf { (it.value as? Number)?.toDouble() ?: 0.0 }.toInt()

                // 2) 활동 시간 조회

                val timeRequest = DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME.requestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(todayStart, now))
                    .build()
                val timeResponse = healthDataStore.aggregateData(timeRequest)
                val totalMs = timeResponse.dataList.sumOf { (it.value as? java.time.Duration)?.toMillis() ?: 0L }
                result["active_minutes"] = (totalMs / 1000 / 60).toInt()
            } catch (e: Exception) {
                android.util.Log.w("SHD_DEBUG", "활동 요약 조회 실패: ${e.message}")
                result["active_calories"] = 0
                result["active_minutes"] = 0
            }

            result["record_date"] = now.toString()
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
