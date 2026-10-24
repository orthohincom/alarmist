#!/usr/bin/env bash
# Builds the unsigned fdroid release twice from clean clones of HEAD in two different folders (as F-Droid's build
# server would) and checks that both APKs are identical and that the app has no INTERNET (or network state)
# permission.
# Uncommitted changes aren't included. Prints "OK" when both hold. Takes a few minutes.
# Usage: scripts/release-check.sh [--keep]   (--keep leaves the clones in the temp folder)
set -uo pipefail
cd "$(dirname "$0")/.."
root="$(pwd)"
keep=false
[ "${1:-}" = "--keep" ] && keep=true

tmp="$(mktemp -d)"
$keep || trap 'rm -rf "$tmp"' EXIT
[ -n "$(git status --porcelain)" ] && echo "note: uncommitted changes are not part of this check"

apk() { echo "$1/app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk"; }

for build in a b; do
    dir="$tmp/build-$build"
    git clone -q --no-local "$root" "$dir" || exit 1
    cp local.properties "$dir/" 2>/dev/null
    # No build cache: the second build must really compile.
    if ! (cd "$dir" && ./gradlew -q --console=plain --no-build-cache :app:assembleFdroidRelease >"$tmp/$build.log" 2>&1); then
        grep -v '^$' "$tmp/$build.log" | head -30
        echo "build $build failed"
        exit 1
    fi
    [ -f "$(apk "$dir")" ] || { echo "no APK at $(apk "$dir")"; exit 1; }
done

status=0
a="$(apk "$tmp/build-a")"
b="$(apk "$tmp/build-b")"
if cmp -s "$a" "$b"; then
    echo "reproducible: $(sha256sum "$a" | cut -c1-16)…"
else
    echo "NOT reproducible; differing entries:"
    python3 -I - "$a" "$b" <<'PY'
import sys, zipfile
a, b = (zipfile.ZipFile(p) for p in sys.argv[1:])
ea = {i.filename: i for i in a.infolist()}
eb = {i.filename: i for i in b.infolist()}
for name in sorted(ea.keys() | eb.keys()):
    x, y = ea.get(name), eb.get(name)
    if x is None or y is None:
        print("  only in one:", name)
    elif (x.CRC, x.file_size, x.date_time, x.compress_type) != (y.CRC, y.file_size, y.date_time, y.compress_type):
        print("  ", name)
PY
    status=1
fi

sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}"
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
if [ -n "$aapt2" ]; then
    perms="$("$aapt2" dump permissions "$a" 2>/dev/null)"
    for perm in INTERNET ACCESS_NETWORK_STATE; do
        if grep -q "android.permission.$perm'" <<<"$perms"; then
            echo "the app requests $perm"
            status=1
        fi
    done
else
    echo "aapt2 not found: permissions not checked"
fi

$keep && echo "clones kept in $tmp"
[ $status -eq 0 ] && echo "OK"
exit $status
