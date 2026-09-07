package io.jimble.util.conf;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 設定
 *
 * <p>
 * 環境別ファイル（{@code application.conf} + {@code application.<env>.conf}）を読む。
 * 環境は システムプロパティ {@code env} または環境変数 {@code ENV}（既定 {@code local}）。
 * </p>
 *
 * <p>
 * <b>新しく設定値を読むときは既定値つきの形を使う</b>（要件 F-U-02）。
 * 設定漏れで起動できなくなるのを避ける。
 * </p>
 *
 * <pre>
 * Conf.conf().getString("cipher.key", "")
 * Conf.conf().getInt("jimble.server.port", 9000)
 * </pre>
 */
public final class Conf {

	/** 環境を指定するキー */
	public static final String KEY_ENV = "env";

	/** 既定の環境 */
	public static final String DEFAULT_ENV = "local";

	/* 環境 */
	private static volatile String env = resolveEnv();

	/* インスタンス */
	private static volatile Conf instance;

	/* 設定 */
	private final Config config;

	/**
	 * コンストラクタ
	 *
	 * <p>部分設定を包む場合や、アプリが独自の設定クラスを作る場合に使う。</p>
	 *
	 * @param config	設定
	 */
	public Conf (Config config) {

		this.config = config;

	}

	/**
	 * 環境を解決する
	 *
	 * @return	環境
	 */
	private static String resolveEnv () {

		String value = System.getProperty(KEY_ENV);
		if (value == null || value.isEmpty()) {
			value = System.getenv("ENV");
		}
		return (value == null || value.isEmpty()) ? DEFAULT_ENV : value;

	}

	/**
	 * 環境
	 *
	 * @return	環境
	 */
	public static String env () {

		return env;

	}

	/**
	 * 設定を取得する
	 *
	 * @return	設定
	 */
	public static Conf conf () {

		Conf current = instance;
		if (current == null) {
			synchronized (Conf.class) {
				current = instance;
				if (current == null) {
					current = new Conf(load());
					instance = current;
				}
			}
		}
		return current;

	}

	/**
	 * 設定を差し替える
	 *
	 * <p>テストと起動時の明示指定のための拡張点。</p>
	 *
	 * @param config	設定
	 */
	public static void replace (Config config) {

		instance = new Conf(Objects.requireNonNull(config, "config"));

	}

	/**
	 * 再読み込みする
	 */
	public static void reload () {

		env = resolveEnv();
		instance = null;

	}

	/**
	 * 設定を読み込む
	 *
	 * @return	設定
	 */
	private static Config load () {

		// 環境別ファイルが基本ファイルを上書きする
		return ConfigFactory
			.parseResourcesAnySyntax("application." + env)
			.withFallback(ConfigFactory.parseResourcesAnySyntax("application"))
			.withFallback(ConfigFactory.systemProperties())
			.resolve();

	}

	/**
	 * 生の設定
	 *
	 * @return	設定
	 */
	public Config config () {

		return config;

	}

	/**
	 * 必須の設定が揃っているか確かめる（要件 F-U-03）
	 *
	 * <pre>
	 * // アプリの main で1回
	 * Conf.conf().require("db.main.url", "db.main.username", "session.secret");
	 * </pre>
	 *
	 * <p>
	 * <b>足りないものをまとめて1回で報告する。</b>
	 * 1つ直しては起動して次で落ちる、を繰り返さないため（要件 F-X-05）。
	 * </p>
	 *
	 * @param keys	必須のキー
	 * @throws IllegalStateException	足りないキーがある場合
	 */
	public void require (String...keys) {

		if (keys == null || keys.length == 0) {
			return;
		}

		List<String> missing = new ArrayList<>();

		for (String key : keys) {

			if (key == null || key.isBlank()) {
				continue;
			}

			if (!has(key) || getString(key, "").isEmpty()) {
				missing.add(key);
			}

		}

		if (missing.isEmpty()) {
			return;
		}

		throw new IllegalStateException(
			"設定が足りません（env=%s）: %s".formatted(env(), String.join(", ", missing)));

	}

	/**
	 * 値があるか
	 *
	 * @param key	キー
	 * @return	あれば true
	 */
	public boolean has (String key) {

		return config.hasPath(key);

	}

	/**
	 * 文字列（既定値つき）
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	値
	 */
	public String getString (String key, String defaultValue) {

		return has(key) ? config.getString(key) : defaultValue;

	}

	/**
	 * 文字列
	 *
	 * <p>無ければ例外。<b>既定値つきの形を優先すること。</b></p>
	 *
	 * @param key	キー
	 * @return	値
	 */
	public String getString (String key) {

		require(key);
		return config.getString(key);

	}

	/**
	 * 部分設定を取得する
	 *
	 * @param key	キー
	 * @return	部分設定
	 */
	public Conf getConf (String key) {

		return new Conf(config.getConfig(key));

	}

	/**
	 * 文字列の一覧
	 *
	 * <p>無ければ空のリスト。</p>
	 *
	 * @param key	キー
	 * @return	値（無ければ空のリスト）
	 */
	public List<String> getStringListOptional (String key) {

		if (!has(key)) {
			return List.of();
		}

		return config.getStringList(key);

	}

	/**
	 * 整数
	 *
	 * <p>無ければ例外。<b>既定値つきの形を優先すること。</b></p>
	 *
	 * @param key	キー
	 * @return	値
	 */
	public int getInt (String key) {

		require(key);
		return config.getInt(key);

	}

	/**
	 * 長整数
	 *
	 * <p>無ければ例外。<b>既定値つきの形を優先すること。</b></p>
	 *
	 * @param key	キー
	 * @return	値
	 */
	public long getLong (String key) {

		require(key);
		return config.getLong(key);

	}

	/**
	 * 真偽値
	 *
	 * <p>無ければ例外。<b>既定値つきの形を優先すること。</b></p>
	 *
	 * @param key	キー
	 * @return	値
	 */
	public boolean getBoolean (String key) {

		require(key);
		return config.getBoolean(key);

	}

	/**
	 * 実数（既定値つき）
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	値
	 */
	public double getDouble (String key, double defaultValue) {

		return has(key) ? config.getDouble(key) : defaultValue;

	}

	/**
	 * 書き込み不可のコンテナで動いているか
	 *
	 * <p>一時ファイルを書けない環境ではファイルキャッシュを使わない。</p>
	 *
	 * @return	書き込み不可なら true
	 */
	public static boolean readOnlyContainer () {

		return conf().getBoolean("jimble.read_only_container", false);

	}

	/**
	 * キーがあることを確認する
	 *
	 * @param key	キー
	 */
	private void require (String key) {

		if (!has(key)) {
			throw new IllegalStateException("設定 %s が見つかりません（環境: %s）".formatted(key, env));
		}
	}

	/**
	 * 整数（既定値つき）
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	値
	 */
	public int getInt (String key, int defaultValue) {

		return has(key) ? config.getInt(key) : defaultValue;

	}

	/**
	 * 長整数（既定値つき）
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	値
	 */
	public long getLong (String key, long defaultValue) {

		return has(key) ? config.getLong(key) : defaultValue;

	}

	/**
	 * 真偽値（既定値つき）
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	値
	 */
	public boolean getBoolean (String key, boolean defaultValue) {

		return has(key) ? config.getBoolean(key) : defaultValue;

	}

	/**
	 * ローカル環境か
	 *
	 * @return	ローカルなら true
	 */
	public boolean isLocal () {

		return DEFAULT_ENV.equals(env);

	}

	/**
	 * ステージング環境か
	 *
	 * @return	ステージングなら true
	 */
	public boolean isStaging () {

		return "staging".equals(env);

	}

	/**
	 * 本番環境か
	 *
	 * @return	本番なら true
	 */
	public boolean isProduction () {

		return "production".equals(env);

	}

}
