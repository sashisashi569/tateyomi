#!/usr/bin/env bash
# Bump tateyomi.versionName following the rules in VERSIONING.md.
set -euo pipefail

kind="${1:-}"
case "$kind" in
    pre|release|fix|feature|major) ;;
    *)
        echo "usage: $0 pre|release|fix|feature|major" >&2
        echo "  pre      最初の公式リリース前の開発版 (0.0.c, c+1)" >&2
        echo "  release  最初の公式リリース (1.0.0)" >&2
        echo "  fix      修正のみの公式リリース (c+1)" >&2
        echo "  feature  機能追加・変更を含む公式リリース (b+1, c=0)" >&2
        echo "  major    互換性が壊れる変更を含む公式リリース (a+1, b=0, c=0)" >&2
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
if (( a < 0 || b > 999 || c > 999 )); then
    echo "invalid version '$current': a >= 0 and b,c <= 999" >&2
    exit 1
fi
if (( a == 0 && b != 0 )); then
    echo "invalid version '$current': development versions must be 0.0.x" >&2
    exit 1
fi

case "$kind" in
    pre)
        if (( a != 0 )); then
            echo "pre is only for development before the first release (0.0.x)" >&2
            exit 1
        fi
        if (( c == 999 )); then
            echo "c is already 999" >&2
            exit 1
        fi
        c=$((c + 1))
        ;;
    release)
        if (( a != 0 )); then
            echo "already released; use fix, feature, or major" >&2
            exit 1
        fi
        a=1
        b=0
        c=0
        ;;
    major)
        if (( a == 0 )); then
            echo "not released yet; use release for 1.0.0" >&2
            exit 1
        fi
        a=$((a + 1))
        b=0
        c=0
        ;;
    feature)
        if (( a == 0 )); then
            echo "not released yet; use pre" >&2
            exit 1
        fi
        if (( b == 999 )); then
            echo "b is already 999; use major" >&2
            exit 1
        fi
        b=$((b + 1))
        c=0
        ;;
    fix)
        if (( a == 0 )); then
            echo "not released yet; use pre" >&2
            exit 1
        fi
        if (( c == 999 )); then
            echo "c is already 999; use feature or major" >&2
            exit 1
        fi
        c=$((c + 1))
        ;;
esac

next="$a.$b.$c"
code=$((2000000 + a * 1000000 + b * 1000 + c))

if grep -q '^tateyomi\.versionName=' "$file"; then
    sed -i "s/^tateyomi\\.versionName=.*/tateyomi.versionName=$next/" "$file"
else
    printf '\ntateyomi.versionName=%s\n' "$next" >>"$file"
fi

echo "$current -> $next (versionCode $code)"
