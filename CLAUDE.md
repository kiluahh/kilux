# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

Kilux — a fork of Termux (Android terminal emulator + Linux environment), renamed and customized. Based on Termux 0.118.0. Merged with termux-api for full API support. Uses date-based versioning (YYYY.MM.DD.N).

## Build Commands

**Before building, run `./version.sh` from repo root** to set the date-based version.

All gradle commands run from `kilux-app-main/`.

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

Five Gradle modules:

| Module | Path | Role |
|--------|------|------|
| `app` | `app/` | Main Android app: Activity, Settings, Bootstrap installer, Service |
| `termux-shared` | `termux-shared/` | Shared library: constants, properties, theme, extra keys, file utils |
| `terminal-emulator` | `terminal-emulator/` | Core terminal emulation engine (TerminalEmulator, TerminalSession) |
| `terminal-view` | `terminal-view/` | Android View layer: TerminalView (canvas rendering), TerminalRenderer |
| `termux-api` | `termux-api/` | Android API library: SocketListener, broadcast receiver, ~40 API implementations (Camera, Clipboard, Notification, Sensor, etc.) |

### Key source files

| File | Purpose |
|------|---------|
| `app/.../TermuxActivity.java` | Main terminal activity, toolbar, drawer wiring |
| `app/.../TermuxInstaller.java` | Bootstrap extraction, motd writing, storage symlinks, CRLF filtering |
| `app/.../TermuxService.java` | Background terminal service, session management |
| `app/.../FileManagerActivity.java` | File manager UI (browse, multi-select, copy/move/delete/rename) |
| `app/.../EditorActivity.java` | Text editor |
| `app/.../filemanager/FileListAdapter.java` | File list adapter with multi-select support |
| `app/.../filemanager/FileOperationHelper.java` | File CRUD operations |
| `app/.../editor/LineNumberEditText.java` | EditText with line number gutter |
| `termux-shared/.../TermuxConstants.java` | App-wide constants (names, URLs, paths, versions) |
| `termux-shared/.../TermuxUtils.java` | App info markdown generation, About page content |
| `termux-shared/.../AndroidUtils.java` | Package info extraction, device info, markdown helpers |
| `termux-shared/.../ExtraKeysView.java` | On-screen extra keys rendering |
| `terminal-view/.../TerminalView.java` | Custom View for terminal display |
| `terminal-view/.../TerminalRenderer.java` | Canvas-based text rendering engine |
| `termux-api/.../TermuxApiReceiver.java` | Broadcast receiver handling API requests from terminal scripts |
| `termux-api/.../SocketListener.java` | Unix socket server receiving script calls |
| `termux-api/.../TermuxAPIConstants.java` | API constants (action names, extras keys) |
| `app/src/main/res/layout/activity_termux.xml` | Main layout (toolbar, drawer, terminal, extra keys) |
| `app/src/main/res/values/strings.xml` | String resources with XML entity `&TERMUX_APP_NAME;` |
| `termux-shared/src/main/res/values/strings.xml` | Same entity definition for shared module |

### About page data flow

`SettingsActivity.configureAboutPreference()` → `TermuxUtils.getAppInfoMarkdownString()` → `AndroidUtils.getAppInfoMarkdownString()` + `TermuxUtils.getAppInfoMarkdownStringInner()` (adds BASED_ON, package variant).

## Important Modifications Made

- App name: **Kilux** (changed in `strings.xml` XML entities + `TermuxConstants.java`)
- Added `BASED_ON` field in About page showing "Termux 0.118.0"
- Added `KILUX_GITHUB_REPO_URL` constant for GitHub attribution
- Top toolbar with hamburger menu opens session drawer
- Terminal toolbar has 3 pages: extra keys → common symbols (14 symbol keys) → single-line text input
- Bottom toolbar divider (thin white line)
- Default extra keys: SHIFT replaces `-`
- Custom welcome message written to `$PREFIX/etc/motd` after bootstrap install
- Release signing key: `app/release-key.jks` (alias: kilux)
- Merged termux-api: ~55 API scripts + 8 native binaries (4 architectures × 2 programs)
- File manager: browse, multi-select, copy/move/delete/rename operations
- Text editor: line number gutter, file open/save
- Runtime CRLF filtering in TermuxInstaller: strips `\r` from scripts corrupted by Windows `core.autocrlf`
- `.gitattributes`: enforces LF line endings for termux-api shell scripts

## Native Code

- `app/src/main/cpp/`: NDK build (`Android.mk`), bootstrap extraction (`termux-bootstrap.c`), bootstrap zips for 4 architectures (aarch64, arm, i686, x86_64)

## CI/CD

`.github/workflows/` contains 6 workflows:
- `debug_build.yml` — debug APK build
- `attach_debug_apks_to_release.yml` — attach APKs to GitHub releases
- `run_tests.yml` — run test suite
- `dependency-submission.yml` — submit dependency graph
- `gradle-wrapper-validation.yml` — validate Gradle wrapper
- `trigger_library_builds_on_jitpack.yml` — trigger JitPack builds

## Design Specs

- `docs/superpowers/specs/` — feature design specifications; check before implementing related features

## Version Info

Date-based versioning (`YYYY.MM.DD.N`). Managed via `version.sh` at repo root.

```bash
./version.sh              # Auto-detect date + sequence, update build.gradle, create changelog
./version.sh -m "summary" # With changelog entry
./version.sh --dry-run    # Preview only, no changes
```

- **versionName**: `YYYY.MM.DD.N` (e.g. `2026.05.20.1`) in `app/build.gradle`
- **versionCode**: `YYYYMMDDN` integer (e.g. `202605201`)
- **Changelogs**: `changelogs/YYYY.MM.DD.N.md` — one file per version
- Sequence N auto-increments by scanning existing changelogs for the current date
- `TERMUX_APP_BASED_ON` in `TermuxConstants.java` — always keep in sync with upstream base version
- `validateVersionName()` in `app/build.gradle` enforces the date format
