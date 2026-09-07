package hello;

import io.jimble.web.router.Controller;

/**
 * 管理画面
 *
 * <p>
 * {@code before} で認証を見る。認証をスキップしたいルートには
 * {@link HelloApp#NO_AUTH} を立てる。
 * </p>
 */
public class AdminController extends Controller {

	{
		path("/admin", () -> {

			before(HelloApp::requireAuth);

			// 認証不要
			post("/login", context -> context.response().send("ログインしました\n"))
				.attribute(HelloApp.NO_AUTH, true);

			get("/secret", context -> context.response().send("秘密のページ\n"));

		});
	}

}
