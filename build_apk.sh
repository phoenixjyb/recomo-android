#!/bin/bash

# APK build script (recomoRemote)
# Usage: ./build_apk.sh [debug|release] [clean] [bump]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
OUTPUT_DIR="${PROJECT_DIR}/output"
VERSION_FILE="${PROJECT_DIR}/version.properties"
BUILD_TYPE="${1:-debug}"
DO_CLEAN=false
DO_BUMP=false

for arg in "$@"; do
    case "$arg" in
        clean) DO_CLEAN=true ;;
        bump) DO_BUMP=true ;;
    esac
done

cd "$PROJECT_DIR"

if [ -f "$VERSION_FILE" ]; then
    VERSION_CODE=$(grep "VERSION_CODE" "$VERSION_FILE" | cut -d'=' -f2)
    VERSION_NAME=$(grep "VERSION_NAME" "$VERSION_FILE" | cut -d'=' -f2)
    NEW_VERSION_CODE=$VERSION_CODE
    if [ "$DO_BUMP" = true ]; then
        NEW_VERSION_CODE=$((VERSION_CODE + 1))
        sed -i '' "s/VERSION_CODE=.*/VERSION_CODE=$NEW_VERSION_CODE/" "$VERSION_FILE"
    fi

    echo "=========================================="
    echo "  Android APK Build"
    echo "=========================================="
    echo "Version: $VERSION_NAME (build $NEW_VERSION_CODE)"
    echo "Build type: $BUILD_TYPE"
    echo "Output dir: $OUTPUT_DIR"
    echo ""
else
    echo "VERSION_CODE=1" > "$VERSION_FILE"
    echo "VERSION_NAME=0.1.0" >> "$VERSION_FILE"
    NEW_VERSION_CODE=1
    VERSION_NAME="0.1.0"
fi

mkdir -p "$OUTPUT_DIR"

if [ "$DO_CLEAN" = true ]; then
    echo "Cleaning..."
    ./gradlew clean
    echo ""
fi

echo "Building APK..."
if [ "$BUILD_TYPE" == "release" ]; then
    ./gradlew :app:assembleRelease
    APK_PATH="${PROJECT_DIR}/app/build/outputs/apk/release/app-release-unsigned.apk"
else
    ./gradlew :app:assembleDebug
    APK_PATH="${PROJECT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
fi

echo ""
echo "Copying APK to output..."
if [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$OUTPUT_DIR/"
    echo "✅ $(basename "$APK_PATH")"
else
    echo "❌ APK not found: $APK_PATH"
fi

echo ""
echo "=========================================="
echo "  Build done"
echo "=========================================="
ls -lh "$OUTPUT_DIR"/*.apk 2>/dev/null || echo "No APK files"
echo ""
