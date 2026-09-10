#!/usr/bin/env bash
#
# 負荷をかけて秒あたりの本数とレイテンシを測る（要件 NF-P-08）
#
#   ./jimble-load/load.sh              素の helidon と jimble を比べる
#   ./jimble-load/load.sh --blog       サンプルアプリ（DB・テンプレート込み）も測る
#
# 差し替えられるもの（環境変数）:
#   CONNECTIONS="1 8 64 256"   試す同時接続の数
#   WARMUP=3                   捨てる秒数（JIT が温まるまで）
#   MEASURE=10                 測る秒数
#   BLOG_OPTS="-Djimble.env=pgtest"   --blog のときのアプリへの引数（DB の切り替えなど）
#
# --blog は DB が要る。examples/blog/conf/application.conf の db.blog_example に
# 繋がらなければ、その相手だけ飛ばして先へ進み、理由をその場に出す
# （ログは jimble-load/build/logs/ に残る）。
#
# サーバーと負荷を別のプロセスで動かす。同じプロセスだと
# 負荷をかける側が相手の CPU を奪い、相手が遅いのか自分が邪魔しているのか分からなくなる。
#
set -euo pipefail

cd "$(dirname "$0")/.."

CONNECTIONS="${CONNECTIONS:-1 8 64 256}"
WARMUP="${WARMUP:-3}"
MEASURE="${MEASURE:-10}"
BLOG=0

for arg in "$@"; do
	case "$arg" in
		--blog) BLOG=1 ;;
		*) echo "知らない引数です: $arg" >&2; exit 2 ;;
	esac
done

# 手元の gradle で回したいときは GRADLE=... で差し替える
GRADLE="${GRADLE:-./gradlew}"
BIN="jimble-load/build/install/jimble-load/bin/jimble-load"

# ログの置き場。build/ は git の管理外なので、置きっぱなしでも邪魔にならない。
# /tmp に置くと、あとから「どこに出たのか」を探すことになる
LOG_DIR="jimble-load/build/logs"

#
# 起動スクリプトは JAVA_HOME の java を使う。
# jimble は Java 25 で作るので、JAVA_HOME が古いと
# 「UnsupportedClassVersionError」という読みにくい形で落ちる。
# 先に見つけて、駄目なら理由を言って止まる。
#
# 見つけた場所は RUN_JAVA_HOME に置くだけで、JAVA_HOME は書き換えない。
# Gradle 自身は古い JDK で動いていることがあり、
# ここで差し替えると今度は Gradle が起動しなくなる。
#
java_ok () {
	local home="$1"
	[ -n "$home" ] && [ -x "$home/bin/java" ] || return 1
	local major
	# 1行目を決め打ちで見ない。JAVA_TOOL_OPTIONS があると先頭に別の行が出る
	major=$("$home/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1)
	[ -n "$major" ] && [ "$major" -ge 25 ]
}

RUN_JAVA_HOME=""

resolve_java () {

	if java_ok "${JAVA_HOME:-}"; then
		RUN_JAVA_HOME="$JAVA_HOME"
		return 0
	fi

	# macOS
	if [ -x /usr/libexec/java_home ]; then
		local found
		found=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
		if java_ok "$found"; then
			RUN_JAVA_HOME="$found"
			return 0
		fi
	fi

	# Linux でよくある置き場
	local candidate
	for candidate in /usr/lib/jvm/*25*; do
		if java_ok "$candidate"; then
			RUN_JAVA_HOME="$candidate"
			return 0
		fi
	done

	echo "Java 25 が見つかりません。JAVA_HOME を Java 25 の場所にしてから流してください" >&2
	return 1

}

resolve_java

echo "== 用意 =="
"$GRADLE" -q :jimble-load:installDist

if [ "$BLOG" = 1 ]; then
	"$GRADLE" -q :examples:blog:installDist
fi

# 起きるまで待つ。決め打ちで待つと、遅い台では温まる前に測り始める
wait_for_port () {
	local port="$1"
	for _ in $(seq 1 100); do
		if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
			exec 3<&- 2>/dev/null || true
			return 0
		fi
		sleep 0.2
	done
	echo "ポート $port が開きませんでした" >&2
	return 1
}

mkdir -p "$LOG_DIR"

PIDS=()
FAILED=()

cleanup () {
	for pid in "${PIDS[@]:-}"; do
		kill "$pid" 2>/dev/null || true
		wait "$pid" 2>/dev/null || true
	done
}

trap cleanup EXIT

# 1つの相手を、接続数を変えながら測る
#   $1 名前  $2 起こすコマンド（文字列）  $3 ポート  $4 叩くパス
run_target () {
	local label="$1" start="$2" port="$3" path="$4"

	echo
	echo "== $label =="

	# shellcheck disable=SC2086
	JAVA_HOME="$RUN_JAVA_HOME" $start >"$LOG_DIR/$label.log" 2>&1 &
	local pid=$!
	PIDS+=("$pid")

	if ! wait_for_port "$port"; then

		# 標準エラーだけに出すと、出力を貼り付けたときに理由が落ちる。
		# 理由が落ちた出力は「何も出なかった」に見える
		echo "  起こせませんでした。ログの最後:"
		tail -20 "$LOG_DIR/$label.log" 2>/dev/null | sed 's/^/    /' || true
		echo "  （全部: $LOG_DIR/$label.log）"

		FAILED+=("$label")

		kill "$pid" 2>/dev/null || true
		wait "$pid" 2>/dev/null || true

		# ここで 1 を返すと set -e で全部止まり、あとの表も出なくなる
		return 0

	fi

	for c in $CONNECTIONS; do
		JAVA_HOME="$RUN_JAVA_HOME" "$BIN" client "http://127.0.0.1:$port$path" "$c" "$WARMUP" "$MEASURE" || true
	done

	kill "$pid" 2>/dev/null || true
	wait "$pid" 2>/dev/null || true
}

echo
echo "接続数: $CONNECTIONS / 温め ${WARMUP}秒 / 測る ${MEASURE}秒"
echo "台: $(uname -srm) / コア $(getconf _NPROCESSORS_ONLN 2>/dev/null || echo '?')"

run_target "helidon" "$BIN server helidon 9010" 9010 "/"
run_target "jimble" "$BIN server jimble 9011" 9011 "/"

# アクセスログを切ったもの。素の helidon は1行も書かないので、
# これを並べないと「上乗せ分」と「helidon が持っていない機能の代金」が混ざる
run_target "jimble-nolog" "$BIN server jimble-nolog 9012" 9012 "/"

if [ "$BLOG" = 1 ]; then
	# サンプルアプリは DB とテンプレートを通る。DB が要る（conf/application.conf を見る）
	run_target "blog" "examples/blog/build/install/blog/bin/blog" 9000 "/"
fi

if [ "${#FAILED[@]}" -gt 0 ]; then
	echo
	echo "== 起こせなかったもの: ${FAILED[*]} =="
	echo "   blog なら、たいてい DB に繋がっていません。"
	echo "   examples/blog/conf/application.conf の db.blog_example を見て、"
	echo "   その DB が動いていることを確かめてください。"
	echo "   テーブルがまだなら: ./gradlew :examples:blog:migrate"
fi

echo
cat <<'NOTE'
== 読み方 ==

  rps は信じてよい。相手が限界まで詰まった状態を作れている。

  p99 は「詰まっていないときの応答の速さ」であって、
  「利用者から見た待ち時間の上限」ではない。
  接続が返ってきてから次を投げる形（閉ループ）なので、
  1本が遅れると次の送信も遅れ、そのぶん待たされた要求が数から抜ける。

  行の末尾に「▲接続>コア」が付いた行は読まないこと。
  負荷をかける側も同じ台で動いていて、接続1本につきスレッドを1本使う。
  コア数を超えたところでは、相手が何であっても同じ数字に寄っていく
  ——測っているのは相手ではなく自分（負荷生成）の限界である。
  実際、10 コアの台で 64 接続を測ったら
  「素の helidon より jimble のほうが速い」ことになった（走るたびに符号が変わる）。

  接続数を固定にしてあるのは、台をまたいで同じ表を並べられるようにするためである。
  読めない行を消すのではなく、印を付けて残してある。

  helidon と jimble-nolog の差が、jimble の素の上乗せ分である。
  jimble と jimble-nolog の差が、アクセスログ1行の代金である
  （素の helidon は1行も書かないので、ここを分けないと混ざる）。
  blog との差は、DB とテンプレートとアプリの分である。

  「失敗 N ★」が出ていたら、その行の数字は読まないこと。
  500 を返すのはたいてい速いので、壊れているときほど良い数字が出る。
NOTE
