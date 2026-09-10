package approval.data;

import db.approval_data_example.ApprovalDataExample;
import db.approval_data_example.table.rate.Rate;

import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.ICache;
import io.jimble.db.lock.DBLock;
import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.value.DBValue;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;

import java.util.List;

/**
 * レート（キャッシュ・ロック・DBValue の題材）
 *
 * <h2>ここで見せたいこと</h2>
 * <p>
 * <b>置き場を変えてもコードは変わらない。</b>
 * {@code cache.type} を {@code db} / {@code memory} / {@code redis} のどれにしても、
 * ここに書いてあることは1行も変わらない。
 * </p>
 * <p>
 * <b>Redis を書かなくても全部動く</b>（要件 F-U-10）。既定は DB キャッシュ、
 * ロックも DB のものが使える。Redis は<b>要るときに足す</b>もので、前提ではない。
 * </p>
 */
public final class RateController {

	/** DBValue のキー：最後に取り込んだ日 */
	public static final String KEY_IMPORTED_ON = "rate.imported_on";

	/** ロックのキー */
	private static final String LOCK_KEY = "rate_refresh";

	/** キャッシュのまとまり（これ単位で捨てられる） */
	private static final String CACHE_GROUP = "rate";

	private RateController () {
	}

	// region 読む（要件 F-U-07）

	/**
	 * 一覧を返す
	 *
	 * <p>
	 * <b>インターフェース（{@link ICache}）で受ける。</b>
	 * 実装（`DBCache` / `MemoryCache` / `RedisCache`）を名指しで書くと、
	 * <b>設定を変えたときにここだけ付いてこない</b>。
	 * </p>
	 *
	 * @param context	コンテキスト
	 */
	static void list (WebContext context) {

		DB db = ApprovalDataExample.db();

		ICache cache = Cache.instance(db);

		String cached = cache.getString("all", CACHE_GROUP);

		if (cached != null && !cached.isEmpty()) {

			/*
			 * <b>キャッシュに入れるのは JSON の文字列である。</b>
			 *
			 * Data.toString() を入れてはいけない——あれは中身を確かめるための表示で、
			 * "Data(1件) {rates=ArrayList(3件)}" のような<b>読めるが復元できない文字列</b>になる。
			 * 入れた側は気づかず、<b>キャッシュに当たったときだけ壊れた応答が返る</b>
			 * （これを実際に踏んだ）。
			 */
			context.response().json("rates", Dson.decodes(cached)).json("from", "cache");
			return;

		}

		List<Data> rates = db.selectList(SQL.select().from(Rate.instance()).orderBy(Rate.id));

		cache.set("all", Dson.encodes(rates), "application/json", CACHE_GROUP);

		context.response().json("rates", rates).json("from", "db");

	}

	// endregion

	// region 書く（要件 F-U-08 / F-U-09）

	/**
	 * レートを1つ入れ替える
	 *
	 * <p>
	 * <b>まとまりごと捨てる</b>（要件 F-U-08）。1件だけ消すより、
	 * <b>消し忘れて古いものが出続ける</b>ほうが怖い。
	 * </p>
	 *
	 * <p>
	 * <b>ロックを取ってから触る</b>（要件 F-U-09）。
	 * ここでは {@link DBLock} を使う——<b>Redis が要らない</b>ためである。
	 * </p>
	 * <ul>
	 *   <li>{@code DBLock} … <b>トランザクションの中だけ</b>。終われば勝手に解ける。
	 *       ただし<b>先に {@code create()} で行を作っておく</b>必要がある</li>
	 *   <li>{@code RedisLock} … Redis が要る。{@code close()} か保持時間で解ける。
	 *       <b>設定していないと例外</b>になる（「取れなかった」と混ぜないため）</li>
	 * </ul>
	 *
	 * @param context	コンテキスト
	 */
	static void refresh (WebContext context) {

		Data body = context.request().bodyAll();

		String code = body.getString("code");
		long value = body.getLong("value");

		if (code == null || code.isEmpty() || value <= 0) {
			throw new HttpException(400, "code と value を入れてください");
		}

		DB db = ApprovalDataExample.db();

		// ロックの行を作っておく。無いと lock() が取れない
		DBLock.create(db, LOCK_KEY);

		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			if (!DBLock.lock(db, LOCK_KEY)) {
				transaction.rollbackEndTransaction();
				throw new HttpException(409, "ほかで書き換え中です");
			}

			int updated = db.update(SQL.update(Rate.instance())
				.set(Rate.value, value)
				.set(Rate.updated_at, Dsl.now())
				.where(Rate.code.eq(code)));

			if (updated == 0) {
				transaction.rollbackEndTransaction();
				throw new HttpException(404, "そのレートはありません: " + code);
			}

			transaction.commitEndTransaction();

		} catch (HttpException ex) {

			throw ex;

		} catch (Exception ex) {

			Log.error(ex, "レートを書き換えられませんでした: %s".formatted(code));
			throw new HttpException(500, "書き換えられませんでした");

		}

		/*
		 * <b>確定したあとで捨てる。</b>
		 *
		 * 先に捨てると、捨ててからコミットまでの間に誰かが読んで、
		 * <b>古い値をキャッシュに入れ直す</b>。
		 */
		Cache.instance(ApprovalDataExample.db()).removeGroup(CACHE_GROUP);

		context.response().json("code", code).json("value", value);

	}

	// endregion

	// region DBValue（要件 F-Y-15）

	/**
	 * 取り込んだ日を読み書きする
	 *
	 * <p>
	 * <b>設定ファイルに置けないもの</b>のための小さな置き場である。
	 * 「最後に取り込んだ日」のように、<b>動いている最中に変わる</b>ものを1つだけ持つ。
	 * </p>
	 *
	 * <p>
	 * 罠が3つある。
	 * </p>
	 * <ul>
	 *   <li><b>読むだけのつもりで INSERT が飛ぶ。</b>無ければ既定値を書き込む</li>
	 *   <li><b>250 文字まで。</b>消す口も無い</li>
	 *   <li><b>プロセスの中のキャッシュは DB 名を持たない。</b>
	 *       DB を2つ使うこのサンプルでは、<b>同じキーを両方で使うと値が混ざる</b>。
	 *       だからキーの頭を分けてある</li>
	 * </ul>
	 *
	 * @param context	コンテキスト
	 */
	static void importedOn (WebContext context) {

		DB db = ApprovalDataExample.db();

		if ("POST".equals(context.request().method())) {

			String on = context.request().bodyAll().getString("on");

			if (on == null || on.isEmpty()) {
				throw new HttpException(400, "on を入れてください");
			}

			DBValue.set(db, KEY_IMPORTED_ON, on);

			context.response().json("imported_on", on);
			return;

		}

		// 無ければ "-" を書き込んだうえで "-" が返る
		context.response().json("imported_on", DBValue.getString(db, KEY_IMPORTED_ON, "-"));

	}

	// endregion

}
