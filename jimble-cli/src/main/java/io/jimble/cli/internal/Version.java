package io.jimble.cli.internal;

/**
 * jimble の版
 *
 * <p>
 * jar のマニフェスト（{@code Implementation-Version}）から読む。
 * <b>ここを手で書かない。</b>雛形が参照する版とビルドした版がずれると、
 * 生成したプロジェクトが解決できない依存を持つことになる。
 * </p>
 */
final class Version {

	/** マニフェストが無いとき（IDE から直接動かしたときなど） */
	private static final String UNKNOWN = "1.0.1-SNAPSHOT";

	private Version () {}

	/**
	 * いまの版
	 *
	 * @return	版
	 */
	static String current () {

		Package pkg = Version.class.getPackage();

		if (pkg == null) {
			return UNKNOWN;
		}

		String version = pkg.getImplementationVersion();

		return version == null || version.isBlank() ? UNKNOWN : version;

	}

}
