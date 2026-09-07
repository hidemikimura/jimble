package io.jimble.web.template;

import io.jimble.util.data.Data;

import java.io.Writer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使うテンプレートエンジンを決める
 *
 * <p>
 * 既定は jte（{@link JteEngine}）。<b>差し替えるときは起動時に1回だけ</b>
 * {@link #use(TemplateEngine)} を呼ぶ（要件 F-W-11）。
 * </p>
 *
 * <pre>
 * // 起動時
 * Templates.use(new MyPebbleEngine());
 * </pre>
 *
 * <p>
 * DI コンテナを使わない（原則3）ので、移送元の
 * {@code app().require(TemplateEngine.class)} に当たるものがここになる。
 * <b>差し替え口は1箇所しかなく、コードから辿れる。</b>
 * </p>
 */
public final class Templates {

	/* 使うエンジン */
	private static volatile TemplateEngine engine = null;

	/* 生成の排他（仮想スレッドで固まらないよう synchronized は使わない。NF-P-02） */
	private static final ReentrantLock LOCK = new ReentrantLock();

	private Templates () {}

	/**
	 * 使うエンジンを決める
	 *
	 * @param templateEngine	エンジン（null で既定に戻す）
	 */
	public static void use (TemplateEngine templateEngine) {

		LOCK.lock();
		try {
			engine = templateEngine;
		} finally {
			LOCK.unlock();
		}

	}

	/**
	 * 使うエンジン
	 *
	 * @return	エンジン
	 */
	public static TemplateEngine engine () {

		TemplateEngine current = engine;

		if (current != null) {
			return current;
		}

		LOCK.lock();
		try {

			if (engine == null) {
				engine = new JteEngine();
			}

			return engine;

		} finally {
			LOCK.unlock();
		}

	}

	/**
	 * 描画して文字列で返す
	 *
	 * <p>
	 * 描画結果をキャッシュに載せる、といったレスポンス以外の用途に使う。
	 * </p>
	 *
	 * @param name	テンプレート名
	 * @param model	モデル
	 * @return	描画結果
	 */
	public static String render (String name, Data model) {

		return engine().render(name, model);

	}

	/**
	 * 描画して書き出す
	 *
	 * @param name	テンプレート名
	 * @param model	モデル
	 * @param out	書き出し先
	 */
	public static void render (String name, Data model, Writer out) {

		engine().render(name, model, out);

	}

	/**
	 * レスポンスの Content-Type
	 *
	 * @return	Content-Type
	 */
	public static String responseContentType () {

		TemplateEngine current = engine();

		return current instanceof JteEngine jte ? jte.responseContentType() : "text/html";

	}

}
