package io.jimble.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * マイグレーションのフォルダ名（{@code db.<名前>.migration_dir}）
 *
 * <p>
 * 同じ表の作りの DB を何本もデータソースにしたとき、<b>どれも同じフォルダの SQL を当てたい</b>。
 * これまでのフォルダ名は {@code schema}（無ければデータソース名）で決まり、名前からは「フォルダ」だと分からなかった。
 * {@code migration_dir} を書けばそちらを使い、書かなければ今までと同じにする。
 * </p>
 */
class DBConfMigrationDirTest {

	@Test
	@DisplayName("migration_dir を書かなければ、これまでどおり schema をフォルダ名にする")
	void fallsBackToSchema () {

		DBConf conf = new DBConf();
		conf.schema("blog");

		assertEquals("blog", conf.migrationDir());

	}

	@Test
	@DisplayName("migration_dir を書けば schema より優先する")
	void migrationDirWins () {

		DBConf conf = new DBConf();
		conf.schema("shop_a");
		conf.migrationDir("shop");

		assertEquals("shop", conf.migrationDir());

	}

	@Test
	@DisplayName("空の migration_dir は書かなかったのと同じ")
	void emptyIsUnset () {

		DBConf conf = new DBConf();
		conf.schema("blog");
		conf.migrationDir("");

		assertEquals("blog", conf.migrationDir());

	}

	@Test
	@DisplayName("read・subs に写すときも引き継ぐ")
	void copied () {

		DBConf conf = new DBConf();
		conf.schema("shop_a");
		conf.migrationDir("shop");

		assertEquals("shop", DBConf.from(conf).migrationDir());

	}

}
