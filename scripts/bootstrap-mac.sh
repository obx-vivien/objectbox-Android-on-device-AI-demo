#!/usr/bin/env bash
set -euo pipefail

# ─── bootstrap-mac.sh ───────────────────────────────────────────────
# Installs and configures the dev environment for the project.
# Safe to rerun (idempotent). Will pause for manual steps.
# ─────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ZPROFILE="$HOME/.zprofile"

info()  { printf '\033[1;34m[info]\033[0m  %s\n' "$*"; }
warn()  { printf '\033[1;33m[warn]\033[0m  %s\n' "$*"; }
ok()    { printf '\033[1;32m[ ok ]\033[0m  %s\n' "$*"; }
fail()  { printf '\033[1;31m[FAIL]\033[0m  %s\n' "$*"; }
pause() {
  printf '\033[1;35m[action required]\033[0m %s\n' "$1"
  read -rp "Press ENTER when done (or Ctrl-C to abort)..."
}

# ─── 1. Homebrew ────────────────────────────────────────────────────
if ! command -v brew &>/dev/null; then
  fail "Homebrew not found. Install it first: https://brew.sh"
  exit 1
fi
ok "Homebrew found: $(brew --version | head -1)"

# ─── 2. Brew Bundle ────────────────────────────────────────────────
info "Running brew bundle (installs git, gh, gradle, temurin@17, android-commandlinetools)..."
brew bundle --file="$PROJECT_DIR/Brewfile"
ok "brew bundle complete"

# ─── 3. Configure ~/.zprofile (idempotent) ──────────────────────────
info "Configuring $ZPROFILE..."

# --- timestamped backup ---
if [[ -f "$ZPROFILE" ]]; then
  cp "$ZPROFILE" "${ZPROFILE}.bak-$(date +%Y%m%d-%H%M%S)"
  ok "Backed up $ZPROFILE"
fi

# --- clean up duplicate brew shellenv lines (interactive) ---
if [[ -f "$ZPROFILE" ]]; then
  info "Brew shellenv lines currently in $ZPROFILE:"
  grep -n 'brew shellenv' "$ZPROFILE" || true
  read -rp "Remove duplicate brew shellenv lines and keep exactly one? [y/N] " yn
  if [[ "$yn" =~ ^[Yy] ]]; then
    sed -i '' '/eval "\$\(\/opt\/homebrew\/bin\/brew shellenv\)"/d' "$ZPROFILE"
  else
    warn "Skipped brew shellenv cleanup."
  fi
fi
touch "$ZPROFILE"

add_if_missing() {
  local marker="$1"
  local block="$2"
  if ! grep -qF "$marker" "$ZPROFILE" 2>/dev/null; then
    printf '\n%s\n' "$block" >> "$ZPROFILE"
    ok "Added to $ZPROFILE: $marker"
  else
    ok "Already in $ZPROFILE: $marker"
  fi
}

# Brew shellenv (single line)
add_if_missing 'eval "$(/opt/homebrew/bin/brew shellenv)"' \
'eval "$(/opt/homebrew/bin/brew shellenv)"'

# ANDROID_HOME
add_if_missing 'export ANDROID_HOME=' \
'# Android SDK
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"'

# JAVA_HOME
add_if_missing 'export JAVA_HOME=' \
'# JDK 17 (Temurin)
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null)"'

# Source it for the rest of this script
eval "$(/opt/homebrew/bin/brew shellenv)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null)"

ok "Shell profile configured"

# ─── 4. Android SDK packages ───────────────────────────────────────
info "Installing Android SDK packages via sdkmanager..."

# Ensure the SDK root exists
mkdir -p "$ANDROID_HOME"

# The android-commandlinetools cask puts sdkmanager at a known location;
# make sure it's on PATH from the brew cask
SDKMANAGER="$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$(command -v sdkmanager 2>/dev/null || true)"
fi

if [[ -z "$SDKMANAGER" || ! -x "$SDKMANAGER" ]]; then
  fail "sdkmanager not found after brew install. Check android-commandlinetools cask."
  exit 1
fi

ANDROID_SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.1"
  "emulator"
  "system-images;android-35;google_apis;arm64-v8a"
)

info "Android SDK licenses need acceptance."
read -rp "Run sdkmanager --licenses now (interactive)? [y/N] " yn
if [[ "$yn" =~ ^[Yy] ]]; then
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses
else
  warn "Skipped licenses. sdkmanager installs may fail until licenses are accepted."
fi

"$SDKMANAGER" --sdk_root="$ANDROID_HOME" --install "${ANDROID_SDK_PACKAGES[@]}"
ok "Android SDK packages installed"

# ─── 5. Xcode (manual step) ────────────────────────────────────────
if [[ -d "/Applications/Xcode.app" ]]; then
  ok "Xcode.app found"
else
  warn "Xcode is NOT installed."
  echo ""
  echo "  Please install Xcode from the Mac App Store:"
  echo "  https://apps.apple.com/app/xcode/id497799835"
  echo ""
  echo "  After installation, open Xcode once to finish setup,"
  echo "  then return here and press ENTER."
  echo ""
  pause "Install Xcode from the App Store, then press ENTER"
fi

# ─── 6. xcode-select + license (needs sudo) ────────────────────────
if [[ -d "/Applications/Xcode.app" ]]; then
  CURRENT_XCODE_PATH="$(xcode-select -p 2>/dev/null || true)"
  DESIRED_XCODE_PATH="/Applications/Xcode.app/Contents/Developer"

  if [[ "$CURRENT_XCODE_PATH" != "$DESIRED_XCODE_PATH" ]]; then
    echo ""
    info "Need to run: sudo xcode-select -s $DESIRED_XCODE_PATH"
    read -rp "Allow sudo for xcode-select? [y/N] " yn
    if [[ "$yn" =~ ^[Yy] ]]; then
      sudo xcode-select -s "$DESIRED_XCODE_PATH"
      ok "xcode-select set to Xcode.app"
    else
      warn "Skipped xcode-select. You'll need to run it manually."
    fi
  else
    ok "xcode-select already points to Xcode.app"
  fi

  info "Need to run: sudo xcodebuild -license accept"
  read -rp "Allow sudo for Xcode license acceptance? [y/N] " yn
  if [[ "$yn" =~ ^[Yy] ]]; then
    sudo xcodebuild -license accept 2>/dev/null || true
    ok "Xcode license accepted"
  else
    warn "Skipped Xcode license. You may need to accept it manually."
  fi
else
  warn "Xcode.app not found — skipping xcode-select and license steps."
fi

# ─── 7. Summary ────────────────────────────────────────────────────
echo ""
info "Bootstrap complete. Run the doctor to verify:"
echo "  bash $SCRIPT_DIR/doctor.sh"
echo ""
