#!/usr/bin/env bash
# tateyomi UI文言の補助チェッカー（助言用）
#
# 規範そのものではない。references/banned-phrases.md の一部を機械的に拾う。
# 文脈判断・意味の改善・重複の検出は人間（エージェント）が行う。
#
# 使い方:
#   .opencode/skills/anti-slop/scripts/check-copy.sh
#   .opencode/skills/anti-slop/scripts/check-copy.sh --strict
#   .opencode/skills/anti-slop/scripts/check-copy.sh --list
#
# --strict : 検出があれば exit 1
# --list   : 日本語を含む文字列を一覧表示する

set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$script_dir/../../../.." && pwd)

strict=0
list_only=0
for arg in "$@"; do
    case "$arg" in
        --strict) strict=1 ;;
        --list) list_only=1 ;;
        -h|--help)
            sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "unknown option: $arg" >&2
            exit 2
            ;;
    esac
done

ui_dir="$root/app/src/main/java/app/tateyomi/ui"
extra_files=("$root/app/src/main/java/app/tateyomi/util/Formatting.kt")
res_dir="$root/app/src/main/res"

if [[ ! -d "$ui_dir" ]]; then
    echo "not found: $ui_dir" >&2
    exit 2
fi

# 正規表現@@ルール名
RULES=$(cat <<'RULES'
することができ@@冗長構文
していただ|させていただ@@過剰敬語
の方は@@冗長な区分
に関して|についての@@冗長構文
という(こと|もの)@@冗長構文
を行(い|う)@@名詞化
から行え@@名詞化
により|によって@@冗長な助詞
と思います|と思われます|かもしれません|でしょう@@曖昧表現
とても|非常に|かなり|様々な|さまざまな|多様な@@誇張
シームレス|直感的|スマート|パワフル|多機能|包括的|革新的|最適化@@マーケティング語
エラーが発生しました|問題が発生しました@@原因不明のエラー
もう一度お試しください@@回復手段なし
各.{0,8}それぞれ@@二重表現
描画され|レンダリングされ|処理され@@実装語
自動で@@「自動的に」に統一
くらい@@曖昧な目安
右上の|左上の|右側の|左側の@@位置依存
\.\.\.@@三点リーダ
！@@感嘆符
　@@全角空白
[A-Za-z0-9] +[ぁ-んァ-ヶ一-龠]@@英字との間隔（未検証）
RULES
)

findings=0

report() {
    printf '%s:%s: [%s] %s\n' "$1" "$2" "$3" "$4"
    findings=$((findings + 1))
}

check_literal() {
    local file=$1 line=$2 literal=$3
    local rule re label
    while IFS= read -r rule; do
        [[ -z "$rule" ]] && continue
        re=${rule%%@@*}
        label=${rule#*@@}
        if printf '%s' "$literal" | grep -qP -- "$re"; then
            report "$file" "$line" "$label" "$literal"
        fi
    done <<< "$RULES"
}

mapfile -t files < <(
    find "$ui_dir" -name '*.kt' -type f 2>/dev/null
    printf '%s\n' "${extra_files[@]}"
    find "$res_dir" -path '*/values*/*' -name 'strings.xml' -type f 2>/dev/null
)

for f in "${files[@]}"; do
    [[ -f "$f" ]] || continue
    rel=${f#"$root"/}
    while IFS= read -r hit; do
        [[ -z "$hit" ]] && continue
        ln=${hit%%:*}
        content=${hit#*:}

        if [[ "$f" == *.xml ]]; then
            if (( list_only )); then
                report "$rel" "$ln" "文言" "$content"
            else
                check_literal "$rel" "$ln" "$content"
            fi
            continue
        fi

        mapfile -t literals < <(
            printf '%s\n' "$content" |
                grep -oP '"[^"]*[\x{3040}-\x{30ff}\x{4e00}-\x{9fff}][^"]*"' || true
        )
        (( ${#literals[@]} == 0 )) && continue

        for literal in "${literals[@]}"; do
            if (( list_only )); then
                report "$rel" "$ln" "文言" "$literal"
            else
                check_literal "$rel" "$ln" "$literal"
            fi
        done
    done < <(grep -nP '[\x{3040}-\x{30ff}\x{4e00}-\x{9fff}]' "$f" 2>/dev/null || true)
done

echo "---"
if (( list_only )); then
    echo "listed: $findings"
    exit 0
fi

if (( findings == 0 )); then
    echo "no findings (advisory check only)"
    exit 0
fi

echo "findings: $findings (advisory; judge in context)"
(( strict )) && exit 1
exit 0
