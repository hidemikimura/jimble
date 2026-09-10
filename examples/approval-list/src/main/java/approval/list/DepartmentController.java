package approval.list;

import db.approval_list_example.ApprovalListExample;
import db.approval_list_example.table.department.Department;

import io.jimble.core.context.ScopeCache;
import io.jimble.db.sql.SQL;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.util.List;

/**
 * 部署（SQL 結果キャッシュの題材。要件 F-D-28）
 *
 * <p>
 * <b>部署はめったに変わらないのに、一覧のたびに引かれる。</b>
 * こういうものがキャッシュの相手である。
 * </p>
 */
public final class DepartmentController {

	private DepartmentController () {
	}

	/**
	 * 一覧を返す（キャッシュに乗る）
	 *
	 * @param context	コンテキスト
	 */
	static void list (WebContext context) {

		context.response().json("departments", cached());

	}

	/**
	 * 名前を変える（要件 F-R-18）
	 *
	 * <p>
	 * <b>ここでキャッシュを消す処理は書かない。</b>
	 * 更新した側が勝手に消す（要件 F-D-28）。
	 * <b>「消し忘れると古いものが出続ける」形にしない</b>ためである。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void rename (WebContext context) {

		Data request = context.request().bodyAll();

		/*
		 * <b>パス変数を Column でそのまま読める</b>（要件 F-R-18 / F-W-03）。
		 * ルートを {@code /departments/{department.id}/rename} と組んであるので、
		 * {@code .} でネストされて {@code {department: {id: "3"}}} になっている
		 */
		long id = request.getLong(Department.id);

		String name = request.getString("name");

		if (name == null || name.isEmpty()) {
			throw new HttpException(400, "名前を入れてください");
		}

		int updated = ApprovalListExample.db().update(
			SQL.update(Department.instance())
				.set(Department.name, name)
				.where(Department.id.eq(id)));

		if (updated == 0) {
			throw new HttpException(404, "部署がありません: " + id);
		}

		context.response().json("id", id).json("name", name);

	}

	/**
	 * 部署の一覧（キャッシュ2段）
	 *
	 * <p>
	 * <b>2段ある。</b>
	 * </p>
	 * <ul>
	 *   <li><b>{@link ScopeCache}（要件 F-W-15）</b>——
	 *       <b>この1リクエストの中だけ</b>。同じリクエストで2回呼んでも SQL は1回</li>
	 *   <li><b>{@code selectListCached}（要件 F-D-28）</b>——
	 *       <b>リクエストをまたいで</b>残る。更新すると勝手に消える</li>
	 * </ul>
	 *
	 * <p>
	 * 上の段が要るのは、<b>下の段でも「キャッシュを引く費用」はかかる</b>ためである
	 * （鍵を作って、保管庫を引いて、と毎回やる）。
	 * </p>
	 *
	 * @return	部署
	 */
	static List<Data> cached () {

		return ScopeCache.current().get("departments", () ->
			ApprovalListExample.db().selectListCached(
				SQL.select()
					.from(Department.instance())
					.orderBy(Department.id)));

	}

}
