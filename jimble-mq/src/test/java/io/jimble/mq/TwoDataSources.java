package io.jimble.mq;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/**
 * データソースを 2 つにする（テスト用）
 *
 * <p>このモジュールのテストの設定には主しか無いので、同じサーバーの別の DB を指すものを足す。</p>
 */
final class TwoDataSources {

	/** 主 */
	static final String MAIN = "jimble_test";

	/** もう1つ */
	static final String OTHER = "jimble_test_sub";

	private TwoDataSources () {}

	/**
	 * 主の設定を写して、もう1つのデータソース（jimble_test_sub）を足す
	 *
	 * <p>このモジュールのテストの設定には主しか無いので、同じサーバーの別の DB を指すものを足す。</p>
	 *
	 * @param base	設定
	 * @return	足した設定
	 */
	static Config withOther (Config base) {

		Config main = base.getConfig("db." + MAIN);
		String url = main.getString("url").replaceFirst("/" + MAIN + "(\\?|$)", "/" + OTHER + "$1");

		Config other = main
			.withValue("main", ConfigValueFactory.fromAnyRef(false))
			.withValue("url", ConfigValueFactory.fromAnyRef(url));

		// 無ければ作る（CI では、このモジュールのテストが jimble-db のテストより先に走り、まだ無いことがある）
		if (main.hasPath("create_database_sql")) {
			other = other.withValue("create_database_sql", ConfigValueFactory.fromAnyRef(
				main.getString("create_database_sql").replace(MAIN, OTHER)));
		}

		return base.withValue("db." + OTHER, other.root());

	}

}
