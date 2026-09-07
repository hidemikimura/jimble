package io.jimble.batch.manager;

import io.jimble.util.conf.Conf;

/**
 * バッチ管理画面の設定（要件 F-B-11）
 *
 * <pre>
 * batch_manager {
 *   enabled  = true
 *   path     = "/jimble/batch/manager"
 *   username = "admin"
 *   password = ${?JIMBLE_BATCH_MANAGER_PASSWORD}
 *   realm    = "jimble batch manager"
 * }
 * </pre>
 *
 * <p>
 * <b>ユーザー名かパスワードが空なら、画面そのものを組み込まない。</b>
 * 移送元は Basic 認証が {@code public static} の差し替え可能なフィールドで、
 * <b>設定し忘れると誰でも見られる状態で公開されていた。</b>
 * </p>
 */
public final class BatchManagerConf {

	/** 設定キー：組み込むか */
	public static final String KEY_ENABLED = "batch_manager.enabled";

	/** 設定キー：パス */
	public static final String KEY_PATH = "batch_manager.path";

	/** 設定キー：ユーザー名 */
	public static final String KEY_USERNAME = "batch_manager.username";

	/** 設定キー：パスワード */
	public static final String KEY_PASSWORD = "batch_manager.password";

	/** 設定キー：realm */
	public static final String KEY_REALM = "batch_manager.realm";

	/** 既定のパス */
	public static final String DEFAULT_PATH = "/jimble/batch/manager";

	/** 既定の realm */
	public static final String DEFAULT_REALM = "jimble batch manager";

	private BatchManagerConf () {}

	/**
	 * 組み込むか
	 *
	 * @return	組み込む場合 = true
	 */
	public static boolean enabled () {

		return Conf.conf().getBoolean(KEY_ENABLED, false);

	}

	/**
	 * パス
	 *
	 * @return	パス
	 */
	public static String path () {

		return Conf.conf().getString(KEY_PATH, DEFAULT_PATH);

	}

	/**
	 * ユーザー名
	 *
	 * @return	ユーザー名
	 */
	public static String username () {

		return Conf.conf().getString(KEY_USERNAME, "");

	}

	/**
	 * パスワード
	 *
	 * @return	パスワード
	 */
	public static String password () {

		return Conf.conf().getString(KEY_PASSWORD, "");

	}

	/**
	 * realm
	 *
	 * @return	realm
	 */
	public static String realm () {

		return Conf.conf().getString(KEY_REALM, DEFAULT_REALM);

	}

	/**
	 * 認証情報が揃っているか
	 *
	 * @return	揃っている場合 = true
	 */
	public static boolean hasCredentials () {

		return !username().isEmpty() && !password().isEmpty();

	}

}
