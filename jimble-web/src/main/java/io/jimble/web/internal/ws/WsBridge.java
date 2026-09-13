package io.jimble.web.internal.ws;

import io.jimble.web.ws.WsHandler;
import io.jimble.web.ws.WsRoutes;
import io.jimble.web.ws.WsSession;
import io.jimble.web.ws.WsSink;
import io.jimble.web.ws.context.WsContext;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsUpgradeException;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.util.string.StringUtil;
import io.jimble.web.router.RouteInfo;
import io.jimble.web.ws.context.WsContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * jimble のルート表を helidon の {@code WsRouting} へ流し込む（要件 F-W-22）
 *
 * <p>
 * <b>helidon の型が出てくるのはこのクラスだけ</b>である。
 * アプリが触るのは {@link WsHandler} と {@link WsSession} だけで、
 * helidon の {@code WsListener} も {@code BufferData} も見えない。
 * </p>
 */
public final class WsBridge {

	private WsBridge () {}

	/**
	 * ルート表から作る
	 *
	 * @param routes	ルート一覧
	 * @return	ルーティング。WebSocket のルートが1つも無ければ null
	 */
	public static WsRouting.Builder build (List<RouteInfo> routes) {

		WsRouting.Builder builder = null;

		for (RouteInfo info : routes) {

			if (!WsRoutes.METHOD.equals(info.method())) {
				continue;
			}

			Supplier<WsHandler> supplier = info.route().attribute(WsRoutes.HANDLER);

			if (supplier == null) {
				continue;
			}

			if (builder == null) {
				builder = WsRouting.builder();
			}

			String path = info.path();

			/*
			 * helidon には「接続ごとに1つ作る」形で渡す。
			 * 1つを使い回すと、接続ごとの状態が混ざる（MQ の D-36 と同じ）。
			 */
			builder.endpoint(path, () -> new Listener(path, supplier.get()));

			Log.info("WebSocket: " + path);

		}

		return builder;

	}

	/**
	 * helidon と jimble のあいだ
	 */
	private static final class Listener implements WsListener {

		/* パス */
		private final String path;

		/* 処理 */
		private final WsHandler handler;

		/* アップグレード時のヘッダ */
		private Data headers = new Data();

		/* 接続 */
		private WsSession session;

		/**
		 * コンストラクタ
		 *
		 * @param path		パス
		 * @param handler	処理
		 */
		private Listener (String path, WsHandler handler) {

			this.path = path;
			this.handler = handler;

		}

		/**
		 * {@inheritDoc}
		 *
		 * <p>
		 * <b>認証はここで通す。</b>Cookie が読めるのはこの時点である。
		 * 断ると 403 になる（繋がってから切るのではない）。
		 * </p>
		 */
		@Override
		public Optional<Headers> onHttpUpgrade (HttpPrologue prologue, Headers headers) throws WsUpgradeException {

			Data collected = new Data();

			headers.forEach(header -> collected.put(
				header.headerName().lowerCase(), header.get()));

			this.headers = collected;

			// この時点ではまだ helidon のセッションが無いので、送れない口を渡す
			WsSession upgrading = new WsSession(NOT_CONNECTED, path, collected);

			if (!handler.onUpgrade(upgrading)) {
				throw new WsUpgradeException("WebSocket のアップグレードを断りました: " + path);
			}

			return Optional.empty();

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onOpen (io.helidon.websocket.WsSession wsSession) {

			this.session = new WsSession(new HelidonWsSink(wsSession), path, headers);

			run(context -> handler.onOpen(context));

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onMessage (io.helidon.websocket.WsSession wsSession, String text, boolean last) {

			run(context -> handler.onMessage(context, text));

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onMessage (io.helidon.websocket.WsSession wsSession, BufferData buffer, boolean last) {

			byte[] bytes = new byte[buffer.available()];
			buffer.read(bytes);

			run(context -> handler.onBinary(context, bytes));

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onClose (io.helidon.websocket.WsSession wsSession, int code, String reason) {

			run(context -> handler.onClose(context, code, reason));

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onError (io.helidon.websocket.WsSession wsSession, Throwable cause) {

			Log.error(cause, "WebSocket で例外が出ました: " + path);

			run(context -> handler.onError(context, cause));

		}

		/**
		 * メッセージ1件ぶんの実行
		 *
		 * <p>
		 * <b>ここで Context を作って捨てる</b>（D-56）。
		 * 接続ごとにすると、何時間も生きる Context が
		 * DB 接続やトランザクションを握ったままになる。
		 * </p>
		 *
		 * @param body	処理
		 */
		private void run (Body body) {

			if (session == null) {
				// onOpen より前に来ることはないはずだが、落とさない
				return;
			}

			try (WsContext context = new WsContext(StringUtil.uniqueString(), session)) {

				context.run(() -> {

					try {
						body.run(context);
					} catch (Exception ex) {
						Log.error(ex, "WebSocket の処理で例外が出ました: " + path);
					}

				});

			}

		}

		/**
		 * メッセージ1件ぶんの処理
		 */
		@FunctionalInterface
		private interface Body {

			/**
			 * 実行する
			 *
			 * @param context	コンテキスト
			 * @throws Exception	失敗した場合
			 */
			void run (WsContext context) throws Exception;

		}

	}


	/**
	 * まだ繋がっていないときの出口
	 *
	 * <p>
	 * アップグレードの判定中は helidon のセッションがまだ無い。
	 * <b>null を渡すのではなく、断る口を渡す</b>——
	 * null だと、送ろうとしたところで {@code NullPointerException} になる。
	 * </p>
	 */
	private static final WsSink NOT_CONNECTED = new WsSink() {

		@Override
		public boolean send (String text) {

			// まだ繋がっていない。onUpgrade の中から送ることはできない
			return false;

		}

		@Override
		public void close (String reason) {

			// 繋がっていないので閉じるものが無い

		}

	};

	/**
	 * helidon のセッションへの出口
	 *
	 * <p><b>helidon の型が出てくるのはここまでである。</b></p>
	 *
	 * @param session	helidon のセッション
	 */
	private record HelidonWsSink (io.helidon.websocket.WsSession session) implements WsSink {

		@Override
		public boolean send (String text) {

			session.send(text, true);
			return true;

		}

		@Override
		public void close (String reason) {

			session.close(io.helidon.websocket.WsCloseCodes.NORMAL_CLOSE, reason);

		}

	}

}
