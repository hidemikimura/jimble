package io.jimble.web.context;

import io.jimble.core.context.Context;
import io.jimble.db.DBSticky;
import io.jimble.db.DBUtil;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
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

	/**
	 * コンストラクタ
	 *
	 * @param source	入力口
	 * @param sink		出力口
	 */
	public WebContext (RequestSource source, ResponseSink sink) {

		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(sink, "sink");

		this.source = source;
		this.request = new Request(this, source);
		this.response = new Response(this, this.request, sink);

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
	 * {@inheritDoc}
	 *
	 * <p>アクセスログを出力する（要件 F-U-05 / NF-O-02）。</p>
	 */
	@Override
	protected void doClose () {

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
