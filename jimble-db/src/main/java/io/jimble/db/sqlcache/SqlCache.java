package io.jimble.db.sqlcache;

import io.jimble.db.redis.RedisClient;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.log.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQL 結果のキャッシュ（要件 F-D-28）
 *
 * <pre>
 * // 明示的に頼んだ SELECT だけキャッシュする
 * Data customer = db.selectCached(
 *     SQL.select()
 *         .from(Customer.instance())
 *         .inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
 *         .where(Customer.id.eq(1)));
 *
 * // 更新は書くだけでよい。関係するキャッシュだけが消える
 * db.update(SQL.update(Customer.instance()).set(Customer.name, "name1").where(Customer.id.eq(1)));
 * </pre>
 *
 * <p>
 * <b>読むほうは明示、消すほうは自動。</b>
 * 逆（読むのが自動で消すのが明示）にすると、
 * <b>消し忘れが黙って古いデータになる</b>。原則5（隠れた I/O を作らない）にも合う。
 * </p>
 *
 * <h2>効かないところ</h2>
 * <ul>
 *   <li><b>トランザクションの中</b>では読みも書きもしない
 *       （まだ確定していない値を残さないため）</li>
 *   <li><b>生 SQL の更新</b>（{@code db.execute("UPDATE ...")}）は
 *       どこに当たるか読めないので<b>全部消す</b>。jimble 自身の表だけを更新する文は消さない（{@link #isUncachedTarget}）</li>
 *   <li>{@code sql_cache.store = "memory"} は<b>その台の中だけ</b>。
 *       複数台なら {@code redis} か {@code db} にすること</li>
 * </ul>
 */
public final class SqlCache {

	/* 入れ物のキー */
	private static final String ROWS = "rows";

	/*
	 * 読み戻すときに通してよいクラス。
	 *
	 * 置き場に書くのは jimble だけだが、<b>Redis や DB が乗っ取られたときに
	 * 任意のクラスを読ませない</b>ようにしておく。
	 *
	 * maxarray は BLOB の列（byte[]）も通すために大きい。そのぶん、読むときに
	 * 「本文より長い配列」を別に断る（{@link #deserialize(String)}。D-251）。
	 */
	private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
		"maxdepth=64;maxarray=10000000;"
			+ "io.jimble.util.data.Data;"
			+ "java.util.*;java.lang.*;java.math.*;java.sql.*;java.time.*;"
			+ "[B;[Ljava.lang.Object;;"
			+ "!*");

	/* キーを作るときの区切り（SQL にもパラメータにも出ない文字） */
	private static final char KEY_SEPARATOR = (char) 1;

	/* 置き場（差し替えられる） */
	private static volatile SqlCacheStore store;

	/* 切ったまま使われたことを1度だけ知らせる */
	private static final AtomicBoolean WARNED_DISABLED = new AtomicBoolean(false);

	/**
	 * コンストラクタ
	 */
	private SqlCache () {

	}

	// region 置き場

	/**
	 * 置き場
	 *
	 * @return	置き場
	 */
	public static SqlCacheStore store () {

		SqlCacheStore current = store;

		if (current == null) {

			synchronized (SqlCache.class) {

				current = store;

				if (current == null) {
					current = create(SqlCacheConf.store());
					store = current;
				}

			}

		}

		return current;

	}

	/**
	 * 置き場を差し替える（テスト用）
	 *
	 * @param value	置き場。null なら設定から作り直す
	 */
	public static void replace (SqlCacheStore value) {

		store = value;

	}

	/**
	 * 置き場を作る
	 *
	 * @param kind	種別
	 * @return	置き場
	 */
	static SqlCacheStore create (String kind) {

		return switch (kind) {

			case SqlCacheConf.STORE_REDIS -> {
				if (!RedisClient.isConfigured()) {
					Log.warn("sql_cache.store = redis ですが redis.host が設定されていません。メモリを使います");
					yield new MemorySqlCacheStore();
				}
				yield new RedisSqlCacheStore();
			}

			case SqlCacheConf.STORE_DB -> new DbSqlCacheStore();

			case SqlCacheConf.STORE_MEMORY -> new MemorySqlCacheStore();

			default -> {
				Log.warn("sql_cache.store に使えない値です: %s（memory / redis / db）。メモリを使います"
					.formatted(kind));
				yield new MemorySqlCacheStore();
			}

		};

	}

	// endregion

	/**
	 * 切ったまま使われたことを知らせる（要件 F-D-28 / D-95）
	 *
	 * <p>
	 * <b>1度だけ出す。</b>{@code selectCached} は1リクエストで何度も呼ばれうるので、
	 * 毎回出すとログが埋まる。
	 * </p>
	 *
	 * <p>
	 * 例外にはしない。<b>本番で調べるためにキャッシュを切ったら
	 * アプリごと止まる</b>のは行きすぎである。
	 * </p>
	 */
	public static void warnDisabled () {

		if (WARNED_DISABLED.compareAndSet(false, true)) {
			Log.warn("selectCached が呼ばれましたが sql_cache.enabled = false です。"
				+ "キャッシュせずにそのまま引きます。使うなら application.conf に sql_cache.enabled = true を書いてください");
		}

	}

	/**
	 * 知らせたことを忘れる（テスト用）
	 */
	public static void resetWarning () {

		WARNED_DISABLED.set(false);

	}

	// region 読み書き

	/**
	 * キーを作る
	 *
	 * <p>SQL とバインドパラメータから作る。<b>同じ SQL でも値が違えば別のキー</b>。</p>
	 *
	 * @param sql		SQL
	 * @param params	バインドパラメータ
	 * @return	キー
	 */
	public static String key (String sql, List<Object> params) {

		return key("", sql, params);

	}

	/**
	 * キーを作る（データソースごと。D-222）
	 *
	 * <p>
	 * <b>データソースの名前と {@code sql_cache.namespace} もキーに入れる。</b>かつては SQL とパラメータだけだったので、
	 * テナントごとのサブ DB で同じ SQL を流すと<b>別のテナントの結果が返り</b>、
	 * 1つの Redis を分け合うアプリや環境のあいだでも結果が混ざった。
	 * </p>
	 *
	 * @param dataSource	データソースの名前
	 * @param sql			SQL
	 * @param params		バインドパラメータ
	 * @return	キー
	 */
	public static String key (String dataSource, String sql, List<Object> params) {

		StringBuilder builder = new StringBuilder()
			.append(SqlCacheConf.namespace()).append(KEY_SEPARATOR)
			.append(dataSource == null ? "" : dataSource).append(KEY_SEPARATOR)
			.append(sql);

		for (Object param : params) {

			builder.append(KEY_SEPARATOR);

			/*
			 * Data の toString() は<b>キー名と型だけ</b>を出す要約なので、
			 * そのまま混ぜると {user_id:1} と {user_id:2} が同じキーになる。
			 * <b>2回目が1回目の結果を返す。</b>中身で区別する（要件 F-D-28 / F-D-30）。
			 */
			if (param instanceof Data data) {
				builder.append(data.getJsonString());
			} else if (param instanceof byte[] bytes) {
				// byte[] の toString() は identity hash。毎回別のキーになる
				builder.append(java.util.HexFormat.of().formatHex(bytes));
			} else {
				builder.append(param);
			}

		}

		String text = builder.toString();
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

		/*
		 * ハッシュだけだと衝突したときに<b>別のクエリの結果が返る</b>ので、長さも混ぜる。
		 * SQL 全文をキーにできればいちばん確かだが、置き場の主キーに入らない。
		 */
		return "%016x%08x".formatted(Hash.sipHash(text), bytes.length);

	}

	/**
	 * 取り出す
	 *
	 * @param key	キー
	 * @return	結果。無ければ null
	 */
	public static List<Data> get (String key) {

		if (!SqlCacheConf.enabled()) {
			return null;
		}

		try {

			String value = store().get(key);

			return value == null ? null : deserialize(value);

		} catch (Exception ex) {

			/*
			 * 置き場が落ちているだけでアプリを止めない。引き直せばよい。
			 * <b>黙って通すのではなくログには出す</b>（Redis が落ちたまま気づかない、を避ける）。
			 */
			Log.error(ex, "SQL結果キャッシュを読めませんでした: " + key);

			return null;

		}

	}

	/**
	 * 入れる
	 *
	 * @param key	キー
	 * @param tags	依存するタグ
	 * @param rows	結果
	 */
	public static void put (String key, Set<String> tags, List<Data> rows) {

		if (!SqlCacheConf.enabled() || tags.isEmpty()) {
			return;
		}

		try {

			store().put(key, tags, serialize(rows), SqlCacheConf.ttl());

		} catch (Exception ex) {

			Log.error(ex, "SQL結果キャッシュに入れられませんでした: " + key);

		}

	}

	/**
	 * 書き出す
	 *
	 * <p>
	 * <b>JSON にしない。</b>JSON にすると型が変わって戻る。
	 * 実測すると {@code BigDecimal} が {@code Float} に丸まり（金額が壊れる）、
	 * {@code Long} が {@code Integer} になり、{@code byte[]} が数値の配列になり、
	 * {@code "true"} という<b>文字列</b>が {@code Boolean} になり、
	 * 日時のミリ秒が落ちる。
	 * <b>キャッシュに当たったときだけ結果が変わる</b>という、いちばん気づけない形になる。
	 * </p>
	 *
	 * @param rows	結果
	 * @return	Base64
	 * @throws Exception	書き出せなかった場合
	 */
	static String serialize (List<Data> rows) throws Exception {

		Data wrapper = new Data();
		wrapper.put(ROWS, new ArrayList<>(rows));

		try (
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bytes)
		) {

			out.writeObject(wrapper);
			out.flush();

			return Base64.getEncoder().encodeToString(bytes.toByteArray());

		}

	}

	/**
	 * 読み戻す
	 *
	 * @param value	Base64
	 * @return	結果
	 * @throws Exception	読み戻せなかった場合
	 */
	static List<Data> deserialize (String value) throws Exception {

		byte[] bytes = Base64.getDecoder().decode(value);

		try (
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))
		) {

			/*
			 * <b>本文より長い配列は作らせない</b>（D-251）。配列の長さは本文の数バイトで書けるので、
			 * maxarray だけでは、数十バイトの値で 1,000 万要素の配列（ArrayList や HashMap の中身も）を
			 * いくつも確保させられた。本物の配列は、1要素に少なくとも1バイトを本文に持つ
			 */
			in.setObjectInputFilter(info -> info.arrayLength() > bytes.length
				? ObjectInputFilter.Status.REJECTED
				: FILTER.checkInput(info));

			if (!(in.readObject() instanceof Data wrapper)) {
				return null;
			}

			List<Data> rows = new ArrayList<>();

			for (Object row : wrapper.getObjectList(ROWS, Object.class)) {
				if (row instanceof Data data) {
					rows.add(data);
				}
			}

			return rows;

		}

	}

	/**
	 * タグのついたものを消す
	 *
	 * @param tags	タグ
	 */
	public static void invalidate (Set<String> tags) {

		if (!SqlCacheConf.enabled() || tags.isEmpty()) {
			return;
		}

		try {

			store().invalidate(tags);

		} catch (Exception ex) {

			/*
			 * <b>消せなかったことは黙って通せない。</b>
			 * 古いデータが出続けるので、全部消しにいく。
			 */
			Log.error(ex, "SQL結果キャッシュを消せませんでした。全部消します: " + tags);

			clear();

		}

	}

	// region キャッシュと関係ないテーブル（D-273）

	/*
	 * 生 SQL で更新しても、キャッシュを消さなくてよいテーブル（小文字）。
	 * jimble 自身のテーブルは、テーブル定義クラスを作らない（codegen が除く。D-68）ので、
	 * selectCached で読まれることが無い。
	 */
	private static final Set<String> UNCACHED_TABLES = java.util.concurrent.ConcurrentHashMap.newKeySet();

	static {
		for (String name : io.jimble.db.FrameworkTables.ALL) {
			UNCACHED_TABLES.add(name.toLowerCase(java.util.Locale.ROOT));
		}
	}

	/*
	 * 生 SQL の更新先（INSERT / REPLACE / UPDATE / DELETE の、最初のテーブル）。
	 * スキーマ付き・引用符付きも読む。読めない形（WITH・コメントで始まる・DELETE t FROM ...）は合わない
	 */
	private static final java.util.regex.Pattern WRITE_TARGET = java.util.regex.Pattern.compile(
		"^\\s*(insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from)\\s+"
			+ "(?:[`\"]?[A-Za-z0-9_$]+[`\"]?\\s*\\.\\s*)?[`\"]?([A-Za-z0-9_$]+)[`\"]?(.*)$"
		, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

	/* 更新先のすぐあとにカンマ（別名つきも）＝ 複数のテーブルを更新する形 */
	private static final java.util.regex.Pattern MULTI_TABLE = java.util.regex.Pattern.compile(
		"^\\s*(?:as\\s+)?[A-Za-z0-9_$`\"]*\\s*,", java.util.regex.Pattern.CASE_INSENSITIVE);

	/* 結合 */
	private static final java.util.regex.Pattern JOIN = java.util.regex.Pattern.compile(
		"\\bjoin\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

	/**
	 * キャッシュと関係ないテーブルを届け出る（D-273）
	 *
	 * <p>
	 * jimble が名前を決められないテーブル（設定で名前を変えられるセッションの表、キューごとの MQ の表）を作るところで呼ぶ。
	 * <b>{@code selectCached} で読むテーブルを届け出てはいけない</b>（生 SQL の更新でキャッシュが消えなくなる）。
	 * </p>
	 *
	 * @param name	テーブル名
	 */
	public static void excludeTable (String name) {

		if (name != null && !name.isEmpty()) {
			UNCACHED_TABLES.add(name.toLowerCase(java.util.Locale.ROOT));
		}

	}

	/**
	 * 生 SQL の更新が、キャッシュと関係ないテーブルだけに当たるか（D-273）
	 *
	 * <p>
	 * <b>2.5.1 までは、生 SQL の更新はどれも「全部消す」だった。</b>jimble 自身も DB のセッション・MQ・レート制限・
	 * remember-me などを生 SQL で更新するので、SQL 結果キャッシュを有効にすると<b>リクエストのたびに全部消えていた</b>
	 * （Redis の置き場ではそのたびにキーを全部走査していた）。
	 * </p>
	 *
	 * <p>
	 * 確かに1つのテーブルだけを更新する形（{@code INSERT INTO t} / {@code UPDATE t SET} / {@code DELETE FROM t}）で、
	 * そのテーブルが届け出たものなら true。<b>読めない形はすべて false</b>（これまでどおり全部消す）：
	 * 複文、{@code UPDATE a, b}・{@code UPDATE ... JOIN}・{@code DELETE ... JOIN}、WITH、コメントで始まるもの。
	 * </p>
	 *
	 * @param sql	SQL
	 * @return	消さなくてよい場合 = true
	 */
	public static boolean isUncachedTarget (String sql) {

		if (sql == null || sql.indexOf(';') >= 0) {
			return false;
		}

		java.util.regex.Matcher matcher = WRITE_TARGET.matcher(sql);

		if (!matcher.matches()) {
			return false;
		}

		if (!UNCACHED_TABLES.contains(matcher.group(2).toLowerCase(java.util.Locale.ROOT))) {
			return false;
		}

		String rest = matcher.group(3);

		if (MULTI_TABLE.matcher(rest).find()) {
			return false;
		}

		// INSERT ... SELECT の結合は読むだけ。UPDATE / DELETE の結合は、結合先も書き換えうる
		boolean insert = matcher.group(1).toLowerCase(java.util.Locale.ROOT).startsWith("insert")
			|| matcher.group(1).toLowerCase(java.util.Locale.ROOT).startsWith("replace");

		return insert || !JOIN.matcher(rest).find();

	}

	// endregion

	/**
	 * 全部消す
	 */
	public static void clear () {

		if (!SqlCacheConf.enabled()) {
			return;
		}

		try {

			store().clear();

		} catch (Exception ex) {

			Log.error(ex, "SQL結果キャッシュを全部消せませんでした");

		}

	}

	// endregion

}
