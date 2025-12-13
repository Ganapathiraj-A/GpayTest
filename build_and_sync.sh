#!/bin/bash
set -e

echo "Building GpayTest APK and Syncing to Drive..."
# Use explicit SDK path if needed, or rely on local.properties
export ANDROID_HOME=/home/ganapathiraj/Android/Sdk

# Run gradle build with -Pg to trigger syncApkToDrive
./gradlew assembleDebug -Pg

echo "Build and Sync Complete."
