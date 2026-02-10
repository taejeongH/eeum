plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.eeum.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.eeum"
        minSdk = 30 // Wear OS 3.0 이상 권장
        targetSdk = 34 // Wear OS 타겟 버전
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ------------------------------------------------------------
    // 1. Wear OS & UI
    // ------------------------------------------------------------
    implementation(libs.play.services.wearable) // Google Play Services (Wearable)
    implementation(platform(libs.androidx.compose.bom)) // Compose BOM
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // Wear OS 전용 Material Design 및 Foundation
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    
    // ------------------------------------------------------------
    // 2. Android Core & Utils
    // ------------------------------------------------------------
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx) // LifecycleScope

    // ------------------------------------------------------------
    // 3. Health Services (심박수 측정 등)
    // ------------------------------------------------------------
    implementation(libs.androidx.health.services.client) // Health Services Client
    implementation(libs.androidx.lifecycle.service) // LifecycleService (백그라운드 서비스용)
    implementation(libs.androidx.concurrent.futures) // ListenableFuture 변환용

    // ------------------------------------------------------------
    // 4. Coroutines
    // ------------------------------------------------------------
    implementation(libs.kotlinx.coroutines.guava) // Guava ListenableFuture 변환 지원
    implementation(libs.kotlinx.coroutines.play.services) // Play Services Task 변환 지원
    implementation(libs.kotlinx.coroutines.android) 

    // ------------------------------------------------------------
    // 5. Testing
    // ------------------------------------------------------------
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
