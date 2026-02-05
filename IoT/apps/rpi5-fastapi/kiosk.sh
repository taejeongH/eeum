#!/usr/bin/env bash
set -euo pipefail

WAIT_HTML="/home/a105/eeum/wait.html"

# 브라우저 찾기
BROWSER=""
for c in chromium chromium-browser; do
  if command -v "$c" >/dev/null 2>&1; then
    BROWSER="$c"
    break
  fi
done
if [[ -z "${BROWSER}" ]]; then
  echo "[kiosk] ERROR: chromium not found"
  exit 1
fi

# GNOME a11y 스키마가 있으므로: OSK 자동 표시 활성화
gsettings set org.gnome.desktop.a11y.applications screen-keyboard-enabled true || true

# squeekboard가 이미 떠 있으면 재시작 (환경 반영)
pkill -f squeekboard 2>/dev/null || true
(squeekboard >/dev/null 2>&1 &)

# "가짜 풀스크린" = fullscreen을 피하고 maximized로
# --app: 탭/주소창 제거
# --start-maximized: fullscreen 아님(중요)
exec "$BROWSER" \
  --ozone-platform=wayland \
  --enable-features=UseOzonePlatform \
  --app="file://${WAIT_HTML}" \
  --start-maximized \
  --incognito \
  --password-store=basic \
  --use-mock-keychain \
  --no-first-run \
  --no-default-browser-check \
  --disable-infobars \
  --disable-session-crashed-bubble \
  --disable-features=TranslateUI \
  --overscroll-history-navigation=0 \
  --disable-pinch \
  --disable-background-networking \
  --disable-sync \
  --disable-features=CloudMessaging \
  --disable-component-update \
  --disable-features=TouchpadOverscrollHistoryNavigation

