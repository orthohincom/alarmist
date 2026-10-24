#!/usr/bin/env bash
# Quiet build + unit tests + lint + format check. Prints only errors; "OK" when green.
# Usage: scripts/check.sh            all modules
#        scripts/check.sh :app       one module
#        scripts/check.sh --fix      apply ktlint formatting first
set -uo pipefail
cd "$(dirname "$0")/.."

tasks=()
fix=false
for arg in "$@"; do
    case "$arg" in
        --fix) fix=true ;;
        :*) tasks+=("$arg:quickCheck") ;;
        *) echo "unknown argument: $arg" >&2; exit 2 ;;
    esac
done
[ ${#tasks[@]} -eq 0 ] && tasks=(quickCheck)

if $fix; then
    ./gradlew -q --console=plain spotlessApply || exit 1
fi

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

if ./gradlew -q --console=plain --continue spotlessCheck "${tasks[@]}" >"$log" 2>&1; then
    echo "OK"
    exit 0
fi

# Drop Gradle's help text; from lint's failure message keep only the issue lines.
awk '
    /^[0-9]+: Task failed|^=+$/ { skip = 0; lint = 0 }
    /^\* Try:/ { skip = 1 }
    skip { next }
    /Lint found errors in the project; aborting build/ { print; lint = 1; next }
    lint { if ($0 ~ /: (Error|Warning): /) print; next }
    /^> Run with --|^[0-9]+: Task failed|^\* What went wrong:|^> Get more help at|^BUILD FAILED|^[0-9]+ actionable tasks?|^Wrote [A-Z]+ report to|^-+$|^=+$|^[[:space:]]*$/ { next }
    { print }
' "$log"

exit 1
