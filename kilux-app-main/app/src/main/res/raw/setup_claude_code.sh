#!/data/data/com.termux/files/usr/bin/bash
#===============================================================================
# Claude Code installer for Termux (Android)
# Tested with Claude Code 2.1.145
#
# This script installs Claude Code in a Termux environment by:
#   1. Installing glibc (64-bit) via apt + direct .deb download
#   2. Installing the @anthropic-ai/claude-code npm package + native binary
#   3. Fixing glibc linker scripts (libc.so)
#   4. Creating a launch wrapper that routes through glibc ld-linux + proot
#   5. Patching cli-wrapper.cjs for Android platform compatibility
#   6. Creating the global config to skip onboarding
#
# The primary challenge is that Termux uses bionic libc while Claude Code's
# native binary is linked against glibc. We solve this by installing a 64-bit
# glibc and invoking the binary through ld-linux. On older Android kernels
# (<10), proot -0 is used to bypass seccomp filters that block newer syscalls.
#===============================================================================
set -e

#-------------------------------------------------------------------------------
# Configuration
#-------------------------------------------------------------------------------
CLAUDE_CODE_VERSION="2.1.145"  # tested version
PREFIX="/data/data/com.termux/files/usr"
HOME_DIR="/data/data/com.termux/files/home"
GLIBC_LIB="$PREFIX/glibc/lib"
NPM_GLOBAL="$PREFIX/lib/node_modules"
CLAUDE_DIR="$NPM_GLOBAL/@anthropic-ai/claude-code"
ARCH_KERNEL=$(uname -m)
APT_SOURCES="$PREFIX/etc/apt/sources.list"
CDN_BASE="https://packages-cf.termux.dev/apt"
GLIBC_REPO="$CDN_BASE/termux-glibc"

#-------------------------------------------------------------------------------
# Helpers
#-------------------------------------------------------------------------------
die() {
    echo "ERROR: $*" >&2
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check network: curl -I $CDN_BASE"
    echo "  2. Check disk:    df -h $PREFIX"
    echo "  3. Check dpkg:    dpkg --audit"
    echo "  4. Manual glibc:  apt install glibc-repo && apt install glibc"
    echo "  5. Change mirror: termux-change-repo"
    exit 1
}

log()  { echo ">>> $*"; }
ok()   { echo "  [OK] $*"; }

verify_ld() {
    [ -f "$GLIBC_LIB/ld-linux-${LD_SUFFIX}.so.2" ] || \
    [ -f "$GLIBC_LIB/ld-linux-${LD_SUFFIX}.so.1" ]
}

verify_network() {
    curl -fsSL --connect-timeout 10 "$CDN_BASE/termux-main/dists/stable/InRelease" >/dev/null 2>&1 || \
        die "Network unreachable. Check Wi-Fi/data or VPN."
}

#-------------------------------------------------------------------------------
# Architecture detection
#-------------------------------------------------------------------------------
# uname -m may return the kernel arch (x86_64) or the Termux userspace arch
# (i686 on 32-bit installs). ld-linux filenames use hyphens (x86-64), not
# underscores (x86_64).
case "$ARCH_KERNEL" in
    x86_64|i686|i386)
        TARGET_ARCH="x86_64"
        LD_SUFFIX="x86-64"
        BIN_PKG="@anthropic-ai/claude-code-linux-x64"
        ;;
    aarch64|armv8l|armv7l)
        TARGET_ARCH="aarch64"
        LD_SUFFIX="aarch64"
        BIN_PKG="@anthropic-ai/claude-code-linux-arm64"
        ;;
    *)
        echo "Unsupported architecture: $ARCH_KERNEL" >&2
        echo "Claude Code requires x86_64 or aarch64 (arm64) CPU." >&2
        echo "Your device reports: $(getprop ro.product.cpu.abi 2>/dev/null || echo unknown)" >&2
        exit 1
        ;;
esac

#-------------------------------------------------------------------------------
echo "=== 1. System checks ==="
#-------------------------------------------------------------------------------
log "Architecture: kernel=$ARCH_KERNEL target=$TARGET_ARCH ld=ld-linux-${LD_SUFFIX}"

# Must have at least ~1.5 GB free for npm + binary
FREE_KB=$(df "$PREFIX" 2>/dev/null | awk 'NR==2{print $4}')
if [ -n "$FREE_KB" ] && [ "$FREE_KB" -lt 800000 ]; then
    die "Less than 800MB free in $PREFIX (${FREE_KB}KB). Claude Code needs ~1.5GB."
fi

verify_network
ok "Network reachable"

# Fix broken mirrors: force Termux default CDN to avoid mid-sync mirrors.
if [ -f "$APT_SOURCES" ]; then
    cp "$APT_SOURCES" "$APT_SOURCES.bak" 2>/dev/null || true
fi
echo "deb $CDN_BASE/termux-main stable main" > "$APT_SOURCES"
apt update -o "Acquire::Retries=5" 2>/dev/null || \
    die "apt update failed. Network issue or damaged package lists."

ok "apt sources set to CDN"

#-------------------------------------------------------------------------------
echo "=== 2. Install dependencies ==="
#-------------------------------------------------------------------------------

# --- glibc ---
if ! verify_ld; then
    log "Installing glibc ($TARGET_ARCH)..."

    # Install glibc-repo to register the glibc apt repository
    apt install glibc-repo -y 2>/dev/null || \
        die "Failed to install glibc-repo package."

    # Try native apt install first (works for x86_64-on-x86_64, aarch64-on-aarch64)
    apt install glibc -y 2>/dev/null || true

    # If ld-linux is still missing (e.g. 32-bit userspace on 64-bit kernel
    # where apt installs wrong arch), download the correct .deb directly.
    if ! verify_ld; then
        log "ld-linux not found after apt. Downloading ${TARGET_ARCH} glibc .deb..."

        GLIBC_PKG_LIST=$(curl -fsSL --connect-timeout 30 \
            "$GLIBC_REPO/dists/glibc/stable/binary-${TARGET_ARCH}/Packages" 2>/dev/null) || \
            die "Failed to fetch glibc package list from $GLIBC_REPO"

        GLIBC_FILENAME=$(echo "$GLIBC_PKG_LIST" \
            | grep -A10 "^Package: glibc$" \
            | grep "^Filename:" \
            | awk '{print $2}' \
            | head -1)

        if [ -z "$GLIBC_FILENAME" ]; then
            # Fallback for known architectures
            case "$TARGET_ARCH" in
                x86_64)  GLIBC_FILENAME="pool/stable/g/glibc/glibc_2.42_x86_64.deb" ;;
                aarch64) GLIBC_FILENAME="pool/stable/g/glibc/glibc_2.41-1_aarch64.deb" ;;
                *) die "No glibc package found for $TARGET_ARCH" ;;
            esac
            log "Using fallback filename: $GLIBC_FILENAME"
        fi

        DEB_URL="$GLIBC_REPO/$GLIBC_FILENAME"
        TMP_DEB="$PREFIX/tmp/glibc-${TARGET_ARCH}.deb"
        mkdir -p "$PREFIX/tmp"

        curl -fsSL --connect-timeout 60 --retry 3 "$DEB_URL" -o "$TMP_DEB" || \
            die "Failed to download glibc from $DEB_URL"

        if [ ! -s "$TMP_DEB" ]; then
            die "Downloaded glibc .deb is empty: $DEB_URL"
        fi

        dpkg-deb -x "$TMP_DEB" "$PREFIX" || \
            die "Failed to extract glibc .deb. Corrupted download?"

        rm -f "$TMP_DEB"
        ok "glibc .deb extracted"
    fi
fi

if ! verify_ld; then
    die "glibc ld-linux STILL missing after install.\n       Expected: $GLIBC_LIB/ld-linux-${LD_SUFFIX}.so.{1,2}\n       Run: find $PREFIX/glibc -name 'ld-linux*'"
fi
ok "glibc (ld-linux-${LD_SUFFIX})"

# --- Node.js ---
if ! command -v npm >/dev/null 2>&1; then
    log "Installing nodejs..."
    apt install nodejs -y 2>/dev/null || \
        die "Failed to install nodejs. Run: apt install nodejs"
fi
NODE_VER=$(node --version 2>/dev/null || echo unknown)
ok "node $NODE_VER"

# --- proot (seccomp bypass for old kernels) ---
if ! command -v proot >/dev/null 2>&1; then
    log "Installing proot..."
    apt install proot -y 2>/dev/null || \
        die "Failed to install proot. Run: apt install proot"
fi
ok "proot $(proot --version 2>/dev/null || echo ok)"

#-------------------------------------------------------------------------------
echo "=== 3. Install Claude Code $CLAUDE_CODE_VERSION ==="
#-------------------------------------------------------------------------------

# Clean up any broken/partial npm install
if [ -d "$CLAUDE_DIR" ] && [ ! -f "$CLAUDE_DIR/package.json" ]; then
    log "Removing broken Claude Code install..."
    rm -rf "$CLAUDE_DIR"
fi

if [ ! -d "$CLAUDE_DIR" ]; then
    log "npm install @anthropic-ai/claude-code..."
    npm install -g @anthropic-ai/claude-code 2>&1 || \
        die "npm failed to install @anthropic-ai/claude-code.\n       Check: npm config list\n       Check: ls -la $CLAUDE_DIR"
fi
ok "wrapper package installed"

log "npm install $BIN_PKG..."
npm install -g "$BIN_PKG" --force 2>&1 || \
    die "npm failed to install $BIN_PKG.\n       Is the package available? npm view $BIN_PKG"

REAL_BIN="$NPM_GLOBAL/$BIN_PKG/claude"
if [ ! -f "$REAL_BIN" ]; then
    die "Native binary not found at $REAL_BIN\n       Expected after installing $BIN_PKG"
fi
ok "native binary ($(du -h "$REAL_BIN" | cut -f1))"

#-------------------------------------------------------------------------------
echo "=== 4. Fix glibc linker scripts ==="
#-------------------------------------------------------------------------------
# libc.so (and others) are GNU ld linker scripts (ASCII), not ELF shared
# objects. The dynamic linker cannot parse them — replace with symlinks to
# the real .so.N files.
FIXED_COUNT=0
for script in "$GLIBC_LIB"/*.so; do
    [ -f "$script" ] || continue
    if head -1 "$script" 2>/dev/null | grep -q "GNU ld script"; then
        base=$(basename "$script")
        # Find the matching .so.N file
        real=$(ls "$GLIBC_LIB/${base}".* 2>/dev/null | grep -v "\.a$" | head -1)
        if [ -n "$real" ]; then
            mv "$script" "${script}.bak"
            ln -sf "$(basename "$real")" "$script"
            FIXED_COUNT=$((FIXED_COUNT + 1))
        fi
    fi
done
ok "Fixed $FIXED_COUNT linker script(s)"

#-------------------------------------------------------------------------------
echo "=== 5. Install launch wrapper ==="
#-------------------------------------------------------------------------------
cat > "$CLAUDE_DIR/bin/claude.exe" << 'WRAPPER_EOF'
#!/data/data/com.termux/files/usr/bin/bash

unset LD_PRELOAD

PREFIX="/data/data/com.termux/files/usr"
GLIBC_LIB="$PREFIX/glibc/lib"
BIONIC_LIB="$PREFIX/lib"

ARCH=$(uname -m)
case "$ARCH" in
    x86_64|i686|i386)
        LD="$GLIBC_LIB/ld-linux-x86-64.so.2"
        REAL="$PREFIX/lib/node_modules/@anthropic-ai/claude-code-linux-x64/claude" ;;
    aarch64|armv8l|armv7l)
        LD="$GLIBC_LIB/ld-linux-aarch64.so.1"
        REAL="$PREFIX/lib/node_modules/@anthropic-ai/claude-code-linux-arm64/claude" ;;
    *) echo "Unsupported: $ARCH" >&2; exit 1 ;;
esac

[ -f "$LD" ]   || { echo "ld not found: $LD" >&2; exit 1; }
[ -f "$REAL" ] || { echo "binary not found: $REAL" >&2; exit 1; }

# Bionic libs first so child processes (pkg, termux-api) use bionic.
# Glibc libs second so the Claude Code binary finds libc.so.6 etc.
"$LD" --library-path "$BIONIC_LIB:$GLIBC_LIB" "$REAL" "$@"
EXIT_CODE=$?
stty sane 2>/dev/null || true

if [ $EXIT_CODE -eq 159 ] && command -v proot >/dev/null 2>&1; then
    proot -0 -i $(id -u):$(id -g) \
        "$LD" --library-path "$BIONIC_LIB:$GLIBC_LIB" "$REAL" "$@"
    stty sane 2>/dev/null || true
fi
exit $EXIT_CODE
WRAPPER_EOF
chmod 755 "$CLAUDE_DIR/bin/claude.exe"
ok "wrapper installed"

#-------------------------------------------------------------------------------
echo "=== 6. Patch cli-wrapper.cjs (Node.js fallback) ==="
#-------------------------------------------------------------------------------
CLI_WRAPPER="$CLAUDE_DIR/cli-wrapper.cjs"
cp "$CLI_WRAPPER" "$CLI_WRAPPER.bak" 2>/dev/null || true

node -e "
var fs = require('fs');
var c = fs.readFileSync('$CLI_WRAPPER', 'utf8');
var changes = 0;

var old1 = \"if (platform === 'android') {\";
if (c.indexOf(old1) !== -1) {
    c = c.replace(old1, \"if (false /* android->linux */) {\");
    changes++;
}

if (c.indexOf(\"platform === 'linux' || platform === 'android'\") === -1) {
    var old2 = \"if (platform === 'linux')\";
    if (c.indexOf(old2) !== -1) {
        c = c.replace(old2, \"if (platform === 'linux' || platform === 'android')\");
        changes++;
    }
}

var old3 = \"if (process.platform !== 'linux' && process.platform !== 'android') {\";
if (c.indexOf(old3) !== -1) {
    c = c.replace(old3,
      \"if (process.platform !== 'linux' && process.platform !== 'android') { return false } else if (process.platform === 'android') { return false\");
    changes++;
}

if (c.indexOf('const isAndroid = process.platform') === -1) {
    var old4 = \"const result = spawnSync(binaryPath, process.argv.slice(2), {\n    stdio: 'inherit',\n    env: { ...process.env, CLAUDE_CODE_INSTALLED_VIA_NPM_WRAPPER: '1' },\n  })\";
    var new4 = [
        \"const isAndroid = process.platform === 'android'\",
        \"let cmd, args, spawnEnv\",
        \"if (isAndroid) {\",
        \"  const glibcPrefix = '/data/data/com.termux/files/usr/glibc'\",
        \"  const ldName = require('os').arch() === 'arm64'\",
        \"    ? 'ld-linux-aarch64.so.1'\",
        \"    : 'ld-linux-x86-64.so.2'\",
        \"  cmd = glibcPrefix + '/lib/' + ldName\",
        \"  args = ['--library-path', glibcPrefix + '/lib', binaryPath, ...process.argv.slice(2)]\",
        \"  const { LD_PRELOAD: _, ...rest } = process.env\",
        \"  spawnEnv = { ...rest, CLAUDE_CODE_INSTALLED_VIA_NPM_WRAPPER: '1' }\",
        \"} else {\",
        \"  cmd = binaryPath\",
        \"  args = process.argv.slice(2)\",
        \"  spawnEnv = { ...process.env, CLAUDE_CODE_INSTALLED_VIA_NPM_WRAPPER: '1' }\",
        \"}\",
        \"const result = spawnSync(cmd, args, {\",
        \"  stdio: 'inherit',\",
        \"  env: spawnEnv,\",
        \"})\"
    ].join('\n');
    if (c.indexOf(old4) !== -1) {
        c = c.replace(old4, new4);
        changes++;
    }
}

fs.writeFileSync('$CLI_WRAPPER', c);
console.log('Applied ' + changes + ' patch(es)');
" || die "Failed to patch cli-wrapper.cjs"

ok "cli-wrapper.cjs patched"

#-------------------------------------------------------------------------------
echo "=== 7. Create global config ==="
#-------------------------------------------------------------------------------
CONFIG_FILE="$HOME_DIR/.claude.json"
if [ ! -f "$CONFIG_FILE" ]; then
    echo '{"hasCompletedOnboarding": true}' > "$CONFIG_FILE"
    ok "Created $CONFIG_FILE"
else
    ok "$CONFIG_FILE already exists"
fi

#-------------------------------------------------------------------------------
echo "=== 8. Verify installation ==="
#-------------------------------------------------------------------------------
if ! command -v claude >/dev/null 2>&1; then
    die "'claude' command not found on PATH."
fi

CLAUDE_OUTPUT=$(claude --version 2>&1) || {
    echo "claude --version failed:" >&2
    echo "$CLAUDE_OUTPUT" >&2
    die "Installation verification failed."
}

echo ""
echo "=============================================="
echo "  Claude Code installed successfully!"
echo "  Version: $CLAUDE_OUTPUT"
echo "=============================================="
