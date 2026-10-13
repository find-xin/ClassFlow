#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MACOS_DIR="${ROOT_DIR}/apps/macos"
DIST_DIR="${ROOT_DIR}/dist"
APP_NAME="ClassFlow"
BUNDLE_DIR="${DIST_DIR}/${APP_NAME}.app"
DMG_NAME="${APP_NAME}-1.0.0-macOS-arm64.dmg"

echo "============================================================"
echo " Packaging ClassFlow for macOS (Apple Silicon arm64)"
echo "============================================================"

# 1. Compile Release Binary
echo "[1/5] Building release binary..."
cd "${MACOS_DIR}"
swift build -c release

RELEASE_BIN="${MACOS_DIR}/.build/release/${APP_NAME}"
if [[ ! -f "${RELEASE_BIN}" ]]; then
    echo "Error: Release binary not found at ${RELEASE_BIN}"
    exit 1
fi

# 2. Prepare .app Bundle Structure
echo "[2/5] Constructing ${APP_NAME}.app bundle..."
rm -rf "${DIST_DIR}"
mkdir -p "${BUNDLE_DIR}/Contents/MacOS"
mkdir -p "${BUNDLE_DIR}/Contents/Resources"

cp "${RELEASE_BIN}" "${BUNDLE_DIR}/Contents/MacOS/${APP_NAME}"
chmod +x "${BUNDLE_DIR}/Contents/MacOS/${APP_NAME}"

# 3. Write Info.plist
echo "[3/5] Generating Info.plist..."
cat << 'EOF' > "${BUNDLE_DIR}/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>ClassFlow</string>
    <key>CFBundleIdentifier</key>
    <string>org.classflow.ClassFlow</string>
    <key>CFBundleName</key>
    <string>ClassFlow</string>
    <key>CFBundleDisplayName</key>
    <string>ClassFlow 课程表</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>15.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSCalendarsUsageDescription</key>
    <string>ClassFlow 需要访问系统日历，以便将您的课程日程同步至系统日历中。</string>
    <key>NSCalendarsFullAccessUsageDescription</key>
    <string>ClassFlow 需要完整访问系统日历权限，以便自动创建专属课程表日历并在调课停课时保持同步。</string>
    <key>NSRemindersUsageDescription</key>
    <string>ClassFlow 需要提醒权限以便发送上课前提醒。</string>
    <key>NSHumanReadableCopyright</key>
    <string>Copyright © 2026 ClassFlow Contributors. Licensed under Apache-2.0.</string>
</dict>
</plist>
EOF

# 4. Create ZIP Distribution
echo "[4/5] Creating ZIP archive..."
cd "${DIST_DIR}"
zip -r -q "${APP_NAME}-1.0.0-macOS-arm64.zip" "${APP_NAME}.app"

# 5. Create DMG Disk Image with Applications symlink
echo "[5/5] Building Apple DMG disk image..."
STAGING_DIR="${DIST_DIR}/staging"
mkdir -p "${STAGING_DIR}"
cp -R "${BUNDLE_DIR}" "${STAGING_DIR}/"
ln -s /Applications "${STAGING_DIR}/Applications"

hdiutil create \
    -volname "ClassFlow" \
    -srcfolder "${STAGING_DIR}" \
    -ov \
    -format UDZO \
    "${DIST_DIR}/${DMG_NAME}"

rm -rf "${STAGING_DIR}"

# 6. Generate Checksums
cd "${DIST_DIR}"
shasum -a 256 "${DMG_NAME}" > "${DMG_NAME}.sha256"
shasum -a 256 "${APP_NAME}-1.0.0-macOS-arm64.zip" > "${APP_NAME}-1.0.0-macOS-arm64.zip.sha256"

echo "============================================================"
echo " Packaging Complete!"
echo " Artifacts in ${DIST_DIR}:"
ls -lh "${DIST_DIR}"
echo "============================================================"
