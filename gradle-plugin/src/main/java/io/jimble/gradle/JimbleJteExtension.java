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

	/** テンプレートの置き場所（ソースセットごとの既定は {@code src/<セット>/jte}） */
	public abstract Property<String> getSourceDirectory();

	/** 生成するクラスのパッケージ */
	public abstract Property<String> getPackageName();

	/** {@code Html} または {@code Plain} */
	public abstract Property<String> getContentType();

	/** 制御構造の行を出力から落とすか */
	public abstract Property<Boolean> getTrimControlStructures();

	/** HTML コメントを出力に残すか */
	public abstract Property<Boolean> getHtmlCommentsPreserved();

}
