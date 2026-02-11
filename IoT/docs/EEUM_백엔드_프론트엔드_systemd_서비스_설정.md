# EEUM_백엔드_프론트엔드_systemd_서비스_설정.md

## 파일: /etc/systemd/system/eeum.service
```ini
[Unit]
Description=EEUM monitor service
After=network-online.target
Wants=network-online.target

StartLimitIntervalSec=30
StartLimitBurst=10

[Service]
Type=simple
User=a105
WorkingDirectory=/home/a105/eeum

EnvironmentFile=/home/a105/eeum/.env
Environment=LOG_LEVEL=DEBUG
Environment=PYTHONUNBUFFERED=1
Environment=PYTHONPATH=/home/a105/eeum

ExecStart=/home/a105/eeum/.venv/bin/python3 /home/a105/eeum/main.py

Restart=always
RestartSec=1

KillSignal=SIGINT
TimeoutStopSec=3
SendSIGKILL=yes
KillMode=control-group

ExecStopPost=-/usr/bin/pkill -u a105 -x ffmpeg
ExecStopPost=-/usr/bin/pkill -u a105 -x aplay
ExecStopPost=-/usr/bin/pkill -u a105 -x arecord

SuccessExitStatus=130 143

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### 적용
```bash
sudo systemctl daemon-reload
sudo systemctl restart eeum.service
systemctl status eeum.service --no-pager
```

### 부팅 자동 시작
```bash
sudo systemctl enable eeum.service
```

### 로그
```bash
sudo journalctl -u eeum.service -f
sudo journalctl -u eeum.service -n 200 --no-pager
```
---
## user service: ~/.config/systemd/user/eeum-kiosk.service
```ini
[Unit]
Description=EEUM Kiosk Browser (http://localhost:8080)
Wants=network-online.target
After=network-online.target graphical-session.target

[Service]
Type=simple
ExecStart=%h/eeum/kiosk.sh
Restart=always
RestartSec=1
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=default.target
```

### 적용/실행
```bash
mkdir -p ~/.config/systemd/user
systemctl --user daemon-reload
systemctl --user enable --now eeum-kiosk.service
systemctl --user status eeum-kiosk.service --no-pager
journalctl --user -u eeum-kiosk.service -f
```
---
## kiosk.sh: ~/eeum/kiosk.sh
```bash
#!/usr/bin/env bash
set -euo pipefail

waitHtmlPath="${WAIT_HTML:-/home/a105/eeum/wait.html}"
chromeProfileDir="${USER_DATA_DIR:-/tmp/eeum-chrome}"

browserCmd=""
for candidate in chromium chromium-browser; do
  if command -v "$candidate" >/dev/null 2>&1; then
    browserCmd="$candidate"
    break
  fi
done
[[ -n "$browserCmd" ]] || exit 1

waitUrl="about:blank"
[[ -f "$waitHtmlPath" ]] && waitUrl="file://${waitHtmlPath}"

gsettings set org.gnome.desktop.a11y.applications screen-keyboard-enabled true >/dev/null 2>&1 || true

commonFlags=(
  --ozone-platform=wayland
  --enable-features=UseOzonePlatform
  --enable-wayland-ime
  --wayland-text-input-version=3
  --start-maximized
  --user-data-dir="$chromeProfileDir"
  --incognito
  --password-store=basic
  --use-mock-keychain
  --no-first-run
  --no-default-browser-check
  --disable-infobars
  --disable-session-crashed-bubble
  --disable-translate
  --disable-features=TranslateUI,CloudMessaging,TouchpadOverscrollHistoryNavigation,UseX11Platform
  --overscroll-history-navigation=0
  --disable-pinch
)

"$browserCmd" --allow-file-access-from-files --app="$waitUrl" "${commonFlags[@]}" &
waitPid=$!

until curl -fsS "http://localhost:8080/ping" >/dev/null; do
  sleep 1
done

kill "$waitPid" 2>/dev/null || true
sleep 0.3

exec "$browserCmd" --app="http://localhost:8080/" "${commonFlags[@]}"
```

### CRLF 제거(필요 시)
```bash
sed -i 's/\r$//' ~/eeum/kiosk.sh
chmod +x ~/eeum/kiosk.sh
```

### wait.html: /home/a105/eeum/wait.html
```html
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>EEUM 시작 중</title>
  <style>
    body {
      margin: 0;
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      font-family: system-ui, sans-serif;
      background: #000;
      color: #fff;
    }
    .box { text-align: center; }
    .sub { margin-top: 8px; opacity: 0.7; font-size: 14px; }
  </style>
</head>
<body>
  <div class="box">
    <div style="font-size:22px;">EEUM 시작 중…</div>
    <div class="sub">서버 준비를 기다리는 중입니다</div>
  </div>
</body>
</html>
```
---
## labwc: OSK 자동 실행
### 파일: ~/.config/labwc/autostart
```bash
squeekboard &
```
---
## labwc: 상단바/데스크톱 비활성
### 대상 ~/.config/labwc/autostart /etc/xdg/labwc/autostart
주석 처리:
```bash
# /usr/bin/lwrespawn /usr/bin/pcmanfm --desktop --profile LXDE-pi &
# /usr/bin/lwrespawn /usr/bin/wf-panel-pi &
# /usr/bin/lxsession-xdg-autostart
```

즉시 반영:
```bash
pkill wf-panel-pi || true
pkill pcmanfm || true
labwc --exit
```

확인:
```bash
ps aux | egrep 'wf-panel-pi|pcmanfm|lwrespawn' | grep -v grep || echo "no panel / no desktop"
```
---
## labwc: Chromium 창 규칙 (rc.xml)
### 파일: ~/.config/labwc/rc.xml
```xml
<?xml version="1.0"?>
<openbox_config xmlns="http://openbox.org/3.4/rc">
  <touch deviceName="WaveShare WS170120" mapToOutput="HDMI-A-1" mouseEmulation="yes"/>

  <windowRules>
    <windowRule identifier="chromium" serverDecorations="no" skipWindowSwitcher="yes" skipTaskbar="yes">
      <action name="Maximize"/>
    </windowRule>

    <windowRule identifier="Chromium" serverDecorations="no" skipWindowSwitcher="yes" skipTaskbar="yes">
      <action name="Maximize"/>
    </windowRule>

    <windowRule identifier="chromium-browser" serverDecorations="no" skipWindowSwitcher="yes" skipTaskbar="yes">
      <action name="Maximize"/>
    </windowRule>

    <windowRule title=".*" app_id="chromium">
      <maximize>yes</maximize>
    </windowRule>

    <windowRule app_id="chromium">
      <skipTaskbar>yes</skipTaskbar>
      <skipPager>yes</skipPager>
    </windowRule>
  </windowRules>

  <mouse>
    <context name="Root">
    </context>
  </mouse>
</openbox_config>
```
---
## 절전 방지: /etc/systemd/logind.conf
```ini
[Login]
IdleAction=ignore
IdleActionSec=0
HandleSuspendKey=ignore
HandleHibernateKey=ignore
HandlePowerKey=ignore
```
적용:
```bash
sudo systemctl restart systemd-logind
```