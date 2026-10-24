#!/usr/bin/env python3
"""
JSON ライブラリの比較の結果をまとめる（run.sh の最後に呼ぶ）

  python3 summarize.py results "jimble fastjson2 wast fory jackson"

results/raw/<lib>-r<round>.tsv（項目・ナノ秒・byte・正しさ）を読み、
回の中央値で results/summary.csv と results/summary.md を書く。
倍率は「jimble を 1 としたときの値」（時間も割り当ても、小さいほど良い）。
"""

import csv
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

LABEL = {
    "jimble": "jimble",
    "fastjson2": "fastjson2",
    "wast": "wast",
    "fory": "Fory JSON",
    "jackson": "Jackson（参考）",
}
INPUT_LABEL = {
    "small": "小（10キー）",
    "medium": "中（100件）",
    "large": "大（4,800件）",
    "numbers": "数値（1,000件）",
    "strings": "日本語とエスケープ（100件）",
}
NAME = re.compile(r"^(\w+)-r(\d+)\.tsv$")


def label_of(item):
    """項目の名前を日本語にする"""
    words = item.split(" ")
    size = INPUT_LABEL.get(words[-1], words[-1])
    rest = " ".join(words[:-1])
    table = {
        "read tree": "読む 文字列 → 木",
        "read tree bytes": "読む byte[] → 木",
        "write tree": "書く 木 → 文字列",
        "write tree bytes": "書く 木 → byte[]",
        "read bean": "読む 文字列 → Bean",
        "read record": "読む 文字列 → record",
        "read bean list": "読む 文字列 → List<Bean>",
        "read record list": "読む 文字列 → List<record>",
        "write bean": "書く Bean → 文字列",
        "write record": "書く record → 文字列",
        "write bean list": "書く List<Bean> → 文字列",
        "write record list": "書く List<record> → 文字列",
        "write bean list bytes": "書く List<Bean> → byte[]",
    }
    return f"{table.get(rest, rest)}・{size}"


def read_raw(raw):
    """(lib, item) → 回ごとの (ns, bytes, 正しさ)。項目の順番も返す"""
    runs = defaultdict(list)
    order = []
    for path in sorted(raw.glob("*.tsv")):
        matched = NAME.match(path.name)
        if not matched:
            continue
        lib = matched.group(1)
        for line in path.read_text(encoding="utf-8").splitlines():
            item, nanos, allocated, correctness = line.split("\t")
            if item not in order:
                order.append(item)
            ns = None if nanos == "NA" else float(nanos)
            byte = None if allocated == "NA" else int(allocated)
            runs[(lib, item)].append((ns, byte, correctness))
    return runs, order


def median_of(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def summarize(runs, order, libs):
    """(lib, item) → {ns, bytes, ok}"""
    summary = {}
    for (lib, item), values in runs.items():
        summary[(lib, item)] = {
            "ns": median_of([v[0] for v in values]),
            "bytes": median_of([v[1] for v in values]),
            "ok": all(v[2] == "ok" for v in values),
            "spread": spread([v[0] for v in values]),
        }
    return summary


def spread(values):
    """回ごとのばらつき（(最大 − 最小) ÷ 中央値）"""
    values = [v for v in values if v is not None]
    if len(values) < 2:
        return None
    return (max(values) - min(values)) / statistics.median(values)


def fmt_time(ns):
    if ns is None:
        return "NA"
    if ns >= 1e6:
        return f"{ns / 1e6:.2f} ms"
    if ns >= 1e3:
        return f"{ns / 1e3:.1f} µs"
    return f"{ns:.0f} ns"


def fmt_bytes(b):
    if b is None:
        return "NA"
    if b >= 1024 * 1024:
        return f"{b / 1024 / 1024:.1f} MB"
    if b >= 1024:
        return f"{b / 1024:.1f} KB"
    return f"{int(b)} B"


def ratio(summary, lib, item, key):
    base = summary.get(("jimble", item), {}).get(key)
    value = summary.get((lib, item), {})
    if not base or value.get(key) is None:
        return "NA"
    mark = "" if value["ok"] else " ※"
    return f"{value[key] / base:.2f}{mark}"


def table(summary, order, libs, key, fmt, title):
    others = [lib for lib in libs if lib != "jimble"]
    lines = [
        f"| 項目 | jimble（{title}） | " + " | ".join(LABEL.get(lib, lib) for lib in others) + " |",
        "|---|---:|" + "---:|" * len(others),
    ]
    for item in order:
        base = summary.get(("jimble", item), {})
        mark = "" if base.get("ok", True) else " ※"
        cells = [ratio(summary, lib, item, key) for lib in others]
        lines.append(f"| {label_of(item)} | {fmt(base.get(key))}{mark} | " + " | ".join(cells) + " |")
    return lines


def write_csv(path, summary, order, libs):
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["item", "lib", "ns", "bytes", "ok", "time_ratio_to_jimble", "bytes_ratio_to_jimble"])
        for item in order:
            base = summary.get(("jimble", item), {})
            for lib in libs:
                value = summary.get((lib, item))
                if value is None:
                    continue
                time_ratio = value["ns"] / base["ns"] if value["ns"] and base.get("ns") else ""
                bytes_ratio = value["bytes"] / base["bytes"] if value["bytes"] is not None and base.get("bytes") else ""
                writer.writerow([item, lib, value["ns"], value["bytes"], value["ok"], time_ratio, bytes_ratio])


def write_md(path, environment, summary, order, libs):
    lines = ["# jimble の JSON とほかのライブラリの比較", ""]
    lines += environment.replace("# 測った環境", "## 測った環境").splitlines()
    lines += [
        "",
        "## 時間（jimble を 1 とした倍率。小さいほど速い）",
        "",
        "jimble の列は 1 回あたりの時間（回の中央値）、ほかの列は「そのライブラリの時間 ÷ jimble の時間」です。",
        "0.25 なら jimble の 4 倍速い、2.00 なら jimble の半分の速さです。",
        "",
    ]
    lines += table(summary, order, libs, "ns", fmt_time, "1 回あたり")
    lines += [
        "",
        "## 割り当て（jimble を 1 とした倍率。小さいほど少ない）",
        "",
        "jimble の列は 1 回あたりに割り当てた量、ほかの列は「そのライブラリの量 ÷ jimble の量」です。",
        "",
    ]
    lines += table(summary, order, libs, "bytes", fmt_bytes, "1 回あたり")

    wrong = [(lib, item) for item in order for lib in libs
             if (lib, item) in summary and not summary[(lib, item)]["ok"]]
    lines += ["", "## 結果が入力と違ったもの（※）", ""]
    if wrong:
        lines += ["結果を Jackson の木にして入力と比べ、違ったもの（または読めなかったもの）です。倍率は参考にしかなりません。", ""]
        lines += [f"- {LABEL.get(lib, lib)}：{label_of(item)}" for lib, item in wrong]
    else:
        lines += ["ありません（どのライブラリも、どの項目も入力と同じ中身を返しました）。"]

    spreads = [s["spread"] for s in summary.values() if s["spread"] is not None]
    if spreads:
        lines += [
            "",
            "## 回ごとのばらつき",
            "",
            f"同じライブラリ・同じ項目の回ごとの時間の差（(最大 − 最小) ÷ 中央値）は、"
            f"中央値 {statistics.median(spreads) * 100:.0f}%・最大 {max(spreads) * 100:.0f}% でした。"
            "これより小さい差は、ばらつきの中に入ります。",
        ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    results = Path(sys.argv[1])
    libs = sys.argv[2].split() if len(sys.argv) > 2 else list(LABEL)
    runs, order = read_raw(results / "raw")
    if not runs:
        sys.exit(f"{results / 'raw'} に結果がありません")
    summary = summarize(runs, order, libs)
    environment_path = results / "environment.md"
    environment = environment_path.read_text(encoding="utf-8") if environment_path.exists() else ""
    write_csv(results / "summary.csv", summary, order, libs)
    write_md(results / "summary.md", environment, summary, order, libs)
    print((results / "summary.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
