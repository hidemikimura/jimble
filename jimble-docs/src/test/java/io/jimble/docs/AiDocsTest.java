package io.jimble.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI に渡すものが古くなっていないか（要件 NF-D-09）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>skill はドキュメントより速く腐る。</b>
 * サイトのページは書き換えれば出ていくが、skill は<b>手で書いた写し</b>なので、
 * ページの名前を変えても、モジュールを増やしても、<b>何も起きない</b>。
 * 気づくのは、利用者の AI が<b>存在しない URL を引きに行ったとき</b>である。
 * </p>
 *
 * <p>
 * ここで見るのは「決め忘れ」だけで、<b>中身が正しいかは人が読む</b>——
 * {@link ApiSurfaceTest} と同じ考え方である。
 * </p>
 */
class AiDocsTest {

	/** skill の置き場 */
	private static final String SKILLS = ".claude/skills";

	/** 既定の言語（ここのページ名を正とする） */
	private static final String LANGUAGE = "ja";

	/** {@code `name.md`} を拾う */
	private static final Pattern PAGE = Pattern.compile("`([a-z0-9-]+)\\.md`");

	/** jimble に無い注釈（原則2 / 原則3で持たないと決めたもの） */
	private static final List<String> FOREIGN = List.of(
		"@RestController", "@Controller", "@RequestMapping", "@GetMapping", "@PostMapping"
		, "@Autowired", "@Inject", "@Component", "@Service", "@Repository", "@Configuration"
		, "@Bean", "@Value(", "@Transactional", "@PreAuthorize", "@Secured", "@RolesAllowed"
		, "@Entity", "@Table(", "@Id", "@Column(", "@Scheduled", "@EnableBatchProcessing"
		, "@JmsListener", "@KafkaListener", "@RabbitListener", "@Path(", "@GET", "@POST"
	);

	/** {@code https://jimble.io/ja/name.md} を拾う */
	private static final Pattern URL = Pattern.compile("https://jimble\\.io/([a-z]{2})/([a-z0-9-]+)\\.md");

	@Test
	@DisplayName("NF-D-09 skill が指すページが実在する")
	void skillsPointAtRealPages () throws IOException {

		Set<String> slugs = slugs();
		List<String> missing = new ArrayList<>();

		for (Path skill : skills()) {

			String text = Files.readString(skill, StandardCharsets.UTF_8);

			for (Matcher matcher = PAGE.matcher(text); matcher.find(); ) {
				if (!slugs.contains(matcher.group(1))) {
					missing.add("%s: `%s.md`".formatted(skill.getFileName(), matcher.group(1)));
				}
			}

			for (Matcher matcher = URL.matcher(text); matcher.find(); ) {
				if (!slugs.contains(matcher.group(2))) {
					missing.add("%s: %s".formatted(skill.getFileName(), matcher.group(0)));
				}
			}

		}

		assertTrue(missing.isEmpty()
			, """
				skill が、もう無いページを指しています。
				利用者の AI がここを引きに行って 404 になります。

				%s

				ページの名前を変えたなら skill も直してください（docs/site/%s/ が正）。
				""".formatted(String.join("\n", missing), LANGUAGE));

	}

	@Test
	@DisplayName("NF-D-09 skill のコード例に、jimble に無い注釈が出ていない")
	void skillsDoNotShowForeignAnnotations () throws IOException {

		/*
		 * <b>`@Override` は使う。</b>禁じているのは<b>枠組みの注釈</b>（原則2 / 原則3）で、
		 * Java 標準の注釈ではない——バッチも MQ も `@Override` だらけである。
		 *
		 * <b>名前を挙げること自体は要る。</b>「これは無い」と書くために出てくるので、
		 * 見るのは<b>コードブロックの中で使ってしまっている</b>ほうだけ。
		 * そこに出ていると、AI は「これが jimble の書き方だ」と読む。
		 */
		List<String> found = new ArrayList<>();

		for (Path skill : skills()) {

			boolean inCode = false;

			for (String line : Files.readString(skill, StandardCharsets.UTF_8).split("\n")) {

				if (line.strip().startsWith("```")) {
					inCode = !inCode;
					continue;
				}

				if (!inCode) {
					continue;
				}

				for (String annotation : FOREIGN) {
					if (line.contains(annotation)) {
						found.add("%s: %s".formatted(skill.getFileName(), line.strip()));
					}
				}

			}

		}

		assertTrue(found.isEmpty()
			, "skill のコード例に、jimble に無い注釈が出ています。AI はこれを真似します。\n"
				+ String.join("\n", found));

	}

	@Test
	@DisplayName("NF-D-09 skill に front matter がある")
	void skillsHaveFrontMatter () throws IOException {

		List<Path> skills = skills();

		assertFalse(skills.isEmpty(), ".claude/skills/ に SKILL.md がありません");

		for (Path skill : skills) {

			String text = Files.readString(skill, StandardCharsets.UTF_8);

			assertTrue(text.startsWith("---\n"), skill + " に front matter がありません");
			assertTrue(text.contains("\nname:"), skill + " に name がありません");
			assertTrue(text.contains("\ndescription:"), skill + " に description がありません");

		}

	}

	// region 補助

	/**
	 * skill の一覧
	 *
	 * @return	SKILL.md のパス
	 * @throws IOException	読めなかった場合
	 */
	private static List<Path> skills () throws IOException {

		Path root = root().resolve(SKILLS);

		if (!Files.isDirectory(root)) {
			return List.of();
		}

		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(path -> path.getFileName().toString().equals("SKILL.md")).sorted().toList();
		}

	}

	/**
	 * サイトにあるページの名前
	 *
	 * @return	名前
	 * @throws IOException	読めなかった場合
	 */
	private static Set<String> slugs () throws IOException {

		Set<String> slugs = new LinkedHashSet<>();

		try (Stream<Path> paths = Files.list(root().resolve("docs/site").resolve(LANGUAGE))) {
			paths.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".md"))
				.forEach(name -> slugs.add(name.substring(0, name.length() - 3)));
		}

		return slugs;

	}

	/**
	 * リポジトリの根
	 *
	 * @return	パス
	 */
	private static Path root () {

		Path path = Path.of("").toAbsolutePath();

		while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
			path = path.getParent();
		}

		if (path == null) {
			throw new IllegalStateException("リポジトリの根が見つかりません");
		}

		return path;

	}

	// endregion

}
