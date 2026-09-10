package approval.forms;

import db.approval_forms_example.ApprovalFormsExample;
import db.approval_forms_example.table.request.Request;
import db.approval_forms_example.table.request_item.RequestItem;

import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.util.List;

/**
 * 保存したものを見る
 *
 * <p>
 * <b>生成した型付きアクセサ（{@link RequestData}）を実際に使うところ。</b>
 * 生成はされていたのに、いままでどのサンプルも使っていなかった。
 * </p>
 */
public final class ShowController {

	private ShowController () {
	}

	/**
	 * 1件返す
	 *
	 * @param context	コンテキスト
	 */
	static void show (WebContext context) {

		long id = context.request().bodyAll().getLong("id");

		Data row = ApprovalFormsExample.db().select(
			SQL.select()
				.from(Request.instance())
				.where(Request.id.eq(id)));

		if (row == null) {
			throw new HttpException(404, "申請がありません: " + id);
		}

		/*
		 * <b>結果はテーブル名でネストされている</b>（要件 F-D-02）ので、
		 * {@code getData} で1段はがしてから型付きアクセサに載せ替える。
		 * {@code extractTableData} ではない——あちらはネストしたまま返る。
		 */
		RequestData data = new RequestData();
		data.putAll(row.getData(Request.instance()));

		List<Data> items = ApprovalFormsExample.db().selectList(
			SQL.select()
				.from(RequestItem.instance())
				.where(RequestItem.request_id.eq(id))
				.orderBy(RequestItem.sort_no));

		context.response()
			.json("id", data.id())
			.json("kind", data.kind())
			.json("amount", data.amount())
			.json("status", data.status())
			.json("items", items);

	}

}
