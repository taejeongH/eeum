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
