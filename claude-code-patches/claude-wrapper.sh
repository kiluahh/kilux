#!/data/data/com.termux/files/usr/bin/bash
# Claude Code launcher for Termux/Android
# Routes the native Linux binary through glibc dynamic linker.

PREFIX=/data/data/com.termux/files/usr
GLIBC_LIB="$PREFIX/glibc/lib"

ARCH=$(uname -m)
case "$ARCH" in
  x86_64|i686|i386)
    LD="$GLIBC_LIB/ld-linux-x86-64.so.2"
    REAL="$PREFIX/lib/node_modules/@anthropic-ai/claude-code-linux-x64/claude" ;;
  aarch64|armv8l|armv7l)
    LD="$GLIBC_LIB/ld-linux-aarch64.so.1"
    REAL="$PREFIX/lib/node_modules/@anthropic-ai/claude-code-linux-arm64/claude" ;;
  *)
    echo "Unsupported arch: $ARCH" >&2
    exit 1 ;;
esac

[ -f "$LD" ] || { echo "glibc ld not found: $LD" >&2; exit 1; }
[ -f "$REAL" ] || { echo "Claude binary not found: $REAL" >&2; exit 1; }

unset LD_PRELOAD

# LD_LIBRARY_PATH with bionic first: child processes spawned by Claude Code
# find bionic libs before glibc ones. This keeps pkg, termux-api etc. working.
export LD_LIBRARY_PATH="$PREFIX/lib:$GLIBC_LIB"

"$LD" "$REAL" "$@"
EXIT_CODE=$?

stty sane 2>/dev/null || true

if [ $EXIT_CODE -eq 159 ] && command -v proot >/dev/null 2>&1; then
    proot -0 "$LD" "$REAL" "$@"
    stty sane 2>/dev/null || true
fi
exit $EXIT_CODE
