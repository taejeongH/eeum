// 최상위 빌드 파일: 모든 서브 프로젝트/모듈에 공통적인 구성 옵션을 정의합니다.
plugins {
    // Android 애플리케이션 플러그인 (버전 관리는 libs.versions.toml에서 수행)
    alias(libs.plugins.android.application) apply false
    // Kotlin Android 플러그인
    alias(libs.plugins.kotlin.android) apply false
    // Jetpack Compose 컴파일러 플러그인
    alias(libs.plugins.kotlin.compose) apply false
}

// Google Services (Firebase) 설정
buildscript {
    dependencies {
        // google-services 플러그인 클래스패스 추가
        classpath("com.google.gms:google-services:4.4.0")
    }
}