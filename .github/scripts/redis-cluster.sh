#!/usr/bin/env bash
#
# Redis クラスター（マスター 3・レプリカ 3）を、使い捨てのコンテナ 1 つで立てる（結合テスト用）
#
#   .github/scripts/redis-cluster.sh            立てる（127.0.0.1:17001〜17006）
#   docker rm -f jimble-bench-redis-cluster     片付ける
#
# <b>6 つのノードを 1 つのコンテナの中で動かす。</b>ノードどうしは 127.0.0.1 で話し、
# クラスターが教えるノードの場所（127.0.0.1:1700x）も、ポートを同じ番号で外へ出しているので
# 手元からそのまま繋がる。ノードごとにコンテナを分けると、コンテナの中の IP を教えてしまい、外から繋がらない。
#
# テストには次で渡す：
#   export JIMBLE_TEST_REDIS_CLUSTER=127.0.0.1:17001,127.0.0.1:17002,127.0.0.1:17003

set -euo pipefail

NAME=jimble-bench-redis-cluster
IMAGE=${JIMBLE_REDIS_CLUSTER_IMAGE:-redis:7-alpine}

docker rm -f "$NAME" >/dev/null 2>&1 || true

docker run -d --name "$NAME" -p 127.0.0.1:17001-17006:17001-17006 "$IMAGE" sh -c '
	for port in 17001 17002 17003 17004 17005 17006; do
		redis-server --port $port --cluster-enabled yes --cluster-config-file nodes-$port.conf \
			--cluster-node-timeout 5000 --save "" --appendonly no --daemonize yes
	done
	sleep 1
	yes yes | redis-cli --cluster create \
		127.0.0.1:17001 127.0.0.1:17002 127.0.0.1:17003 127.0.0.1:17004 127.0.0.1:17005 127.0.0.1:17006 \
		--cluster-replicas 1
	tail -f /dev/null
' >/dev/null

# スロットが全部割り当たり、レプリカが追いつくまで待つ
for _ in $(seq 1 60); do
	if docker exec "$NAME" redis-cli -p 17001 cluster info 2>/dev/null | grep -q "cluster_state:ok" \
		&& [ "$(docker exec "$NAME" redis-cli -p 17001 cluster nodes | grep -c slave)" = 3 ]; then
		echo "Redis クラスター: 127.0.0.1:17001〜17006（マスター 3・レプリカ 3）"
		exit 0
	fi
	sleep 1
done

echo "Redis クラスターが立ち上がりませんでした" >&2
docker logs "$NAME" >&2
exit 1
