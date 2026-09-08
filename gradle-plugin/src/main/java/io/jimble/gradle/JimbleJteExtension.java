package io.jimble.gradle;

import org.gradle.api.provider.Property;

/**
 * jte プラグインの設定
 *
 * <pre>
 * jte {
 *     sourceDirectory = "src/main/jte"                  // テンプレートの置き場所
 *     packageName     = "gg.jte.generated.precompiled"  // 生成するクラスのパッケージ
 *     contentType     = "Html"                          // Html | Plain
 *     trimControlStructures = true
 * }
 * </pre>
 */
public abstract class JimbleJteExtension {

	/**
	 * コンストラクタ
	 */
	public JimbleJteExtension () {

	}

	/**
	 * テンプレートの置き場所（ソースセットごとの既定は {@code src/<セット>/jte}）
	 *
	 * @return	置き場所
	 */
	public abstract Property<String> getSourceDirectory();

	/**
	 * 生成するクラスのパッケージ
	 *
	 * @return	パッケージ
	 */
	public abstract Property<String> getPackageName();

	/**
	 * 出力の種類
	 *
	 * @return	{@code Html} または {@code Plain}
	 */
	public abstract Property<String> getContentType();

	/**
	 * 制御構造の行を出力から落とすか
	 *
	 * @return	落とす場合 = true
	 */
	public abstract Property<Boolean> getTrimControlStructures();

	/**
	 * HTML コメントを出力に残すか
	 *
	 * @return	残す場合 = true
	 */
	public abstract Property<Boolean> getHtmlCommentsPreserved();

}
