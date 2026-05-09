# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

Kilux — a fork of Termux (Android terminal emulator + Linux environment), renamed and customized. v0.119.0, based on Termux 0.118.0.

## Build Commands

```bash
# Debug build
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" \
ANDROID_HOME="C:/Users/inkre/AppData/Local/Android/Sdk" \
ANDROID_NDK_HOME="C:/Users/inkre/AppData/Local/Android/Sdk/ndk/29.0.14206865" \
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/termux-app_apt-android-7-debug_*.apk

# Release build (requires signing key)
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/termux-app_apt-android-7-release_universal.apk

# Run tests
./gradlew test

# Incremental install to emulator
adb -s emulator-5554 install -r app/build/outputs/apk/debug/termux-app_apt-android-7-debug_x86_64.apk
```

## Project Architecture

Four Gradle modules:

| Module | Path | Role |
|--------|------|------|
| `app` | `app/` | Main Android app: Activity, Settings, Bootstrap installer, Service |
| `termux-shared` | `termux-shared/` | Shared library: constants, properties, theme, extra keys, file utils |
| `terminal-emulator` | `terminal-emulator/` | Core terminal emulation engine (TerminalEmulator, TerminalSession) |
| `terminal-view` | `terminal-view/` | Android View layer: TerminalView (canvas rendering), TerminalRenderer |

### Key source files

| File | Purpose |
|------|---------|
| `app/.../TermuxActivity.java` | Main terminal activity, toolbar, drawer wiring |
| `app/.../TermuxInstaller.java` | Bootstrap extraction, motd writing, storage symlinks |
| `app/.../TermuxService.java` | Background terminal service, session management |
| `termux-shared/.../TermuxConstants.java` | App-wide constants (names, URLs, paths, versions) |
| `termux-shared/.../TermuxUtils.java` | App info markdown generation, About page content |
| `termux-shared/.../AndroidUtils.java` | Package info extraction, device info, markdown helpers |
| `termux-shared/.../ExtraKeysView.java` | On-screen extra keys rendering |
| `terminal-view/.../TerminalView.java` | Custom View for terminal display |
| `terminal-view/.../TerminalRenderer.java` | Canvas-based text rendering engine |
| `app/src/main/res/layout/activity_termux.xml` | Main layout (toolbar, drawer, terminal, extra keys) |
| `app/src/main/res/values/strings.xml` | String resources with XML entity `&TERMUX_APP_NAME;` |
| `termux-shared/src/main/res/values/strings.xml` | Same entity definition for shared module |

### About page data flow

`SettingsActivity.configureAboutPreference()` → `TermuxUtils.getAppInfoMarkdownString()` → `AndroidUtils.getAppInfoMarkdownString()` + `TermuxUtils.getAppInfoMarkdownStringInner()` (adds BASED_ON, package variant).

## Important Modifications Made

- App name: **Kilux** (changed in `strings.xml` XML entities + `TermuxConstants.java`)
- Version: **0.119.0** (code 119)
- Added `BASED_ON` field in About page showing "Termux 0.118.0"
- Added `KILUX_GITHUB_REPO_URL` constant for GitHub attribution
- Top toolbar with hamburger menu opens session drawer
- Bottom toolbar divider (thin white line)
- Default extra keys: SHIFT replaces `-`
- Custom welcome message written to `$PREFIX/etc/motd` after bootstrap install
- Release signing key: `app/release-key.jks` (alias: kilux)

## Version Info

- `versionCode` in `app/build.gradle` (defaultConfig)
- `versionName` in `app/build.gradle` (can be overridden via `TERMUX_APP_VERSION_NAME` env var)
- `TERMUX_APP_BASED_ON` in `TermuxConstants.java` — always keep in sync with upstream base version
