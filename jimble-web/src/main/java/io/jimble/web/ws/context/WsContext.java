package io.jimble.web.ws.context;

import io.jimble.core.context.Context;
import io.jimble.util.log.Log;
import io.jimble.web.ws.WsSession;

/**
 * WebSocket のメッセージ1件のコンテキスト（要件 F-C-01 / F-W-22）
 *
 * <p>
 * <b>メッセージごとに1つ作って捨てる</b>（D-56）。MQ とまったく同じ形である。
 * </p>
 *
 * <p>
 * 接続ごとに1つにする案もあったが、やめた。
 * WebSocket の接続は何時間も生きる。
 * <b>その間ずっと Context が居座り、トランザクションやフェッチャを握れば
 * 接続プールをそのぶん食い続ける。</b>SSE と同じ踏み方で、しかも SSE より長い。
 * </p>
 *
 * <p>
 * 接続に紐づけたいもの（誰が繋いでいるか、どの部屋か）は
 * {@link WsSession#attributes()} に置く。<b>ここに置くと次のメッセージには残らない。</b>
 * </p>
 */
public final class WsContext extends Context<WsContext> {

	/* 接続 */
	private final WsSession session;

	/**
	 * コンストラクタ
	 *
	 * @param executionId	実行ID
	 * @param session		接続
	 */
	public WsContext (String executionId, WsSession session) {

		super(executionId);

		this.session = session;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected WsContext self () {

		return this;

	}

	/**
	 * 接続
	 *
	 * @return	接続
	 */
	public WsSession session () {

		return session;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void failedCloseTask (Throwable cause) {

		Log.error(cause, "実行の終わりの後始末に失敗しました: ws %s".formatted(session.path()));

	}

}
