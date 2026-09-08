package io.jimble.db.migration.code;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.migration.MigrationException;
import io.jimble.util.data.Data;

/**
 * コードマイグレーション
 *
 * <p>
 * SQL では書けない移行（既存データの詰め替えなど）を Java で書く（要件 F-G-18）。
 * 実行状態は {@code migration_code} テーブルで管理し、<b>一度成功したものは二度と実行しない。</b>
 * </p>
 *
 * <pre>
 * public class V20260901FillUserKana extends AbstractCodeMigration {
 *
 *     &#64;Override
 *     protected int versionYyyyMmDd () { return 20260901; }
 *
 *     &#64;Override
 *     protected void execute () {
 *         ...
 *         addExecuteInfo("updated", count);
 *     }
 * }
 * </pre>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>実行判定の SQL が {@code state} を取っていなかった。</b>
 *       {@code SELECT version FROM migration_code} の結果に対して {@code state} を見ていたため、
 *       <b>{@code waiting} で止まったものが二度と再実行されなかった。</b>{@code state} も取るようにした</li>
 *   <li><b>失敗を握りつぶさない。</b>{@link MigrationException} を投げて起動を止める（要件 F-G-16）</li>
 * </ol>
 */
public abstract class AbstractCodeMigration {

	/* 実行情報 */
	private final Data executeInfo = new Data();

	/* エラー情報 */
	private final Data errorInfo = new Data();

	/**
	 * 実行情報を追加する
	 *
	 * <p>{@code migration_code.execute_info} に JSON で残る。</p>
	 *
	 * @param key	キー
	 * @param value	値
	 */
	protected void addExecuteInfo (String key, Object value) {

		executeInfo.put(key, value);

	}

	/**
	 * エラー情報を追加する
	 *
	 * <p>
	 * 1件でも入っていると状態は {@code error} になる。
	 * 「処理は最後まで通ったが一部が移行できなかった」を残すための口。
	 * </p>
	 *
	 * @param key	キー
	 * @param value	値
	 */
	protected void addErrorInfo (String key, Object value) {

		errorInfo.put(key, value);

	}

	/**
	 * バージョン
	 *
	 * <p>この順に実行される。同じ値のときはクラス名順。</p>
	 *
	 * @return	yyyyMMdd
	 */
	protected abstract int versionYyyyMmDd ();

	/**
	 * 実行内容
	 */
	protected abstract void execute ();

	/**
	 * バージョン（並び替え用）
	 *
	 * @return	yyyyMMdd
	 */
	public final int version () {

		return versionYyyyMmDd();

	}

	/**
	 * 実行する
	 *
	 * <p>未実行のものだけを実行する。</p>
	 *
	 * @throws MigrationException	実行に失敗した場合
	 */
	public final void migrate () {

		if (!isDoMigration()) {
			return;
		}

		create();
		running();

		try {
			execute();
		} catch (Exception ex) {
			addErrorInfo("exception", ex.toString());
			completed();
			throw new MigrationException("コードマイグレーションに失敗しました: " + versionKey(), ex);
		}

		completed();

		if (!errorInfo.isEmpty()) {
			throw new MigrationException("コードマイグレーションがエラーを記録しました: %s / %s".formatted(versionKey(), errorInfo));
		}

	}

	/**
	 * 実行するか
	 *
	 * @return	実行する場合 = true
	 */
	private boolean isDoMigration () {

		DB db = DBUtil.getMainDB();

		// 移送元は version しか取っていなかったため state の判定が常に外れていた
		Data row = db.select("SELECT version, state FROM migration_code WHERE version = ?", versionKey());

		if (db.isError()) {
			throw new MigrationException("コードマイグレーションの状態を取得できませんでした: " + versionKey(), db.getError());
		}

		return row == null || MigrationCodeState.waiting.name().equals(row.getString("state"));

	}

	/**
	 * 実行前に登録する
	 */
	private void create () {

		DB db = DBUtil.getMainDB();

		db.insert(Sqls.insertIgnoreInto(db.dialect(), "migration_code") + """
				 (
					version
					, state
					, execute_info
					, error_info
				) VALUES (
					?
					, ?
					, ?
					, ?
				)
			""" + Sqls.insertIgnoreTail(db.dialect())
			, versionKey()
			, MigrationCodeState.waiting.name()
			, null
			, null
		);

		if (db.isError()) {
			throw new MigrationException("コードマイグレーションを登録できませんでした: " + versionKey(), db.getError());
		}

	}

	/**
	 * 実行中にする
	 */
	private void running () {

		DBUtil.getMainDB().update("""
				UPDATE migration_code SET
					state = ?
					, execute_info = ?
					, error_info = ?
				WHERE
					version = ?
			"""
			, MigrationCodeState.running.name()
			, null
			, null
			, versionKey()
		);

	}

	/**
	 * 終了状態にする
	 */
	private void completed () {

		DBUtil.getMainDB().update("""
				UPDATE migration_code SET
					state = ?
					, execute_info = ?
					, error_info = ?
				WHERE
					version = ?
			"""
			, (errorInfo.isEmpty() ? MigrationCodeState.completed : MigrationCodeState.error).name()
			, executeInfo.isEmpty() ? null : executeInfo
			, errorInfo.isEmpty() ? null : errorInfo
			, versionKey()
		);

	}

	/**
	 * バージョンキー
	 *
	 * @return	バージョンキー
	 */
	private String versionKey () {

		return versionYyyyMmDd() + "-" + getClass().getCanonicalName();

	}

}
