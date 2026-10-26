#!/usr/bin/env bash
#
# SQL の組み立て・SQL 結果キャッシュに当たる SELECT・Cookie を付けた応答の CPU を、
# Maven Central の公開版（基準）とこのリポジトリのソースで測り比べる
#
#   ./benchmarks/sql-cpu/run.sh
#
# 変えられるもの（環境変数）:
#   BASE=2.7.4          比べる基準の版（Maven Central）
#   BENCH='SqlBench|WebBench'  測るもの（JMH の正規表現）
#   FORKS=2             JVM を起動し直す回数
#
# 必要なもの: Java 25、python3、bench 用の MySQL（127.0.0.1:13306、ユーザー jimble、DB jimble_test）。
#   docker run -d --name jimble-bench-mysql -p 127.0.0.1:13306:3306 \
#     -e MYSQL_ROOT_PASSWORD=root -e MYSQL_USER=jimble -e MYSQL_PASSWORD=jimble -e MYSQL_DATABASE=jimble_test mysql:8.4
#   （DB に行くのは pkSelect と、キャッシュを温める最初の 1 回だけ）
#
set -euo pipefail

cd "$(dirname "$0")"
HERE="$(pwd)"
ROOT="$(cd ../.. && pwd)"

BASE="${BASE:-2.7.4}"
BENCH="${BENCH:-SqlBench|WebBench}"
FORKS="${FORKS:-2}"

JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || echo "${JAVA_HOME:-}")"
JAVA="$JAVA_HOME/bin/java"
[ -x "$JAVA" ] || { echo "Java 25 が見つかりません" >&2; exit 1; }

mkdir -p "$HERE/results"

measure () {

	local label="$1"
	shift

	rm -rf "$HERE/build/install"
	(cd "$ROOT" && ./gradlew -q -p benchmarks/sql-cpu "$@" installDist)

	"$JAVA" -cp "$HERE/build/install/bench-sql-cpu/lib/*" org.openjdk.jmh.Main \
		-f "$FORKS" -prof gc -rf json -rff "$HERE/results/${label}.json" "$BENCH" \
		> "$HERE/results/${label}.log" 2>&1 \
		|| { echo "${label} が失敗しました（results/${label}.log）" >&2; exit 1; }

	# DB を往復するものは、待ちを除いたスレッドの CPU 時間で見る（最後の回を使う）
	"$JAVA" -Xms1g -Xmx1g -cp "$HERE/build/install/bench-sql-cpu/lib/*" bench.CpuPerQuery \
		> "$HERE/results/${label}-cpu.txt" 2>&1 \
		|| { echo "${label} の CPU 時間が測れませんでした（results/${label}-cpu.txt）" >&2; exit 1; }

}

echo "基準（${BASE}）"
measure base "-Pjimble.version=${BASE}"

echo "このリポジトリのソース"
measure local -Pjimble.local=true

python3 "$HERE/summarize.py" "$HERE/results/base.json" "$HERE/results/local.json" "$BASE" > "$HERE/results/summary.md"
{
	echo
	echo "スレッドの CPU 時間（1 回あたり。DB の往復の待ちを含まない）"
	echo
	echo "- ${BASE}: $(grep '^round' "$HERE/results/base-cpu.txt" | tail -1 | cut -f2-)"
	echo "- このソース: $(grep '^round' "$HERE/results/local-cpu.txt" | tail -1 | cut -f2-)"
} >> "$HERE/results/summary.md"
cat "$HERE/results/summary.md"
