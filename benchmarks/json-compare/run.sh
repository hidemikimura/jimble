#!/usr/bin/env bash
#
# jimble の JSON（Dson）と fastjson2・wast・Apache Fory JSON・Jackson を比べる
#
#   ./benchmarks/json-compare/run.sh
#
# 変えられるもの（環境変数）:
#   ROUNDS=3                                  何回くり返すか（回ごとに JVM を起動し直し、順番をずらす）
#   LIBS="jimble fastjson2 wast fory jackson" 比べるライブラリ（先頭が jimble であること。倍率の基準になる）
#   WARMUP_MS=1000                            項目ごとの空回し（ミリ秒）。測る時間はこの 5 分の 1 × 5 ラウンド
#   JVM_OPTS="-Xms2g -Xmx2g"                  どのライブラリにも同じものを渡す
#   JIMBLE=release                            release なら Maven Central の公開版、local ならこのリポジトリのソース
#   SKIP_BUILD=1                              ビルドを飛ばす
#
# 必要なもの: Java 25、python3
#
set -euo pipefail

cd "$(dirname "$0")"
HERE="$(pwd)"
ROOT="$(cd ../.. && pwd)"

ROUNDS="${ROUNDS:-3}"
LIBS="${LIBS:-jimble fastjson2 wast fory jackson}"
WARMUP_MS="${WARMUP_MS:-1000}"
JVM_OPTS="${JVM_OPTS:--Xms2g -Xmx2g}"
JIMBLE="${JIMBLE:-release}"

RESULTS="$HERE/results"
RAW="$RESULTS/raw"
LOGS="$RESULTS/logs"
LIB_DIR="$HERE/build/install/bench-json-compare/lib"

# region 準備

JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || echo "${JAVA_HOME:-}")"
JAVA="$JAVA_HOME/bin/java"
[ -x "$JAVA" ] || { echo "Java 25 が見つかりません" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 が見つかりません" >&2; exit 1; }

case "$JIMBLE" in
	release) GRADLE_ARGS=() ;;
	local)   GRADLE_ARGS=(-Pjimble.local=true) ;;
	*) echo "JIMBLE は release か local です: $JIMBLE" >&2; exit 2 ;;
esac

rm -rf "$RAW" "$LOGS"
mkdir -p "$RAW" "$LOGS"

# endregion

# region ビルド

build () {

	[ "${SKIP_BUILD:-0}" = "1" ] && return 0

	# 前の版の jar が lib に残らないように消してから作る
	rm -rf "$HERE/build/install"
	(cd "$ROOT" && ./gradlew -q -p benchmarks/json-compare "${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}" installDist)

}

# endregion

# region 測った環境

jimble_version () {

	if [ "$JIMBLE" = "local" ]; then
		echo "このリポジトリのソース（$(cd "$ROOT" && git rev-parse --short HEAD)$(cd "$ROOT" && git diff --quiet HEAD -- jimble-util || echo '・未コミットの変更あり')）"
	else
		echo "$(ls "$LIB_DIR" | sed -n 's/^jimble-util-\(.*\)\.jar$/\1/p')（Maven Central）"
	fi

}

version_of () {
	ls "$LIB_DIR" | sed -n "s/^$1-\(.*\)\.jar$/\1/p"
}

environment () {

	{
		echo "# 測った環境"
		echo
		echo "| | |"
		echo "|---|---|"
		echo "| 日時 | $(date '+%Y-%m-%d %H:%M %Z') |"
		echo "| 機種 | $(sysctl -n machdep.cpu.brand_string)・$(sysctl -n hw.ncpu) コア・$(( $(sysctl -n hw.memsize) / 1024 / 1024 / 1024 ))GB |"
		echo "| OS | $(sw_vers -productName) $(sw_vers -productVersion) |"
		echo "| Java | $("$JAVA" -version 2>&1 | head -1) |"
		echo "| JVM の引数 | \`${JVM_OPTS}\`（どのライブラリも同じ） |"
		echo "| jimble | $(jimble_version) |"
		echo "| fastjson2 | $(version_of fastjson2) |"
		echo "| wast | $(version_of wast) |"
		echo "| Apache Fory JSON | $(version_of fory-json) |"
		echo "| Jackson | $(version_of jackson-databind) |"
		echo "| 比べたライブラリ | ${LIBS} |"
		echo "| 空回し／測る時間 | 項目ごとに ${WARMUP_MS}ms ／ $(( WARMUP_MS / 5 ))ms × 5 ラウンドの中央値 |"
		echo "| 回数 | ${ROUNDS} 回（回ごとに JVM を起動し直し、測る順番をずらす）。中央値を使う |"
	} > "$RESULTS/environment.md"

}

# endregion

build
environment

for round in $(seq 1 "$ROUNDS"); do

	echo "${round} 回目"

	# 回ごとに順番をずらす（1 回目 a b c、2 回目 b c a、…）。
	# Mac の温度などの影響が、特定のライブラリに寄らないようにする
	read -r -a list <<< "$LIBS"
	shift_by=$(( (round - 1) % ${#list[@]} ))
	ordered=("${list[@]:$shift_by}" "${list[@]:0:$shift_by}")

	for lib in "${ordered[@]}"; do
		echo "  ${lib}"
		# shellcheck disable=SC2086
		"$JAVA" $JVM_OPTS -Dbench.warmupMs="$WARMUP_MS" -Dstdout.encoding=UTF-8 \
			--add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
			-cp "$LIB_DIR/*" bench.JsonCompare "$lib" "$RAW/${lib}-r${round}.tsv" \
			> "$LOGS/${lib}-r${round}.log" 2>&1 \
			|| { echo "${lib} が失敗しました（$LOGS/${lib}-r${round}.log）" >&2; exit 1; }
	done

done

python3 "$HERE/summarize.py" "$RESULTS" "$LIBS"
