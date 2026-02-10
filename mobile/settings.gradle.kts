pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 프로젝트 리포지토리 설정 실패 시 빌드 중단 (일관성 유지)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 프로젝트 이름 설정
rootProject.name = "eeum"

// 모듈 포함 설정
include(":app")  // 모바일 앱 모듈
include(":wear") // Wear OS 모듈
 