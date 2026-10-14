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
echo " Including Native WidgetKit Extension (.appex)"
echo "============================================================"

# 1. Compile Release Binary
echo "[1/6] Building release binary..."
cd "${MACOS_DIR}"
swift build -c release

RELEASE_BIN="${MACOS_DIR}/.build/release/${APP_NAME}"
if [[ ! -f "${RELEASE_BIN}" ]]; then
    echo "Error: Release binary not found at ${RELEASE_BIN}"
    exit 1
fi

# 2. Prepare .app Bundle Structure
echo "[2/6] Constructing ${APP_NAME}.app bundle..."
rm -rf "${DIST_DIR}"
mkdir -p "${BUNDLE_DIR}/Contents/MacOS"
mkdir -p "${BUNDLE_DIR}/Contents/Resources"
mkdir -p "${BUNDLE_DIR}/Contents/PlugIns"

cp "${RELEASE_BIN}" "${BUNDLE_DIR}/Contents/MacOS/${APP_NAME}"
chmod +x "${BUNDLE_DIR}/Contents/MacOS/${APP_NAME}"

# 3. Write Info.plist
echo "[3/6] Generating Info.plist..."
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

# 4. Compile and Embed WidgetKit Extension (.appex)
echo "[4/6] Compiling and embedding WidgetKit Extension (ClassFlowWidgets.appex)..."
WIDGET_DIR="${BUNDLE_DIR}/Contents/PlugIns/ClassFlowWidgets.appex"
mkdir -p "${WIDGET_DIR}/Contents/MacOS"
mkdir -p "${WIDGET_DIR}/Contents/Resources"
mkdir -p /tmp/clang-cache

WIDGET_SRC_DIR="${MACOS_DIR}/WidgetExtension"
SDK_PATH=$(xcrun --show-sdk-path --sdk macosx)

swiftc \
    -module-cache-path /tmp/clang-cache \
    -parse-as-library \
    -target arm64-apple-macos15.0 \
    -sdk "${SDK_PATH}" \
    -framework WidgetKit \
    -framework SwiftUI \
    -framework AppKit \
    -framework Foundation \
    -O \
    -o "${WIDGET_DIR}/Contents/MacOS/ClassFlowWidgets" \
    "${WIDGET_SRC_DIR}/WidgetData.swift" \
    "${WIDGET_SRC_DIR}/WidgetViews.swift" \
    "${WIDGET_SRC_DIR}/WidgetExtension.swift"

chmod +x "${WIDGET_DIR}/Contents/MacOS/ClassFlowWidgets"

cat << 'EOF' > "${WIDGET_DIR}/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>zh_CN</string>
    <key>CFBundleDisplayName</key>
    <string>ClassFlow 课程小组件</string>
    <key>CFBundleExecutable</key>
    <string>ClassFlowWidgets</string>
    <key>CFBundleIdentifier</key>
    <string>org.classflow.ClassFlow.ClassFlowWidgets</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>ClassFlowWidgets</string>
    <key>CFBundlePackageType</key>
    <string>XPC!</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>CFBundleSupportedPlatforms</key>
    <array>
        <string>MacOSX</string>
    </array>
    <key>LSMinimumSystemVersion</key>
    <string>15.0</string>
    <key>NSExtension</key>
    <dict>
        <key>NSExtensionPointIdentifier</key>
        <string>com.apple.widgetkit-extension</string>
    </dict>
</dict>
</plist>
EOF

# Code signing appex extension and application bundle with App Sandbox entitlements
echo "Code signing ClassFlowWidgets.appex and ClassFlow.app with Sandbox entitlements..."
codesign --force --sign - --entitlements "${MACOS_DIR}/Entitlements/ClassFlowWidgets.entitlements" "${WIDGET_DIR}"
codesign --force --sign - --entitlements "${MACOS_DIR}/Entitlements/ClassFlow.entitlements" "${BUNDLE_DIR}"

# Install / update to /Applications if writable and register with macOS
LSREGISTER="/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
if [[ -d "/Applications" && -w "/Applications" ]]; then
    echo "Installing updated ClassFlow.app into /Applications..."
    rm -rf "/Applications/ClassFlow.app"
    cp -R "${BUNDLE_DIR}" "/Applications/ClassFlow.app"
    if [[ -x "${LSREGISTER}" ]]; then
        "${LSREGISTER}" -f "/Applications/ClassFlow.app" || true
    fi
    pluginkit -a "/Applications/ClassFlow.app/Contents/PlugIns/ClassFlowWidgets.appex" || true
else
    if [[ -x "${LSREGISTER}" ]]; then
        "${LSREGISTER}" -f "${BUNDLE_DIR}" || true
    fi
fi

# 5. Create ZIP Distribution
echo "[5/6] Creating ZIP archive..."
cd "${DIST_DIR}"
zip -r -q "${APP_NAME}-1.0.0-macOS-arm64.zip" "${APP_NAME}.app"

# 6. Create DMG Disk Image with Applications symlink
echo "[6/6] Building Apple DMG disk image..."
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

# Generate Checksums
cd "${DIST_DIR}"
shasum -a 256 "${DMG_NAME}" > "${DMG_NAME}.sha256"
shasum -a 256 "${APP_NAME}-1.0.0-macOS-arm64.zip" > "${APP_NAME}-1.0.0-macOS-arm64.zip.sha256"

echo "============================================================"
echo " Packaging Complete!"
echo " Artifacts in ${DIST_DIR}:"
ls -lh "${DIST_DIR}"
echo ""
echo " PlugIns in ${APP_NAME}.app:"
ls -lh "${BUNDLE_DIR}/Contents/PlugIns"
echo "============================================================"
