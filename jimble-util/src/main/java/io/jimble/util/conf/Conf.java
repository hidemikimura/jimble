package io.jimble.util.conf;

import io.jimble.util.log.Log;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 設定
 *
 * <p>
 * 環境別ファイル（{@code application.conf} + {@code application.<env>.conf}）を読む。
 * 環境は システムプロパティ {@code env} または環境変数 {@code ENV}（既定 {@code local}）。
 * </p>
 *
 * <h2>どこから読むか（要件 D-80 / D-81）</h2>
 * <p>
 * <b>クラスパスだけを見る。</b>jar の中（アプリのビルドで {@code conf/} を
 * リソースに足してある）か、開発中なら {@code build/resources/main} である。
 * </p>
 *
 * <p>
 * <b>読むのは1つだけ。</b>
 * </p>
 * <ol>
 *   <li>{@code application.<env>.conf} があれば<b>それ</b></li>
 *   <li>無ければ {@code application.conf}</li>
 * </ol>
 *
 * <p>
 * 共通の設定は<b>環境別ファイルの先頭で読み込む</b>（HOCON の {@code include}）。
 * </p>
 *
 * <pre>
 * // application.local.conf
 * include "application.conf"
 *
 * db { main { url = "jdbc:mariadb://127.0.0.1:3306/app_local" } }
 * </pre>
 *
 * <p>
 * <b>裏で重ねない。</b>重ねる形（環境別 &gt; 共通 の自動フォールバック）だと、
 * 書いてある {@code include} が効いているのかフレームワークが足しているのか
 * <b>ファイルを見ても分からない</b>。読むファイルは1つ、
 * 続きは<b>ファイルに書いてあるとおり</b>にする（原則1）。
 * </p>
 *
 * <p>
 * {@code include} を書き忘れると共通の設定が丸ごと消えるので、
 * <b>起動時に見て言う</b>（{@link #missingFromEnvFile()}）。
 * </p>
 *
 * <h2>jar の外は見ない</h2>
 * <p>
 * 前は {@code -Djimble.conf.dir} で指した jar の外の {@code conf/} を
 * 先に読んでいた（D-71）。<b>やめた。</b>同じ名前のファイルが2か所にある形は、
 * 「直したのに効かない」の原因がどちらなのか<b>動かしてみるまで分からない</b>。
 * 設定を変えるならビルドし直す。<b>動いている jar と設定が1対1になる。</b>
 * </p>
 *
 * <p>
 * <b>実際に読んだファイルは {@link #sources()} で取れる</b>ので、起動ログに出せる。
 * クラスパスに同じ名前が2つある（jar と {@code resources} の両方など）ときも、
 * <b>両方見える</b>。
 * </p>
 *
 * <p>
 * <b>新しく設定値を読むときは既定値つきの形を使う</b>（要件 F-U-02）。
 * 設定漏れで起動できなくなるのを避ける。
 * </p>
 *
 * <pre>
 * Conf.conf().getString("cipher.key", "")
 * Conf.conf().getInt("server.port", 9000)
 * </pre>
 */
public final class Conf {

	/**
	 * 環境を指定するシステムプロパティ
	 *
	 * <p>
	 * <b>これが正。</b>{@code jimble.} を付けない {@code env} も読むが、
	 * ドキュメントも起動コマンドもこちらで書いてある。
	 * </p>
	 */
	public static final String PROPERTY_ENV = "jimble.env";

	/**
	 * 環境を指定するキー（{@code jimble.} 無し）
	 *
	 * @deprecated {@link #PROPERTY_ENV} を使う
	 */
	@Deprecated
	public static final String KEY_ENV = "env";

	/** 環境を指定する環境変数 */
	public static final String ENV_ENV = "ENV";

	/** 既定の環境 */
	public static final String DEFAULT_ENV = "local";

	/** 設定ファイルの基本名 */
	private static final String BASE_NAME = "application";

	/** 探す拡張子（typesafe config の AnySyntax と同じ） */
	private static final String[] EXTENSIONS = { ".conf", ".json", ".properties" };

	/* 環境 */
	private static volatile String env = resolveEnv();

	/* インスタンス */
	private static volatile Conf instance;

	/* 実際に読んだファイル（起動ログ用） */
	private static volatile List<String> sources = List.of();

	/* 共通ファイルにしか無かったキー（include の書き忘れを言うため） */
	private static volatile List<String> missing = List.of();

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
	 * <p>
	 * {@code -Djimble.env} &gt; {@code -Denv} &gt; 環境変数 {@code ENV} &gt; {@code local}。
	 * </p>
	 *
	 * <h2>直したところ</h2>
	 * <p>
	 * <b>{@code jimble.env} を読んでいなかった。</b>
	 * ドキュメントも README も雛形も
	 * {@code java -Djimble.env=prod -jar app.jar} と書いてあるのに、
	 * 読んでいたのは {@code env} だけだった。
	 * </p>
	 *
	 * <p>
	 * <b>間違えても何も言わずに {@code local} で動く。</b>
	 * 環境別ファイル（{@code application.prod.conf}）が読まれず、
	 * {@code isProduction()} も false のままになる。
	 * 本番でこれが起きても、動いてしまうので気づけない。
	 * </p>
	 *
	 * @return	環境
	 */
	private static String resolveEnv () {

		String value = System.getProperty(PROPERTY_ENV);

		if (value == null || value.isEmpty()) {
			value = System.getProperty(KEY_ENV);
		}

		if (value == null || value.isEmpty()) {
			value = System.getenv(ENV_ENV);
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
		sources = List.of("（差し替え）");
		missing = List.of();

	}

	/**
	 * 再読み込みする
	 */
	public static void reload () {

		env = resolveEnv();

		// 環境が変わったら、知らない環境の警告はもう一度出してよい
		unknownEnvWarned.set(false);
		instance = null;

	}

	/**
	 * 設定を読み込む
	 *
	 * @return	設定
	 */
	private static Config load () {

		/*
		 * 環境別ファイルの中の include "application.conf" は、
		 * ここでの parse のときに typesafe config が解決する。
		 * つまり envFile には共通の設定も入っている。
		 */
		Config envFile = ConfigFactory.parseResourcesAnySyntax(BASE_NAME + "." + env);
		Config baseFile = ConfigFactory.parseResourcesAnySyntax(BASE_NAME);

		boolean useEnvFile = !envFile.isEmpty();

		List<String> found = new ArrayList<>();
		collectSources(useEnvFile ? BASE_NAME + "." + env : BASE_NAME, found);
		sources = List.copyOf(found);

		missing = useEnvFile ? missingKeys(envFile, baseFile) : List.of();

		// 読むのは1つだけ。共通を足すのはファイルの include の仕事
		return (useEnvFile ? envFile : baseFile)
			.withFallback(ConfigFactory.systemProperties())
			.resolve();

	}

	/**
	 * 共通ファイルにしか無いキー
	 *
	 * <p>
	 * 環境別ファイルに {@code include "application.conf"} を書き忘れると、
	 * <b>共通の設定が丸ごと落ちる</b>。落ちたことに気づけるように、
	 * 何が落ちたかを起動時に言う。
	 * </p>
	 *
	 * <p>空なら問題なし（{@code include} が効いているか、共通ファイルが無い）。</p>
	 *
	 * @return	共通ファイルにしか無いトップレベルのキー
	 */
	public static List<String> missingFromEnvFile () {

		return missing;

	}

	/**
	 * 共通ファイルにしか無いキーを拾う
	 *
	 * <p>
	 * <b>葉まで見る。</b>トップレベルだけを比べると、
	 * {@code server { port = ... }} のように名前が同じで
	 * <b>中身が丸ごと違う</b>ものを見逃す。
	 * </p>
	 *
	 * <p>
	 * 報告するのは<b>トップレベルの名前だけ</b>にまとめる。
	 * {@code include} を書き忘れると数百件になり、ログとして読めなくなるためである。
	 * </p>
	 *
	 * <p>
	 * 値は見ない（<b>解決していない</b>設定なので、
	 * {@code ${?ENV}} を触ると落ちる）。あるか無いかだけを見る。
	 * </p>
	 *
	 * @param envFile	環境別ファイル（include 解決済み）
	 * @param baseFile	共通ファイル
	 * @return	共通にしか無いキーのトップレベル名（重複なし）
	 */
	private static List<String> missingKeys (Config envFile, Config baseFile) {

		Set<String> result = new LinkedHashSet<>();

		collectMissing(baseFile.root(), envFile.root(), null, result);

		// 設定の並び順は保たれないので、名前順にして毎回同じログにする
		List<String> sorted = new ArrayList<>(result);
		sorted.sort(null);

		return List.copyOf(sorted);

	}

	/**
	 * 共通にしか無い葉を探して、トップレベルの名前を集める
	 *
	 * @param base		共通の側
	 * @param env		環境別の側（同じ位置。無ければ null）
	 * @param topLevel	いま辿っているトップレベルの名前（最初は null）
	 * @param result	結果
	 */
	private static void collectMissing (ConfigObject base, ConfigObject env
		, String topLevel, Set<String> result) {

		for (Map.Entry<String, ConfigValue> entry : base.entrySet()) {

			String name = topLevel == null ? entry.getKey() : topLevel;

			if (result.contains(name)) {
				// 1つ落ちていれば十分。同じ名前を何度も辿らない
				continue;
			}

			ConfigValue other = env == null ? null : env.get(entry.getKey());

			if (other == null) {
				result.add(name);
				continue;
			}

			if (entry.getValue() instanceof ConfigObject child
				&& other instanceof ConfigObject otherChild) {
				collectMissing(child, otherChild, name, result);
			}

		}

	}

	/**
	 * 実際に読んだ設定ファイル
	 *
	 * <p>
	 * 読むのは<b>1つ</b>（環境別があればそれ、無ければ共通）。
	 * クラスパスの<b>どこにあったか</b>（jar の中か {@code build/resources} か）
	 * まで分かる形で返す。同じ名前が2か所にあれば<b>両方返す</b>
	 * （典型的には事故なので、見えるようにしておく）。
	 * </p>
	 *
	 * <p>
	 * {@code include} で読み込まれたファイルはここには出ない。
	 * <b>それはファイルに書いてある</b>。
	 * </p>
	 *
	 * @return	見つかった設定ファイル（無ければ空）
	 */
	public static List<String> sources () {

		return sources;

	}

	/**
	 * クラスパスにある設定ファイルを集める
	 *
	 * <p>
	 * 読み込み自体は typesafe config がやる。ここは<b>起動ログに出すため</b>に
	 * 同じ名前で同じように探しているだけである。
	 * </p>
	 *
	 * @param baseName	基本名（拡張子なし）
	 * @param result	結果
	 */
	private static void collectSources (String baseName, List<String> result) {

		/*
		 * typesafe config と同じクラスローダを見る。
		 * jimbleRun（同じ JVM でアプリを入れ替える）では、
		 * アプリのクラスパスは<b>スレッドのコンテキスト</b>にしか無い。
		 */
		ClassLoader loader = Thread.currentThread().getContextClassLoader();

		if (loader == null) {
			loader = Conf.class.getClassLoader();
		}

		for (String extension : EXTENSIONS) {

			try {

				Enumeration<URL> urls = loader.getResources(baseName + extension);

				while (urls.hasMoreElements()) {
					result.add(urls.nextElement().toString());
				}

			} catch (IOException ignore) {
				// 一覧が取れないだけ。読み込みには影響しない
			}

		}

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

	// region 時間と大きさ（単位は値に書く。要件 D-159）

	/**
	 * 時間（単位つきの値）
	 *
	 * <pre>
	 * session.timeout = 30m
	 * mq.poll_min     = 200ms
	 * batch.all_stop  = 24h
	 * </pre>
	 *
	 * <h4>なぜ素の数値を断るのか</h4>
	 * <p>
	 * <b>単位をキーの名前に書いていたときは、取り違えても何も起きなかった。</b>
	 * {@code assets.max_age = 3600000}——秒のつもりの欄にミリ秒を書いた設定は、
	 * <b>そのまま通って 41 日のキャッシュになる</b>。
	 * 逆にミリ秒の欄に秒を書けば、<b>3600 倍せっかちな設定</b>になる。
	 * どちらも例外もログも出ない。
	 * </p>
	 *
	 * <p>
	 * <b>単位を値に書かせれば、取り違えようが無い。</b>
	 * 書き忘れたときは<b>起動時に落として、直し方を言う</b>——
	 * 「読めなかったので既定値にしました」は、いちばん困る答えである。
	 * </p>
	 *
	 * <p>
	 * 使える単位は HOCON のもの（{@code ns} / {@code us} / {@code ms} /
	 * {@code s} / {@code m} / {@code h} / {@code d}、および
	 * {@code milliseconds} のような綴り）である。
	 * </p>
	 *
	 * @param key			キー
	 * @param defaultValue	既定値
	 * @return	値
	 */
	public Duration getDuration (String key, Duration defaultValue) {

		if (!has(key)) {
			return defaultValue;
		}

		requireUnit(key, "時間", "30m / 90s / 200ms / 24h");

		return config.getDuration(key);

	}

	/**
	 * 大きさ（単位つきの値）
	 *
	 * <pre>
	 * server.max_request_size = 10MiB
	 * upload.max_file_size    = 512KiB
	 * </pre>
	 *
	 * <p>
	 * {@link #getDuration(String, Duration)} と同じ理由で、<b>素の数値は断る</b>。
	 * {@code max_request_size = 10} が<b>10 バイト</b>なのか
	 * <b>10 メガバイト</b>なのかは、書いた人にしか分からない。
	 * </p>
	 *
	 * @param key			キー
	 * @param defaultValue	既定値（バイト）
	 * @return	値（バイト）
	 */
	public long getBytes (String key, long defaultValue) {

		if (!has(key)) {
			return defaultValue;
		}

		requireUnit(key, "大きさ", "10MiB / 512KiB / 1GB");

		return config.getBytes(key);

	}

	/**
	 * 単位が書いてあることを確かめる
	 *
	 * @param key		キー
	 * @param what		何の値か
	 * @param examples	書き方の例
	 */
	private void requireUnit (String key, String what, String examples) {

		if (config.getValue(key).valueType() != ConfigValueType.NUMBER) {
			return;
		}

		/*
		 * <b>直し方まで書く。</b>「単位が要ります」だけだと、
		 * どう書けばよいのかを探すところから始まる。
		 */
		throw new IllegalStateException(
			"設定 %s は%sなので、単位を値に書いてください（例: %s）。いまの値: %s"
				.formatted(key, what, examples, config.getValue(key).unwrapped()));

	}

	/**
	 * 下限だけを効かせる
	 *
	 * @param value	値
	 * @param min	下限
	 * @return	値（下限より小さければ下限）
	 */
	public static Duration atLeast (Duration value, Duration min) {

		return value.compareTo(min) < 0 ? min : value;

	}

	/**
	 * 上下の限を効かせる
	 *
	 * @param value	値
	 * @param min	下限
	 * @param max	上限
	 * @return	値
	 */
	public static Duration clamp (Duration value, Duration min, Duration max) {

		if (value.compareTo(min) < 0) {
			return min;
		}

		return value.compareTo(max) > 0 ? max : value;

	}

	// endregion

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

	// region 環境の判定

	/** 環境の正式名：ローカル */
	public static final String ENV_LOCAL = DEFAULT_ENV;

	/** 環境の正式名：ステージング */
	public static final String ENV_STAGING = "staging";

	/** 環境の正式名：本番 */
	public static final String ENV_PRODUCTION = "production";

	/**
	 * 環境の別名（略記 → 正式名）
	 *
	 * <p>
	 * <b>ドキュメントが `prod` と書いていて、コードが `production` としか一致していなかった</b>（D-155）。
	 * `-Djimble.env=prod` で動かしているアプリでは <b>{@code isProduction()} が false のまま</b>で、
	 * 本番の分岐が丸ごと素通りしていた。
	 * </p>
	 *
	 * <p>
	 * <b>ここで受けるのは判定だけである。</b>環境別ファイルの名前（{@code application.prod.conf}）は
	 * {@link #env()} の生の値をそのまま使う——<b>読むファイルが書いたとおりでなくなるほうが分かりにくい</b>。
	 * </p>
	 */
	private static final Map<String, String> ENV_ALIASES = Map.of(
		"dev", ENV_LOCAL
		, "development", ENV_LOCAL
		, "stg", ENV_STAGING
		, "stage", ENV_STAGING
		, "prd", ENV_PRODUCTION
		, "prod", ENV_PRODUCTION);

	/** 知っている環境（正式名） */
	private static final Set<String> KNOWN_ENVS = Set.of(ENV_LOCAL, ENV_STAGING, ENV_PRODUCTION);

	/* 知らない環境を1度だけ言う */
	private static final AtomicBoolean unknownEnvWarned = new AtomicBoolean(false);

	/**
	 * 判定に使う環境名
	 *
	 * <p>
	 * 大小と前後の空白をそろえ、別名を正式名に直したもの。
	 * <b>表に無ければ、そのまま返して1度だけ警告する</b>——
	 * {@code producton} のような打ち間違いは、黙って local に倒すと本番で気づけない。
	 * </p>
	 *
	 * @return	正式名
	 */
	public static String normalizedEnv () {

		String value = env == null ? "" : env.strip().toLowerCase(Locale.ROOT);

		String resolved = ENV_ALIASES.getOrDefault(value, value);

		if (!KNOWN_ENVS.contains(resolved) && unknownEnvWarned.compareAndSet(false, true)) {
			Log.warn("""
				知らない環境です: %s
				  isLocal() / isStaging() / isProduction() は、どれも false になります。
				  使えるのは local / staging / production です
				  （略記 dev・development / stg・stage / prod・prd も同じものとして扱います）。
				  環境別ファイルは application.%s.conf を探しています。
				"""
				.formatted(env, env));
		}

		return resolved;

	}

	/**
	 * ローカル環境か
	 *
	 * <p>{@code dev} / {@code development} も同じものとして扱う。</p>
	 *
	 * @return	ローカルなら true
	 */
	public boolean isLocal () {

		return ENV_LOCAL.equals(normalizedEnv());

	}

	/**
	 * ステージング環境か
	 *
	 * <p>{@code stg} / {@code stage} も同じものとして扱う。</p>
	 *
	 * @return	ステージングなら true
	 */
	public boolean isStaging () {

		return ENV_STAGING.equals(normalizedEnv());

	}

	/**
	 * 本番環境か
	 *
	 * <p>{@code prod} / {@code prd} も同じものとして扱う。</p>
	 *
	 * @return	本番なら true
	 */
	public boolean isProduction () {

		return ENV_PRODUCTION.equals(normalizedEnv());

	}

	// endregion

}
