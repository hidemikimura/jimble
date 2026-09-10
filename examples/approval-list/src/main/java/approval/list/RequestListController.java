package approval.list;

import db.approval_list_example.ApprovalListExample;
import db.approval_list_example.table.department.Department;
import db.approval_list_example.table.request.Request;
import db.approval_list_example.table.staff.Staff;

import io.jimble.db.data.ResultSetFetcher;
import io.jimble.db.data.SelectListResponse;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.query.select.SelectQuery;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.util.csv.CsvWriter;
import io.jimble.util.data.Data;
import io.jimble.util.paging.Paging;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 申請の一覧・集計・書き出し
 *
 * <p>
 * <b>N+1 を作らない。</b>申請を引いてから社員を1件ずつ引く、をやらない。
 * </p>
 */
public final class RequestListController {

	/** CSV に出す上限 */
	private static final int CSV_LIMIT = 10_000;

	/** 1ページの既定 */
	private static final int PER = 10;

	/** 状態：承認済み */
	private static final String STATUS_APPROVED = "approved";

	/** 「大」とみなす合計（円） */
	private static final long SIZE_LARGE = 500_000;

	/** 「中」とみなす合計（円） */
	private static final long SIZE_MEDIUM = 200_000;

	private RequestListController () {
	}

	// region 一覧

	/**
	 * 一覧を返す（要件 F-V-05 / F-D-05）
	 *
	 * @param context	コンテキスト
	 */
	static void list (WebContext context) {

		Data request = context.request().bodyAll();

		Paging paging = context.request().paging(PER);

		SelectBuilder builder = joined()
			.orderByDesc(Request.created_at)
			.orderByDesc(Request.id)
			.paging(paging);

		conditions(request).forEach(builder::where);

		/*
		 * <b>selectList ではなく selectListWithRowCount。</b>
		 * selectList だと総件数が 0 のままなので、<b>最終ページが分からない</b>
		 * （画面には「1/0 ページ」と出る）。
		 */
		SelectListResponse response = ApprovalListExample.db().selectListWithRowCount(builder);

		context.response()
			.json("items", flatten(response.list))
			.json("page", paging.page())
			.json("per", paging.per())
			.json("total", paging.totalCount())
			.json("max_page", paging.maxPage());

	}

	/**
	 * 申請・社員・部署を1本で引く
	 *
	 * @return	組み立て途中のもの
	 */
	private static SelectBuilder joined () {

		return SQL.select()
			.from(Request.instance())
			.inner(Staff.instance()).on(Request.staff_id.eq(Staff.id))
			.inner(Department.instance()).on(Staff.department_id.eq(Department.id));

	}

	/**
	 * 絞り込みを組む
	 *
	 * <p>
	 * <b>空の {@code in} を作らない</b>（要件 F-D-07）。
	 * {@code in(空)} は「どれにも当たらない」なので、フレームワークは
	 * <b>SQL を組み立てた時点で例外にする</b>——
	 * 黙って条件ごと外すと<b>全件が返る</b>ので、取り違えると事故になる。
	 * だから<b>呼ぶ側が「空なら積まない」と書く</b>。
	 * </p>
	 *
	 * @param request	リクエスト
	 * @return	条件
	 */
	private static List<IWhere> conditions (Data request) {

		List<IWhere> conditions = new ArrayList<>();

		List<Object> statuses = request.getObjectListOptional("status", Object.class);

		if (!statuses.isEmpty()) {
			conditions.add(Request.status.in(statuses));
		}

		long departmentId = request.getLong("department_id");

		if (departmentId > 0) {
			conditions.add(Department.id.eq(departmentId));
		}

		String kind = request.getString("kind");

		if (kind != null && !kind.isEmpty()) {
			conditions.add(Request.kind.eq(kind));
		}

		long minAmount = request.getLong("min_amount");

		if (minAmount > 0) {
			conditions.add(Request.amount.ge(minAmount));
		}

		return conditions;

	}

	/**
	 * テーブル名のネストを外す（要件 F-D-02 / F-A-11）
	 *
	 * <p>
	 * JOIN の結果は {@code {request: {...}, staff: {...}, department: {...}}} で返る。
	 * <b>そのまま返してもよい</b>が、一覧の JSON としては平らなほうが扱いやすいので、
	 * ここでは列名の頭にテーブル名を付けて並べ直している。
	 * </p>
	 *
	 * @param rows	行
	 * @return	平らにしたもの
	 */
	private static List<Data> flatten (List<Data> rows) {

		List<Data> items = new ArrayList<>(rows.size());

		for (Data row : rows) {

			Data item = new Data();

			item.put("id", row.getData(Request.instance()).getLong("id"));
			item.put("kind", row.getData(Request.instance()).getString("kind"));
			item.put("amount", row.getData(Request.instance()).getLong("amount"));
			item.put("status", row.getData(Request.instance()).getString("status"));
			item.put("staff_name", row.getData(Staff.instance()).getString("name"));
			item.put("department_name", row.getData(Department.instance()).getString("name"));

			items.add(item);

		}

		return items;

	}

	// endregion

	// region 集計

	/**
	 * 部署ごとにまとめる（要件 F-D-05 / F-D-09）
	 *
	 * <p>
	 * <b>ページングは付けない。</b>集計とページングを重ねると、
	 * 総件数を数える SQL が<b>集計を包んだ形</b>になる。
	 * <b>この組み合わせは jimble にテストが無い</b>ので、サンプルでは踏まない。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void summary (WebContext context) {

		List<Data> rows = ApprovalListExample.db().selectList(
			SQL.select(
					Department.name.as("department")
					, Dsl.count(Request.id).as("count")
					, Dsl.sum(Request.amount).as("total")
					, Dsl.max(Request.amount).as("max")

					/*
					 * <b>条件付きの集計。</b>{@code SUM(CASE WHEN … THEN … ELSE 0 END)} で、
					 * 「承認されたぶんだけ」を1本の SQL で出す。
					 *
					 * <b>CASE は {@link SelectQuery} に包む。</b>
					 * {@code Dsl.sum(Dsl.caseWhen()...)} と直接渡すと型が合わない——
					 * {@code caseWhen()} が返すのは式（{@code Case}）で、
					 * {@code sum} が要るのは<b>選択できるもの</b>（{@code ISelect}）である
					 */
					, Dsl.sum(new SelectQuery().dsl(Dsl.caseWhen()
						.when(Request.status.eq(STATUS_APPROVED)).then(Request.amount)
						.elseCase(0L))).as("approved_total")

					/*
					 * <b>集計した値そのものを条件にできる</b>（D-135）。
					 * 前はこれが書けず、区分けを Java 側で付けていた——
					 * {@code Dsl.sum(...)} に比較が生えていなかったためである
					 * （無理に組むと {@code HAVING ( >= ?)} という壊れた SQL になった）。
					 */
					, new SelectQuery().dsl(Dsl.caseWhen()
						.when(Dsl.sum(Request.amount).ge(SIZE_LARGE)).then("大")
						.when(Dsl.sum(Request.amount).ge(SIZE_MEDIUM)).then("中")
						.elseCase("小")).as("size"))

				.from(Request.instance())
				.inner(Staff.instance()).on(Request.staff_id.eq(Staff.id))
				.inner(Department.instance()).on(Staff.department_id.eq(Department.id))
				.groupBy(Department.id, Department.name)
				.orderByDesc(Dsl.sum(Request.amount)));

		context.response().json("departments", rows);

	}

	// endregion

	// region 書き出し

	/**
	 * CSV に書き出す（要件 F-D-12 / F-Y-08）
	 *
	 * <p>
	 * <b>全件をメモリに載せない。</b>1行ずつ読んで1行ずつ書く。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void exportCsv (WebContext context) {

		/*
		 * <b>本文を書き始める前にヘッダを決める。</b>
		 * 書き出したあとで付けても、もう送ってしまっている
		 */
		context.response().setResponseHeader("Content-Type", "text/csv; charset=UTF-8");
		context.response().setResponseHeader("Content-Disposition", "attachment; filename=\"requests.csv\"");

		try (ResultSetFetcher fetcher = new ResultSetFetcher();
			OutputStream out = context.response().outputStream();
			OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
			CsvWriter csv = new CsvWriter(writer)) {

			ApprovalListExample.db().selectListWithFetcher(fetcher
				, joined().orderBy(Request.id).limit(CSV_LIMIT));

			csv.writeLine("id", "部署", "社員", "種別", "金額", "状態");

			for (Data row : fetcher) {

				csv.writeLine(
					row.getData(Request.instance()).getLong("id")
					, row.getData(Department.instance()).getString("name")
					, row.getData(Staff.instance()).getString("name")
					, row.getData(Request.instance()).getString("kind")
					, row.getData(Request.instance()).getLong("amount")
					, row.getData(Request.instance()).getString("status"));

			}

		} catch (Exception ex) {
			throw new HttpException(500, "CSV を書き出せませんでした", ex);
		}

	}

	// endregion

}
