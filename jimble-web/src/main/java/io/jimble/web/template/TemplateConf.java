package io.jimble.web.template;

import io.jimble.util.conf.Conf;

/**
 * テンプレートの設定
 *
 * <pre>
 * template {
 *   content_type = Html                            # Html | Plain
 *   package      = "gg.jte.generated.precompiled"  # 事前コンパイルの出力パッケージ
 *   json_fallback = false                          # Accept: application/json ならデータを JSON で返すか
 * }
 * </pre>
 *
 * <p>
 * <b>{@code package} は Gradle プラグイン側の設定と揃っていないと、
 * 実行時にテンプレートが見つからない。</b>既定のまま触らないのが安全。
 * </p>
 */
public final class TemplateConf {

	/** 設定キー：種別 */
	public static final String KEY_CONTENT_TYPE = "template.content_type";

	/** 設定キー：事前コンパイルの出力パッケージ */
	public static final String KEY_PACKAGE = "template.package";

	/**
	 * 設定キー：{@code view()} のページに {@code Accept: application/json} で来たら、データを JSON で返すか（D-210）
	 *
	 * <p>
	 * <b>既定は false。</b>2.2.2 までは必ず返していた（移送元の振る舞い）。
	 * データはテンプレートに渡したもの<b>全部</b>なので、テンプレートが出していない列
	 * （メール、パスワードのハッシュなど）まで、{@code curl -H 'Accept: application/json'} で読めた。
	 * </p>
	 */
	public static final String KEY_JSON_FALLBACK = "template.json_fallback";

	/** 既定の種別 */
	public static final String DEFAULT_CONTENT_TYPE = "Html";

	/** 既定のパッケージ（jte の既定と揃える） */
	public static final String DEFAULT_PACKAGE = "gg.jte.generated.precompiled";

	private TemplateConf () {}

	/**
	 * 種別
	 *
	 * @return	{@code Html} または {@code Plain}
	 */
	public static String contentType () {

		return Conf.conf().getString(KEY_CONTENT_TYPE, DEFAULT_CONTENT_TYPE);

	}

	/**
	 * 事前コンパイルの出力パッケージ
	 *
	 * @return	パッケージ
	 */
	public static String packageName () {

		return Conf.conf().getString(KEY_PACKAGE, DEFAULT_PACKAGE);

	}

	/**
	 * {@code view()} のページで、JSON を求められたらデータを返すか（D-210）
	 *
	 * @return	返すなら true（既定 false）
	 */
	public static boolean jsonFallback () {

		return Conf.conf().getBoolean(KEY_JSON_FALLBACK, false);

	}

}
