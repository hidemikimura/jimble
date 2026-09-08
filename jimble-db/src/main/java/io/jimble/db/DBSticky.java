package io.jimble.db;

import io.jimble.util.log.Log;
import java.util.List;
import io.jimble.db.dialect.Sqls;
import io.jimble.core.lifecycle.AppLifecycle;
import io.jimble.util.conf.Conf;
import io.jimble.util.thread.ThreadUtil;
import io.jimble.util.data.Data;
import io.jimble.db.version.DBVersion;

/**
 * 書き込み直後の参照を書き込み側へ寄せる（要件 F-D-19）
 *
 * <p>
 * 読み取り用のレプリカがあるとき、書き込んだ直後に参照すると
 * <b>まだ複製が届いておらず、いま書いたものが見えない</b>ことがある。
 * 書き込みから {@value #STICKY_INTERVAL} ミリ秒のあいだは
 * 参照も書き込み側へ回すことで、これを避ける。
 * </p>
 *
 * <p>
 * リクエスト（あるいはバッチ・MQ の1実行）ごとに1つ作り、
 * {@link #scopedValue} に束ねて使う。束ねられていなければ
 * {@link #sticky()} は常に false を返すので、何も起きない。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>「使わない」設定が効いていなかった。</b>移送元の {@code isUse} は
 *       {@code db_sticky} テーブルへの読み書きを止めるだけで、
 *       <b>{@code isSticky()} も {@code updateLastUpdatedAt()} も {@code isUse} を見ていなかった。</b>
 *       {@code db_sticky.use = false} にしても固定は起き続ける</li>
 *   <li><b>レプリカが無くても毎リクエスト SELECT していた。</b>
 *       移送元はコンストラクタで無条件に {@code db_sticky} を引いていた。
 *       レプリカが無ければ固定するもしないも無いので、引く意味が無い</li>
 *   <li><b>{@link #apply()} を呼んでいるところが1つも無かった。</b>
 *       つまり {@code db_sticky} テーブルには<b>誰も書き込んでいなかった</b>。
 *       コンストラクタの SELECT は常に空振りし、後片付けのスレッドは
 *       空のテーブルを消し続けていた。<b>リクエストをまたぐ固定は動いていない</b></li>
 *   <li><b>設定キーが {@code jooby.db_sticky.use} だった</b>（{@link #KEY_USE}）</li>
 * </ol>
 */
public class DBSticky {

	/* スレッド参照用 */
	public static final ScopedValue<DBSticky> scopedValue = ScopedValue.newInstance();

	/* sticky間隔（50ms） */
	private static final long STICKY_INTERVAL = 50;

	/** 設定キー：sticky を使うか（移送元は jooby.db_sticky.use だった） */
	public static final String KEY_USE = "db_sticky.use";

	/**
	 * 更新
	 */
	public static void updated () {

		if (scopedValue.isBound()) {
			DBSticky dbSticky = scopedValue.get();
			dbSticky.updateLastUpdatedAt();
		}

	}

	/**
	 * sticky判定
	 *
	 * @return  stickyの必要がある場合 = true
	 */
	public static boolean sticky () {

		if (scopedValue.isBound()) {
			DBSticky dbSticky = scopedValue.get();
			return dbSticky.isSticky();
		}

		return false;

	}

	/**
	 * 初期化
	 */
	public static void init () {

		DBVersion dbVersion = new DBVersion(FrameworkTables.DB_STICKY, "DB sticky");
		dbVersion.add(1)
			.mysql("""
				create table db_sticky (
					id              bigint unsigned auto_increment comment 'ID' primary key,
					cookie_id       varchar(250)              not null comment 'Cookie ID',
					last_updated_at bigint unsigned default 0 not null comment '最終更新日時',
					constraint db_sticky_pk_2 unique (cookie_id)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin comment '%s'
			""".formatted(dbVersion.placeholder()))
			.postgresql("""
				create table db_sticky (
					id              bigserial primary key,
					cookie_id       varchar(250)     not null,
					last_updated_at bigint default 0 not null,
					constraint db_sticky_pk_2 unique (cookie_id)
				)
			""");
		dbVersion.add(2).any("""
				create index db_sticky__index_1 on db_sticky (last_updated_at)
			""");
		dbVersion.apply(DBUtil.getMainDB());

		if (AppLifecycle.isBatch()) {
			Thread.startVirtualThread(() -> {
				int counter = 0;
				while (!AppLifecycle.isStopped()) {
					ThreadUtil.sleep(1000);
					counter++;
					if (counter > 60) {
						counter = 0;
						DBUtil.getMainDB().delete(
							"""
								DELETE FROM db_sticky
								WHERE
									last_updated_at < ?
							"""
							, System.currentTimeMillis() - STICKY_INTERVAL
						);
					}
				}
			});
		}

	}


	/* Cookie ID */
	private final String cookieId;

	/* 最終更新日時 */
	private long lastUpdatedAt = 0;

	/* 固定するかどうか */
	private final boolean isUse;

	/* リクエストをまたいで覚えておくかどうか */
	private final boolean isPersist;

	/**
	 * コンストラクタ
	 *
	 * @param cookieId		リクエストをまたいで同じ相手だと分かる値。
	 *						null または空なら<b>この実行の中だけ</b>固定する
	 * @param isUseDBSticky	固定するかどうか。null なら設定（{@link #KEY_USE}）に従う
	 */
	public DBSticky (String cookieId, Boolean isUseDBSticky) {

		this.cookieId = cookieId;

		/*
		 * 移送元はここで DBUtil.isUseRead() も見ていたので、
		 * レプリカが無い環境では isUse が false になった。
		 * ところが isSticky() は isUse を見ていなかったため、
		 * 「使わない」と決めたはずの isUse は事実上どこにも効いていなかった。
		 *
		 * ここでは isUse を「固定するかどうか」だけにし、
		 * DB への読み書き（＝リクエストをまたぐ固定）は isPersist に分ける。
		 * レプリカが無ければ DB.getReadConnection() が
		 * hasReadDataSource() で弾くので、固定してもしなくても結果は同じ。
		 * わざわざ毎リクエスト SELECT する理由が無い。
		 */
		this.isUse = isUseDBSticky != null
			? isUseDBSticky
			: Conf.conf().getBoolean(KEY_USE, true);

		this.isPersist = this.isUse
			&& cookieId != null && !cookieId.isEmpty()
			&& DBUtil.isUseDB() && DBUtil.isUseRead();

		if (isPersist) {
			Data row = DBUtil.getMainDB().newWriteDB().select("""
				SELECT
					last_updated_at
				FROM
					db_sticky
				WHERE
					cookie_id = ?
			""", cookieId);
			if (row != null) {
				lastUpdatedAt = row.getLong("last_updated_at");
			}
		}

	}

	/**
	 * 更新
	 */
	public void updateLastUpdatedAt () {

		if (!isUse) {
			return;
		}

		lastUpdatedAt = System.currentTimeMillis();

	}

	/**
	 * 反映
	 */
	public void apply () {

		if (lastUpdatedAt <= 0) {
			return;
		}

		if (isPersist) {

			try (DB db = DBUtil.getMainDB()) {

				db.insert("""
					INSERT INTO db_sticky (
						cookie_id
						, last_updated_at
					) VALUES (
						?
						, ?
					)
					""" + Sqls.upsert(db.dialect(), List.of("cookie_id"), "last_updated_at")
					, cookieId, lastUpdatedAt);

			} catch (Exception ex) {
				Log.error(ex, "db_sticky を更新できませんでした");
			}

		}

	}

	/**
	 * sticky判定
	 *
	 * @return  stickyの必要がある場合 = true
	 */
	public boolean isSticky () {

		if (!isUse) {
			return false;
		}

		if (lastUpdatedAt <= 0) {
			return false;
		}

		return System.currentTimeMillis() - lastUpdatedAt <= STICKY_INTERVAL;

	}

}
