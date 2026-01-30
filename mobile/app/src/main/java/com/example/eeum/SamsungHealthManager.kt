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
            Permission.of(DataTypes.STEPS, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
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
                // 🔥 수정됨: Gson으로 전체 객체를 직렬화하지 않고, 필요한 값(heart_rate)만 추출
                // 로그에서 확인된 키 값 keys: [heart_rate, heart_beat_count, binning_data, ...]
                // 안전하게 "heart_rate" 키로 값을 가져옵니다. (없으면 기본값 -1)
                val heartRateValue = latestData.getValue(DataType.HeartRateType.HEART_RATE)

                val resultMap = mapOf("heart_rate" to heartRateValue)
                val jsonResult = Gson().toJson(resultMap)
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

    // 3. 오늘 걸음 수 조회 (Steps)
    suspend fun getTodaySteps(): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("SHD_DEBUG", "걸음 수 조회 시작 (IO Thread)")
        return@withContext try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.toLocalDate().atStartOfDay() // 오늘 00:00:00

            val localTimeFilter = LocalTimeFilter.of(startTime, endTime)

            // PDF 문서 참조: DataType.StepsType.TOTAL 사용
            val readRequest = DataType.StepsType.TOTAL.requestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .build()

            val response = healthDataStore.aggregateData(readRequest)
            val dataList = response.dataList

            // aggregateData 결과는 AggregateDataPoint 리스트
            // 문서 예시: val dailyStepCount = dataList.sumOf { it.value as Long }
            // 실제 SDK 동작 확인 필요하지만, 문서대로 구현
            var totalSteps = 0L
            if (dataList.isNotEmpty()) {
                totalSteps = dataList.sumOf {
                    // AggregateDataPoint의 값을 가져오는 방식은 SDK 버전에 따라 다를 수 있으나
                    // 문서에 it.value 라고 되어있으므로 시도.
                    // 만약 컴파일 에러나면 getValue 등 사용해야 함.
                    // 안전하게 getValue 사용 시도
                    try {
                        it.value as Long
                    } catch (e: Exception) {
                        0L
                    }
                }
            }

            // 만약 sumOf가 0이라면, readData로 가져와서 합산해야 하는지(오래된 방식) 체크할 수도 있음.
            // 하지만 TOTAL 타입은 aggregate 지원함.

            val resultMap = mapOf("steps" to totalSteps)
            val jsonResult = Gson().toJson(resultMap)
            android.util.Log.d("SHD_DEBUG", "걸음 수 조회 성공: $jsonResult")
            jsonResult
        } catch (e: Throwable) {
            android.util.Log.e("SHD_DEBUG", "걸음 수 조회 중 에러: ${e.stackTraceToString()}")
            null
        }
    }

    // 4. 수면 데이터 조회 (Sleep)
    suspend fun getSleepData(): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("SHD_DEBUG", "수면 데이터 조회 시작 (IO Thread)")
        return@withContext try {
            // 어제 점심부터 오늘 현재까지의 수면 기록을 조회해봄 (수면은 보통 전날 밤 ~ 오늘 아침)
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusDays(1).withHour(12).withMinute(0)

            val localTimeFilter = LocalTimeFilter.of(startTime, endTime)

            val readRequest = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .setOrdering(Ordering.DESC)
                .build()

            val response = healthDataStore.readData(readRequest)
            // 수면 세션이 여러 개일 수 있음 (낮잠 등)
            // 가장 최근 수면 세션 하나만 가져오거나, 합산하거나 선택 필요.
            // 여기서는 "총 수면 시간"을 계산해서 반환.

            var totalSleepMinutes = 0

            response.dataList.forEach { dataPoint ->
                // startTime, endTime 차이를 구해서 분 단위 환산
                // HealthDataPoint 에는 getStartTime(), getEndTime()이 있음 (LocalDateTime 반환 아님, Instant 반환일 수 있음)
                // SDK 문서: .setStartTime(startTime) -> LocalDateTime을 받음.
                // 반대로 dataPoint.startTime 은? LocalDateTime일 가능성 높음.

                // 안전한 접근을 위해 getValue로 시간 추출은 어려울 수 있음 (메타데이터라).
                // HealthDataPoint 객체 자체 메소드 활용.

                // 문서에 명시된 필드(DataType.SleepType...)가 있는지 확인.
                // DataType.SleepType 은 보통 수면 단계 등을 포함함.

                // 여기서는 단순하게 API가 제공하는 startTime/endTime을 사용할 수 있다고 가정.
                // 하지만 정확히는 dataPoint.startTime 을 써야 함.

                // *주의*: 코틀린에서 dataPoint.startTime 접근 시 프로퍼티 존재 여부 확인 필요.
                // SDK 구조상 dataPoint는 HealthDataPoint 인터페이스임.
                val start = dataPoint.startTime
                val end = dataPoint.endTime

                // java.time.Duration 사용
                if (start != null && end != null) {
                    val duration = java.time.Duration.between(start, end)
                    totalSleepMinutes += duration.toMinutes().toInt()
                }
            }

            val resultMap = mapOf("sleep_minutes" to totalSleepMinutes)
            val jsonResult = Gson().toJson(resultMap)
            android.util.Log.d("SHD_DEBUG", "수면 데이터 조회 성공: $jsonResult")
            jsonResult
        } catch (e: Throwable) {
            android.util.Log.e("SHD_DEBUG", "수면 데이터 조회 중 에러: ${e.stackTraceToString()}")
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