package io.jimble.web.server;

import io.jimble.core.executor.Executor;
import io.jimble.util.log.Log;
import io.jimble.web.context.WebContext;
import io.jimble.web.router.ErrorHandler;
import io.jimble.web.router.Handler;

/**
 * リクエスト処理の段
 *
 * <p>
 * <b>「レスポンス送信済みなら以降を実行しない」という判定をここ1箇所に閉じる。</b>
 * {@code jooby_base} は同じ判定を7箇所にコピーしていた（要件 F-C-13）。
 * </p>
 */
final class Stage {

	/* コンテキスト */
	private final WebContext context;

	/* 完了判定 */
	private boolean done = false;

	/**
	 * コンストラクタ
	 *
	 * @param context	コンテキスト
	 */
	Stage (WebContext context) {

		this.context = context;

	}

	/**
	 * 完了しているか
	 *
	 * @return	完了していれば true
	 */
	boolean isDone () {

		return done;

	}

	/**
	 * 1段実行する
	 *
	 * <p>すでに完了していれば何もしない。</p>
	 *
	 * @param handler	処理
	 * @throws Exception	処理中の例外
	 */
	void run (Handler handler) throws Exception {

		if (done) {
			return;
		}

		handler.handle(context);

		if (context.response().isSent()) {
			done = true;
		}

	}

	/**
	 * エラーハンドラを1つ実行する
	 *
	 * <p>
	 * <b>ハンドラ自身が失敗しても止めない</b>（要件 F-C-15）。
	 * ここで投げ返すと、エラーハンドラのバグで
	 * <b>クライアントに何も返らなくなる</b>。握って次のハンドラに進む。
	 * </p>
	 *
	 * @param hook			エラーハンドラ
	 * @param cause			原因
	 * @param statusCode	ステータス
	 */
	void runError (ErrorHandler hook, Throwable cause, int statusCode) {

		if (done) {
			return;
		}

		try {

			hook.handle(context, cause, statusCode);

		} catch (Throwable failure) {

			Log.error(failure, "エラーハンドラで例外が発生しました");

		} finally {

			/*
			 * <b>送ってから落ちたハンドラ</b>も送信済みである。
			 * catch で抜けて判定を飛ばすと、
			 * <b>もう返したあとに次のハンドラが動く</b>。
			 */
			if (context.response().isSent()) {
				done = true;
			}

		}

	}

	/**
	 * Executorキューを回す
	 *
	 * <p>
	 * キャンセルされたら残りを破棄し、キャンセル処理を実行して打ち切る。
	 * </p>
	 *
	 * @throws Exception	処理中の例外
	 */
	void runExecutors () throws Exception {

		while (!done) {

			Executor<WebContext> executor = context.pollExecutor();
			if (executor == null) {
				return;
			}

			executor.execute(context);

			if (context.response().isSent()) {
				done = true;
				return;
			}

			if (executor.isCanceled()) {
				context.clearExecutors();
				executor.onCancel(context);
				done = true;
				return;
			}

		}

	}

	/**
	 * まだ送信していなければ送信する
	 */
	void send () {

		if (context.response().isSent()) {
			done = true;
			return;
		}

		context.response().send();
		done = true;

	}

}
