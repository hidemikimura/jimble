package io.jimble.db;

/**
 * 名前だけを持つデータソース（テスト用。つながない）
 *
 * <p>{@code DBSource.name(String)} がパッケージの中だけなので、このクラスは {@code io.jimble.db} に置いている。</p>
 */
public final class TestDataSources {

	private TestDataSources () {}

	/**
	 * 名前だけを持つデータソース
	 *
	 * @param name	名前
	 * @return	データソース
	 */
	public static DBSource named (String name) {

		DBSource source = new DBSource();
		source.name(name);

		return source;

	}

}
