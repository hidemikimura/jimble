# 測った環境

| | |
|---|---|
| 日時 | 2026-10-24 19:03 JST |
| 機種 | Apple M5・10 コア・32GB |
| OS | macOS 26.7.1 |
| Java | openjdk version "25.0.3" 2026-04-21 LTS |
| JVM の引数 | `-Xms2g -Xmx2g`（どのライブラリも同じ） |
| jimble | 2.5.12（Maven Central） |
| fastjson2 | 2.0.66 |
| wast | 0.0.29.1 |
| Apache Fory JSON | 1.7.7 |
| Jackson | 3.2.4 |
| 比べたライブラリ | jimble fastjson2 wast fory jackson |
| 空回し／測る時間 | 項目ごとに 1000ms ／ 200ms × 5 ラウンドの中央値 |
| 回数 | 3 回（回ごとに JVM を起動し直し、測る順番をずらす）。中央値を使う |
