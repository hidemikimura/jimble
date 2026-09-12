package io.jimble.web.session;

import java.util.List;
import io.jimble.db.dialect.Sqls;
import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.version.DBVersion;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DB セッション（要件 F-S-01）
 *
 * <p>
 * {@code session} テーブル（テーブル名は設定で変えられる）に JSON で持つ。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>同時保存でデータが消えていた。</b>移送元は「既存なら UPDATE、無ければ
 *       {@code INSERT IGNORE}」だった。同じセッション ID で2本同時に走ると、
 *       後の {@code INSERT IGNORE} が<b>黙って無視されて中身が失われる。</b>
 *       {@code ON DUPLICATE KEY UPDATE} の1文にした
 *       （移送元にもコメントアウトされた同じ SQL が残っていた）</li>
 *   <li><b>掃除がバッチインスタンス限定の常駐スレッドだった。</b>
 *       {@code BatchExecutor.isBatchInstance()} のときだけ仮想スレッドを回して
 *       1分おきに DELETE していた。バッチを動かしていない構成では<b>永遠に溜まる。</b>
 *       {@link #cleanupExpired()} を用意し、リクエスト処理の中から
 *       間隔を見て呼ぶ（遅延削除。要件 F-S-09）</li>
 *   <li>テーブル作成をコンストラクタから外した。DB が未初期化の時点で
 *       {@code new} されると落ちるため、最初に使うときに1回だけ行う</li>
 * </ol>
 */
public final class DbSessionStore implements SessionStore {

	/** 掃除を試みる間隔 */
	private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(10);

	/* テーブル名 */
	private final String tableName;

	/* タイムアウト（分） */
	private final long timeoutMinutes;

	/* テーブル作成済み */
	private volatile boolean initialized = false;

	/* 最後に掃除した時刻 */
	private final AtomicReference<Instant> lastCleanup = new AtomicReference<>(Instant.EPOCH);

	/**
	 * コンストラクタ（設定から作る）
	 */
	public DbSessionStore () {

		this(SessionConf.table(), SessionConf.timeout().toMinutes());

	}

	/**
	 * コンストラクタ
	 *
	 * @param tableName			テーブル名
	 * @param timeoutMinutes	タイムアウト（分）
	 */
	public DbSessionStore (String tableName, long timeoutMinutes) {

		this.tableName = tableName;
		this.timeoutMinutes = timeoutMinutes;

	}

	// region SessionStore

	@Override
	public SessionEntry load (WebContext context) {

		initialize();

		String sessionId = SessionId.get(context);
		if (sessionId == null || sessionId.isEmpty()) {
			// まだ Cookie が無い。ここでは発行しない（保存するときに発行する）
			return SessionEntry.empty();
		}

		Data row = DBUtil.getMainDB().select("""
			SELECT * FROM %s
			WHERE session_id = ? AND last_accessed_at >= %s
			""".formatted(
				DBUtil.getMainDB().dialect().identifier(tableName)
				, DBUtil.getMainDB().dialect().intervalFromNow("MINUTE", true))
			, sessionId
			, timeoutMinutes
		);

		cleanupIfDue();

		if (row == null) {
			return SessionEntry.empty();
		}

		return new SessionEntry(row.getDataOptional("data"), true);

	}

	@Override
	public void save (WebContext context, SessionEntry entry) {

		initialize();

		String sessionId = SessionId.getOrCreate(context);

		/*
		 * 移送元は「既存なら UPDATE / 無ければ INSERT IGNORE」で、
		 * 同時実行のとき INSERT が黙って捨てられていた。
		 */
		try (DB db = DBUtil.getMainDB()) {

			db.insert("""
				INSERT INTO %s (session_id, data, created_at, last_accessed_at)
				VALUES (?, ?, NOW(), NOW())
				""".formatted(db.dialect().identifier(tableName))
				+ Sqls.upsert(db.dialect(), List.of("session_id"), "data", "last_accessed_at")
				, sessionId
				, entry.data()
			);

		} catch (Exception ex) {
			Log.error(ex, "セッションを保存できませんでした");
		}

	}

	@Override
	public void touch (WebContext context, SessionEntry entry) {

		initialize();

		String sessionId = SessionId.get(context);
		if (sessionId == null || sessionId.isEmpty()) {
			// セッションが無いなら延ばすものも無い
			return;
		}

		DBUtil.getMainDB().update(
			"UPDATE %s SET last_accessed_at = NOW() WHERE session_id = ?"
				.formatted(DBUtil.getMainDB().dialect().identifier(tableName))
			, sessionId
		);

	}

	@Override
	public void destroy (WebContext context) {

		initialize();

		String sessionId = SessionId.get(context);
		if (sessionId != null && !sessionId.isEmpty()) {
			DBUtil.getMainDB().delete(
				"DELETE FROM %s WHERE session_id = ?"
					.formatted(DBUtil.getMainDB().dialect().identifier(tableName))
				, sessionId
			);
		}

		SessionId.remove(context);

	}

	// endregion

	// region 掃除（要件 F-S-09）

	/**
	 * 期限切れを消す
	 *
	 * <p>バッチから直接呼んでもよい。</p>
	 *
	 * @return	消した件数
	 */
	public int cleanupExpired () {

		initialize();

		lastCleanup.set(Instant.now());

		return DBUtil.getMainDB().delete("""
			DELETE FROM %s
			WHERE last_accessed_at < %s
			""".formatted(
				DBUtil.getMainDB().dialect().identifier(tableName)
				, DBUtil.getMainDB().dialect().intervalFromNow("MINUTE", true))
			, timeoutMinutes
		);

	}

	/**
	 * 間隔が来ていれば掃除する
	 *
	 * <p>
	 * 常駐スレッドを持たない代わりに、リクエストのついでに間隔を見て消す。
	 * <b>失敗してもリクエストは通す</b>（掃除はリクエストの本題ではない）。
	 * </p>
	 */
	private void cleanupIfDue () {

		Instant last = lastCleanup.get();
		Instant now = Instant.now();

		if (Duration.between(last, now).compareTo(CLEANUP_INTERVAL) < 0) {
			return;
		}

		// 同時に何本も走らせない
		if (!lastCleanup.compareAndSet(last, now)) {
			return;
		}

		try {
			cleanupExpired();
		} catch (Exception ex) {
			Log.warn("セッションの掃除に失敗しました: " + ex.getMessage());
		}

	}

	// endregion

	/**
	 * テーブルを作る（1回だけ）
	 */
	private void initialize () {

		if (initialized) {
			return;
		}

		synchronized (this) {

			if (initialized) {
				return;
			}

			DBVersion dbVersion = new DBVersion(tableName, "セッション");
			dbVersion.add(1)
				.mysql("""
					CREATE TABLE `%s` (
						session_id varchar(255) not null primary key
						, data json null
						, created_at datetime not null
						, last_accessed_at datetime not null
					) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
					""".formatted(tableName, dbVersion.placeholder())
					, "create index %s__index_1 on `%s` (last_accessed_at)".formatted(tableName, tableName))
				.postgresql("""
					CREATE TABLE "%s" (
						session_id varchar(255) not null primary key
						, data jsonb null
						, created_at timestamp not null
						, last_accessed_at timestamp not null
					)
					""".formatted(tableName)
					, "create index %s__index_1 on \"%s\" (last_accessed_at)".formatted(tableName, tableName));

			DB db = DBUtil.getMainDB();
			if (!dbVersion.apply(db)) {
				throw new IllegalStateException("セッションテーブルを作成できませんでした: " + tableName);
			}

			initialized = true;

		}

	}

}
