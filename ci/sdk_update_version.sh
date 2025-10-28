#!/bin/bash

# Must be major, minor or patch
level_change=$1
auto_increase=$2

# Extract the version from pubspec.yaml
version_name=$(awk -F"'" '/PUBLISH_VERSION/ {print $2}' zero_bounce_sdk/build.gradle)

major=$(echo $version_name | awk -F. '{print $1}')
minor=$(echo $version_name | awk -F. '{print $2}')
patch=$(echo $version_name | awk -F'[.+]' '{print $3}')

echo "Current version: $version_name"


# Increment version
if [ "$level_change" = "major" ]; then
  major=$(echo "scale=0; $major + 1" | bc)
  minor=0
  patch=0
elif [ "$level_change" = "minor" ]; then
  minor=$(echo "scale=0; $minor + 1" | bc)
  patch=0
elif [ "$level_change" = "patch" ]; then
  patch=$(echo "scale=0; $patch + 1" | bc)
else
  echo "Level not found, use major, minor or patch"
  exit 1
fi

# If auto-increase is true, then
if [ "$auto_increase" = true ]; then
  # If patch is greater than 9, increment the minor also.
  if [ "$patch" -gt 9 ]; then
    patch=0
    minor=$((minor + 1))
  fi

  # If minor is greater than 9, increment the major also.
  if [ "$minor" -gt 9 ]; then
    minor=0
    major=$((major + 1))
  fi
fi

# Construct the new version
new_version="$major.$minor.$patch"

echo "New version: $new_version"

# Update the pubspec.yaml with the new version
sed -i.bak -E "s/(PUBLISH_VERSION = ).*/\1'$new_version'/" zero_bounce_sdk/build.gradle && rm zero_bounce_sdk/build.gradle.bak
