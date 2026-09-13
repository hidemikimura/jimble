package io.jimble.db.data;

import io.jimble.util.data.Data;
import io.jimble.util.paging.Paging;

import java.util.List;

/**
 * 件数付きの複数件取得の結果
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドは 1.0 のあと
 * アクセサに置き換えられないので、<b>検証も防御的コピーも入れられなくなる</b>。
 * 作るのは {@code DB} だけなので、コンストラクタは公開していない。
 * </p>
 */
public final class SelectListResponse {

	/** 一覧 */
	private final List<Data> list;

	/** 総件数 */
	private final long rowCount;

	/** ページング（渡されていなければ null） */
	private final Paging paging;

	/**
	 * 作る
	 *
	 * <p>
	 * <b>3つまとめて渡す。</b>1本ずつ埋める形（public フィールド）に戻さないこと——
	 * 途中まで埋めたものが外へ出る道ができる。
	 * </p>
	 *
	 * @param list		一覧
	 * @param rowCount	総件数
	 * @param paging	ページング（無ければ null）
	 */
	public SelectListResponse (List<Data> list, long rowCount, Paging paging) {

		this.list = list;
		this.rowCount = rowCount;
		this.paging = paging;

	}

	/**
	 * 一覧
	 *
	 * @return	一覧
	 */
	public List<Data> list () {

		return list;

	}

	/**
	 * 総件数
	 *
	 * @return	総件数
	 */
	public long rowCount () {

		return rowCount;

	}

	/**
	 * ページング
	 *
	 * @return	ページング（渡していなければ null）
	 */
	public Paging paging () {

		return paging;

	}

	@Override
	public String toString () {

		return "SelectListResponse(" + (list == null ? 0 : list.size()) + "件 / 全" + rowCount + "件)";

	}

}
