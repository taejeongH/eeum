# 일반_와이파이_연결시_자동_시간동기화_rdate.md

## 목적
- wlan0가 “일반 인터넷망(핫스팟/일반 Wi-Fi)” 연결될 때만 자동 시간 동기화
- time 서버 접근 불가(보안망 등)면 아무것도 안 함
- 출력/로그 없음

## 전제
- rdate 경로: /usr/sbin/rdate
---
## 1) rdate 경로 확인
```bash
command -v rdate
```
---
## 2) NetworkManager dispatcher 스크립트 생성
```bash
sudo tee /etc/NetworkManager/dispatcher.d/90-time-sync >/dev/null <<'EOF'
#!/bin/bash
IFACE="$1"
STATUS="$2"

# wlan0 up 때만
if [ "$IFACE" = "wlan0" ] && [ "$STATUS" = "up" ]; then
  # time 서버 접근 불가면 종료(보안망 대비)
  ping -c1 -W1 time.bora.net >/dev/null 2>&1 || exit 0

  /usr/sbin/rdate -s time.bora.net >/dev/null 2>&1
  /sbin/hwclock -w >/dev/null 2>&1
fi
EOF
```
---
## 3) 권한 설정
```bash
sudo chown root:root /etc/NetworkManager/dispatcher.d/90-time-sync
sudo chmod 755 /etc/NetworkManager/dispatcher.d/90-time-sync
```
---
## 4) NetworkManager 재시작
```bash
sudo systemctl restart NetworkManager
```
---
## 5) 테스트
```bash
sudo nmcli device disconnect wlan0
sudo nmcli device connect wlan0
date
timedatectl
```
---
## 6) 스크립트 수동 테스트(선택)
```bash
sudo /etc/NetworkManager/dispatcher.d/90-time-sync wlan0 up
```