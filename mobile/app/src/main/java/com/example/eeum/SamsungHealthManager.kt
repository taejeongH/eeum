package com.example.eeum

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // 2. 최신 심박수 데이터 1건 조회 (수정됨)
    suspend fun getLatestHeartRate(): String? = withContext(Dispatchers.IO) { // 💡 IO 스레드로 강제 전환
        android.util.Log.d("SHD_DEBUG", "심박수 데이터 조회 시작 (IO Thread)")

        return@withContext try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusHours(24) // 💡 테스트를 위해 조회 범위를 하루로 확장

            // 💡 이 Builder 과정에서 SpillingKt 에러가 발생하므로 변수 생성을 try 내부로 이동
            val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
                .setOrdering(Ordering.DESC)
                .setLimit(1)
                .build()

            val response = healthDataStore.readData(readRequest)
            val latestData = response.dataList.firstOrNull()

            if (latestData != null) {
                val jsonResult = Gson().toJson(latestData)
                android.util.Log.d("SHD_DEBUG", "데이터 조회 성공: $jsonResult")
                jsonResult
            } else {
                android.util.Log.d("SHD_DEBUG", "조회된 심박수 데이터가 없습니다.")
                null
            }
        } catch (e: Throwable) { // 💡 Exception 대신 Throwable을 써서 NoClassDefFoundError까지 포착
            android.util.Log.e("SHD_DEBUG", "심박수 조회 중 치명적 에러: ${e.stackTraceToString()}")
            null
        }
    }
}

//    suspend fun getTodaySteps(): String? {
//        // 오늘 00:00:00부터 현재까지의 범위를 설정합니다.
//        val endTime = LocalDateTime.now()
//        val startTime = endTime.withHour(0).withMinute(0).withSecond(0).withNano(0)
//        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
//
//        // 1. 문서 지침에 따라 requestBuilder를 구성합니다.
//        val readRequest = DataType.StepsType.TOTAL.requestBuilder
//            .setLocalTimeFilterWithGroup(
//                localtimeFilter,
//                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1) // 1시간 단위 그룹화
//            )
//            .setOrdering(Ordering.ASC)
//            .build()
//
//        return try {
//            android.util.Log.d("SHD_DEBUG", "집계 요청 시작")
//            val response = healthDataStore.aggregateData(readRequest)
//            android.util.Log.d("SHD_DEBUG", "집계 완료: ${response.dataList.size}")
//            val dataList = response.dataList
//
//            // 🔥 자동완성 목록(image_15d857.png)에서 확인된 getValueOrDefault 사용
//            // 각 시간대별(HOURLY) 데이터를 합산하여 오늘 전체 걸음수를 구합니다.
//            val dailyStepCount = dataList.sumOf { it.getValueOrDefault(0L) }
//            // 🔥 로그캣에 태그 'SHD_DEBUG'로 값을 출력합니다.
//            android.util.Log.d("SHD_DEBUG", "추출된 오늘 총 걸음수: $dailyStepCount")
//
//            val result = mapOf("count" to dailyStepCount)
//            Gson().toJson(result)
//        } catch (e: Exception) {
//            null
//        }
//    }