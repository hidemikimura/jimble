package io.jimble.util.paging;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

/**
 * ページング（要件 F-V-06）
 *
 * <pre>
 * paging {
 *   name_page = "page"   # ページ番号のリクエストパラメータ名
 *   name_per  = "per"    # 取得件数のリクエストパラメータ名
 *   max_per   = 200      # 1ページに出せる件数の上限（per=all も含む）
 * }
 * </pre>
 *
 * <h2>キーの名前が値に見えていた（要件 D-159）</h2>
 * <p>
 * <b>設定キーは {@code paging.page} / {@code paging.per} だった。</b>
 * 入るのは<b>リクエストパラメータの「名前」</b>なのに、
 * 読む人には<b>件数を入れる欄</b>に見える。
 * {@code paging.per = 50} と書くと<b>パラメータ名が {@code "50"} になり</b>、
 * {@code ?per=20} は読まれなくなって、件数は黙って既定の 10 に落ちた。
 * </p>
 *
 * <p>
 * <b>名前を入れる欄だと分かる名前にした</b>——{@code paging.name_page} /
 * {@code paging.name_per}。件数を変えたい人は {@code paging.max_per} を見る。
 * </p>
 */
public class Paging {

	/** 設定キー：ページ番号のリクエストキー名 */
	public static final String KEY_NAME_PAGE = "paging.name_page";

	/** 設定キー：取得件数のリクエストキー名 */
	public static final String KEY_NAME_PER = "paging.name_per";

	/**
	 * 設定キー：1ページに出せる件数の上限（要件 D-159）
	 *
	 * <p>
	 * <b>{@code per=all} にも効く。</b>上限が無いと、
	 * <b>誰でも {@code ?per=all} と打つだけで全件を引ける</b>——
	 * 100 万行のテーブルで1回やられれば、それだけで止まる。
	 * </p>
	 */
	public static final String KEY_MAX_PER = "paging.max_per";

	/** 既定のページ番号キー名 */
	public static final String DEFAULT_NAME_PAGE = "page";

	/** 既定の取得件数キー名 */
	public static final String DEFAULT_NAME_PER = "per";

	/** 既定の取得件数 */
	public static final long DEFAULT_PER = 10;

	/** 既定の件数上限 */
	public static final long DEFAULT_MAX_PER = 200;

	/**
	 * ページ番号のリクエストキー名（要件 F-V-06）
	 *
	 * <p>
	 * 移送元は {@code static final} で<b>クラス初期化のときに1回だけ</b>設定を読んでいた。
	 * 設定を読み込み直しても反映されず、Conf が未初期化の時点で
	 * このクラスに触れると落ちる。呼ばれるたびに読む。
	 * </p>
	 *
	 * @return	キー名
	 */
	public static String namePage () {

		return Conf.conf().getString(KEY_NAME_PAGE, DEFAULT_NAME_PAGE);

	}

	/**
	 * 取得件数のリクエストキー名（要件 F-V-06）
	 *
	 * @return	キー名
	 */
	public static String namePer () {

		return Conf.conf().getString(KEY_NAME_PER, DEFAULT_NAME_PER);

	}

	/**
	 * 1ページに出せる件数の上限（要件 D-159）
	 *
	 * @return	上限（0 以下なら上限なし）
	 */
	public static long maxPer () {

		return Conf.conf().getLong(KEY_MAX_PER, DEFAULT_MAX_PER);

	}

	/**
	 * ページング指定あり判定
	 *
	 * @param requestData	リクエスト情報
	 * @return	ページング指定ありの場合 = true
	 */
	public static boolean hasPaging (Data requestData) {

		if (!requestData.containsKey(namePage()) && !requestData.containsKey(namePer())) {
			return false;
		}

		String page = requestData.getString(namePage());
		String per = requestData.getString(namePer());
		if ((page == null || page.isEmpty())
			&& (per == null || per.isEmpty())) {
			return false;
		}

		boolean enablePage = page != null && !page.isEmpty();
		if (enablePage) {
			try {
				long longPage = Long.parseLong(page);
			} catch (Exception ex) {
				enablePage = false;
			}
		}

		boolean enablePer = per != null && !per.isEmpty();
		if (enablePer) {
			if (!"all".equalsIgnoreCase(per)) {
				try {
					long longPer = Long.parseLong(per);
				} catch (Exception ex) {
					enablePer = false;
				}
			}
		}

		return enablePage || enablePer;

	}

	// region ページ番号

	/* ページ番号 */
	private long page = 1;

	/**
	 * ページ番号
	 *
	 * @return  ページ番号
	 */
	public long page () {

		return this.page;

	}

	// endregion

	// region 取得件数

	/* 取得件数 */
	private long per = 10;

	/**
	 * 取得件数
	 *
	 * @return  取得件数
	 */
	public long per () {

		return this.per;

	}

	// endregion

	// region 全件取得

	/* 全件取得 */
	private boolean perAll = false;

	/**
	 * 全件取得
	 *
	 * @return	全件取得
	 */
	public boolean perAll () {

		return this.perAll;

	}

	// endregion

	// region 最終ページ番号

	/* 最終ページ番号 */
	private long maxPage = 0;

	/**
	 * 最終ページ番号
	 *
	 * @return  最終ページ番号
	 */
	public long maxPage () {

		return maxPage;

	}

	// endregion

	// region 総件数

	/* 総件数 */
	private long totalCount = 0;

	/**
	 * 総件数
	 *
	 * @return  総件数
	 */
	public long totalCount () {

		return this.totalCount;

	}

	// endregion

	// region データ開始位置

	/* データ開始位置 */
	private long start = 1;

	/**
	 * データ開始位置
	 *
	 * @return  データ開始位置
	 */
	public long start () {

		return start;

	}

	// endregion

	// region データ件数

	/* データ件数 */
	private long count = 0;

	/**
	 * データ件数
	 *
	 * @return  データ件数
	 */
	public long count () {

		return count;

	}

	// endregion

	/**
	 * データ件数を設定する
	 *
	 * @param count         取得件数
	 * @param totalCount    総件数
	 */
	public void set (long count, long totalCount) {

		if (perAll) {
			this.per = totalCount;
		}

		this.count = count;
		this.totalCount = totalCount;
		calc();

	}

	/**
	 * 計算
	 */
	private void calc () {

		// 最終ページ番号
		// per=all で0件の場合、set()でperに総件数0が入るためゼロ除算になる。全件取得は常に1ページ扱いとする
		if (per <= 0) {
			maxPage = 1;
			return;
		}
		maxPage = totalCount / per + (totalCount % per > 0 ? 1 : 0);
		if (maxPage <= 0) {
			maxPage = 1;
		}

	}

	/**
	 * リクエスト情報を読み込む
	 *
	 * @param requestData   リクエストデータ
	 * @param per           取得件数
	 */
	public void load (Data requestData, long per) {

		if ("all".equalsIgnoreCase(requestData.getString(namePer()))) {

			long max = maxPer();

			if (max > 0) {

				/*
				 * <b>{@code per=all} にも上限を掛ける</b>（要件 D-159）。
				 * 掛けないと、<b>誰でも {@code ?per=all} と打つだけで全件を引ける</b>。
				 * <b>断らずに上限まで返す</b>——断ると、
				 * これまで動いていた管理画面が 400 になる。
				 */
				load(requestData.getLong(namePage()), max);

				return;

			}

			// 全件取得
			this.perAll = true;
			this.page = 1;
			this.per = -1;
			this.start = 1;

		} else {

			long _per = requestData.getLong(namePer());
			if (_per <= 0) {
				_per = per;
			}
			if (_per <= 0) {
				_per = DEFAULT_PER;
			}

			load(requestData.getLong(namePage()), _per);

		}

	}

	/**
	 * ページ番号、取得件数を設定する
	 *
	 * @param page  ページ番号
	 * @param per   取得件数
	 */
	public void load (long page, long per) {

		this.perAll = false;

		// ページ
		if (page > 0) {
			this.page = page;
		}

		// 取得件数
		if (per > 0) {

			long max = maxPer();

			this.per = max > 0 && per > max ? max : per;

		}

		// データ開始位置
		start = (this.page - 1) * this.per + 1;

	}

}
