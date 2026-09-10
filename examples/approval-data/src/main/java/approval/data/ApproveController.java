package approval.data;

import db.approval_data_audit_example.ApprovalDataAuditExample;
import db.approval_data_audit_example.table.audit_log.AuditLog;
import db.approval_data_example.ApprovalDataExample;
import db.approval_data_example.table.notice.Notice;
import db.approval_data_example.table.request.Request;

import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.util.ArrayList;
import java.util.List;

/**
 * 承認と一括登録
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * 承認は<b>2つのテーブルにまたがる</b>——申請の状態を変え、通知を1件作る。
 * <b>片方だけ通ってしまう</b>のを防ぐのがトランザクションである。
 * </p>
 */
public final class ApproveController {

	private ApproveController () {
	}

	// region 承認（要件 F-D-15 / F-D-16）

	/**
	 * 承認する
	 *
	 * @param context	コンテキスト
	 */
	static void approve (WebContext context) {

		long id = context.request().bodyAll().getLong(Request.id);
		long by = context.request().bodyAll().getLong("by");

		if (by <= 0) {
			throw new HttpException(400, "by（承認する人）を入れてください");
		}

		DB db = ApprovalDataExample.db();

		try (DBTransaction transaction = new DBTransaction(db)) {

			/*
			 * <b>先にトランザクションを始める。</b>
			 *
			 * forUpdate() は書込接続を自分で取らない。
			 * 参照用の接続（read）を設定していると、
			 * 始める前に引いた forUpdate は<b>レプリカでロックを取る</b>ことになる——
			 * それは誰も止めない。
			 */
			transaction.beginTransaction();

			Data request = db.select(SQL.select()
				.from(Request.instance())
				.where(Request.id.eq(id))
				.forUpdate());

			if (request == null) {
				transaction.rollbackEndTransaction();
				throw new HttpException(404, "申請がありません: " + id);
			}

			if (!"pending".equals(request.getString(Request.status))) {
				transaction.rollbackEndTransaction();
				throw new HttpException(409, "もう決まっています: " + request.getString(Request.status));
			}

			db.update(SQL.update(Request.instance())
				.set(Request.status, "approved")
				.set(Request.decided_by, by)
				.set(Request.decided_at, Dsl.now())
				.where(Request.id.eq(id)));

			if (db.isError()) {
				transaction.rollbackEndTransaction();
				throw new HttpException(500, "更新できませんでした");
			}

			db.insert(SQL.insert(Notice.instance())
				.value(Notice.request_id, id)
				.value(Notice.to_staff_id, request.getLong(Request.staff_id))
				.value(Notice.kind, "approved")
				.value(Notice.created_at, Dsl.now()));

			if (db.isError()) {
				transaction.rollbackEndTransaction();
				throw new HttpException(500, "通知を作れませんでした");
			}

			transaction.commitEndTransaction();

		} catch (HttpException ex) {

			throw ex;

		} catch (Exception ex) {

			Log.error(ex, "承認に失敗しました: id=%d".formatted(id));
			throw new HttpException(500, "承認に失敗しました");

		}

		/*
		 * 監査ログは<b>別のデータベース</b>である（要件 F-D-14）。
		 *
		 * <b>上のトランザクションには入らない。</b>別の接続だからである。
		 * だから承認が確定したあとに書く——先に書くと、
		 * 承認が戻ったのに「承認した」というログだけが残る。
		 *
		 * 逆に、ここで落ちると<b>承認は済んでいるのにログが無い</b>。
		 * 2つの DB にまたがる以上どちらかになるので、
		 * <b>どちらが困らないほうかを決めて書く</b>しかない。
		 * jimble は分散トランザクションをやらない。
		 */
		audit(by, "approve", "request:" + id);

		context.response().json("id", id).json("status", "approved");

	}

	/**
	 * 監査ログを書く（別のデータベース）
	 *
	 * @param staffId	だれが
	 * @param action	何を
	 * @param target	どれに
	 */
	static void audit (long staffId, String action, String target) {

		DB db = ApprovalDataAuditExample.db();

		db.insert(SQL.insert(AuditLog.instance())
			.value(AuditLog.staff_id, staffId)
			.value(AuditLog.action, action)
			.value(AuditLog.target, target)
			.value(AuditLog.created_at, Dsl.now()));

		if (db.isError()) {
			// 監査が書けなくても業務は止めない。ただし黙らない
			Log.error("監査ログを書けませんでした: %s / %s".formatted(action, String.valueOf(db.getError())));
		}

	}

	/**
	 * 監査ログを読む
	 *
	 * @param context	コンテキスト
	 */
	static void auditList (WebContext context) {

		List<Data> rows = ApprovalDataAuditExample.db().selectList(SQL.select()
			.from(AuditLog.instance())
			.orderByDesc(AuditLog.id)
			.limit(50));

		context.response().json("audit", rows);

	}

	// endregion

	// region 一括登録（要件 F-D-08）

	/**
	 * まとめて登録する
	 *
	 * <p>
	 * <b>SQL は全部同じでなければならない。</b>1本の文にパラメータだけを積み替えるためである。
	 * だから <b>{@code value()} を積む順もループの中で揃える</b>。
	 * 揃っていなければ {@code DB_998} を立てて null が返る
	 * （直すまでは、例外も警告も無しに値が横にずれて入っていた）。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void importRequests (WebContext context) {

		int count = context.request().bodyAll().getInt("count");

		if (count <= 0 || count > 1000) {
			throw new HttpException(400, "count は 1〜1000 です");
		}

		List<io.jimble.db.sql.InsertBuilder> builders = new ArrayList<>();

		for (int i = 1; i <= count; i++) {

			// 順を揃える。ここを崩すと SQL が変わる
			builders.add(SQL.insert(Request.instance())
				.value(Request.staff_id, 1L)
				.value(Request.amount, (long) (i * 100))
				.value(Request.status, "pending")
				.value(Request.created_at, Dsl.now()));

		}

		DB db = ApprovalDataExample.db();

		List<Long> ids = db.insertBatch(builders);

		if (ids == null) {
			throw new HttpException(500, "一括登録に失敗しました: " + db.getError());
		}

		/*
		 * <b>戻るのは件数ではなく採番値である。</b>
		 * executeBatch は件数（List&lt;Integer&gt;）を返すので、そちらとは型が違う。
		 */
		context.response().json("inserted", ids.size()).json("first_id", ids.getFirst());

	}

	// endregion

}
