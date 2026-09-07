package io.jimble.cli;

import java.util.Locale;

/**
 * プロジェクトの名前から、パッケージ名などを決める（要件 F-X-01）
 *
 * <p>
 * <b>名前を1つ聞いて、残りは全部そこから決める。</b>
 * 対話でいくつも聞くと、答えを間違えたときに作り直すことになる。
 * </p>
 *
 * <pre>
 * my-blog   → パッケージ myblog  / DB 名 my_blog
 * my_blog   → パッケージ myblog  / DB 名 my_blog
 * My Blog   → パッケージ myblog  / DB 名 my_blog
 * MyBlog    → パッケージ myblog  / DB 名 myblog
 * </pre>
 *
 * <p>
 * <b>大文字の切れ目では区切らない。</b>{@code MyBlog} を {@code my_blog} にすると
 * 読みやすいが、{@code APIServer} が {@code a_p_i_server} になる。
 * 「見た目から結果が予想できる」ほうを採る。
 * </p>
 *
 * @param name			そのままの名前（ディレクトリ名になる）
 * @param packageName	パッケージ名
 * @param databaseName	DB 名の候補
 */
public record ProjectName(String name, String packageName, String databaseName) {

	/** Java の予約語のうち、パッケージ名になりうるもの */
	private static final java.util.Set<String> RESERVED = java.util.Set.of(
		"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class"
		, "const", "continue", "default", "do", "double", "else", "enum", "extends", "final"
		, "finally", "float", "for", "goto", "if", "implements", "import", "instanceof"
		, "int", "interface", "long", "native", "new", "package", "private", "protected"
		, "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized"
		, "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
		, "_", "record", "var", "yield"
	);

	/**
	 * 名前から決める
	 *
	 * @param name	名前
	 * @return	決めたもの
	 * @throws IllegalArgumentException	名前として使えない場合
	 */
	public static ProjectName of (String name) {

		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("プロジェクト名を指定してください（例: jimble new my-blog）");
		}

		String trimmed = name.trim();

		/*
		 * ディレクトリを作るので、パス区切りが混ざっていたら止める。
		 * 「jimble new ../etc」で外へ出られないようにする。
		 */
		if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
			throw new IllegalArgumentException(
				"プロジェクト名にパスは書けません: %s".formatted(trimmed));
		}

		String packageName = toPackageName(trimmed);

		if (packageName.isEmpty()) {
			throw new IllegalArgumentException(
				"プロジェクト名から Java のパッケージ名を作れません: %s（英数字を入れてください）"
					.formatted(trimmed));
		}

		if (RESERVED.contains(packageName)) {
			throw new IllegalArgumentException(
				"「%s」は Java の予約語なのでパッケージ名にできません".formatted(packageName));
		}

		return new ProjectName(trimmed, packageName, toDatabaseName(trimmed));

	}

	/**
	 * パッケージ名にする
	 *
	 * <p>英数字だけを残し、小文字にする。先頭が数字なら落とす。</p>
	 *
	 * @param name	名前
	 * @return	パッケージ名
	 */
	private static String toPackageName (String name) {

		StringBuilder builder = new StringBuilder();

		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {

			if (c >= 'a' && c <= 'z') {
				builder.append(c);
				continue;
			}

			// 先頭が数字だと識別子にならない
			if (c >= '0' && c <= '9' && !builder.isEmpty()) {
				builder.append(c);
			}

		}

		return builder.toString();

	}

	/**
	 * DB 名にする
	 *
	 * <p>区切りを {@code _} に寄せる。</p>
	 *
	 * @param name	名前
	 * @return	DB 名
	 */
	private static String toDatabaseName (String name) {

		String replaced = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");

		// 前後の _ を落とす
		replaced = replaced.replaceAll("^_+", "").replaceAll("_+$", "");

		return replaced.isEmpty() ? "app" : replaced;

	}

}
