package approval.pages;

import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

/**
 * 転送先（リバースプロキシの相手）
 *
 * <p>
 * <b>これは「別のサービス」のつもりのもの</b>である。
 * ふつうは別のプロセス・別のホストにあるが、
 * サンプルとして動かせるように<b>同じプロセスの別ポート</b>に立てている。
 * </p>
 *
 * <p>
 * 中身は受け取ったものをそのまま返すだけである。
 * そうすると、<b>プロキシが何を足して何を落としたか</b>が
 * 転送先の目から見える。
 * </p>
 */
public class UpstreamApp extends JimbleApp {

	/**
	 * ルート定義
	 */
	public UpstreamApp () {

		/*
		 * プロキシが足すものを、そのまま返す。
		 *
		 * X-Forwarded-For / X-Forwarded-Host / X-Forwarded-Proto / X-Real-IP は
		 * <b>プロキシが足している</b>。転送元では付いていない。
		 */
		get("/echo", context -> context.response()
			.json("from", "upstream")
			.json("path", context.request().rawPath())
			.json("query", context.request().query())
			.json("x_forwarded_for", context.request().header().getStringOptional("x-forwarded-for"))
			.json("x_forwarded_host", context.request().header().getStringOptional("x-forwarded-host"))
			.json("x_forwarded_proto", context.request().header().getStringOptional("x-forwarded-proto"))
			.json("x_real_ip", context.request().header().getStringOptional("x-real-ip"))
			/*
			 * 転送元が付けた Host は落ちている。
			 * hop-by-hop として落とされ、転送先向けの Host に張り替えられる
			 */
			.json("host", context.request().header().getStringOptional("host")));

		// 本文とメソッドがそのまま通ることを見るため
		post("/echo", context -> context.response()
			.json("from", "upstream")
			.json("method", context.request().method())
			.json("body", context.request().bodyAll()));

		// 転送先が返したステータスがそのまま返ることを見るため
		get("/teapot", context -> context.response().code(418).json("from", "upstream"));

		/*
		 * リダイレクトは<b>プロキシが追わない</b>（Redirect.NEVER）。
		 * 302 がそのままクライアントへ返る
		 */
		get("/moved", context -> {
			context.response().setResponseHeader("Location", "/echo");
			context.response().send(302);
		});

	}

	/**
	 * 転送先を立てる
	 *
	 * @return	サーバー（ポートは 0 なので空きポートが割り当てられる）
	 */
	public static JimbleServer start () {

		return JimbleServer.start(new UpstreamApp(), 0);

	}

}
