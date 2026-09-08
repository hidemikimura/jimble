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
 *       どこに当たるか読めないので<b>全部消す</b></li>
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

		StringBuilder builder = new StringBuilder(sql);

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
	private static String serialize (List<Data> rows) throws Exception {

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
	private static List<Data> deserialize (String value) throws Exception {

		byte[] bytes = Base64.getDecoder().decode(value);

		try (
			ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))
		) {

			in.setObjectInputFilter(FILTER);

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
