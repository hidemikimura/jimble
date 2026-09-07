package blog.ws;

import blog.BlogApp;
import db.blog_example.table.post.Post;
import io.jimble.util.data.Data;
import io.jimble.web.ws.WsHandler;
import io.jimble.web.ws.WsSession;
import io.jimble.web.ws.context.WsContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 記事の更新を流す（要件 F-W-22）
 *
 * <pre>
 * ws://localhost:9000/ws/posts
 *
 * → {"command":"latest"}      いまの一覧を返す
 * → {"command":"subscribe"}   以降 notify() で流れてくる
 * </pre>
 *
 * <h2>配信の一覧はアプリが持つ</h2>
 *
 * <p>
 * <b>jimble は接続の一覧を持たない</b>（要件 F-W-22）。
 * フレームワークが持つと、複数インスタンス構成で
 * 「自分のインスタンスに繋いでいる人にしか届かない」ものになる。
 * それは配信としては間違っているのに、<b>1台で動かしているうちは正しく見える。</b>
 * </p>
 *
 * <p>
 * <b>このクラスが持っている一覧も、同じ意味で1台ぶんである。</b>
 * 複数台にするなら、MQ（要件 F-M-03）か Redis の Pub/Sub を挟んで、
 * 各インスタンスが自分の繋ぎ先へ配ること。
 * </p>
 */
public class PostFeedHandler implements WsHandler {

	/** 購読している接続（このインスタンスぶんだけ） */
	private static final Set<WsSession> SUBSCRIBERS = ConcurrentHashMap.newKeySet();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onOpen (WsContext context) {

		context.session().send(new Data()
			.putData("type", "welcome")
			.putData("commands", List.of("latest", "subscribe")));

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
// docs:begin ws-on-message
	public void onMessage (WsContext context, String message) {

		Data request = Data.fromJsonString(message);

		switch (request.getStringOptional("command")) {

			case "latest" -> context.session().send(new Data()
				.putData("type", "latest")
				.putData("posts", titles()));

			case "subscribe" -> {
				SUBSCRIBERS.add(context.session());
				context.session().send(new Data().putData("type", "subscribed"));
			}

			/*
			 * 何が使えるのかを返す。
			 * 黙って無視すると、クライアント側は
			 * 「届いていないのか、コマンドが違うのか」が分からない。
			 */
			default -> context.session().send(new Data()
				.putData("type", "error")
				.putData("message", "知らないコマンドです")
				.putData("commands", List.of("latest", "subscribe")));

		}

	}
// docs:end

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onClose (WsContext context, int code, String reason) {

		SUBSCRIBERS.remove(context.session());

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onError (WsContext context, Throwable cause) {

		SUBSCRIBERS.remove(context.session());

	}

	/**
	 * 購読している相手に流す
	 *
	 * <p><b>このインスタンスに繋いでいる相手だけ</b>である（クラスの説明を参照）。</p>
	 *
	 * @param title	記事のタイトル
	 */
	public static void notifyNewPost (String title) {

		Data event = new Data()
			.putData("type", "new_post")
			.putData("title", title);

		// 送れなかった相手は落とす
		SUBSCRIBERS.removeIf(session -> !session.send(event));

	}

	/**
	 * いまのタイトル一覧
	 *
	 * @return	タイトル
	 */
	private static List<String> titles () {

		return BlogApp.listPosts().stream()
			.map(row -> row.extractTableData(Post.instance()).getString(Post.title))
			.toList();

	}

}
