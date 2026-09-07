package io.jimble.web.sse;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * サーバーから送り続ける口（要件 F-W-21）
 *
 * <pre>
 * get("/events", context -&gt; {
 *
 *     try (SseStream sse = context.response().sse()) {
 *
 *         while (sse.isOpen()) {
 *             sse.send("tick", new Data().putData("at", new Date()));
 *             sse.sleep(Duration.ofSeconds(1));
 *         }
 *
 *     }
 *
 * });
 * </pre>
 *
 * <h2>いちばん大事なこと：終わらないループを書かない</h2>
 *
 * <p>
 * <b>相手が切ったことは、こちらからは分からない。</b>実測した結果、
 * クライアントが TCP を閉じたあとの書き込みは次のどちらかになる。
 * </p>
 *
 * <ol>
 *   <li>{@code IOException} になる（このクラスが拾って {@link #isOpen()} を false にする）</li>
 *   <li><b>止まったまま帰ってこない</b></li>
 * </ol>
 *
 * <p>
 * どちらになるかは環境で変わる。<b>2 が起きると、その実行スレッドは
 * {@code send()} の中で止まったままになる。</b>
 * Java のソケットには書き込みのタイムアウトが無く
 * （helidon の {@code SocketOptions} も connect と read だけ）、
 * <b>外から止める手段が無い。</b>
 * </p>
 *
 * <p>
 * したがって <b>「相手が切るまで回し続ける」ループを書いてはいけない。</b>
 * {@link #isOpen()} が見ているのは<b>相手の生死ではなく、
 * 上限に達していないかと明示的な close だけ</b>である。
 * 上限は {@link SseConf}（既定 5 分）で決まる。
 * </p>
 *
 * <p>
 * <b>正しい形は「有限のぶんを送って閉じる」である。</b>
 * ジョブの進捗、決まった件数の通知、リクエストに対する応答（MCP がこれ）。
 * 押し続けたい場合も、<b>ひと区切り送って閉じ、クライアントに繋ぎ直させる。</b>
 * SSE はもともとそういう仕組みで、切れたクライアントは
 * {@code retry:}（このクラスが開いたときに1回送る）のあとに勝手に戻ってくる。
 * 途中のプロキシも長い接続は切る。
 * </p>
 *
 * <h2>ほかに気をつけること</h2>
 * <ol>
 *   <li><b>トランザクションやフェッチャを開いたまま張らない。</b>
 *       ふつうのクエリは1回ごとに接続をプールへ返すが、
 *       <b>トランザクションの中と {@code selectListWithFetcher} の間は返さない。</b>
 *       その状態で繋ぎっぱなしにすると<b>人数分だけプールを食う。</b>
 *       10 人で {@code maximumPoolSize = 10} が尽き、
 *       「遅い」でも「エラー」でもなく<b>新しいリクエストが接続待ちで止まる</b>形で出る</li>
 *   <li><b>ヘッダは張る前に決める。</b>1件目を書いた時点で変えられない</li>
 * </ol>
 */
public final class SseStream implements Closeable {

	/** 中身の型 */
	public static final String CONTENT_TYPE = "text/event-stream; charset=UTF-8";

	/* 出力先 */
	private final OutputStream out;

	/* 閉じたか */
	private final AtomicBoolean closed = new AtomicBoolean(false);

	/* いつまで張っていられるか（ナノ秒。0 以下なら無制限） */
	private final long deadlineNanos;

	/* 送れる件数の上限（0 以下なら無制限） */
	private final long maxEvents;

	/* 送った件数 */
	private long sentCount;

	/**
	 * コンストラクタ
	 *
	 * <p>フレームワーク内部から呼ぶ。{@code context.response().sse()} を使うこと。</p>
	 *
	 * @param out			出力先
	 * @param maxDuration	張っていられる上限。null か 0 以下なら無制限
	 * @param maxEvents		送れる件数の上限。0 以下なら無制限
	 */
	public SseStream (OutputStream out, Duration maxDuration, long maxEvents) {

		this.out = out;
		this.maxEvents = maxEvents;
		this.deadlineNanos = maxDuration == null || maxDuration.isZero() || maxDuration.isNegative()
			? 0
			: System.nanoTime() + maxDuration.toNanos();

	}

	/**
	 * まだ送ってよいか
	 *
	 * <p>
	 * <b>相手が生きているかどうかは見ていない。</b>見られないからである（クラスの説明を参照）。
	 * ここが見ているのは<b>上限に達していないか</b>と<b>閉じられていないか</b>だけである。
	 * </p>
	 *
	 * @return	まだ送ってよい場合 = true
	 */
	public boolean isOpen () {

		if (closed.get()) {
			return false;
		}

		if (maxEvents > 0 && sentCount >= maxEvents) {
			Log.debug("SSE の件数の上限に達しました: " + maxEvents);
			return false;
		}

		if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) {
			Log.debug("SSE の時間の上限に達しました");
			return false;
		}

		return true;

	}

	/**
	 * 残り時間
	 *
	 * @return	残り。無制限なら null
	 */
	public Duration remaining () {

		if (deadlineNanos <= 0) {
			return null;
		}

		return Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));

	}

	/**
	 * 送る
	 *
	 * @param event	1件
	 * @return	送れた場合 = true。相手が閉じていれば false
	 */
	public boolean send (SseEvent event) {

		if (closed.get()) {
			return false;
		}

		try {

			out.write(event.format().getBytes(StandardCharsets.UTF_8));

			/*
			 * 1件ごとに流す。
			 * まとめてしまうと「あとで一気に届く」ことになり、
			 * SSE を使う意味が無くなる。
			 */
			out.flush();

			sentCount++;

			return true;

		} catch (IOException ex) {

			/*
			 * ここへは<b>ほとんど来ない</b>（helidon は書き込みの失敗を返さない。
			 * クラスの説明を参照）。来たときのために閉じておく。
			 */
			closed.set(true);
			return false;

		}

	}

	/**
	 * 送る
	 *
	 * @param data	本文
	 * @return	送れた場合 = true
	 */
	public boolean send (String data) {

		return send(SseEvent.of(data));

	}

	/**
	 * 送る
	 *
	 * @param name	種別
	 * @param data	本文
	 * @return	送れた場合 = true
	 */
	public boolean send (String name, String data) {

		return send(SseEvent.of(name, data));

	}

	/**
	 * JSON で送る
	 *
	 * @param name	種別
	 * @param data	本文
	 * @return	送れた場合 = true
	 */
	public boolean send (String name, Data data) {

		return send(SseEvent.json(name, data));

	}

	/**
	 * キープアライブを送る
	 *
	 * <p>
	 * コメント行（{@code :}）を1つ送る。中身は無い。
	 * <b>無通信が続くと、途中のプロキシや相手の idle タイムアウトで切られる。</b>
	 * 何も流れない時間が長くなる口では、定期的に呼ぶこと。
	 * </p>
	 *
	 * @return	送れた場合 = true
	 */
	public boolean keepAlive () {

		return sendWithoutCounting(SseEvent.KEEP_ALIVE);

	}

	/**
	 * 件数に数えずに送る
	 *
	 * <p>
	 * 中身のないもの（キープアライブ、{@code retry:} の合図）に使う。
	 * これらを数えると、{@code maxEvents = 3} と書いたのに
	 * <b>本文が2件しか届かない</b>ことになる。
	 * </p>
	 *
	 * @param event	1件
	 * @return	送れた場合 = true
	 */
	public boolean sendWithoutCounting (SseEvent event) {

		long before = sentCount;
		boolean sent = send(event);
		sentCount = before;

		return sent;

	}

	/**
	 * 待つ
	 *
	 * <p>
	 * 待っている間に閉じられたら false を返す。
	 * {@code Thread.sleep} を直接呼ぶより、こちらのほうがループを抜けやすい。
	 * </p>
	 *
	 * @param duration	待つ時間
	 * @return	まだ開いている場合 = true
	 */
	public boolean sleep (Duration duration) {

		/*
		 * 上限を越えて眠らない。
		 * 1時間眠る指定をされても、上限で起きる。
		 */
		Duration remaining = remaining();
		long millis = remaining == null
			? duration.toMillis()
			: Math.min(duration.toMillis(), remaining.toMillis());

		if (millis > 0) {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				closed.set(true);
			}
		}

		return isOpen();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close () {

		if (closed.getAndSet(true)) {
			return;
		}

		try {
			out.close();
		} catch (IOException ex) {
			// 相手がもう居ないだけ。閉じるときに騒がない
			Log.debug("SSE を閉じるときに切れていました: " + ex.getMessage());
		}

	}

}
