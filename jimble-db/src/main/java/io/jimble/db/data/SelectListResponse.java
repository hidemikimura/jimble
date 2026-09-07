package io.jimble.db.data;

import io.jimble.util.data.Data;
import io.jimble.util.paging.Paging;

import java.util.List;

/**
 * 件数付き複数件取得レスポンス
 */
public class SelectListResponse {

	/* 一覧 */
	public List<Data> list;

	/* 総件数 */
	public long rowCount;

	/* ページング */
	public Paging paging;

}
