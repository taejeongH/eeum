plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize") // sh sdk

    //Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.eeum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.eeum"
        minSdk = 29 // 삼성헬스 요구사항
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // libs 폴더에 있는 aar 파일을 명시적으로 포함
    // 실제 파일명인 'samsung-health-data-api-1.0.0.aar'로 정확히 수정합니다.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("samsung-health-data-api-1.0.0.aar"))))
    // 1. 코틀린 표준 라이브러리 (버전 1.9.22로 통일)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
//    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8") // 👈 SpillingKt 해결사
    // 2. 코루틴 라이브러리 (1.7.3 버전으로 통일)
    // 핵심: core와 android 버전이 반드시 같아야 합니다.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 3. 삼성 헬스 SDK 필수 런타임 추가
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime")
    // 4. Lifecycle (2.7.0 버전 유지)
//    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // SDK 내부에서 사용하는 Gson 필수
    implementation("com.google.code.gson:gson:2.10.1")
    // 기본 유지 //
    implementation("androidx.appcompat:appcompat:1.6.1") // 의존성 추가
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    //Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging")
}

//// ... (기존 코드들 건드리지 마시고 맨 아래에 추가하세요)
//
//// 1. 프론트엔드 경로 설정
//val frontendDir = file("${project.projectDir}/../../frontend")
//
//// 2. npm run build 실행 작업
//tasks.register<Exec>("buildVueApp") {
//    workingDir = frontendDir
//
//    // 수정된 부분: toLowerCase() 대신 ignoreCase = true 옵션 사용
//    if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
//        commandLine("npm.cmd", "run", "build")
//    } else {
//        commandLine("npm", "run", "build")
//    }
//}
//
//// 3. 빌드 결과물(dist)을 안드로이드 assets로 복사
//tasks.register<Copy>("copyVueAssets") {
//    dependsOn("buildVueApp")
//    from("$frontendDir/dist")
//    into("${project.projectDir}/src/main/assets")
//
//    // 🔥 항상 새로 복사하도록 설정
//    outputs.upToDateWhen { false }
//}
//// 4. 빌드 전 실행 연결
//tasks.named("preBuild") {
//    dependsOn("copyVueAssets")
//}

