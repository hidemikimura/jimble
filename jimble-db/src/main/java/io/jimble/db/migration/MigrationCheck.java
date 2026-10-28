package io.jimble.db.migration;

import java.util.List;

/**
 * マイグレーションが当たっているかを確かめた結果（1 データソース）
 *
 * <p>{@link Migration#check(io.jimble.db.DBSource, Class)} が返す。DB には書かずに作る。</p>
 *
 * @param dataSource	データソース名
 * @param notApplied	未適用のファイル（記録が無い・{@code complete} でない）
 * @param extra			余分な記録（ファイルが無いのに記録がある）
 * @param hashMismatch	ハッシュ違い（当てたあとにファイルが書き換わった）
 * @since 2.7.8
 */
public record MigrationCheck (String dataSource, List<String> notApplied, List<String> extra, List<String> hashMismatch) {

	/**
	 * コンストラクタ
	 *
	 * @param dataSource	データソース名
	 * @param notApplied	未適用のファイル
	 * @param extra			余分な記録
	 * @param hashMismatch	ハッシュ違い
	 */
	public MigrationCheck {

		notApplied = List.copyOf(notApplied);
		extra = List.copyOf(extra);
		hashMismatch = List.copyOf(hashMismatch);

	}

	/**
	 * ずれが無いか
	 *
	 * @return	未適用・余分・ハッシュ違いのどれも無い場合 = true
	 */
	public boolean isClean () {

		return notApplied.isEmpty() && extra.isEmpty() && hashMismatch.isEmpty();

	}

}
