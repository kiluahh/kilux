# Claude Code Offline Integration Design

## Goal

Bundle Claude Code (`@anthropic-ai/claude-code@2.1.112`) + Node.js runtime into the Kilux APK so users can run `claude` out-of-box without network.

## Why 2.1.112

Anthropic added Android platform detection after 2.1.112 — postinstall fails on Termux. Locking at 2.1.112 bypasses this. Auto-updater disabled via `DISABLE_AUTOUPDATER=1`.

## Architecture

```
APK Assets
├── termux-api/           (existing)
└── claude-code/          (new)
    ├── nodejs-<arch>.tar.xz        — prebuilt Node.js runtime
    ├── claude-code-<arch>.tar.xz   — full npm install output
    ├── settings.json.template      — ~/.claude/settings.json template
    └── claude.json                 — ~/.claude.json
```

All paths under `$PREFIX` (/data/data/com.termux/files/usr):
- node binary → `bin/node`
- node shared lib → `lib/`
- node_modules → `lib/node_modules/`
- claude → `bin/claude` (symlink to `../lib/node_modules/.bin/claude`)

Config under `$HOME` (/data/data/com.termux/files/home):
- `~/.claude/settings.json` — DeepSeek API settings, autoupdater disabled
- `~/.claude.json` — `hasCompletedOnboarding: true`

## Install Flow

Bootstrap (existing) → termux-api (existing) → installNodeJS → installClaudeCode → writeClaudeConfig

### installNodeJS()

1. Detect CPU ABI (arm64-v8a→aarch64, armeabi-v7a→arm, x86→i686, x86_64→x86_64)
2. Open `claude-code/nodejs-<arch>.tar.xz` from assets
3. Extract to `$PREFIX/` (bin/node, lib/libnode.so.xx, include/, share/)

### installClaudeCode()

1. Open `claude-code/claude-code-<arch>.tar.xz` from assets
2. Extract to `$PREFIX/lib/node_modules/`
3. Create symlink: `$PREFIX/bin/claude` → `../lib/node_modules/.bin/claude`

### writeClaudeConfig()

Write `~/.claude/settings.json`:
```json
{
  "env": {
    "DISABLE_AUTOUPDATER": "1",
    "ANTHROPIC_BASE_URL": "https://api.deepseek.com/anthropic",
    "ANTHROPIC_AUTH_TOKEN": "<YOUR_DEEPSEEK_API_KEY>",
    "ANTHROPIC_MODEL": "deepseek-v4-pro[1m]",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "deepseek-v4-flash[1m]",
    "ANTHROPIC_DEFAULT_HAIKU_MODEL": "deepseek-v4-flash",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "deepseek-v4-pro[1m]",
    "CLAUDE_CODE_MAX_OUTPUT_TOKENS": "384000"
  }
}
```

Write `~/.claude.json`:
```json
{
  "hasCompletedOnboarding": true
}
```

If either file already exists, skip writing — don't overwrite user config.

## Code Changes

| File | Change |
|------|--------|
| `TermuxInstaller.java` | Add `installNodeJS()`, `installClaudeCode()`, `writeClaudeConfig()` |
| `TermuxConstants.java` | Add `CLAUDE_CONFIG_HOME_DIR` path constants |
| `app/src/main/assets/claude-code/` | New asset directory with prebuilt archives |

### Constants to add (TermuxConstants.java)

```java
public static final String CLAUDE_CONFIG_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.claude";
public static final String CLAUDE_SETTINGS_FILE_PATH = CLAUDE_CONFIG_HOME_DIR_PATH + "/settings.json";
public static final String CLAUDE_ONBOARDING_FILE_PATH = CLAUDE_CONFIG_HOME_DIR_PATH + "/claude.json";
public static final String CLAUDE_CONFIG_TEMPLATE_ASSET = "claude-code/settings.json.template";
public static final String CLAUDE_ONBOARDING_ASSET = "claude-code/claude.json";
public static final String CLAUDE_NODEJS_ASSET_PREFIX = "claude-code/nodejs-";
public static final String CLAUDE_CODE_ASSET_PREFIX = "claude-code/claude-code-";
public static final String CLAUDE_VERSION = "2.1.112";
```

## Pre-build Preparation

On a real Termux device:
1. `pkg install nodejs` → tar up `$PREFIX/bin/node`, `$PREFIX/lib/libnode*`, `$PREFIX/include/node/`, `$PREFIX/share/node/` → `nodejs-aarch64.tar.xz`
2. `npm install -g @anthropic-ai/claude-code@2.1.112 --force` → tar up `$PREFIX/lib/node_modules/` → `claude-code-aarch64.tar.xz`
3. Place both in `app/src/main/assets/claude-code/`

## Security

- API key uses placeholder `<YOUR_DEEPSEEK_API_KEY>` — user must replace it
- Config files not overwritten if already present
- Claude runs with same filesystem permissions as Termux
