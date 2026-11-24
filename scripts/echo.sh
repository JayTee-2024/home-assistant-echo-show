#!/usr/bin/env bash
# Puts builds on the Echo Show over Wireless debugging (Settings → System → Developer options).
#
#   scripts/echo.sh pair <ip:port> <code>      once: the address and code from "Pair device with pairing code"
#   scripts/echo.sh connect <ip:port>          the "IP address & Port" on the Wireless debugging screen
#   scripts/echo.sh install <apk>              installs the app and starts it
#   scripts/echo.sh logs [tag]                 the Echo's log, or only lines with this tag
#   scripts/echo.sh instrument <apk> <test-apk>  runs the on-device tests
#
# The pairing key and the last address are kept in .deploy/ (git-ignored), so pairing is needed once.
# The Echo's port changes when it restarts or Wireless debugging is switched off and on: run connect again.
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
deploy="$repo/.deploy"
mkdir -p "$deploy"
adb_bin="$deploy/platform-tools/adb"
package="net.badacid.homeassist.echoshow"

# adb keeps its key in $HOME/.android; pointing HOME at .deploy keeps it with the project.
adb() { HOME="$deploy" "$adb_bin" "$@"; }

fail() {
    echo "$1" >&2
    exit 1
}

ensure_adb() {
    [ -x "$adb_bin" ] && return
    echo "Downloading Android's platform tools…"
    local zip="$deploy/platform-tools.zip"
    # A fixed version with its checksum, so what is run is what was checked. To update: change both.
    curl -fsSL -o "$zip" https://dl.google.com/android/repository/platform-tools_r37.0.3-linux.zip ||
        fail "Could not download Android's platform tools from dl.google.com."
    echo "214548146cadb2ca3508143d8a1d4be4c59de5bb837253e3ede89ed74cf450d1  $zip" | sha256sum -c --quiet - ||
        { rm -f "$zip"; fail "The platform tools download does not match its checksum. Nothing was installed."; }
    python3 -c "import zipfile, sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$zip" "$deploy"
    rm -f "$zip"
    chmod +x "$adb_bin"
}

# Connects to the remembered address (adb's server does not survive between shell sessions).
ensure_connected() {
    [ -f "$deploy/echo-address" ] || fail "No Echo address yet. Run: scripts/echo.sh connect <ip:port>"
    local address
    address="$(cat "$deploy/echo-address")"
    if ! timeout 20 env HOME="$deploy" "$adb_bin" connect "$address" | grep -q "connected to"; then
        fail "Could not reach the Echo at $address. Is Wireless debugging still on? Its port changes when the Echo restarts: run scripts/echo.sh connect with the new one."
    fi
    adb -s "$address" get-state > /dev/null 2>&1 ||
        fail "The Echo at $address did not accept this computer. Pair again: scripts/echo.sh pair <ip:port> <code>"
    export ANDROID_SERIAL="$address"
}

ensure_adb
command="${1:-}"
case "$command" in
    pair)
        [ $# -eq 3 ] || fail "Use: scripts/echo.sh pair <ip:port> <code>"
        timeout 30 env HOME="$deploy" "$adb_bin" pair "$2" "$3" | grep -q "Successfully paired" ||
            fail "Pairing failed. The code works only while the pairing box is open on the Echo."
        echo "Paired. Now run: scripts/echo.sh connect <the IP address & Port on the Wireless debugging screen>"
        ;;
    connect)
        [ $# -eq 2 ] || fail "Use: scripts/echo.sh connect <ip:port>"
        echo "$2" > "$deploy/echo-address"
        ensure_connected
        echo "Connected to the Echo at $2."
        ;;
    install)
        [ $# -eq 2 ] || fail "Use: scripts/echo.sh install <apk>"
        [ -f "$2" ] || fail "No file at $2."
        ensure_connected
        adb install -r "$2" | tail -1
        adb shell am start -n "$package/.app.MainActivity" > /dev/null
        echo "Installed and started."
        ;;
    logs)
        ensure_connected
        if [ $# -ge 2 ]; then adb logcat -d -s "$2"; else adb logcat -d -t 300; fi
        ;;
    instrument)
        [ $# -eq 3 ] || fail "Use: scripts/echo.sh instrument <apk> <test-apk>"
        ensure_connected
        adb install -r "$2" | tail -1
        adb install -r "$3" | tail -1
        adb shell am instrument -w "$package.test/androidx.test.runner.AndroidJUnitRunner"
        ;;
    *)
        sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
