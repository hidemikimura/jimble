package io.jimble.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 実コードから抜いてくるコード片（要件 NF-D-03）
 *
 * <p>
 * ドキュメントに書いたコードは、放っておくと必ず古くなる。
 * <b>「コンパイルが通っているコード」だけを載せる</b>のが唯一の防ぎ方である。
 * </p>
 *
 * <p>
 * ソースにこう印を置く。
 * </p>
 *
 * <pre>
 * // docs:begin hello-route
 * get("/hello", context -&gt; context.response().send("hello, jimble"));
 * // docs:end
 * </pre>
 *
 * <p>
 * ドキュメント側ではこう書く。
 * </p>
 *
 * <pre>
 * ```java snippet=hello-route
 * ```
 * </pre>
 *
 * <p>
 * <b>印が見つからなければビルドで落とす。</b>
 * 「引用元を消したのにドキュメントには古いコードが残っている」を、
 * 静かに通してしまわないため。
 * </p>
 */
final class Snippets {

	/** 始まりの印 */
	private static final String BEGIN = "docs:begin ";

	/** 終わりの印 */
	private static final String END = "docs:end";

	/* 名前 → 中身 */
	private final Map<String, String> byName = new LinkedHashMap<>();

	/* 名前 → どこから来たか */
	private final Map<String, String> sources = new LinkedHashMap<>();

	/**
	 * 集める
	 *
	 * @param roots	探す場所
	 * @return	集めたもの
	 */
	static Snippets collect (List<Path> roots) {

		Snippets snippets = new Snippets();

		for (Path root : roots) {

			if (!Files.isDirectory(root)) {
				continue;
			}

			try (Stream<Path> paths = Files.walk(root)) {

				paths.filter(Files::isRegularFile)
					.filter(Snippets::isSource)
					.forEach(snippets::read);

			} catch (IOException ex) {

				throw new UncheckedIOException("ソースを読めませんでした: " + root, ex);

			}

		}

		return snippets;

	}

	/**
	 * 読めるファイルか
	 *
	 * @param path	パス
	 * @return	読める場合 = true
	 */
	private static boolean isSource (Path path) {

		String name = path.getFileName().toString();

		return name.endsWith(".java")
			|| name.endsWith(".kts")
			|| name.endsWith(".jte")
			|| name.endsWith(".conf")
			|| name.endsWith(".sql");

	}

	/**
	 * 1ファイルから抜く
	 *
	 * @param path	パス
	 */
	private void read (Path path) {

		List<String> lines;

		try {
			lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			// 読めないファイルは飛ばす（バイナリなど）
			return;
		}

		String name = null;
		List<String> body = new ArrayList<>();

		for (String line : lines) {

			if (name == null) {

				int index = line.indexOf(BEGIN);

				if (index >= 0) {
					name = line.substring(index + BEGIN.length()).trim();
					body = new ArrayList<>();
				}

				continue;

			}

			if (line.contains(END)) {

				if (byName.containsKey(name)) {
					throw new IllegalStateException(
						"同じ名前のコード片が2つあります: %s（%s と %s）"
							.formatted(name, sources.get(name), path));
				}

				byName.put(name, trimIndent(body));
				sources.put(name, path.toString());

				name = null;
				continue;

			}

			body.add(line);

		}

		if (name != null) {
			throw new IllegalStateException(
				"docs:end が見つかりません: %s（%s）".formatted(name, path));
		}

	}

	/**
	 * 引く
	 *
	 * @param name	名前
	 * @return	中身
	 * @throws IllegalStateException	見つからない場合
	 */
	String get (String name) {

		String body = byName.get(name);

		if (body == null) {
			/*
			 * ここで落とすのが要点である。
			 * 空で通すと、引用元を消したことに誰も気づかない。
			 */
			throw new IllegalStateException("""
				コード片が見つかりません: %s

				ソースに印を置いてください。
				  // docs:begin %s
				  ...
				  // docs:end

				いまある印: %s""".formatted(name, name, String.join(", ", byName.keySet())));
		}

		return body;

	}

	/**
	 * 集めた数
	 *
	 * @return	数
	 */
	int size () {

		return byName.size();

	}

	/**
	 * 名前
	 *
	 * @return	名前
	 */
	List<String> names () {

		return List.copyOf(byName.keySet());

	}

	/**
	 * 共通の字下げを落とす
	 *
	 * <p>
	 * メソッドの中から抜くと、全行がタブ2つぶん右に寄っている。
	 * そのまま載せると読みにくい。
	 * </p>
	 *
	 * @param lines	行
	 * @return	落としたもの
	 */
	private static String trimIndent (List<String> lines) {

		// 前後の空行を落とす
		int from = 0;
		int to = lines.size();

		while (from < to && lines.get(from).isBlank()) {
			from++;
		}

		while (to > from && lines.get(to - 1).isBlank()) {
			to--;
		}

		List<String> body = lines.subList(from, to);

		if (body.isEmpty()) {
			return "";
		}

		int common = Integer.MAX_VALUE;

		for (String line : body) {

			if (line.isBlank()) {
				continue;
			}

			int indent = 0;
			while (indent < line.length() && (line.charAt(indent) == '\t' || line.charAt(indent) == ' ')) {
				indent++;
			}

			common = Math.min(common, indent);

		}

		if (common == Integer.MAX_VALUE || common == 0) {
			return String.join("\n", body);
		}

		List<String> trimmed = new ArrayList<>();

		for (String line : body) {
			trimmed.add(line.length() >= common ? line.substring(common) : line.strip());
		}

		return String.join("\n", trimmed);

	}

}
