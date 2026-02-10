#!/usr/bin/env bash
set -euo pipefail

WAIT_HTML="${WAIT_HTML:-/home/a105/eeum/wait.html}"
USER_DATA_DIR="${USER_DATA_DIR:-/tmp/eeum-chrome}"

# ----- find browser -----
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

# ----- URL 결정 -----
if [[ -f "$WAIT_HTML" ]]; then
  WAIT_URL="file://${WAIT_HTML}"
else
  echo "[kiosk] WARN: WAIT_HTML not found: $WAIT_HTML (fallback to about:blank)"
  WAIT_URL="about:blank"
fi

# ----- OSK 힌트 -----
gsettings set org.gnome.desktop.a11y.applications screen-keyboard-enabled true >/dev/null 2>&1 || true

# ----- Chromium: Wayland + OSK 반응성 핵심 옵션 -----
exec "$BROWSER" \
  --ozone-platform=wayland \
  --enable-features=UseOzonePlatform \
  --enable-wayland-ime \
  --wayland-text-input-version=3 \
  --app="$WAIT_URL" \
  --start-maximized \
  --user-data-dir="$USER_DATA_DIR" \
  --incognito \
  --password-store=basic \
  --use-mock-keychain \
  --no-first-run \
  --no-default-browser-check \
  --disable-infobars \
  --disable-session-crashed-bubble \
  --disable-translate \
  --disable-features=TranslateUI,CloudMessaging,TouchpadOverscrollHistoryNavigation,UseX11Platform \
  --overscroll-history-navigation=0 \
  --disable-pinch \
  --disable-background-networking \
  --disable-sync \
  --disable-component-update
