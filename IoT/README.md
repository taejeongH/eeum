# EEUM IoT 프로젝트

EEUM IoT 디바이스 중 RPi5와 ESP32의 소프트웨어를 관리하는 저장소입니다.  
라즈베리파이(RPi) 기반 메인 디바이스 코드와  
ESP32 기반 서브 디바이스(센서/주변기기) 코드,  
그리고 운영·배포를 위한 문서를 함께 관리합니다.

---

## 디렉터리 구조

.
├─ apps  
│  ├─ rpi  
│  └─ esp32  
│  
├─ frontend
│ 
├─ docs  
│  ├─ systemd_journal_영구_저장_설정.md  
│  ├─ EEUM_백엔드_프론트엔드_systemd_서비스_설정.md  
│  ├─ Jetson_Orin_Nano_RPi_와이파이_유선공유_nftables.md  
│  ├─ 일반_와이파이_연결시_자동_시간동기화_rdate.md  
│  ├─ Python_venv_requirements_lock_설치.md  
│  └─ RPi_wlan1_프로비저닝_AP_nmcli_설정.md  
│  
└─ README.md

---

## apps

### apps/rpi
- Python 기반 백엔드 및 디바이스 제어 코드
- 개발 환경: Linux / VS Code
- 코드 스타일:
  - Black (formatter)
  - Ruff (lint)
  - 설정: `pyproject.toml`

### apps/esp32
- ESP32 Arduino 코드
- 개발 환경: Arduino IDE
- 자동 포맷/린트 미사용
- 주석 및 네이밍 규칙만 수동 적용

---

## frontend
- UI / 화면 렌더링 관련 코드
- 본 저장소에서는 구조만 관리
- 상세 구현 및 빌드 방식은 별도 기준에 따름

---

## docs

운영 및 배포에 필요한 설정 문서 모음입니다.  
각 문서는 단독 실행 가능한 절차 기준으로 작성되어 있습니다.

### 시스템 / 로그

- [journald 로그 영구 저장 설정](docs/systemd_journal_영구_저장_설정.md)

### 서비스 실행 (systemd)

- [백엔드 · 프론트엔드 systemd 서비스 설정](docs/EEUM_백엔드_프론트엔드_systemd_서비스_설정.md)

### 네트워크 / 공유

- [Jetson Orin Nano <-> RPi Wi-Fi 공유 (nftables NAT)](docs/Jetson_Orin_Nano_RPi_와이파이_유선공유_nftables.md)
- [일반 Wi-Fi 연결 시 자동 시간 동기화 (rdate)](docs/일반_와이파이_연결시_자동_시간동기화_rdate.md)

### 개발 환경

- [Python venv + requirements.lock 설치](docs/Python_venv_requirements_lock_설치.md)

### 프로비저닝 / AP

- [RPi wlan1 프로비저닝 AP 설정 (nmcli)](docs/RPi_wlan1_프로비저닝_AP_nmcli_설정.md)

---

## 운영 원칙

- 재부팅 후에도 유지되는 설정만 사용
- systemd / NetworkManager 기준
- 현장 배포 기준 문서
- 불필요한 출력 및 로그 최소화

---

## 참고

- RPi OS + NetworkManager
- Python 실행은 .venv 기준
- 오디오 및 네트워크 설정은 하드웨어 의존성 있음
