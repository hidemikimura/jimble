package io.jimble.db;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 1つの接続先（{@code db.<name>}）
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドは 1.0 のあと
 * アクセサに置き換えられない。とくに {@code subsDbSourceMap} は
 * <b>中の {@code HashMap} がそのまま外に出ていた</b>ので、
 * アプリから {@code clear()} できた——プロセス全体のサブ接続が消える。
 * </p>
 *
 * <p>組み立てるのはこのパッケージの中（{@code DBUtil}）だけである。</p>
 */
public final class DBSource {

	/** DB ソース名 */
	private String name;

	/* SQL 結果キャッシュで使う名前（別の DB を指すサブなら「親.サブ」。null なら name） */
	private String cacheName;

	/** 親 DB ソース（サブのときだけ入る） */
	private DBSource parent = null;

	/** データソース */
	private DataSource dataSource;

	/** 接続設定 */
	private DBConf conf;

	/** 読み取り専用データソース */
	private DBSource readSource;

	/** サブデータソース */
	private final Map<String, DBSource> subsDbSourceMap = new HashMap<>();

	/* SQL の方言（要件 F-D-30） */
	private Dialect dialect = null;

	/**
	 * DB ソース名
	 *
	 * @return	名前
	 */
	public String name () {

		return name;

	}

	/**
	 * SQL 結果キャッシュで使う名前
	 *
	 * <p>
	 * トップレベルのデータソースは {@link #name()} と同じ。<b>サブは、指している DB で決める</b>（2.7.8）。
	 * 親と同じ URL なら親と同じ名前、別の DB なら「親.サブ」（同じ DB を指すサブ同士は同じ名前）。
	 * </p>
	 *
	 * <p>
	 * サブの {@link #name()} は親と同じなので、かつてはサブを別の DB に向けると、同じ SQL・同じ値のキャッシュが
	 * 親やほかのサブと混ざり、別の DB の結果が返っていた。一方、プールを分けるために親と同じ DB を指すサブは、
	 * 書いたら親のキャッシュも消えなければならない（同じ表）ので、名前を分けない。
	 * {@link #name()} の意味（マイグレーション・{@code DBVersion}・{@code DBUtil.getDataSource} の引き先）は変えず、
	 * キャッシュにだけ別の名前を使う。
	 * </p>
	 *
	 * @return	名前
	 * @since 2.7.8
	 */
	public String cacheName () {

		return cacheName == null ? name : cacheName;

	}

	/**
	 * SQL 結果キャッシュで使う名前 を決める
	 *
	 * @param cacheName	名前
	 */
	void cacheName (String cacheName) {

		this.cacheName = cacheName;

	}

	/**
	 * DB ソース名を決める
	 *
	 * @param name	名前
	 */
	void name (String name) {

		this.name = name;

	}

	/**
	 * 親 DB ソース
	 *
	 * @return	親（サブでなければ null）
	 */
	public DBSource parent () {

		return parent;

	}

	/**
	 * 親 DB ソースを決める
	 *
	 * @param parent	親
	 */
	void parent (DBSource parent) {

		this.parent = parent;

	}

	/**
	 * データソース
	 *
	 * @return	データソース
	 */
	public DataSource dataSource () {

		return dataSource;

	}

	/**
	 * データソースを決める
	 *
	 * @param dataSource	データソース
	 */
	void dataSource (DataSource dataSource) {

		this.dataSource = dataSource;

	}

	/**
	 * 接続設定
	 *
	 * @return	設定
	 */
	public DBConf conf () {

		return conf;

	}

	/**
	 * 接続設定を決める
	 *
	 * @param conf	設定
	 */
	void conf (DBConf conf) {

		this.conf = conf;

	}

	/**
	 * 読み取り専用データソース
	 *
	 * @return	読み取り専用（無ければ null）
	 */
	public DBSource readSource () {

		return readSource;

	}

	/**
	 * 読み取り専用データソースを決める
	 *
	 * @param readSource	読み取り専用
	 */
	void readSource (DBSource readSource) {

		this.readSource = readSource;

	}

	/**
	 * サブデータソースの名前
	 *
	 * <p>
	 * <b>中の {@code Map} は返さない。</b>返すと外から消せる——
	 * 消えたことに気づけるのは、次にそのサブを引いたときである。
	 * </p>
	 *
	 * @return	名前の一覧（読み取り専用）
	 */
	public java.util.Set<String> subDbSourceNames () {

		return java.util.Collections.unmodifiableSet(subsDbSourceMap.keySet());

	}

	/**
	 * サブデータソース（枠組みの中だけ）
	 *
	 * <p>中の {@code Map} をそのまま返すので、外へ出さないこと。</p>
	 *
	 * @return	サブ
	 */
	Map<String, DBSource> subsDbSourceMap () {

		return subsDbSourceMap;

	}

	/**
	 * サブデータソースを足す
	 *
	 * @param name		名前
	 * @param source	サブ
	 */
	void putSubDBSource (String name, DBSource source) {

		subsDbSourceMap.put(name, source);

	}

	/**
	 * SQL の方言
	 *
	 * <p>
	 * {@code db.<name>.product} で決まる（既定は mysql）。
	 * <b>{@link DB} がこれを SQL ビルダーに渡す</b>ので、
	 * アプリのコードは製品を知らなくてよい。
	 * </p>
	 *
	 * @return	方言
	 */
	public Dialect dialect () {

		Dialect current = dialect;

		if (current == null) {
			current = Dialects.of(conf == null ? null : conf.product());
			dialect = current;
		}

		return current;

	}

	/**
	 * 読み取り専用データソースを取得する
	 *
	 * @return	読み取り専用データソース
	 */
	public DataSource getReadDataSource() {

		if (readSource != null && readSource.dataSource() != null) {
			return readSource.dataSource();
		}

		return dataSource;

	}

	/**
	 * 読み書き可能データソースを取得する
	 *
	 * @return	読み書き可能データソース
	 */
	public DataSource getWriteDataSource() {

		return dataSource;

	}

	/**
	 * サブDBソースを取得する
	 *
	 * @param name	名前
	 * @return	サブDBソース
	 */
	public DBSource getSubDBSource (String name) {

		if (parent != null) {
			return parent.getSubDBSource(name);
		}

		return subsDbSourceMap.get(name);

	}

	/**
	 * 読み取り専用データソース存在判定
	 *
	 * @return	読み取り専用データソースが存在する場合 = true
	 */
	public boolean hasReadDataSource () {

		return readSource != null;

	}

}
