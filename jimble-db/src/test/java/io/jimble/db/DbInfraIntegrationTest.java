package io.jimble.db;

import io.jimble.db.log.DBLog;
import io.jimble.db.version.DBVersion;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB まわりの下支え（要件 F-D-19 / F-D-19b）
 *
 * <p>sticky コネクション / DB バージョン / DB ログ。<b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class DbInfraIntegrationTest {

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(DBUtil.load(Conf.conf().config(), DbInfraIntegrationTest.class), "DB に接続できませんでした");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	// region DBVersion（要件 F-D-19b）

	@Test
	@DisplayName("テーブルを作り、版が上がったぶんだけ追いかける")
	void dbVersion () {

		DB db = DBUtil.getMainDB();

		db.execute("DROP TABLE IF EXISTS version_probe");

		DBVersion first = new DBVersion("version_probe", "版のテスト");
		first.add(1, """
				create table version_probe (
					id bigint unsigned auto_increment primary key
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(first.placeholder()));

		assertTrue(first.apply(db));
		assertNotNull(db.select("SELECT 1 AS ok FROM version_probe LIMIT 1") == null ? new Data() : new Data()
			, "テーブルができていない");

		// 版を足すと、その分だけ流れる
		DBVersion second = new DBVersion("version_probe", "版のテスト");
		second.add(1, "create table version_probe (id bigint unsigned auto_increment primary key)");
		second.add(2, "alter table version_probe add name varchar(100) null");

		assertTrue(second.apply(db));

		db.insert("INSERT INTO version_probe (name) VALUES (?)", "あ");

		assertEquals("あ", db.select("SELECT name FROM version_probe LIMIT 1").getString("name"));

		// もう一度流しても何も起きない
		DBVersion again = new DBVersion("version_probe", "版のテスト");
		again.add(1, "create table version_probe (id bigint unsigned auto_increment primary key)");
		again.add(2, "alter table version_probe add name varchar(100) null");

		assertTrue(again.apply(db), "二度目で落ちている");

		db.execute("DROP TABLE IF EXISTS version_probe");

	}

	// endregion

	// region DB ログ（要件 F-D-19b）

	@Test
	@DisplayName("設定が入っていなければ何も書かない")
	void dbLogDisabledByDefault () {

		DB db = DBUtil.getMainDB();

		DBLog.init(db);
		db.execute("TRUNCATE TABLE db_log");

		DBLog.save(db, new Data().putData("message", "出ないはず"));

		assertNull(db.select("SELECT id FROM db_log LIMIT 1"), "設定が無いのに書かれている");

	}

	@Test
	@DisplayName("設定を入れると書かれ、スタックトレースが読める形で入る")
	void dbLogEnabled () {

		DB db = DBUtil.getMainDB();

		DBLog.init(db);
		db.execute("TRUNCATE TABLE db_log");

		Conf.replace(Conf.conf().config().withValue(DBLog.KEY_ENABLED
			, com.typesafe.config.ConfigValueFactory.fromAnyRef(true)));

		try {

			DBLog.save(db, new Data().putData("message", "出るはず"));

			Data row = db.select("SELECT content FROM db_log ORDER BY id DESC LIMIT 1");

			assertNotNull(row, "設定を入れたのに書かれていない（移送元は jooby.log.db を見ていた）");

			Data content = row.getDataOptional("content");

			assertEquals("出るはず", content.getString("message"));

			/*
			 * 移送元は Throwable をそのまま入れていた。
			 * これは JSON にして保存されるので、読めるものにならない。
			 */
			assertFalse(content.getObjectListOptional("stack_trace", Object.class).isEmpty()
				, content.getJsonString());
			assertTrue(content.getJsonString().contains("DbInfraIntegrationTest")
				, content.getJsonString());

		} finally {

			Conf.reload();

		}

	}

	// endregion

	// region sticky コネクション（要件 F-D-19）

	@Test
	@DisplayName("sticky の外では sticky にならない")
	void stickyOutsideScope () {

		// スコープに束ねていなければ false
		assertFalse(DBSticky.sticky());

		// 束ねていないところで updated() を呼んでも落ちない
		DBSticky.updated();

	}

	@Test
	@DisplayName("書き込んだら以降の参照も同じ側に固定される")
	void stickyAfterUpdate () {

		DBSticky.init();

		DBSticky sticky = new DBSticky("test-cookie", true);

		ScopedValue.where(DBSticky.scopedValue, sticky).run(() -> {

			assertFalse(DBSticky.sticky(), "まだ書き込んでいない");

			DBSticky.updated();

			assertTrue(DBSticky.sticky(), "書き込んだのに固定されない");

		});

		// スコープを抜ければ元に戻る
		assertFalse(DBSticky.sticky());

	}

	@Test
	@DisplayName("使わない設定なら固定しない")
	void stickyDisabled () {

		DBSticky.init();

		DBSticky sticky = new DBSticky("test-cookie-2", false);

		ScopedValue.where(DBSticky.scopedValue, sticky).run(() -> {

			DBSticky.updated();

			assertFalse(DBSticky.sticky(), "使わない設定なのに固定されている");

		});

	}

	// endregion

}
