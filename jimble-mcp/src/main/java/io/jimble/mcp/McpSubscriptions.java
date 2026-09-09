package io.jimble.mcp;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 開いている購読（要件 F-MCP-13）
 *
 * <p>
 * {@code subscriptions/listen} は<b>終わらない要求</b>である。クライアントが切るまで、
 * サーバーから通知を流し続ける。{@code resources/subscribe} と GET のストリームは
 * この1本に置き換わった（2026-07-28）。
 * </p>
 *
 * <h2>何を流すかはクライアントが選ぶ</h2>
 * <p>
 * <b>頼まれていない種類は流してはいけない</b>（仕様 MUST）。
 * 受け付けた種類は最初の {@code acknowledged} で返すので、
 * クライアントは<b>自分が頼んだものと突き合わせられる</b>。
 * </p>
 *
 * <h2>jimble が流せるのは resources の2つだけ</h2>
 * <p>
 * ツールとプロンプトは<b>起動時に明示登録する</b>（原則2 / 要件 F-MCP-01）ので、
 * 動いているあいだに増えも減りもしない。つまり
 * {@code toolsListChanged} / {@code promptsListChanged} は<b>発火しようが無い</b>。
 * 頼まれても {@code acknowledged} に含めない——<b>「対応している」と答えて
 * 一生届かないほうが悪い</b>。
 * </p>
 */
public final class McpSubscriptions {

	/** 購読の識別子ごとの購読 */
	private static final Map<Object, Subscription> OPEN = new ConcurrentHashMap<>();

	/** 何番目の購読か（ログ用） */
	private static final AtomicLong SEQUENCE = new AtomicLong();

	private McpSubscriptions () {
	}

	// region 開く・閉じる

	/**
	 * 購読を開く
	 *
	 * @param id		{@code subscriptions/listen} の要求の識別子（＝購読の識別子）
	 * @param params	要求の引数
	 * @param sink		流す先
	 * @return 購読
	 */
	public static Subscription open (Object id, Data params, Sink sink) {

		Data wanted = params.getDataOptional("notifications");

		Subscription subscription = new Subscription(id, sink
			, wanted.getBoolean("resourcesListChanged")
			, Set.copyOf(wanted.getStringListOptional("resourceSubscriptions")));

		OPEN.put(key(id), subscription);

		Log.debug("MCP の購読を開きました: id=%s / 通し番号=%d / リソース=%d 件".formatted(
			id, SEQUENCE.incrementAndGet(), subscription.resourceUris.size()));

		/*
		 * <b>いちばん最初に acknowledged を送る</b>（仕様 MUST）。
		 * ここで返すのは<b>受け付けた種類だけ</b>で、対応していないものは落とす。
		 */
		subscription.send(McpProtocol.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED
			, acknowledged(subscription));

		return subscription;

	}

	/**
	 * 受け付けた種類
	 *
	 * @param subscription 購読
	 * @return 引数
	 */
	private static Data acknowledged (Subscription subscription) {

		Data notifications = new Data();

		if (subscription.resourcesListChanged) {
			notifications.put("resourcesListChanged", true);
		}

		if (!subscription.resourceUris.isEmpty()) {
			notifications.put("resourceSubscriptions", new ArrayList<>(subscription.resourceUris));
		}

		Data params = new Data();
		params.put("notifications", notifications);

		return params;

	}

	/**
	 * 購読を閉じる（クライアントが取り消した）
	 *
	 * @param id 購読の識別子
	 */
	public static void cancel (Object id) {

		Subscription subscription = OPEN.remove(key(id));

		if (subscription != null) {
			subscription.close(false);
		}

	}

	/**
	 * 購読を閉じる（サーバー側から終わる）
	 *
	 * <p>
	 * <b>元の要求への応答を返してから閉じる</b>（仕様の graceful closure）。
	 * 上限に達したときや、繋いでいる相手が居なくなったときに呼ぶ。
	 * </p>
	 *
	 * <p>
	 * {@link #cancel} との違いは<b>応答を返すかどうか</b>である。
	 * クライアントが {@code notifications/cancelled} で取り消したときは、
	 * 仕様が「取り消された要求に応答を返してはならない」と定めているので
	 * {@link #cancel} を使う。
	 * </p>
	 *
	 * @param id 購読の識別子
	 */
	public static void complete (Object id) {

		Subscription subscription = OPEN.remove(key(id));

		if (subscription != null) {
			subscription.close(true);
		}

	}

	/**
	 * 開いているものを全部、きれいに閉じる
	 *
	 * <p>
	 * 止めるときに呼ぶ。<b>応答を返してから閉じる</b>ので、
	 * クライアントは「切れた」ではなく「終わった」と分かる（仕様の graceful closure）。
	 * </p>
	 */
	public static void closeAll () {

		for (Object id : List.copyOf(OPEN.keySet())) {

			Subscription subscription = OPEN.remove(id);

			if (subscription != null) {
				subscription.close(true);
			}

		}

	}

	/**
	 * 開いている数（テスト用）
	 *
	 * @return 数
	 */
	public static int openCount () {

		return OPEN.size();

	}

	// endregion

	// region 流す

	/**
	 * リソースが変わったことを流す
	 *
	 * @param uri リソースの URI
	 */
	static void resourceUpdated (String uri) {

		Data params = new Data();
		params.put("uri", uri);

		for (Subscription subscription : OPEN.values()) {
			if (subscription.resourceUris.contains(uri)) {
				subscription.send(McpProtocol.NOTIFICATION_RESOURCES_UPDATED, params);
			}
		}

	}

	/**
	 * リソースの一覧が変わったことを流す
	 */
	static void resourcesListChanged () {

		for (Subscription subscription : OPEN.values()) {
			if (subscription.resourcesListChanged) {
				subscription.send(McpProtocol.NOTIFICATION_RESOURCES_LIST_CHANGED, new Data());
			}
		}

	}

	// endregion

	/**
	 * 流す先
	 *
	 * <p>
	 * HTTP なら SSE のストリーム、stdio なら共通の標準出力である。
	 * </p>
	 */
	@FunctionalInterface
	public interface Sink {

		/**
		 * 1つ書き出す
		 *
		 * @param message JSON-RPC のメッセージ
		 * @throws Exception 書けなかった
		 */
		void write (Data message) throws Exception;

		/**
		 * 閉じる
		 */
		default void close () {
		}

	}

	/**
	 * 1つの購読
	 */
	public static final class Subscription {

		/** 購読の識別子（＝要求の識別子） */
		private final Object id;

		/** 流す先 */
		private final Sink sink;

		/** リソースの一覧の変化を流すか */
		private final boolean resourcesListChanged;

		/** 変化を流すリソース */
		private final Set<String> resourceUris;

		/** 閉じたか */
		private volatile boolean closed = false;

		/** 送ったもの（テスト用） */
		private final List<Data> sent = new CopyOnWriteArrayList<>();

		/**
		 * コンストラクタ
		 *
		 * @param id					購読の識別子
		 * @param sink					流す先
		 * @param resourcesListChanged	一覧の変化を流すか
		 * @param resourceUris			変化を流すリソース
		 */
		private Subscription (Object id, Sink sink, boolean resourcesListChanged, Set<String> resourceUris) {

			this.id = id;
			this.sink = sink;
			this.resourcesListChanged = resourcesListChanged;
			this.resourceUris = resourceUris;

		}

		/**
		 * 通知を1つ流す
		 *
		 * @param method	メソッド
		 * @param params	引数（{@code _meta} はここで足す）
		 */
		void send (String method, Data params) {

			if (closed) {
				return;
			}

			Data meta = params.getDataOptional("_meta");
			meta.put(McpProtocol.META_SUBSCRIPTION_ID, id);
			params.put("_meta", meta);

			Data message = new Data();
			message.put("jsonrpc", McpProtocol.JSONRPC_VERSION);
			message.put("method", method);
			message.put("params", params);

			sent.add(message);

			try {
				sink.write(message);
			} catch (Exception ex) {
				/*
				 * <b>相手が切っただけで、こちらは落とさない。</b>
				 * 書けなくなった購読は閉じる（残しておくと、以後ずっと失敗し続ける）
				 */
				Log.debug("MCP の購読に書けませんでした（閉じます）: id=%s（%s）".formatted(id, ex));
				close(false);
				OPEN.remove(key(id));
			}

		}

		/**
		 * 閉じる
		 *
		 * @param graceful 応答を返してから閉じるか
		 */
		void close (boolean graceful) {

			if (closed) {
				return;
			}

			/*
			 * <b>先に応答を書いてから印を立てる。</b>
			 * 逆にすると send() が「閉じている」と見て何も書かない。
			 */
			if (graceful) {

				Data meta = new Data();
				meta.put(McpProtocol.META_SUBSCRIPTION_ID, id);

				Data result = new Data();
				result.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
				result.put("_meta", meta);

				try {
					sink.write(McpDispatch.result(id, result));
				} catch (Exception ex) {
					Log.debug("MCP の購読を閉じる応答が書けませんでした: id=%s（%s）".formatted(id, ex));
				}

			}

			closed = true;

			sink.close();

		}

		/**
		 * 閉じているか
		 *
		 * @return 閉じていれば true
		 */
		public boolean isClosed () {

			return closed;

		}

		/**
		 * 送ったもの（テスト用）
		 *
		 * @return 送ったもの
		 */
		public List<Data> sent () {

			return List.copyOf(sent);

		}

	}

	/**
	 * 識別子を揃える
	 *
	 * <p>
	 * <b>JSON-RPC の識別子は数でも文字列でもよい。</b>JSON を読み直すと
	 * {@code 1} が {@code Integer} にも {@code Long} にもなるので、
	 * <b>そのままキーにすると取り消しが効かない</b>。
	 * </p>
	 *
	 * @param id 識別子
	 * @return キー
	 */
	private static Object key (Object id) {

		if (id instanceof Number number) {
			return number.longValue();
		}

		return id;

	}

}
