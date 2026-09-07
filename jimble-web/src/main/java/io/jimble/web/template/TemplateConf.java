package io.jimble.web.template;

import io.jimble.util.conf.Conf;

/**
 * テンプレートの設定
 *
 * <pre>
 * template {
 *   content_type = Html                            # Html | Plain
 *   package      = "gg.jte.generated.precompiled"  # 事前コンパイルの出力パッケージ
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

}
