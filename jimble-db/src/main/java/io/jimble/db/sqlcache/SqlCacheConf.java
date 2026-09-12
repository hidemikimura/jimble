package io.jimble.db.sqlcache;

import io.jimble.util.conf.Conf;

import java.time.Duration;

/**
 * SQL 結果キャッシュの設定（要件 F-D-28）
 *
 * <pre>
 * sql_cache {
 *   enabled = true       # 既定は false。書かないとキャッシュは動かない
 *   store   = "memory"   # memory | redis | db
 *   ttl     = 300        # 秒。0 で無期限
 *   max     = 10000      # memory のときだけ。持つ件数の上限
 * }
 * </pre>
 *
 * <h2>既定が false である理由</h2>
 * <p>
 * <b>切っているアプリに、更新のたびの後始末を払わせないため</b>である。
 * 有効だと、{@code selectCached} を1度も呼んでいなくても
 * すべての {@code insert} / {@code update} / {@code delete} で
 * 「どの行に当たるか」を組み立てる（WHERE を読み、テーブルのキーを引く）。
 * <b>使っていない機能の代金を全員が払う</b>形になる。
 * </p>
 *
 * <p>
 * 代わりに<b>「書いたのに効かない」を作らない</b>手当てを入れてある。
 * 起動時の構成ログに出し、切ったまま {@code selectCached} を呼んだら
 * <b>初回だけ警告を出す</b>（毎回は出さない）。
 * </p>
 */
public final class SqlCacheConf {

	/** 設定キー：置き場 */
	public static final String KEY_STORE = "sql_cache.store";

	/** 設定キー：有効か */
	public static final String KEY_ENABLED = "sql_cache.enabled";

	/** 設定キー：期限（秒） */
	public static final String KEY_TTL = "sql_cache.ttl";

	/** 設定キー：件数の上限（memory のみ） */
	public static final String KEY_MAX = "sql_cache.max";

	/** 置き場：メモリ */
	public static final String STORE_MEMORY = "memory";

	/** 置き場：Redis */
	public static final String STORE_REDIS = "redis";

	/** 置き場：DB */
	public static final String STORE_DB = "db";

	/** 既定の置き場 */
	public static final String DEFAULT_STORE = STORE_MEMORY;

	/** 既定の件数の上限 */
	public static final int DEFAULT_MAX = 10000;

	/** 既定の期限（秒） */
	public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

	/*
	 * 有効かどうかは<b>更新のたびに見る</b>ので、読み直しを最小にする。
	 *
	 * 設定を丸ごと読み直したか（Conf.reload / Conf.replace）は
	 * インスタンスが入れ替わったかで分かる。参照の比較1つで済む。
	 */
	private static volatile Conf enabledSource = null;

	/* 有効か（上のインスタンスに対する答え） */
	private static volatile boolean enabledValue = false;

	/**
	 * コンストラクタ
	 */
	private SqlCacheConf () {

	}

	/**
	 * 置き場
	 *
	 * @return	置き場
	 */
	public static String store () {

		String value = Conf.conf().getString(KEY_STORE, DEFAULT_STORE).trim();

		return value.isEmpty() ? DEFAULT_STORE : value;

	}

	/**
	 * 使うか
	 *
	 * <p>
	 * <b>既定は false。</b>書かないとキャッシュは動かず、
	 * 更新側の後始末も<b>1行も走らない</b>。
	 * </p>
	 *
	 * @return	使うなら true
	 */
	public static boolean enabled () {

		Conf current = Conf.conf();

		if (current != enabledSource) {
			enabledValue = current.getBoolean(KEY_ENABLED, false);
			enabledSource = current;
		}

		return enabledValue;

	}

	/**
	 * 期限
	 *
	 * <p>
	 * <b>更新があれば消えるので、期限は取りこぼしの保険である。</b>
	 * 既定を無期限にしないのは、消し漏れが<b>直るきっかけを持たない</b>ためである。
	 * </p>
	 *
	 * <p>取りこぼしうるところ：</p>
	 * <ul>
	 *   <li>別のプロセス・別のツールから DB を直接書き換えた</li>
	 *   <li>読み取り用レプリカから引いた値を入れた直後に、
	 *       他所の更新がすれ違った（{@code get} してから {@code put} するまでの隙間）</li>
	 * </ul>
	 *
	 * @return	期限。0 秒なら無期限
	 */
	public static Duration ttl () {

		return Conf.atLeast(Conf.conf().getDuration(KEY_TTL, DEFAULT_TTL), Duration.ZERO);

	}

	/**
	 * 件数の上限（memory のみ）
	 *
	 * @return	上限
	 */
	public static int max () {

		int value = Conf.conf().getInt(KEY_MAX, DEFAULT_MAX);

		return value <= 0 ? DEFAULT_MAX : value;

	}

}
