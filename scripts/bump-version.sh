#!/usr/bin/env bash
# Bump tateyomi.versionName following the rules in VERSIONING.md.
set -euo pipefail

kind="${1:-}"
case "$kind" in
    feature|fix) ;;
    *)
        echo "usage: $0 feature|fix" >&2
        exit 2
        ;;
esac

root="$(cd "$(dirname "$0")/.." && pwd)"
file="$root/gradle.properties"

current="$(sed -n 's/^tateyomi\.versionName=//p' "$file" | head -n 1)"
if [[ -z "$current" ]]; then
    echo "tateyomi.versionName is missing from $file" >&2
    exit 1
fi

IFS=. read -r a b c <<<"$current"
if [[ ! "$a" =~ ^[0-9]+$ || ! "$b" =~ ^[0-9]+$ || ! "$c" =~ ^[0-9]+$ ]]; then
    echo "invalid version '$current': expected a.b.c" >&2
    exit 1
fi
if (( a < 1 || b > 20 || c > 20 )); then
    echo "invalid version '$current': a >= 1 and b,c <= 20" >&2
    exit 1
fi

if [[ "$kind" == "feature" ]]; then
    b=$((b + 1))
    c=0
    if (( b > 20 )); then
        a=$((a + 1))
        b=0
    fi
else
    c=$((c + 1))
    if (( c > 20 )); then
        c=0
        b=$((b + 1))
        if (( b > 20 )); then
            b=0
            a=$((a + 1))
        fi
    fi
fi

next="$a.$b.$c"
code=$((a * 21 * 21 + b * 21 + c))

if grep -q '^tateyomi\.versionName=' "$file"; then
    sed -i "s/^tateyomi\\.versionName=.*/tateyomi.versionName=$next/" "$file"
else
    printf '\ntateyomi.versionName=%s\n' "$next" >>"$file"
fi

echo "$current -> $next (versionCode $code)"
