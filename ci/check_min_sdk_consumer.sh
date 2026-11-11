#!/usr/bin/env bash
# Guards the SDK's declared minSdk. It stages the release publication (AAR, POM,
# Gradle module metadata) into build/central-staging, then builds
# ci/min-sdk-consumer, an app whose minSdk is the minSdkVersion written into the
# published AAR. Manifest merging fails if any runtime dependency declares a
# higher minSdk, so a dependency that silently raises the floor fails CI.
#
# Usage: ci/check_min_sdk_consumer.sh [extra gradle args, e.g. --offline]
set -euo pipefail
cd "$(dirname "$0")/.."

staging=build/central-staging
rm -rf "$staging"
./gradlew "$@" --init-script ci/no-signing.init.gradle \
  :zero_bounce_sdk:publishReleasePublicationToCentralStagingRepository

artifact_dir="$staging/com/zerobounce/android/zerobouncesdk"
version=$(ls "$artifact_dir" | grep -E '^[0-9]' | head -n 1)
aar="$artifact_dir/$version/zerobouncesdk-$version.aar"
min_sdk=$(unzip -p "$aar" AndroidManifest.xml | sed -n 's/.*minSdkVersion="\([0-9]*\)".*/\1/p')
compile_sdk=$(unzip -p "$aar" META-INF/com/android/build/gradle/aar-metadata.properties \
  | sed -n 's/^minCompileSdk=\([0-9]*\).*/\1/p')
agp_version=$(sed -n "s/.*com\.android\.tools\.build:gradle:\([^'\"]*\).*/\1/p" build.gradle | head -n 1)

if [ -z "$version" ] || [ -z "$min_sdk" ] || [ -z "$compile_sdk" ] || [ -z "$agp_version" ]; then
  echo "Could not read version ($version), minSdk ($min_sdk), minCompileSdk ($compile_sdk) or AGP ($agp_version)." >&2
  exit 1
fi
echo "Building a minSdk $min_sdk consumer of zerobouncesdk $version (compileSdk $compile_sdk, AGP $agp_version)."

./gradlew "$@" -p ci/min-sdk-consumer :app:assembleDebug \
  -PzbRepo="$PWD/$staging" -PzbVersion="$version" \
  -PconsumerMinSdk="$min_sdk" -PconsumerCompileSdk="$compile_sdk" -PagpVersion="$agp_version"
