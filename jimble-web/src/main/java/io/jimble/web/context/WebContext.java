package io.jimble.web.context;

import io.jimble.core.context.Context;
import io.jimble.db.DBSticky;
import io.jimble.db.DBUtil;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.metrics.Metrics;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.ServerConf;
import io.jimble.web.cookie.Cookies;
import io.jimble.web.flash.Flash;
import io.jimble.web.http.RequestSource;
import io.jimble.web.http.ResponseSink;
import io.jimble.web.request.Request;
import io.jimble.web.response.Response;
import io.jimble.web.router.RouteMatch;
import io.jimble.web.session.Session;
import io.jimble.web.session.SessionConf;
import io.jimble.web.session.SessionStore;
import io.jimble.web.session.SessionStores;

import java.util.Objects;

/**
 * Webリクエストのコンテキスト
 *
 * <p>
 * HTTP サーバー実装（helidon）の型はここに漏らさない。{@link Request} / {@link Response}
 * の実装が包む。
 * </p>
 *
 * <p>
 * Cookie と Flash はここから取る。<b>どちらも遅延生成</b>で、
 * 使わないリクエストでは何も作らない（要件 F-S-12）。
 * </p>
 */
public final class WebContext extends Context<WebContext> {

	/* リクエスト */
	private final Request request;

	/* レスポンス */
	private final Response response;

	/* マッチ結果 */
	private RouteMatch route;

	/* Cookie（遅延生成） */
	private Cookies cookies;

	/* Flash（遅延生成） */
	private Flash flash;

	/* セッション（遅延生成） */
	private Session session;

	/* セッションの保存先（リクエスト単位で差し替えられる。要件 F-S-11） */
	private SessionStore sessionStore;

	/* 入力口（Cookie の遅延生成に要る） */
	private final RequestSource source;

	/* 書き込み直後の参照先（遅延生成。要件 F-D-19） */
	private DBSticky dbSticky;

	/* 内部呼び出しの外側（要件 F-W-27）。通常のリクエストでは null */
	private final WebContext outer;

	/* このリクエストを捌いているディスパッチャ */
	private Dispatcher dispatcher;

	/**
	 * コンストラクタ
	 *
	 * @param source	入力口
	 * @param sink		出力口
	 */
	public WebContext (RequestSource source, ResponseSink sink) {

		this(source, sink, null);

	}

	/**
	 * コンストラクタ
	 *
	 * @param source	入力口
	 * @param sink		出力口
	 * @param outer		内部呼び出しの外側。通常のリクエストでは null
	 */
	private WebContext (RequestSource source, ResponseSink sink, WebContext outer) {

		super(outer);

		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(sink, "sink");

		this.outer = outer;
		this.source = source;
		this.request = new Request(this, source);
		this.response = new Response(this, this.request, sink);

		if (outer != null) {
			this.dispatcher = outer.dispatcher;
		}

	}

	/**
	 * 内部呼び出しのコンテキストを作る（要件 F-W-27）
	 *
	 * <p>
	 * <b>実行IDを外側から引き継ぐ。</b>
	 * 1本のリクエストの中で起きたことが、ログで1つに繋がる。
	 * </p>
	 *
	 * <p>
	 * アクセスログは出さない（外側がすでに1行出す）。
	 * 書き込み直後の参照先（要件 F-D-19）も<b>外側のものを共有する</b>。
	 * 内側で書き込んだことを外側が知らないと、
	 * <b>内部呼び出しで登録した直後に外側で一覧を引くと、いま入れたものが無い。</b>
	 * </p>
	 *
	 * @param source	入力口
	 * @param sink		出力口
	 * @param outer		外側のコンテキスト
	 * @return	コンテキスト
	 */
	public static WebContext internal (RequestSource source, ResponseSink sink, WebContext outer) {

		return new WebContext(source, sink, Objects.requireNonNull(outer, "outer"));

	}

	/**
	 * 内部呼び出しか
	 *
	 * @return	内部呼び出しなら true
	 */
	public boolean isInternal () {

		return outer != null;

	}

	/**
	 * このリクエストを捌いているディスパッチャ
	 *
	 * <p>
	 * <b>実装済みの API を内部から呼ぶ</b>ときの入口（要件 F-W-27）。
	 * </p>
	 *
	 * @return	ディスパッチャ。ディスパッチ前は null
	 */
	public Dispatcher dispatcher () {

		return dispatcher;

	}

	/**
	 * ディスパッチャを設定する
	 *
	 * <p>フレームワーク内部から呼ぶ。アプリケーションからは呼ばない。</p>
	 *
	 * @param dispatcher	ディスパッチャ
	 */
	public void dispatcher (Dispatcher dispatcher) {

		this.dispatcher = dispatcher;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected WebContext self () {

		return this;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 書き込み直後の参照先（要件 F-D-19）を束ねる。
	 * {@link Context} は {@code jimble-core} にあり {@code jimble-db} を見られないので、
	 * ここで足す。
	 * </p>
	 */
	@Override
	protected ScopedValue.Carrier scopedValues () {

		return super.scopedValues()
			.where(DBSticky.scopedValue, dbSticky());

	}

	/**
	 * リクエスト
	 *
	 * @return	リクエスト
	 */
	public Request request () {

		return request;

	}

	/**
	 * レスポンス
	 *
	 * @return	レスポンス
	 */
	public Response response () {

		return response;

	}

	/**
	 * Cookie
	 *
	 * <p>初めて呼ばれたときに受信 Cookie を解析する（要件 F-S-12）。</p>
	 *
	 * @return	Cookie
	 */
	public Cookies cookies () {

		/*
		 * 内部呼び出しは外側と同じものを使う（要件 F-W-27）。
		 * 別に持つと、内側で発行した Cookie が
		 * <b>内側の出力口に書かれて捨てられる</b>。
		 * セッションを新しく作った場合は、
		 * DB には行ができるのに相手はその id を知らない、という形になる。
		 */
		if (outer != null) {
			return outer.cookies();
		}

		if (cookies == null) {
			cookies = new Cookies(source);
		}

		return cookies;

	}

	/**
	 * Flash
	 *
	 * <p>
	 * 初めて呼ばれたときに受信 flash を読み、<b>同時に失効させる</b>（要件 F-S-07）。
	 * </p>
	 *
	 * @return	Flash
	 */
	public Flash flash () {

		// 内部呼び出しは外側と同じもの（読むと同時に失効するので、2つ作ると片方が空になる）
		if (outer != null) {
			return outer.flash();
		}

		if (flash == null) {
			flash = new Flash(cookies());
		}

		return flash;

	}

	/**
	 * セッション
	 *
	 * <p>
	 * 初めて呼ばれたときに作る。<b>読み書きするまで保存先には触らない</b>（要件 F-S-12）。
	 * </p>
	 *
	 * @return	セッション
	 */
	public Session session () {

		/*
		 * 内部呼び出しは外側と同じものを使う（要件 F-W-27）。
		 *
		 * 別に持つと3つ壊れる。
		 * (1) 内側で作ったセッションの id が相手に届かない（Cookie が捨てられる）
		 * (2) 外側と内側が同じセッションを別々に読み書きし、あとに保存したほうが勝つ
		 * (3) スコープごとに保存先を変えていると、内側だけ既定の保存先を読む
		 */
		if (outer != null) {
			return outer.session();
		}

		if (session == null) {
			session = new Session(this, sessionStore == null ? SessionStores.defaultStore() : sessionStore);
		}

		return session;

	}

	/**
	 * セッションの保存先をこのリクエストだけ変える（要件 F-S-11）
	 *
	 * <p>
	 * <b>{@link #session()} を呼ぶ前に</b>設定すること。すでに作られていたら例外にする
	 * （途中で保存先が変わると、読んだ先と書く先が食い違う）。
	 * </p>
	 *
	 * @param sessionStore	保存先
	 */
	public void sessionStore (SessionStore sessionStore) {

		// 内部呼び出しはセッションを外側と共有しているので、保存先も外側のもの
		if (outer != null) {
			outer.sessionStore(sessionStore);
			return;
		}

		if (session != null) {
			throw new IllegalStateException("セッションを使い始めたあとで保存先は変えられません");
		}

		this.sessionStore = sessionStore;

	}

	/**
	 * 溜めた Cookie を書き出す
	 *
	 * <p>
	 * フレームワーク内部から、送信の直前に呼ぶ。
	 * Cookie を1度も触っていなければ何もしない。
	 * </p>
	 *
	 * @param sink	出力口
	 */
	public void flushCookies (ResponseSink sink) {

		/*
		 * 内部呼び出しは書き出さない（要件 F-W-27）。
		 * Cookie は外側と共有していて、書き出しは1度だけしか効かない。
		 * ここで内側の出力口に書くと、<b>外側が書くころには空になっている。</b>
		 */
		if (outer != null) {
			return;
		}

		if (cookies == null) {
			return;
		}

		cookies.flush(sink);

	}

	/**
	 * マッチ結果
	 *
	 * @return	マッチ結果。ディスパッチ前は null
	 */
	public RouteMatch route () {

		return route;

	}

	// region 書き込み直後の参照先（要件 F-D-19）

	/**
	 * 書き込み直後の参照先
	 *
	 * <p>
	 * 読み取り用のレプリカがあるとき、書き込んだ直後の参照を書き込み側へ回す。
	 * <b>移送元（jooby_base）はこれをリクエストのスコープに束ねていたが、
	 * jimble には移していなかった。</b>そのため
	 * {@code DBSticky.sticky()} は常に false で、
	 * <b>登録した直後に一覧を引くと、いま入れたものが無い</b>ことがあった。
	 * </p>
	 *
	 * @return	参照先
	 */
	private DBSticky dbSticky () {

		/*
		 * 内部呼び出しは外側のものを使う（要件 F-W-27）。
		 * 別々に持つと、内側で書き込んだことが外側に伝わらない。
		 */
		if (outer != null) {
			return outer.dbSticky();
		}

		if (dbSticky == null) {
			dbSticky = new DBSticky(stickyKey(), null);
		}

		return dbSticky;

	}

	/**
	 * リクエストをまたいで同じ相手だと分かる値
	 *
	 * <p>
	 * すでに来ているセッション Cookie の値を使う。
	 * <b>この用途のために Cookie を新しく発行しない</b>（要件 F-S-12）。
	 * 移送元は {@code cid} という Cookie を全リクエストで無条件に発行していた。
	 * </p>
	 *
	 * <p>
	 * レプリカが無ければ固定するもしないも無いので、Cookie の解析すらしない。
	 * </p>
	 *
	 * @return	値。無ければ null（この実行の中だけ固定する）
	 */
	private String stickyKey () {

		if (!DBUtil.isUseRead()) {
			return null;
		}

		return cookies().get(SessionConf.cookieName());

	}

	// endregion

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@code jimble-core} はログを持たないので、ここで出す。
	 * 畳み忘れの後始末（要件 F-D-16）が失敗したことを黙って捨てない。
	 * </p>
	 */
	@Override
	protected void failedCloseTask (Throwable cause) {

		Log.error(cause, "実行の終わりの後始末に失敗しました: %s %s"
			.formatted(request.method(), request.path()));

	}

	/**
	 * メトリクスに入れる（要件 NF-O-04）
	 *
	 * <p>
	 * <b>名前には「マッチしたルートの型」を使う。</b>{@code request.path()} をそのまま使うと、
	 * {@code /aaa} {@code /aab} … と叩かれるだけで名前が無限に増える（`Metrics` の上限に当たって
	 * 数えるのをやめてしまう）。どのルートにも当たらなかったものは1つにまとめる。
	 * </p>
	 *
	 * <p>
	 * 内部呼び出し（要件 F-W-27）はここへ来ない（先に戻っている）。
	 * アクセスログと同じで、<b>数えると1リクエストが2回になる</b>。
	 * </p>
	 */
	private void recordMetrics () {

		Metrics.count("http.request");
		Metrics.count("http.status.%dxx".formatted(response.code() / 100));

		String name = route != null && route.matched()
			? "%s %s".formatted(request.method(), route.route().pattern())
			: "(unmatched)";

		Metrics.record("http.%s".formatted(name), elapsed().toNanos());

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>アクセスログを出力する（要件 F-U-05 / NF-O-02）。</p>
	 */
	@Override
	protected void doClose () {

		/*
		 * 内部呼び出し（要件 F-W-27）はアクセスログを出さない。
		 * 外側がすでに1行出しているので、同じ実行IDで2行出ると
		 * 「1リクエスト1行」で数えている集計がずれる。
		 * 追える必要はあるので、デバッグには残す。
		 */
		if (outer != null) {

			Log.debug("内部呼び出し: %s %s %d (%.1fms)".formatted(
				request.method(), request.path(), response.code(), elapsed().toNanos() / 1000000d));

			/*
			 * セッションの保存し忘れ（要件 F-S-03）は外側が見る。
			 * セッションも書き込み直後の参照先も外側と共有しているので、
			 * ここで見ると同じことを2回言うことになる。
			 */
			source.cleanup();

			return;

		}

		Data fields = new Data();
		fields.put("method", request.method());
		fields.put("path", request.path());
		fields.put("query", request.query());
		fields.put("status", response.code());
		fields.put("elapsed", elapsed().toNanos() / 1000000d);
		fields.put("matched", route != null && route.matched());

		/*
		 * ボットのアクセスは別のロガーへ（要件 F-H-05）。
		 * まとめて出すと、ボットの分で人のアクセスが埋もれる。
		 */
		boolean isBot = ServerConf.botAccessLog() && request.isBotAccess();

		fields.put("bot", isBot);

		Log.access("%s %s %d".formatted(request.method(), request.path(), response.code()), fields, isBot);

		recordMetrics();

		warnUnsavedSession();

		/*
		 * 書き込みがあったことを次のリクエストへ残す（要件 F-D-19）。
		 * 移送元はこれを呼んでいるところが1つも無く、
		 * db_sticky テーブルには誰も書き込んでいなかった。
		 */
		if (dbSticky != null) {
			dbSticky.apply();
		}

		// アップロードの一時ファイルを消す（要件 F-W-06）
		source.cleanup();

	}

	/**
	 * 保存し忘れたセッションを警告する（要件 F-S-03）
	 *
	 * <p>
	 * 「変えたのに {@code save()} を呼んでいない」を黙って捨てない。
	 * 明示保存（要件 F-S-02）にしている以上、書き忘れは必ず起きる。
	 * </p>
	 */
	private void warnUnsavedSession () {

		if (session == null || !session.isUnsavedChange()) {
			return;
		}

		Log.warn("セッションを変更しましたが save() が呼ばれていません: %s %s"
			.formatted(request.method(), request.path()));

	}

	/**
	 * マッチ結果を設定する
	 *
	 * <p>フレームワーク内部から呼ぶ。アプリケーションからは呼ばない。</p>
	 *
	 * @param route	マッチ結果
	 */
	public void route (RouteMatch route) {

		this.route = route;

	}

}
