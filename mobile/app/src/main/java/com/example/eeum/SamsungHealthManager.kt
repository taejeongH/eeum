package com.example.eeum

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
// 와일드카드 대신 명시적으로 임포트 [cite: 40]
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import java.time.LocalDateTime
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit

//import com.samsung.android.sdk.health.data.*
//import com.samsung.android.sdk.health.data.permission.AccessType
//import com.samsung.android.sdk.health.data.permission.Permission
//import com.samsung.android.sdk.health.data.request.Ordering
//import com.samsung.android.sdk.health.data.error.*
//import java.time.LocalDateTime

class SamsungHealthManager(private val context: Context) {
    // 삼성 헬스 데이터 스토어 인스턴스 가져오기
    private val healthDataStore = HealthDataService.getStore(context)

    // 1. 권한 체크 및 요청
    suspend fun checkAndRequestPermissions(activity: Activity): Boolean {
        val permSet = setOf(
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.STEPS, AccessType.READ)
        )

        return try {
            val granted = healthDataStore.getGrantedPermissions(permSet)
            if (granted.containsAll(permSet)) {
                true
            } else {
                healthDataStore.requestPermissions(permSet, activity)
                false
            }
        } catch (e: HealthDataException) {
            // 삼성 헬스 미설치 등 해결 가능한 에러 처리
            if (e is ResolvablePlatformException && e.hasResolution) {
                e.resolve(activity)
            }
            false
        }
    }

    // 2. 최신 심박수 데이터 1건 조회
    suspend fun getLatestHeartRate(): String? {
        val endTime = LocalDateTime.now()
        val startTime = endTime.minusMinutes(10) // 최근 10분 데이터 필터

        val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
            .setOrdering(Ordering.DESC) // 최신순 정렬
            .setLimit(1)
            .build()

        return try {
            val response = healthDataStore.readData(readRequest)
            val latestData = response.dataList.firstOrNull()
            Gson().toJson(latestData) // Vue로 전달하기 위해 JSON 변환
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTodaySteps(): String? {
        // 오늘 00:00:00부터 현재까지의 범위를 설정합니다.
        val endTime = LocalDateTime.now()
        val startTime = endTime.withHour(0).withMinute(0).withSecond(0).withNano(0)
        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)

        // 1. 문서 지침에 따라 requestBuilder를 구성합니다.
        val readRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(
                localtimeFilter,
                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1) // 1시간 단위 그룹화
            )
            .setOrdering(Ordering.ASC)
            .build()

        return try {
            android.util.Log.d("SHD_DEBUG", "집계 요청 시작")
            val response = healthDataStore.aggregateData(readRequest)
            android.util.Log.d("SHD_DEBUG", "집계 완료: ${response.dataList.size}")
            val dataList = response.dataList

            // 🔥 자동완성 목록(image_15d857.png)에서 확인된 getValueOrDefault 사용
            // 각 시간대별(HOURLY) 데이터를 합산하여 오늘 전체 걸음수를 구합니다.
            val dailyStepCount = dataList.sumOf { it.getValueOrDefault(0L) }
            // 🔥 로그캣에 태그 'SHD_DEBUG'로 값을 출력합니다.
            android.util.Log.d("SHD_DEBUG", "추출된 오늘 총 걸음수: $dailyStepCount")

            val result = mapOf("count" to dailyStepCount)
            Gson().toJson(result)
        } catch (e: Exception) {
            null
        }
    }
}