# EEUM 프로젝트 빌드 및 외부 서비스 가이드

본 문서는 EEUM(이음) 프로젝트의 빌드, 배포 방법 및 연동된 외부 서비스 정보를 통합하여 제공합니다.

---

## 개발 및 빌드 환경

### 기술 스택 버전
- **Backend**: Java 21 (OpenJDK), Spring Boot 3.5.9, Gradle 8.5
- **Frontend**: Node.js (v18+), Vue.js 3, Vite
- **Database**: MySQL 8.0 (AWS RDS), Redis
- **Infra**: AWS EC2, S3, Nginx, Docker, MQTT(Mosquitto)
- **IDE**: 
  - **IntelliJ IDEA**: 2024.1+ (Spring Boot 및 Java 21 지원)
  - **Android Studio**: Ladybug (2024.2.1) 이상 (AGP 9.0.0 지원)
  - **VS Code**: 최신 버전 (Vue.js 확장 도구 포함 권장)

---

## 빌드 및 배포 상세

### 환경 변수 및 사전 설정
빌드 및 실행 시 다음 설정이 필요합니다.

- **Spring Profile**: `secret` 또는 `prod` 활성화 필요
- **주요 환경 변수**:
  - `AWS_ACCESS_KEY` / `AWS_SECRET_KEY`: S3 접근용
  - `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`: 데이터베이스 접속용
  - `GMS_KEY` / `RUNPOD_KEY`: AI 서비스 API 연동용

### 배포 특이사항
- **Docker 기반**: 모든 서비스는 컨테이너화되어 배포됩니다.
- **Reverse Proxy**: Nginx를 통해 SSL 적용 및 포트 포워딩(`80` -> `443`, `/api` -> `8080`)을 수행합니다.
- **IoT 통신**: MQTT Broker는 8888 포트(SSL)를 사용하여 장치와 통신합니다.

---

## 주요 설정 파일 목록

| 경로 | 설명 | 비고 |
| :--- | :--- | :--- |
| `backend/src/main/resources/application-secret.yml` | API 키, DB 계정, AWS 정보 등 | **보안 주의** |
| `backend/src/main/resources/serviceAccountKey.json` | Firebase Admin SDK 인증키 | FCM 알림용 |
| `backend/src/main/resources/certs/` | JWT 서명용 RSA 키 페어 | `.pem` 파일 |
| `frontend/.env` | 프론트엔드 API 주소 설정 | |
| `mobile/app/google-services.json` | Firebase 안드로이드 설정 파일 | 모바일용 |
| `mobile/local.properties` | 안드로이드 로컬 설정 | |
| `IoT/apps/rpi5-fastapi/.env` | 라즈베리파이 게이트웨이 환경 변수 | 장치 고유값 등 |

---

## 외부 서비스 정보

| 서비스 구분 | 이름 | 주요 활용 내용 | 관련 설정 위치 |
| :--- | :--- | :--- | :--- |
| **인증** | 카카오(Kakao) | 소셜 로그인 및 사용자 인증 | `application-secret.yml` |
| **AI (Voice)** | RunPod | 목소리 학습(Cloning) 및 TTS 생성 | `application-secret.yml` |
| **AI (Chat)** | OpenAI (GMS) | 낙상 상황 대화 분석 (Sentimental) | `application-secret.yml` |
| **클라우드** | AWS S3 | 음성 파일(.wav), 이미지 파일 저장 | `application-secret.yml` |
| **데이터베이스** | AWS RDS | MySQL 사용자 및 도메인 데이터 저장 | `application-secret.yml` |
| **알림** | Firebase (FCM) | 낙상 감지 시 보호자 앱 푸시 알림 | `serviceAccountKey.json` |
| **메일** | Google SMTP (Gmail) | 계정 인증 및 알림 메일 발송 | `application-secret.yml` |
| **헬스케어** | Samsung Health | 갤럭시 워치 기반 심박수 데이터 수집 | `mobile/app/libs` |
| **통신** | MQTT | IoT 장치 간 실시간 이벤트 전송 | `application-secret.yml` |

---
