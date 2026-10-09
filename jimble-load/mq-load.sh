#!/usr/bin/env bash
#
# MQ が秒あたり何件さばけるかを測る
#
#   ./jimble-load/mq-load.sh
#
# 差し替えられるもの（環境変数）:
#   PRODUCTS="mysql postgresql"   測る DB
#   THREADS="2 8 32"              ワーカーのスレッド数
#   WORKS="0 5"                   1件の処理ミリ秒（0 = MQ そのものの上乗せだけ）
#   MESSAGES=20000                処理ミリ秒が 0 のときに積む件数
#   PER_THREAD=500                処理ミリ秒が 0 でないときに、スレッド1本あたりに積む件数
#
# DB はテスト用のコンテナを使い、無ければ立てる（止めはしない）:
#   jimble-bench-mysql  127.0.0.1:13306（mysql:8.4）
#   jimble-bench-pg     127.0.0.1:15432（postgres:18.6）
# 手元の 3306 / 5432 には繋がない。
#
set -euo pipefail

cd "$(dirname "$0")/.."

PRODUCTS="${PRODUCTS:-mysql postgresql}"
THREADS="${THREADS:-2 8 32}"
WORKS="${WORKS:-0 5}"
MESSAGES="${MESSAGES:-20000}"
PER_THREAD="${PER_THREAD:-500}"

GRADLE="${GRADLE:-./gradlew}"
BIN="jimble-load/build/install/jimble-load/bin/jimble-load"

MYSQL_CONTAINER=jimble-bench-mysql
MYSQL_PORT=13306
PG_CONTAINER=jimble-bench-pg
PG_PORT=15432

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
	JAVA_HOME="$(/usr/libexec/java_home -v 25)"
	export JAVA_HOME
fi

# region DB

start_mysql () {

	if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
		docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
		docker run -d --name "$MYSQL_CONTAINER" -p "127.0.0.1:${MYSQL_PORT}:3306" \
			-e MYSQL_ROOT_PASSWORD=root -e MYSQL_USER=jimble -e MYSQL_PASSWORD=jimble \
			mysql:8.4 >/dev/null
	fi

	for _ in $(seq 1 90); do
		docker exec "$MYSQL_CONTAINER" mysql -uroot -proot -e "SELECT 1" >/dev/null 2>&1 && break
		sleep 1
	done

	docker exec "$MYSQL_CONTAINER" mysql -uroot -proot -e "
		CREATE DATABASE IF NOT EXISTS mq_load COLLATE utf8mb4_bin;
		GRANT ALL ON mq_load.* TO 'jimble'@'%';" 2>/dev/null

}

start_pg () {

	if ! docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER"; then
		docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
		docker run -d --name "$PG_CONTAINER" -p "127.0.0.1:${PG_PORT}:5432" \
			-e POSTGRES_USER=jimble -e POSTGRES_PASSWORD=jimble -e POSTGRES_DB=jimble_test \
			postgres:18.6 >/dev/null
	fi

	for _ in $(seq 1 60); do
		docker exec "$PG_CONTAINER" pg_isready -U jimble >/dev/null 2>&1 && break
		sleep 1
	done
	sleep 2

	docker exec "$PG_CONTAINER" psql -U jimble -d jimble_test -tAc \
		"SELECT 1 FROM pg_database WHERE datname = 'mq_load'" | grep -q 1 \
		|| docker exec "$PG_CONTAINER" psql -U jimble -d jimble_test -qc "CREATE DATABASE mq_load" >/dev/null

}

url_of () {
	case "$1" in
		mysql)      echo "jdbc:mariadb://127.0.0.1:${MYSQL_PORT}/mq_load?permitMysqlScheme=&useSSL=false&allowPublicKeyRetrieval=true" ;;
		postgresql) echo "jdbc:postgresql://127.0.0.1:${PG_PORT}/mq_load" ;;
		*) echo "知らない DB です: $1" >&2; exit 2 ;;
	esac
}

# endregion

"$GRADLE" -q :jimble-load:installDist

for product in $PRODUCTS; do
	case "$product" in
		mysql)      start_mysql ;;
		postgresql) start_pg ;;
	esac
done

echo "== MQ の処理件数（$(git rev-parse --short HEAD)） =="

for product in $PRODUCTS; do
	for work in $WORKS; do
		for threads in $THREADS; do

			if [ "$work" = 0 ]; then
				messages="$MESSAGES"
			else
				messages=$(( threads * PER_THREAD ))
			fi

			"$BIN" mq "$product" "$(url_of "$product")" "$threads" "$messages" "$work" \
				2>/dev/null | grep -E '^(mysql|postgresql)'

		done
	done
done
