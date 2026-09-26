#!/bin/sh
# Build a release binary and wrap it into GestureLauncher.app (ad-hoc signed).
set -eu
cd "$(dirname "$0")/.."

APP=GestureLauncher.app
swift build -c release --product GestureLauncher
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
cp .build/release/GestureLauncher "$APP/Contents/MacOS/"
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key><string>GestureLauncher</string>
  <key>CFBundleIdentifier</key><string>dev.mr04vv.GestureLauncher</string>
  <key>CFBundleName</key><string>Gesture Launcher</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>0.1.0</string>
  <key>LSMinimumSystemVersion</key><string>15.0</string>
  <key>LSUIElement</key><true/>
</dict>
</plist>
PLIST
codesign --force --sign - "$APP"
echo "Built $(pwd)/$APP"
