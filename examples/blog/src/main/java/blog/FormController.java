package blog;

import io.jimble.util.convertor.UploadFile;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;
import io.jimble.web.router.Controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * サーバーサイドのフォーム（要件 F-S-05〜07 / F-W-06）
 *
 * <p>
 * <b>ブラウザから普通に POST するフォーム</b>を1つ置く。
 * これが無いと CSRF / Flash / Cookie / ファイルアップロードが
 * サンプルで一度も動かない（要件 10.3）。
 * </p>
 *
 * <pre>
 * GET  /form   フォームを出す（CSRF トークンを発行する）
 * POST /form   受け取って、リダイレクトして、Flash で結果を出す
 * </pre>
 *
 * <p>
 * <b>POST のあとはリダイレクトする</b>（PRG）。
 * そのままページを返すと、リロードで二重投稿になる。
 * リダイレクト先へ渡すメッセージが Flash である（要件 F-S-07）。
 * </p>
 */
public class FormController extends Controller {

	/** 画像の置き場所 */
	static final String UPLOAD_DIR = "build/uploads";

	/** 受け付ける画像の拡張子 */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

	/** 前に見た記事を覚えておく Cookie */
	private static final String COOKIE_LAST_POST = "last_post";

	{

// docs:begin csrf-form
		path("/form", () -> {

			/*
			 * 状態を変えるものだけ検証する。
			 * GET / HEAD / OPTIONS / TRACE は素通しする（Csrf.SAFE_METHODS）。
			 */
			before(Csrf::verify);

			get("", FormController::show);
			post("", FormController::submit);

		});
// docs:end

	}

	/**
	 * フォームを出す
	 *
	 * @param context	コンテキスト
	 */
	private static void show (WebContext context) {

		/*
		 * ここで Cookie にトークンが載る（要件 F-S-06）。
		 * フォームを出すページで呼ぶ。
		 */
		context.response().putData("csrf_token", Csrf.token(context));

		/*
		 * 直前の POST が残したメッセージ。
		 * 読んだ時点で消える（要件 F-S-07）。
		 */
		context.response().putData("message", context.flash().get("message"));

		// 前に見た記事（Cookie。要件 F-S-05）
		context.response().putData("last_post", context.cookies().get(COOKIE_LAST_POST));

		context.response().putData("posts", BlogApp.listPosts());
		context.response().view("blog/form.jte");

	}

	/**
	 * 受け取る
	 *
	 * @param context	コンテキスト
	 * @throws Exception	保存に失敗した場合
	 */
	private static void submit (WebContext context) throws Exception {

		Data request = context.request().bodyAll();

		String title = request.getStringOptional("title");

		if (title.isEmpty()) {

			context.flash().put("message", "タイトルを入れてください");
			context.response().redirect("/form");
			return;

		}

		// 画像（要件 F-W-06）
		String imageName = saveImage(context);
		if (imageName != null) {
			request.putData("image_name", imageName);
		}

		long id = BlogApp.insertPostWithNotice(request);

		if (id <= 0) {
			context.flash().put("message", "登録できませんでした");
			context.response().redirect("/form");
			return;
		}

		// 次に来たときに分かるように覚えておく（30日）
		context.cookies().put(COOKIE_LAST_POST, String.valueOf(id), 30L * 24 * 60 * 60);

		context.flash().put("message", "「%s」を登録しました（ID %d）".formatted(title, id));

		/*
		 * POST のあとはリダイレクトする。
		 * そのまま返すと、リロードで同じものがもう1件入る。
		 */
		context.response().redirect("/form");

	}

	/**
	 * 画像を保存する（要件 F-W-06）
	 *
	 * <p>
	 * <b>送られてきたファイル名をそのまま使わない。</b>
	 * {@code ../../etc/passwd} のような名前が来ると、置き場所の外へ書けてしまう。
	 * 名前はこちらで付け、拡張子だけを見る。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @return	保存したファイル名。画像が無ければ null
	 * @throws Exception	保存に失敗した場合
	 */
	// docs:begin upload-save
	private static String saveImage (WebContext context) throws Exception {

		UploadFile uploadFile = firstFile(context);

		if (uploadFile == null || uploadFile.fileSize <= 0) {
			return null;
		}

		String extension = extensionOf(uploadFile.fileName);

		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new io.jimble.web.http.HttpException(
				400, "受け付けられない形式です: %s（%s のみ）"
					.formatted(uploadFile.fileName, String.join(" ", ALLOWED_EXTENSIONS)));
		}

		Path dir = Path.of(UPLOAD_DIR);
		Files.createDirectories(dir);

		String saved = UUID.randomUUID() + extension;

		/*
		 * 一時ファイルはリクエストが終わると消える（要件 F-W-06）。
		 * 残したいものはここで移す。
		 */
		Files.copy(uploadFile.file.toPath(), dir.resolve(saved), StandardCopyOption.REPLACE_EXISTING);

		return saved;

	}
	// docs:end

	/**
	 * 最初のファイル
	 *
	 * @param context	コンテキスト
	 * @return	ファイル。無ければ null
	 */
	private static UploadFile firstFile (WebContext context) {

		for (Object value : context.request().bodyFile().values()) {

			if (value instanceof UploadFile uploadFile) {
				return uploadFile;
			}

			if (value instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof UploadFile uploadFile) {
						return uploadFile;
					}
				}
			}

		}

		return null;

	}

	/**
	 * 拡張子
	 *
	 * @param fileName	ファイル名
	 * @return	拡張子（{@code .png} の形。無ければ空文字）
	 */
	private static String extensionOf (String fileName) {

		if (fileName == null) {
			return "";
		}

		int index = fileName.lastIndexOf('.');

		return index <= 0 ? "" : fileName.substring(index).toLowerCase(Locale.ROOT);

	}

}
