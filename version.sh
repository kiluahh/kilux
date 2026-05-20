#!/bin/bash
# version.sh — Date-based version manager for Kilux
# Usage: ./version.sh [-m "summary"] [--dry-run]

set -e

CHANGELOG_DIR="changelogs"
BUILD_GRADLE="kilux-app-main/app/build.gradle"

DRY_RUN=false
MESSAGE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        -m) MESSAGE="$2"; shift 2 ;;
        *) echo "Usage: ./version.sh [-m \"summary\"] [--dry-run]"; exit 1 ;;
    esac
done

TODAY=$(date +%Y.%m.%d)
TODAY_CODE=$(date +%Y%m%d)

# Count existing versions for today
if [ -d "$CHANGELOG_DIR" ]; then
    COUNT=$(find "$CHANGELOG_DIR" -maxdepth 1 -name "${TODAY}.*.md" 2>/dev/null | wc -l | tr -d ' ')
else
    COUNT=0
fi

SEQ=$((COUNT + 1))
VERSION_NAME="${TODAY}.${SEQ}"
VERSION_CODE="${TODAY_CODE}${SEQ}"

if $DRY_RUN; then
    echo "Would set version: $VERSION_NAME (code: $VERSION_CODE)"
    echo "Changelog: $CHANGELOG_DIR/${VERSION_NAME}.md"
    exit 0
fi

# Update build.gradle
if [ -f "$BUILD_GRADLE" ]; then
    sed -i "s/versionCode [0-9]*/versionCode $VERSION_CODE/" "$BUILD_GRADLE"
    sed -i "s/versionName \"[^\"]*\"/versionName \"$VERSION_NAME\"/" "$BUILD_GRADLE"
else
    echo "Error: $BUILD_GRADLE not found"
    exit 1
fi

# Create changelog
mkdir -p "$CHANGELOG_DIR"
CHANGELOG_FILE="$CHANGELOG_DIR/${VERSION_NAME}.md"

cat > "$CHANGELOG_FILE" << EOF
# $VERSION_NAME

**Date:** $(date +%Y-%m-%d)
**versionCode:** $VERSION_CODE

## Changes

${MESSAGE:-<!-- 填写本次更新内容 -->}
EOF

echo "Version: $VERSION_NAME (code: $VERSION_CODE)"
echo "Changelog: $CHANGELOG_FILE"
