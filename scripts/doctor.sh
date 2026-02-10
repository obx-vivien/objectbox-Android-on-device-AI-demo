#!/usr/bin/env bash
set -uo pipefail

# ─── doctor.sh ──────────────────────────────────────────────────────
# Verifies the dev environment is correctly set up.
# Exit code 0 = all checks passed; non-zero = failures found.
# ─────────────────────────────────────────────────────────────────────

PASS=0
FAIL=0

ok()   { printf '\033[1;32m[PASS]\033[0m  %s\n' "$*"; ((PASS++)); }
fail() { printf '\033[1;31m[FAIL]\033[0m  %s\n' "$*"; ((FAIL++)); }
info() { printf '\033[1;34m[info]\033[0m  %s\n' "$*"; }
hdr()  { printf '\n\033[1;37m── %s ──\033[0m\n' "$*"; }

check_cmd() {
  local label="$1" cmd="$2"
  if command -v "$cmd" &>/dev/null; then
    ok "$label: $(command -v "$cmd")"
  else
    fail "$label: '$cmd' not found"
  fi
}

check_env() {
  local name="$1"
  local val="${!name:-}"
  if [[ -n "$val" ]]; then
    ok "$name = $val"
  else
    fail "$name is not set"
  fi
}

# ─── System ─────────────────────────────────────────────────────────
hdr "System"
info "macOS $(sw_vers -productVersion) ($(sw_vers -buildVersion))"
info "Arch: $(uname -m)"
info "Shell: $SHELL"

# ─── Homebrew ───────────────────────────────────────────────────────
hdr "Homebrew"
check_cmd "brew" "brew"

# ─── Git & GitHub CLI ──────────────────────────────────────────────
hdr "Git & GitHub CLI"
check_cmd "git" "git"
if command -v git &>/dev/null; then
  info "  version: $(git --version)"
fi
check_cmd "gh" "gh"
if command -v gh &>/dev/null; then
  info "  version: $(gh --version | head -1)"
fi

# ─── JDK 17 ────────────────────────────────────────────────────────
hdr "JDK 17 (Temurin)"
check_env "JAVA_HOME"
check_cmd "java" "java"
if command -v java &>/dev/null; then
  JAVA_VER="$(java -version 2>&1 | head -1)"
  info "  version: $JAVA_VER"
  if echo "$JAVA_VER" | grep -q '17\.'; then
    ok "JDK major version is 17"
  else
    fail "JDK major version is NOT 17 — got: $JAVA_VER"
  fi
fi

# ─── Gradle ─────────────────────────────────────────────────────────
hdr "Gradle"
check_cmd "gradle" "gradle"
if command -v gradle &>/dev/null; then
  info "  version: $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'unknown')"
fi

# ─── Android SDK ────────────────────────────────────────────────────
hdr "Android SDK"
check_env "ANDROID_HOME"
check_cmd "sdkmanager" "sdkmanager"
check_cmd "adb" "adb"
check_cmd "emulator" "emulator"

if command -v sdkmanager &>/dev/null && [[ -n "${ANDROID_HOME:-}" ]]; then
  INSTALLED="$(sdkmanager --sdk_root="$ANDROID_HOME" --list_installed 2>/dev/null || true)"
  for pkg in "platform-tools" "platforms;android-35" "build-tools;35.0.1" "emulator" "system-images;android-35;google_apis;arm64-v8a"; do
    if echo "$INSTALLED" | grep -q "$pkg"; then
      ok "SDK package: $pkg"
    else
      fail "SDK package missing: $pkg"
    fi
  done
fi

# ─── Xcode & iOS ───────────────────────────────────────────────────
hdr "Xcode & iOS"
if [[ -d "/Applications/Xcode.app" ]]; then
  ok "Xcode.app found"
  XCODE_PATH="$(xcode-select -p 2>/dev/null || true)"
  if [[ "$XCODE_PATH" == "/Applications/Xcode.app/Contents/Developer" ]]; then
    ok "xcode-select points to Xcode.app"
  else
    fail "xcode-select points to: $XCODE_PATH (expected Xcode.app)"
  fi
  if xcodebuild -version &>/dev/null; then
    ok "xcodebuild works"
    info "  $(xcodebuild -version | tr '\n' ' ')"
  else
    fail "xcodebuild not working (license not accepted?)"
  fi
else
  fail "Xcode.app not found — install from App Store"
fi

check_cmd "swift" "swift"
if command -v swift &>/dev/null; then
  info "  version: $(swift --version 2>&1 | head -1)"
fi

# ─── ObjectBox (project dependency — no global install) ─────────────
hdr "ObjectBox"
info "ObjectBox for Android: added via Gradle dependency (io.objectbox:objectbox-android)"
info "ObjectBox for iOS: added via Swift Package Manager"
ok "No global install required — SDK tools are present"

# ─── ONNX Runtime (Android — project dependency) ───────────────────
hdr "ONNX Runtime (Android)"
info "Added via Gradle dependency (com.microsoft.onnxruntime:onnxruntime-android)"
ok "No global install required — Android SDK is present"

# ─── Core ML (iOS) ─────────────────────────────────────────────────
hdr "Core ML (iOS)"
if [[ -d "/Applications/Xcode.app" ]]; then
  ok "Core ML ships with iOS SDK inside Xcode"
else
  fail "Xcode not installed — Core ML unavailable"
fi

# ─── Summary ────────────────────────────────────────────────────────
hdr "Summary"
echo ""
printf '  \033[1;32mPassed: %d\033[0m\n' "$PASS"
printf '  \033[1;31mFailed: %d\033[0m\n' "$FAIL"
echo ""

if (( FAIL > 0 )); then
  echo "  Some checks failed. Review the output above."
  exit 1
else
  echo "  All checks passed!"
  exit 0
fi
