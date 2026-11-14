package io.jimble.db.data;

import java.util.List;

/**
 * 結果の列の読み方（キー・型の分け・1 行を読む段取り。3.0.0）
 *
 * <p>作ったあとは変えない。{@link ResultColumnCache} に置いて、同じ SQL の結果で使い回す。</p>
 *
 * @param keys		列ごとのキー
 * @param types		列ごとの型の分け
 * @param plan		1 行を読む段取り（作らない結果なら null。そのときは {@link ResultSetConverter} で読む）
 * @param labels	作るときに渡された位置ごとのキー（ビルダーが書いたもの。無ければ null）
 * @param jdbcTypes	作るときの列ごとの JDBC の型（{@code ResultSetMetaData#getColumnType}。使う前に今の結果と比べる）
 */
record ResultColumns (List<ResultSetIterator.KeyInfo> keys, List<Integer> types, RowPlan plan, List<String> labels, int[] jdbcTypes) {}
