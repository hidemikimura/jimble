package io.jimble.web.session;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

/**
 * セッションの保存先（要件 F-S-01）
 *
 * <p>
 * 実装は4つ：{@link EmptySessionStore}（なし） / {@link DbSessionStore} /
 * {@link RedisSessionStore} / {@link CookieSessionStore}。
 * </p>
 *
 * <p>
 * <b>アプリ側のコードは保存先を知らない</b>（要件 F-S-10）。
 * {@code context.session().get/put/save} だけを使う。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li>引数を jooby の {@code AppContext} から {@link WebContext} にした</li>
 *   <li><b>やり取りを {@code Map<String,String>} から {@link Data} にした。</b>
 *       移送元は保存先の都合で使う {@code __update} フラグを
 *       <b>アプリから見えるセッションデータに混ぜていた</b>ので、
 *       「既存かどうか」は {@link SessionEntry} で返すようにした</li>
 * </ol>
 */
public interface SessionStore {

	/**
	 * 読み込む
	 *
	 * @param context	コンテキスト
	 * @return	セッション（無ければ空の新規）
	 */
	SessionEntry load (WebContext context);

	/**
	 * 保存する
	 *
	 * @param context	コンテキスト
	 * @param entry		セッション
	 */
	void save (WebContext context, SessionEntry entry);

	/**
	 * 最終アクセス日時だけ更新する
	 *
	 * <p>中身は変わっていないが、生存期間を延ばしたいとき。</p>
	 *
	 * @param context	コンテキスト
	 * @param entry		セッション
	 */
	void touch (WebContext context, SessionEntry entry);

	/**
	 * 破棄する
	 *
	 * @param context	コンテキスト
	 */
	void destroy (WebContext context);

}
