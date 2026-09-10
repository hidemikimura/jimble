package io.jimble.load;

import io.jimble.util.conf.Conf;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.ServerConf;
import io.jimble.web.server.JimbleServer;

import io.helidon.webserver.WebServer;

/**
 * 測る相手（要件 NF-P-08）
 *
 * <p>
 * <b>どちらも同じことをする。</b>{@code GET /} に短い文字列を返すだけである。
 * 中身を揃えてあるので、<b>差はそのまま「jimble の上乗せ分」</b>になる。
 * </p>
 *
 * <p>
 * サンプルアプリ（{@code examples/blog}）は<b>ここには入れない。</b>
 * あれは DB もテンプレートも通るので、<b>そのまま起こしたほうが実物に近い</b>
 * （{@code load.sh} が {@code :examples:blog:run} で起こす）。
 * </p>
 */
public final class Targets {

	/** 返す中身（両方で同じにする） */
	private static final String BODY = "hello";

	private Targets () {
	}

	/**
	 * 素の helidon
	 *
	 * <p>jimble が乗っているサーバーそのもの。ここが上限である。</p>
	 *
	 * @param port	ポート
	 * @return	止めるためのもの
	 */
	public static Runnable helidon (int port) {

		WebServer server = WebServer.builder()
			.port(port)
			.routing(routing -> routing.get("/", (request, response) -> response.send(BODY)))
			.build()
			.start();

		return server::stop;

	}

	/**
	 * jimble（最小のルート1本）
	 *
	 * @param port	ポート
	 * @return	止めるためのもの
	 */
	public static Runnable jimble (int port) {

		return jimble(port, true);

	}

	/**
	 * jimble（最小のルート1本）
	 *
	 * <p>
	 * <b>アクセスログの有無で2回測るためにある。</b>
	 * 素の helidon は1行も書かないので、そのままでは
	 * <b>「上乗せ分」と「helidon が持っていない機能の代金」が混ざる</b>。
	 * </p>
	 *
	 * @param port		ポート
	 * @param accessLog	アクセスログを出すか
	 * @return	止めるためのもの
	 */
	public static Runnable jimble (int port, boolean accessLog) {

		if (!accessLog) {
			/*
			 * <b>設定を読む前に置く。</b>Conf は最初に読んだ内容を持ち回すので、
			 * あとから置いても効かない（念のため読み直す）。
			 */
			System.setProperty(ServerConf.KEY_ACCESS_LOG, "false");
			Conf.reload();
		}

		JimbleServer server = JimbleServer.start(new MinimalApp(), port);

		return server::stop;

	}

	/** ルートを1本だけ持つアプリ */
	private static final class MinimalApp extends JimbleApp {

		{
			get("/", context -> context.response().send(BODY));
		}

	}

}
