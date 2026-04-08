# Python_venv_requirements_lock_설치.md

## 목적
- ~/eeum 에서 venv(.venv) 생성
- 의존성은 `requirements.txt.lock`으로 한 번에 설치
- 필요한 시스템 패키지(ffmpeg/alsa 등)도 같이 설치
---
## 1) 초기화 (기존 venv 정리)
```bash
cd ~/eeum

deactivate 2>/dev/null || true
rm -rf .venv venv env
```
---
## 2) 시스템 패키지 설치(필수)
```bash
sudo apt update
sudo apt install -y \
  python3-venv python3-pip python3-full \
  ffmpeg alsa-utils libasound2-dev libsndfile1
```
---
## 3) venv 생성/활성화
```bash
python3 -m venv .venv
source .venv/bin/activate
```
---
### 확인:
```bash
which python
python -c "import sys; print(sys.prefix); print(sys.base_prefix)"
```
---
## 4) requirements.txt.lock로 설치
```bash
python -m pip install -U pip wheel setuptools
python -m pip install -r requirements.txt.lock
```