"""JMH の結果（基準とソース）を表にする: summarize.py base.json local.json 基準の版"""
import json
import sys

NAMES = {
	"pkBuild": "主キー 1 行の SELECT を組み立てる（SQL とパラメータ）",
	"joinBuild": "JOIN・IN・ORDER BY・LIMIT の SELECT を組み立てる",
	"cachedSelect": "SQL 結果キャッシュに当たる SELECT（DB に行かない）",
	"pkSelect": "主キー 1 行の SELECT を DB に投げる（往復を含む）",
	"cookieResponse": "Cookie を 1 つ付けて応答する（署名・ヘッダ・後始末を含む）",
}


def load (path):
	result = {}
	for row in json.load(open(path)):
		name = row["benchmark"].rsplit(".", 1)[1]
		alloc = row["secondaryMetrics"].get("gc.alloc.rate.norm", {}).get("score")
		result[name] = (row["primaryMetric"]["score"], row["primaryMetric"]["scoreError"], alloc)
	return result


base = load(sys.argv[1])
local = load(sys.argv[2])

print(f"| | {sys.argv[3]} | このソース | 時間 | 確保 |")
print("|---|---:|---:|---:|---:|")
for key in NAMES:
	if key not in base or key not in local:
		continue
	b, l = base[key], local[key]
	time = (l[0] - b[0]) / b[0] * 100
	alloc = (l[2] - b[2]) / b[2] * 100 if b[2] else 0
	print(f"| {NAMES[key]} | {b[0]:,.0f} ns・{b[2]:,.0f} B | {l[0]:,.0f} ns・{l[2]:,.0f} B | {time:+.0f}% | {alloc:+.0f}% |")
