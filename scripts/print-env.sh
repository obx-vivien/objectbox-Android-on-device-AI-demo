#!/usr/bin/env bash

# ─── print-env.sh ───────────────────────────────────────────────────
# Prints key environment variables and tool versions.
# ─────────────────────────────────────────────────────────────────────

hdr() { printf '\n\033[1;37m── %s ──\033[0m\n' "$*"; }
val() { printf '  \033[1;34m%-20s\033[0m %s\n' "$1:" "$2"; }

hdr "Environment Variables"
val "ANDROID_HOME" "${ANDROID_HOME:-<not set>}"
val "JAVA_HOME" "${JAVA_HOME:-<not set>}"

hdr "PATH Checks"
for dir in \
  "$HOME/Library/Android/sdk/cmdline-tools/latest/bin" \
  "$HOME/Library/Android/sdk/platform-tools" \
  "$HOME/Library/Android/sdk/emulator" \
  "/opt/homebrew/bin"; do
  if echo "$PATH" | tr ':' '\n' | grep -qF "$dir"; then
    val "$dir" "on PATH"
  else
    val "$dir" "NOT on PATH"
  fi
done

hdr "Tool Versions"
val "brew"    "$(brew --version 2>/dev/null | head -1 || echo 'not found')"
val "git"     "$(git --version 2>/dev/null || echo 'not found')"
val "gh"      "$(gh --version 2>/dev/null | head -1 || echo 'not found')"
val "java"    "$(java -version 2>&1 | head -1 || echo 'not found')"
val "gradle"  "$(gradle --version 2>/dev/null | grep '^Gradle' || echo 'not found')"
val "adb"     "$(adb --version 2>/dev/null | head -1 || echo 'not found')"
val "emulator" "$(emulator -version 2>/dev/null | head -1 || echo 'not found')"
val "swift"   "$(swift --version 2>&1 | head -1 || echo 'not found')"
val "xcodebuild" "$(xcodebuild -version 2>/dev/null | tr '\n' ' ' || echo 'not found')"
echo ""
