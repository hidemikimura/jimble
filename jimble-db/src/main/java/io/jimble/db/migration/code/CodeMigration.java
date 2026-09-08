package io.jimble.db.migration.code;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.lock.DBLock;
import io.jimble.db.migration.Migration;
import io.jimble.db.migration.MigrationException;
import io.jimble.db.version.DBVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * コードマイグレーションの登録と実行
 *
 * <p>
 * <b>クラスパスの走査はしない。</b>アプリが実行したいものを明示的に登録する。
 * 移送元は Guava の {@code ClassPath} で {@code <package>.migration} 以下を走査していたが、
 * 「アノテーションと DI を使わない・コードを上から辿れば分かる」（原則2）と
 * 「起動時のリフレクションスキャンを行わない」（NF-P-03）に反するため置き換えた。
 * </p>
 *
 * <pre>
 * public static void main (String[] args) {
 *     CodeMigration.add(
 *         new V20260901FillUserKana()
 *         , new V20260915MoveOrderStatus()
 *     );
 *     Migration.install();
 *     ...
 * }
 * </pre>
 */
public final class CodeMigration {

	/* 登録されたコードマイグレーション */
	private static final List<AbstractCodeMigration> MIGRATIONS = new ArrayList<>();

	private CodeMigration () {}

	// region 登録

	/**
	 * コードマイグレーションを登録する
	 *
	 * <p>登録順ではなく {@code versionYyyyMmDd()} 順に実行される。</p>
	 *
	 * @param migrations	コードマイグレーション
	 */
	public static void add (AbstractCodeMigration...migrations) {

		if (migrations == null) {
			return;
		}

		for (AbstractCodeMigration migration : migrations) {
			if (migration != null) {
				MIGRATIONS.add(migration);
			}
		}

	}

	/**
	 * 登録されたコードマイグレーション（バージョン順）
	 *
	 * @return	コードマイグレーション
	 */
	public static List<AbstractCodeMigration> migrations () {

		List<AbstractCodeMigration> list = new ArrayList<>(MIGRATIONS);

		list.sort(
			Comparator
				.comparingInt(AbstractCodeMigration::version)
				.thenComparing(o -> o.getClass().getName())
		);

		return list;

	}

	/**
	 * 登録を全て消す（テスト用）
	 */
	public static void clear () {

		MIGRATIONS.clear();

	}

	// endregion

	// region 実行

	/**
	 * 登録されたコードマイグレーションを実行する
	 *
	 * <p>
	 * SQL マイグレーションと同じロックキーを取る。<b>スキーマの変更中にデータを触らないため。</b>
	 * （移送元は {@code code_migration} を作って {@code migration} をロックしており、
	 * 作ったキーと取るキーが食い違っていた）
	 * </p>
	 *
	 * @throws MigrationException	実行に失敗した場合
	 */
	public static void execute () {

		if (!DBUtil.isUseDB() || MIGRATIONS.isEmpty()) {
			return;
		}

		init();

		DB lockDB = DBUtil.getMainDB();
		DBLock.create(lockDB, Migration.LOCK_KEY);

		try (lockDB) {

			lockDB.beginTransaction();

			if (!DBLock.lock(lockDB, Migration.LOCK_KEY)) {
				throw new MigrationException("コードマイグレーションのロックを取得できませんでした");
			}

			for (AbstractCodeMigration migration : migrations()) {
				migration.migrate();
			}

			lockDB.commitEndTransaction();

		} catch (MigrationException ex) {

			rollbackQuietly(lockDB);
			throw ex;

		} catch (Exception ex) {

			rollbackQuietly(lockDB);
			throw new MigrationException("コードマイグレーションに失敗しました", ex);

		}

	}

	/**
	 * ロールバックする（失敗しても握りつぶす）
	 *
	 * @param db	DB
	 */
	private static void rollbackQuietly (DB db) {

		try {
			db.rollbackEndTransaction();
		} catch (Exception ignore) {
			// ロールバック自体の失敗は元の例外を隠さないよう黙る
		}

	}

	/**
	 * 管理テーブルを作る
	 */
	public static void init () {

		DBVersion dbVersion = new DBVersion("migration_code", "コードマイグレーション情報");
		dbVersion.add(1)
			.mysql("""
				create table migration_code (
					version varchar(250) not null primary key
					, state varchar(250) not null
					, execute_info json null
					, error_info json null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
			""".formatted(dbVersion.placeholder()))
			.postgresql("""
				create table migration_code (
					version varchar(250) not null primary key
					, state varchar(250) not null
					, execute_info jsonb null
					, error_info jsonb null
				)
			""");

		if (!dbVersion.apply(DBUtil.getMainDB())) {
			throw new MigrationException("migration_code テーブルを作成できませんでした");
		}

	}

	// endregion

}
