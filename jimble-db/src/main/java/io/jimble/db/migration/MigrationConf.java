package io.jimble.db.migration;

import io.jimble.util.conf.Conf;

/**
 * マイグレーション設定
 *
 * <p>
 * 設定は {@code application.conf} の {@code migration} ブロックで指定する。
 * </p>
 *
 * <pre>
 * migration {
 *   # 起動時に適用するか。auto = ローカル以外で適用する（既定）
 *   on_startup = auto          # auto | true | false
 *   # down を実行するか（既定 false。D-2）
 *   down = false
 *   # ロック待ちの上限秒数（F-G-19）
 *   lock_timeout_seconds = 60
 *   # SQL ファイルを置くリソースディレクトリ
 *   resource_dir = "migration"
 * }
 * </pre>
 *
 * <p>
 * <b>{@code on_startup} は環境判定に依存しない形でも指定できる</b>（要件 F-G-17）。
 * {@code auto} のときだけ {@code Conf.isLocal()} を見る。
 * </p>
 */
public final class MigrationConf {

	/** 設定キー：起動時適用 */
	public static final String KEY_ON_STARTUP = "migration.on_startup";

	/** 設定キー：down の実行 */
	public static final String KEY_DOWN = "migration.down";

	/** 設定キー：ロック待ちの上限秒数 */
	public static final String KEY_LOCK_TIMEOUT_SECONDS = "migration.lock_timeout_seconds";

	/** 設定キー：SQL ファイルのリソースディレクトリ */
	public static final String KEY_RESOURCE_DIR = "migration.resource_dir";

	/** 既定のロック待ち上限秒数 */
	public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 60;

	/** 既定のリソースディレクトリ */
	public static final String DEFAULT_RESOURCE_DIR = "migration";

	private MigrationConf () {}

	/**
	 * 起動時に適用するか
	 *
	 * <p>
	 * {@code auto}（既定）はローカル以外で適用する。ローカルは Gradle の
	 * {@code migrate} タスクで適用するため起動時には何もしない（要件 F-G-07 / O-11）。
	 * </p>
	 *
	 * @return	適用する場合 = true
	 */
	public static boolean isOnStartup () {

		String value = Conf.conf().getString(KEY_ON_STARTUP, "auto");

		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}

		// auto
		return !Conf.conf().isLocal();

	}

	/**
	 * down を実行するか
	 *
	 * <p>既定は実行しない（D-2）。</p>
	 *
	 * @return	実行する場合 = true
	 */
	public static boolean isDownEnabled () {

		return Conf.conf().getBoolean(KEY_DOWN, false);

	}

	/**
	 * ロック待ちの上限秒数
	 *
	 * @return	秒数
	 */
	public static int lockTimeoutSeconds () {

		return Conf.conf().getInt(KEY_LOCK_TIMEOUT_SECONDS, DEFAULT_LOCK_TIMEOUT_SECONDS);

	}

	/**
	 * SQL ファイルのリソースディレクトリ
	 *
	 * @return	ディレクトリ名
	 */
	public static String resourceDir () {

		return Conf.conf().getString(KEY_RESOURCE_DIR, DEFAULT_RESOURCE_DIR);

	}

}
