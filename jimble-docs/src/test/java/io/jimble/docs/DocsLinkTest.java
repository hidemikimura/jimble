package io.jimble.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 例外・警告・jimbleCheck が案内する引き先が実在する（要件 D-189）
 *
 * <p>
 * <b>引き先が 404 だと、AI はそこで諦めて推測で直す。</b>付けないより悪い。
 * ページの名前を変えたときに、案内だけが古いまま残るのをここで止める。
 * </p>
 */
class DocsLinkTest {

	/* Docs.see("x") / Docs.url("x") */
	private static final Pattern DOCS_CALL = Pattern.compile("Docs\\.(?:see|url)\\(\\s*\"([a-z0-9\\-]+)\"\\s*\\)");

	/* jimbleCheck の DOCS + "x.md" */
	private static final Pattern CHECK_LINK = Pattern.compile("DOCS\\s*\\+\\s*\"([a-z0-9\\-]+)\\.md\"");

	@Test
	@DisplayName("D-189 案内している jimble.io のページが、日本語と英語の両方にある")
	void linkedPagesExist () throws IOException {

		Path root = projectRoot();

		TreeSet<String> pages = new TreeSet<>();
		List<String> problems = new ArrayList<>();

		try (Stream<Path> files = Files.walk(root)) {

			for (Path file : files
				.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> path.toString().contains("/src/main/java/"))
				.filter(path -> !path.toString().contains("/build/"))
				.toList()) {

				String text = Files.readString(file, StandardCharsets.UTF_8);

				for (Pattern pattern : List.of(DOCS_CALL, CHECK_LINK)) {
					Matcher matcher = pattern.matcher(text);
					while (matcher.find()) {
						pages.add(matcher.group(1));
						for (String lang : List.of("ja", "en")) {
							if (!Files.exists(root.resolve("docs/site/%s/%s.md".formatted(lang, matcher.group(1))))) {
								problems.add("%s: docs/site/%s/%s.md がありません".formatted(root.relativize(file), lang, matcher.group(1)));
							}
						}
					}
				}

			}

		}

		assertTrue(pages.size() >= 8, "案内が少なすぎる（探し方が壊れている）: " + pages);
		assertTrue(problems.isEmpty(), String.join("\n", problems));

	}

	private static Path projectRoot () {

		Path at = Path.of("").toAbsolutePath();

		while (at != null) {
			if (Files.exists(at.resolve("settings.gradle.kts")) && Files.isDirectory(at.resolve("docs/site"))) {
				return at;
			}
			at = at.getParent();
		}

		throw new AssertionError("リポジトリの根が見つかりません");

	}

}
