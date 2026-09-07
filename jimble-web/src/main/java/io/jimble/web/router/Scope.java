package io.jimble.web.router;

import java.util.ArrayList;
import java.util.List;

/**
 * フックのスコープ（要件 F-R-10 / D-69）
 *
 * <p>
 * {@code before} / {@code after} / {@code error} は<b>書いた場所に付く</b>。
 * パスのノードには付かない。
 * </p>
 *
 * <pre>
 * path("/admin", () -&gt; {
 *     before(AdminController::requireAuth);   // ← このブロックの中だけ
 *     get("/users", ...);                     // ← 効く
 * });
 *
 * path("/admin", () -&gt; {
 *     get("/login", ...);                     // ← 効かない（別のブロック）
 * });
 * </pre>
 *
 * <p>
 * スコープの親子は<b>コードのネスト</b>そのものである。
 * {@code path(...)} が子を作り、{@code install(...)} が子コントローラの
 * スコープを自分の下にぶら下げる（だからアプリ全体の {@code before} は
 * install した先にも効く）。
 * </p>
 *
 * <h2>なぜパスのノードをやめたか</h2>
 * <p>
 * 「同じパスに後から誰かが足したルート」まで守られる形は、
 * <b>コードを読んでも効いているかどうか分からない。</b>
 * 実際、移送した RSS アプリでは {@code AdminController} の認証フックが
 * 別に登録した SPA のログイン画面まで捕まえていた。
 * </p>
 *
 * <p>
 * 速さの面でも、パスのノードに付ける形は<b>リクエストのたびに親を辿って集め直す</b>
 * 必要があった。書いた場所に付くなら、ルートごとのフック列は起動時に決まる。
 * </p>
 */
final class Scope {

	/* 親（一番外側は null） */
	private Scope parent;

	/* 子（確定を配るために持つ） */
	private final List<Scope> children = new ArrayList<>();

	/* before（登録順） */
	private final List<Handler> befores = new ArrayList<>();

	/* after（登録順） */
	private final List<Handler> afters = new ArrayList<>();

	/* error（登録順） */
	private final List<ErrorHandler> errors = new ArrayList<>();

	/* 流量制限（このブロックに書かれたもの。要件 F-R-22） */
	private io.jimble.web.ratelimit.RateLimit rateLimit;

	/* 確定したか */
	private boolean sealed;

	/**
	 * コンストラクタ（一番外側）
	 */
	Scope () {

	}

	/**
	 * コンストラクタ
	 *
	 * @param parent	親
	 */
	private Scope (Scope parent) {

		this.parent = parent;
		parent.children.add(this);

	}


	// region 登録

	/**
	 * 子スコープを作る
	 *
	 * @return	子スコープ
	 */
	Scope child () {

		checkOpen("スコープ");

		return new Scope(this);

	}

	/**
	 * before を追加する
	 *
	 * @param handler	処理
	 */
	void before (Handler handler) {

		checkOpen("before");
		befores.add(handler);

	}

	/**
	 * after を追加する
	 *
	 * @param handler	処理
	 */
	void after (Handler handler) {

		checkOpen("after");
		afters.add(handler);

	}

	/**
	 * error を追加する
	 *
	 * @param handler	処理
	 */
	void error (ErrorHandler handler) {

		checkOpen("error");
		errors.add(handler);

	}

	/**
	 * 流量制限を設定する（要件 F-R-15 / F-R-22）
	 *
	 * <p>
	 * <b>このブロックの中のルート全部</b>にかかる。
	 * ルートが自分で持っていればそちらが勝ち、
	 * 入れ子になっていれば<b>内側が勝つ</b>。
	 * </p>
	 *
	 * @param value	宣言
	 */
	void rateLimit (io.jimble.web.ratelimit.RateLimit value) {

		checkOpen("rateLimit");
		rateLimit = value;

	}

	/**
	 * 効く流量制限を探す（内側 → 外側）
	 *
	 * @return	宣言。無ければ null
	 */
	io.jimble.web.ratelimit.RateLimit resolveRateLimit () {

		for (Scope scope = this; scope != null; scope = scope.parent) {

			if (scope.rateLimit != null) {
				return scope.rateLimit;
			}

		}

		return null;

	}

	/**
	 * 別のスコープを自分の下に取り込む（{@code install}）
	 *
	 * @param source	取り込むスコープ
	 */
	void adopt (Scope source) {

		checkOpen("install");
		source.checkOpen("install");

		if (source.parent != null) {
			throw new IllegalStateException(
				"このコントローラは既に install されています。"
					+ "同じインスタンスを2か所に install することはできません");
		}

		source.parent = this;
		children.add(source);

	}

	// endregion


	// region 確定

	/**
	 * 確定したか
	 *
	 * @return	確定していれば true
	 */
	boolean isSealed () {

		return sealed;

	}

	/**
	 * 一番外側のスコープ
	 *
	 * @return	一番外側のスコープ
	 */
	Scope root () {

		return parent == null ? this : parent.root();

	}

	/**
	 * 自分と子孫を確定する
	 *
	 * <p>
	 * 確定した後にフックを足そうとしたら落とす。
	 * <b>「足したのに効かない」を黙って通さない</b>ためである。
	 * </p>
	 */
	void seal () {

		sealed = true;

		for (Scope child : children) {
			child.seal();
		}

	}

	/**
	 * before を組み立てる（外側 → 内側）
	 *
	 * @return	before
	 */
	List<Handler> resolveBefores () {

		List<Handler> result = new ArrayList<>();
		collectBefores(result);
		return List.copyOf(result);

	}

	/**
	 * after を組み立てる（内側 → 外側）
	 *
	 * @return	after
	 */
	List<Handler> resolveAfters () {

		List<Handler> result = new ArrayList<>();

		for (Scope scope = this; scope != null; scope = scope.parent) {
			result.addAll(scope.afters);
		}

		return List.copyOf(result);

	}

	/**
	 * error を組み立てる（内側 → 外側）
	 *
	 * @return	error
	 */
	List<ErrorHandler> resolveErrors () {

		List<ErrorHandler> result = new ArrayList<>();

		for (Scope scope = this; scope != null; scope = scope.parent) {
			result.addAll(scope.errors);
		}

		return List.copyOf(result);

	}

	/**
	 * このスコープ自身の error
	 *
	 * <p>
	 * 未マッチ（404）のときに使う。ルートが決まっていないので
	 * どのスコープにいるかも決まらないが、<b>一番外側の error だけは適用する</b>
	 * （アプリ全体の 404 ページ用。要件 F-R-09b）。
	 * </p>
	 *
	 * @return	error
	 */
	List<ErrorHandler> ownErrors () {

		return List.copyOf(errors);

	}

	// endregion


	/**
	 * まだ足せるか確かめる
	 *
	 * @param what	足そうとしたもの
	 */
	private void checkOpen (String what) {

		if (sealed) {
			throw new IllegalStateException(
				"""
				ルートの確定後に %s を追加することはできません。
				  フックは起動時（最初のマッチ）に確定します。
				  ルート定義はコントローラの初期化ブロックの中で完結させてください。
				""".formatted(what));
		}

	}

	/**
	 * before を集める（外側から）
	 *
	 * @param result	結果
	 */
	private void collectBefores (List<Handler> result) {

		if (parent != null) {
			parent.collectBefores(result);
		}

		result.addAll(befores);

	}

}
