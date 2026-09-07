package io.jimble.db.migration;

import io.jimble.db.DBUtil;
import io.jimble.db.migration.code.AbstractCodeMigration;
import io.jimble.db.migration.code.CodeMigration;
import io.jimble.db.migration.code.MigrationCodeState;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * コードマイグレーションが実 DB に対して動くことの確認
 *
 * <p>
 * <b>開発用 DB が必要</b>（要件 D-16）。実行は次のとおり。
 * </p>
 *
 * <pre>
 * ./gradlew :jimble-db:dbTest
 * </pre>
 */
@Tag("db")
class CodeMigrationIntegrationTest {

	/** 実行された順（各テストの先頭で空にする） */
	private static final List<String> EXECUTED = new ArrayList<>();

	// region 準備

	@BeforeAll
	static void loadDataSource () {

		Conf.reload();
		assertTrue(
			DBUtil.load(Conf.conf().config(), CodeMigrationIntegrationTest.class)
			, "DB に接続できませんでした。application.dbtest.conf を確認してください");

	}

	@AfterAll
	static void stopDataSource () {

		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		CodeMigration.clear();
		CodeMigration.init();
		EXECUTED.clear();

		DBUtil.getMainDB().execute("DELETE FROM migration_code");

	}

	@AfterEach
	void unregister () {

		CodeMigration.clear();

	}

	// endregion

	// region テスト

	@Test
	@DisplayName("登録した順ではなくバージョン順に実行される")
	void executeInVersionOrder () {

		CodeMigration.add(new Later(), new Earlier());

		CodeMigration.execute();

		assertEquals(List.of("earlier", "later"), EXECUTED);

	}

	@Test
	@DisplayName("完了したものは二度と実行されない")
	void executeOnce () {

		CodeMigration.add(new Earlier());

		CodeMigration.execute();
		CodeMigration.execute();

		assertEquals(List.of("earlier"), EXECUTED);
		assertEquals(MigrationCodeState.completed.name(), state(Earlier.class));

	}

	@Test
	@DisplayName("実行情報が JSON で残る")
	void executeInfo () {

		CodeMigration.add(new Earlier());

		CodeMigration.execute();

		Data row = row(Earlier.class);
		assertNotNull(row.getString("execute_info"));
		assertTrue(row.getString("execute_info").contains("moved"), row.getString("execute_info"));

	}

	@Test
	@DisplayName("例外を投げたら状態が error になり、起動を止める")
	void executeFailure () {

		CodeMigration.add(new Broken());

		assertThrows(MigrationException.class, CodeMigration::execute);

		assertEquals(MigrationCodeState.error.name(), state(Broken.class));
		assertTrue(row(Broken.class).getString("error_info").contains("exception"));

	}

	@Test
	@DisplayName("エラー情報を積んだら完走しても error になる")
	void executeWithErrorInfo () {

		CodeMigration.add(new PartiallyFailed());

		assertThrows(MigrationException.class, CodeMigration::execute);

		assertEquals(MigrationCodeState.error.name(), state(PartiallyFailed.class));

	}

	@Test
	@DisplayName("登録が無ければ何もしない")
	void executeWithoutRegistration () {

		CodeMigration.execute();

		assertEquals(0, DBUtil.getMainDB()
			.select("SELECT COUNT(*) AS cnt FROM migration_code").getLong("cnt"));

	}

	// endregion

	// region テスト用のコードマイグレーション

	/** 先に実行されるべきもの */
	public static class Earlier extends AbstractCodeMigration {

		@Override
		protected int versionYyyyMmDd () {

			return 20260101;

		}

		@Override
		protected void execute () {

			EXECUTED.add("earlier");
			addExecuteInfo("moved", 3);

		}

	}

	/** 後に実行されるべきもの */
	public static class Later extends AbstractCodeMigration {

		@Override
		protected int versionYyyyMmDd () {

			return 20260201;

		}

		@Override
		protected void execute () {

			EXECUTED.add("later");

		}

	}

	/** 例外を投げるもの */
	public static class Broken extends AbstractCodeMigration {

		@Override
		protected int versionYyyyMmDd () {

			return 20260301;

		}

		@Override
		protected void execute () {

			throw new IllegalStateException("移行できないデータがあります");

		}

	}

	/** 完走するがエラー情報を積むもの */
	public static class PartiallyFailed extends AbstractCodeMigration {

		@Override
		protected int versionYyyyMmDd () {

			return 20260401;

		}

		@Override
		protected void execute () {

			addErrorInfo("skipped_id", 42);

		}

	}

	// endregion

	// region ヘルパー

	/**
	 * 状態を取る
	 *
	 * @param cls	コードマイグレーションのクラス
	 * @return	状態
	 */
	private String state (Class<? extends AbstractCodeMigration> cls) {

		return row(cls).getString("state");

	}

	/**
	 * レコードを取る
	 *
	 * @param cls	コードマイグレーションのクラス
	 * @return	レコード
	 */
	private Data row (Class<? extends AbstractCodeMigration> cls) {

		Data row = DBUtil.getMainDB().select(
			"SELECT * FROM migration_code WHERE version LIKE ?", "%" + cls.getCanonicalName());

		assertNotNull(row, cls.getCanonicalName() + " のレコードがありません");

		return row;

	}

	// endregion

}
