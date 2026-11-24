#!/usr/bin/env bash
# Makes the app's signing key, once. Usage: scripts/make-signing-key.sh [folder]   (default: signing/)
# Writes <folder>/home-assist.jks and <folder>/signing.properties (with a random password). Keep both safe
# and out of git: with a different key, every Echo would have to uninstall the app to update it.
set -euo pipefail
folder="${1:-$(cd "$(dirname "$0")/.." && pwd)/signing}"
mkdir -p "$folder"
[ ! -e "$folder/home-assist.jks" ] || { echo "There is already a key in $folder. Not replacing it." >&2; exit 1; }
password="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)"
keytool -genkeypair -keystore "$folder/home-assist.jks" -storetype PKCS12 -alias home-assist \
  -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Home Assist for Echo Show" \
  -storepass "$password" -keypass "$password" >/dev/null
umask 077
printf 'storeFile=home-assist.jks\nstorePassword=%s\nkeyAlias=home-assist\nkeyPassword=%s\n' "$password" "$password" > "$folder/signing.properties"
echo "Made $folder/home-assist.jks and $folder/signing.properties. Keep a copy somewhere safe."
