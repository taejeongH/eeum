import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize") // Samsung Health SDK 데이터 처리를 위해 필요
    id("com.google.gms.google-services") // Firebase 연동
}

android {
    namespace = "com.example.eeum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.eeum"
        minSdk = 29 // Samsung Health SDK 최소 요구사항
        targetSdk = 36 // 최신 Android 버전 타겟팅
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties 파일 로드 (비공개 키, URL 등)
        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                load(file.inputStream())
            }
        }

        // 환경 변수 설정 (기본값 제공)
        val webviewUrl = localProperties.getProperty("WEBVIEW_URL") ?: "https://i14a105.p.ssafy.io"
        val apiBaseUrl = localProperties.getProperty("API_BASE_URL") ?: "https://i14a105.p.ssafy.io"

        // BuildConfig에 필드 추가
        buildConfigField("String", "WEBVIEW_URL", "\"$webviewUrl\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // 릴리즈 빌드 시 난독화 비활성화 (필요 시 true로 변경)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig 클래스 생성 허용
    }
}

dependencies {
    // ------------------------------------------------------------
    // 1. 로컬 라이브러리 (Samsung Health SDK)
    // ------------------------------------------------------------
    // libs 폴더 내의 samsung-health-data-api-1.0.0.aar 파일 포함
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("samsung-health-data-api-1.0.0.aar"))))

    // ------------------------------------------------------------
    // 2. Kotlin & Coroutines
    // ------------------------------------------------------------
    // Kotlin BOM을 사용하여 표준 라이브러리 버전 관리 (libs.versions.toml 권장하지만 BOM 사용 시 명시적 버전 불필요)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // Coroutines: 비동기 처리를 위한 핵심 라이브러리 (Android 전용 포함)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Samsung Health SDK 런타임 의존성
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime")

    // ------------------------------------------------------------
    // 3. Android Core & UI (Jetpack Compose)
    // ------------------------------------------------------------
    implementation("androidx.appcompat:appcompat:1.6.1") // 호환성 지원
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM: Compose 라이브러리 버전 일관성 유지
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ------------------------------------------------------------
    // 4. Wearable Integration (데이터 레이어)
    // ------------------------------------------------------------
    implementation(libs.play.services.wearable) // Wearable Data Layer API
    implementation(libs.kotlinx.coroutines.play.services) // Play Services용 코루틴 확장

    // ------------------------------------------------------------
    // 5. Firebase (FCM)
    // ------------------------------------------------------------
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    // ------------------------------------------------------------
    // 6. Utils & WorkManager
    // ------------------------------------------------------------
    implementation("com.google.code.gson:gson:2.10.1") // JSON 파싱
    implementation(libs.androidx.work.runtime.ktx) // 백그라운드 작업

    // ------------------------------------------------------------
    // 7. Testing
    // ------------------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

