package io.jimble.web.template;

import gg.jte.ContentType;
import gg.jte.TemplateNotFoundException;
import gg.jte.output.WriterOutput;
import io.jimble.util.data.Data;

import java.io.Writer;

/**
 * jte でのテンプレート描画（要件 F-W-08 / F-W-10 / O-13）
 *
 * <p>
 * <b>事前コンパイル済みのテンプレートしか描画しない。</b>
 * 依存しているのは {@code gg.jte:jte-runtime} で、
 * <b>この成果物にはコンパイラが入っていない。</b>
 * 「実行時コンパイルに対応しない」（要件 F-W-10）を、
 * 設定ではなく<b>依存関係で保証している。</b>
 * </p>
 *
 * <h2>テンプレートの形</h2>
 *
 * <p>受け取るのは {@link Data} 1つだけである。</p>
 *
 * <pre>
 * &#64;import io.jimble.util.data.Data
 * &#64;param Data data
 *
 * &lt;h1&gt;${data.getString("title")}&lt;/h1&gt;
 * </pre>
 *
 * <p>
 * {@code Data} は {@code Map} を継承しているので、
 * jte にそのまま渡すと<b>「キーごとに名前つき引数へ割り当てる」ほうに解釈される。</b>
 * {@code Object} にキャストして渡し、1引数として扱わせている。
 * </p>
 */
public final class JteEngine implements TemplateEngine {

	/* jte 本体 */
	private final gg.jte.TemplateEngine engine;

	/* 種別 */
	private final ContentType contentType;

	/**
	 * コンストラクタ（設定から組み立てる）
	 */
	public JteEngine () {

		this(ContentType.valueOf(TemplateConf.contentType()), TemplateConf.packageName());

	}

	/**
	 * コンストラクタ
	 *
	 * @param contentType	種別
	 * @param packageName	事前コンパイルの出力パッケージ
	 */
	public JteEngine (ContentType contentType, String packageName) {

		this.contentType = contentType;
		this.engine = gg.jte.TemplateEngine.createPrecompiled(
			// クラスパスから読むのでディレクトリは要らない
			null
			, contentType
			, JteEngine.class.getClassLoader()
			, packageName
		);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void render (String name, Data model, Writer out) {

		try {

			// Map ではなく1つの引数として渡す
			engine.render(name, (Object) model, new WriterOutput(out));

		} catch (TemplateNotFoundException ex) {

			throw new TemplateException(("テンプレートが見つかりません: %s"
				+ " / 事前コンパイルされていない可能性があります。"
				+ "ビルドに io.jimble.jte プラグインを入れて generateJte を通してください")
				.formatted(name), ex);

		} catch (Exception ex) {

			throw new TemplateException("テンプレートの描画に失敗しました: %s".formatted(name), ex);

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean has (String name) {

		try {
			return engine.hasTemplate(name);
		} catch (Exception ex) {
			return false;
		}

	}

	/**
	 * レスポンスの Content-Type
	 *
	 * @return	Content-Type
	 */
	public String responseContentType () {

		return contentType == ContentType.Html ? "text/html" : "text/plain";

	}

}
