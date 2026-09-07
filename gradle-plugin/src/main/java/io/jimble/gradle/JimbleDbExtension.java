package io.jimble.gradle;

import org.gradle.api.provider.Property;

/**
 * {@code jimble} ブロックの設定
 *
 * <pre>
 * jimble {
 *     // 生成コードの置き場所（既定 src/main/java）
 *     sourceRoot = "src/main/java"
 *     // compileJava の前に migrate → codegen を流すか（既定はローカルのみ）
 *     autoGenerate = true
 *     // 実行時の env（既定 local）
 *     env = "local"
 * }
 * </pre>
 */
public abstract class JimbleDbExtension {

	/** 既定のソースルート */
	public static final String DEFAULT_SOURCE_ROOT = "src/main/java";

	/** 既定の環境名 */
	public static final String DEFAULT_ENV = "local";

	/**
	 * 生成コードのソースルート
	 *
	 * @return	ソースルート
	 */
	public abstract Property<String> getSourceRoot ();

	/**
	 * {@code compileJava} の前に {@code migrate} → {@code codegen} を流すか
	 *
	 * <p>
	 * 既定は「{@link #getEnv()} が {@code local} のときだけ」（要件 F-G-07）。
	 * <b>環境判定に依存しない形でも指定できる</b>（要件 F-G-17）ので、
	 * ここに true / false を直接書けばそれが優先される。
	 * </p>
	 *
	 * @return	流す場合 = true
	 */
	public abstract Property<Boolean> getAutoGenerate ();

	/**
	 * 実行時に渡す環境名
	 *
	 * <p>{@code -Denv=<値>} として CLI に渡る。</p>
	 *
	 * @return	環境名
	 */
	public abstract Property<String> getEnv ();

}
