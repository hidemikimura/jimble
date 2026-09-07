package hello;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.HttpException;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;

/**
 * 最小のサンプルアプリケーション
 *
 * <pre>
 * ./gradlew :examples:hello:run
 *
 * curl http://localhost:9000/hello
 * curl http://localhost:9000/users/42
 * curl "http://localhost:9000/search/a%2Fb"
 * curl -i http://localhost:9000/admin/secret
 * </pre>
 */
public class HelloApp extends JimbleApp {

	/** 認証をスキップする */
	static final io.jimble.web.router.AttributeKey<Boolean> NO_AUTH =
		new io.jimble.web.router.AttributeKey<>("no_auth", false);

	{

		// エラーは全部ここで整形する（未マッチの 404 もここに来る）
		// docs:begin error-handler
		error((context, cause, statusCode) ->
			context.response().code(statusCode).send("エラー: %d %s%n".formatted(statusCode, cause.getMessage())));
		// docs:end

		// docs:begin hello-route
		get("/hello", context -> context.response().send("hello, jimble\n"));
		// docs:end

		get("/users/{id}", context ->
			context.response().send("user id = %s%n".formatted(context.route().variables().get("id"))));

		// %2F を含んでもパスパラメータ1つとして受け取れる
		get("/search/{keyword}", context ->
			context.response().send("keyword = %s%n".formatted(context.route().variables().get("keyword"))));

		get("/files/*", context ->
			context.response().send("file = %s%n".formatted(context.route().variables().wildcard())));

		// ヘルスチェック
		get("/health_check", context -> context.response().send());
		head("/health_check", context -> context.response().send());

		install(AdminController::new);

	}

	/**
	 * 認証を要求する
	 *
	 * @param context	コンテキスト
	 */
	static void requireAuth (WebContext context) {

		if (context.route().route().attribute(NO_AUTH)) {
			return;
		}

		if (!"secret".equals(context.request().header().getString("x-token"))) {
			throw new HttpException(401, "認証が必要です");
		}

	}

	/**
	 * メインエントリポイント
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		JimbleServer.start(new HelloApp());

	}

}
