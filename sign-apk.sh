#!/bin/bash

# SPDX-FileCopyrightText: 2026 David Ventura
# SPDX-License-Identifier: GPL-3.0-only

set -euo pipefail

if [ "$#" -ne 4 ]; then
    echo "Usage: $0 <keystore_path> <store_password> <key_password> <key_alias>"
    exit 1
fi

KEYSTORE_PATH="$1"
STORE_PASSWORD="$2"
KEY_PASSWORD="$3"
KEY_ALIAS="$4"

UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED_DIR="signed"
SIGNED_APK="$SIGNED_DIR/motion-sickness-release.apk"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_ROOT" ] && [ -f local.properties ]; then
    SDK_ROOT="$(sed -n 's/^sdk.dir=//p' local.properties | head -n1)"
fi

if [ -z "$SDK_ROOT" ]; then
    echo "ANDROID_SDK_ROOT is not set and local.properties does not contain sdk.dir"
    exit 1
fi

BUILD_TOOLS_DIR="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

if [ ! -f "$UNSIGNED_APK" ]; then
    echo "Missing APK: $UNSIGNED_APK"
    echo "Run ./gradlew :app:assembleRelease first"
    exit 1
fi

if [ ! -x "$APKSIGNER" ]; then
    echo "Could not find apksigner under $BUILD_TOOLS_DIR"
    exit 1
fi

mkdir -p "$SIGNED_DIR"

"$APKSIGNER" sign \
    --alignment-preserved \
    --ks "$KEYSTORE_PATH" \
    --ks-pass pass:"$STORE_PASSWORD" \
    --ks-key-alias "$KEY_ALIAS" \
    --key-pass pass:"$KEY_PASSWORD" \
    --out "$SIGNED_APK" \
    "$UNSIGNED_APK"

echo "Signed APK: $SIGNED_APK"
