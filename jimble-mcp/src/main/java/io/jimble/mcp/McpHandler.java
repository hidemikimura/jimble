package io.jimble.mcp;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.sse.SseEvent;
import io.jimble.web.sse.SseStream;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * MCP を HTTP で受ける（Streamable HTTP）
 *
 * <p>
 * <b>ここは HTTP の層だけである。</b>{@code Origin} の検証、ヘッダと本文の突き合わせ、
 * ステータスコード、SSE。JSON-RPC の中身は {@link McpDispatch} が見る。
 * stdio（要件 F-MCP-12）が同じ {@link McpDispatch} を通すので、
 * <b>振る舞いが2つに分かれない</b>。
 * </p>
 */
public final class McpHandler {

	/** 応答の種別 */
	private static final String CONTENT_TYPE_JSON = "application/json";

	/** 振り分け */
	private final McpDispatch dispatch;

	/**
	 * コンストラクタ
	 *
	 * @param registry	登録簿
	 */
	public McpHandler (McpRegistry registry) {

		this.dispatch = new McpDispatch(registry);

	}

	/**
	 * 受ける
	 *
	 * @param context	コンテキスト
	 * @throws Exception	どうしようもない失敗
	 */
	public void handle (WebContext context) throws Exception {

		/*
		 * 1. Origin（仕様 MUST）。
		 *    DNS リバインディングで、外のページからローカルの MCP サーバーを
		 *    叩かれるのを防ぐ。
		 */
		String origin = context.request().header().getStringOptional("origin");

		if (!McpHeaders.isAllowedOrigin(origin, McpConf.allowedOrigins())) {
			send(context, 403, McpErrors.of(null, McpErrors.INVALID_REQUEST
				, "許可されていないオリジンです: " + origin));
			return;
		}

		// 2. 本文を読む
		Data body = context.request().bodyJson();

		Object id = body == null ? null : body.get("id");

		/*
		 * 3. ヘッダと本文の突き合わせ（要件 F-MCP-05）。
		 *    <b>版より先に見る。</b>ヘッダが本文と食い違っているのに
		 *    版だけ通してしまうと、あとの検証が「どちらの値で」通ったのか分からなくなる。
		 */
		String version = header(context, McpProtocol.HEADER_PROTOCOL_VERSION);

		/*
		 * <b>server/discover だけは版のヘッダを求めない。</b>
		 * 版を知るための呼び出しに版を要求すると、初めて繋ぐクライアントは詰む。
		 * 判断は {@link McpDispatch} と揃えてある
		 */
		boolean discover = body != null
			&& McpProtocol.METHOD_SERVER_DISCOVER.equals(body.getStringOptional("method"));

		if (id != null && version == null && !discover) {
			send(context, 400, McpErrors.of(id, McpErrors.HEADER_MISMATCH
				, "%s ヘッダがありません".formatted(McpProtocol.HEADER_PROTOCOL_VERSION)));
			return;
		}

		if (id != null && McpProtocol.VERSION.equals(version)) {

			String mismatch = McpHeaders.validate(context, body);

			if (mismatch != null) {
				send(context, 400, McpErrors.of(id, McpErrors.HEADER_MISMATCH, mismatch));
				return;
			}

		}

		// 4. 中身は共通の振り分けへ
		McpResponse response = dispatch.handle(context, body, version);

		if (response.stream()) {
			listen(context, id, body.getDataOptional("params"));
			return;
		}

		if (response.body() == null) {
			/*
			 * 通知は 202 を本文なしで返す（仕様 MUST）。
			 *
			 * code(202).send() ではいけない。Accept に application/json があると
			 * 「JSON を求められている」と見なして空の {} を返してしまう。
			 * send(202) は本文なしで送る。
			 */
			context.response().send(response.httpStatus());
			return;
		}

		send(context, response.httpStatus(), response.body());

	}

	// region 購読（要件 F-MCP-13）

	/**
	 * 購読を開いて、切られるまで流し続ける
	 *
	 * <p>
	 * <b>この POST の応答が、そのまま長い SSE のストリームになる。</b>
	 * 2026-07-28 で GET のストリームが仕様から消えたので、
	 * サーバーからの通知はここを通るしかない。
	 * </p>
	 *
	 * <h2>書くのはこのスレッドだけ</h2>
	 * <p>
	 * <b>通知は別のスレッドから来る</b>（アプリが {@code McpNotify} を呼んだところ）。
	 * そこから応答のストリームへ直接書くと、<b>書けたように見えて相手には届かない</b>——
	 * helidon の応答は要求を処理しているスレッドに紐づいていて、
	 * ほかのスレッドが書いたぶんは socket へ流れない。例外も出ない。
	 * </p>
	 * <p>
	 * そこで通知はいったん<b>待ち行列</b>に入れ、
	 * <b>この輪だけが書く</b>。SSE の1本を1スレッドで扱うのは、
	 * 書き込みが混ざらない形でもある。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param id		要求の識別子（＝購読の識別子）
	 * @param params	引数
	 */
	private void listen (WebContext context, Object id, Data params) {

		/*
		 * <b>上限を置かない。</b>置くと、溢れたときに
		 * 「通知が1つ黙って消える」形になる。通知は詰まるより溜まったほうがよい
		 */
		BlockingQueue<Data> pending = new LinkedBlockingQueue<>();

		/*
		 * <b>接続を1本、長く握る。</b>SSE と同じ費用がかかる（要件 NF-P-07）。
		 * 上限は SSE の設定（sse.max_duration_seconds）に従う。
		 * <b>永遠に張らせない</b>のは、相手が切ったことを検知できないためである。
		 */
		try (SseStream sse = context.response().sse()) {

			/*
			 * 閉じたことを輪に知らせる合図。
			 * <b>中身は見ずに同一性で見分ける</b>ので、空でよい。
			 * これが無いと、取り消されても<b>空行を送る時間まで接続が居座る</b>
			 */
			Data closed = new Data();

			// acknowledged もここで行列に入る（書くのは下の輪）
			McpSubscriptions.Subscription subscription
				= McpSubscriptions.open(id, params, new McpSubscriptions.Sink() {

					@Override
					public void write (Data message) {

						pending.add(message);

					}

					@Override
					public void close () {

						pending.add(closed);

					}

				});

			while (sse.isOpen()) {

				Data message = pending.poll(KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);

				if (message == closed) {
					break;
				}

				if (message != null) {

					if (!sse.sendWithoutCounting(SseEvent.of(message.getJsonString()))) {
						break;
					}

					continue;

				}

				// 行列が空のまま時間が来た。閉じられていれば終わり
				if (subscription.isClosed()) {
					break;
				}

				// 途中の機器に切られないよう、たまに空行を送る
				if (!sse.keepAlive()) {
					break;
				}

			}

			/*
			 * <b>こちらから終わるときは応答を返す</b>（仕様の graceful closure）。
			 * 返さずに切ると、クライアントは「落ちた」と思って繋ぎ直す。
			 * 取り消しで閉じられていた場合は、ここは何もしない
			 * （仕様は「取り消された要求に応答を返してはならない」と定めている）。
			 */
			McpSubscriptions.complete(id);

			// 締めの応答を含め、行列に残っているものを書き切る
			for (Data message = pending.poll(); message != null; message = pending.poll()) {

				if (message == closed) {
					break;
				}

				if (!sse.sendWithoutCounting(SseEvent.of(message.getJsonString()))) {
					break;
				}

			}

		} catch (Exception ex) {

			/*
			 * <b>黙って閉じない。</b>ここで落ちると、クライアントには
			 * 「何も来ないまま切れた」としか見えない。理由はサーバー側にしか残らない
			 */
			io.jimble.util.log.Log.error(ex, "MCP の購読が途中で終わりました: id=%s".formatted(id));

			McpSubscriptions.cancel(id);

		}

	}

	/** 空行を送る間隔（秒） */
	private static final long KEEP_ALIVE_SECONDS = 15;

	// endregion

	// region 送る

	/**
	 * 送る
	 *
	 * @param context	コンテキスト
	 * @param code		ステータスコード
	 * @param body		本文
	 */
	private static void send (WebContext context, int code, Data body) {

		context.response().code(code);
		context.response().setResponseHeader("Content-Type", CONTENT_TYPE_JSON + "; charset=UTF-8");
		context.response().send(body.getJsonString(), CONTENT_TYPE_JSON);

	}

	/**
	 * ヘッダを引く
	 *
	 * @param context	コンテキスト
	 * @param name		名前
	 * @return	値。無ければ null
	 */
	private static String header (WebContext context, String name) {

		String value = context.request().header().getStringOptional(name.toLowerCase(Locale.ROOT));

		return value.isEmpty() ? null : value;

	}

	// endregion

}
