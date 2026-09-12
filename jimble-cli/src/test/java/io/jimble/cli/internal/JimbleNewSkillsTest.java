package io.jimble.cli.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code jimble new} が skill を置くか（要件 NF-D-09）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>skill の正は {@code .claude/skills/} の1か所だけ</b>で、
 * 雛形へはビルドが写している。写し方は3つの場所に分かれている——
 * {@code build.gradle.kts} の {@code copySkills}、{@link Skeleton#ENTRIES}、
 * そして jar の作り方。<b>どれが欠けても、静かに配られなくなる</b>。
 * </p>
 *
 * <p>
 * 欠けても<b>ビルドは通り、{@code jimble new} も成功する</b>。
 * 気づくのは、利用者の AI が Spring のつもりで書き始めたときである。
 * </p>
 */
class JimbleNewSkillsTest {

	/** skill の正の置き場 */
	private static final String SKILLS = ".claude/skills";

	@Test
	@DisplayName("NF-D-09 jimble new が .claude/skills/ に skill を置く")
	void generatesSkills (@TempDir Path dir) throws IOException {

		Path root = NewCommand.run("my-blog", dir);

		Set<String> names = names();

		assertFalse(names.isEmpty(), SKILLS + " に skill がありません");

		for (String name : names) {

			Path skill = root.resolve(".claude/skills/%s/SKILL.md".formatted(name));

			assertTrue(Files.exists(skill), "置かれていません: " + skill);

			String text = Files.readString(skill, StandardCharsets.UTF_8);

			assertTrue(text.startsWith("---\n"), name + " の front matter がありません");
			assertTrue(text.contains("\nname: " + name), name + " の name が合っていません");

		}

	}

	@Test
	@DisplayName("NF-D-09 skill を足したら Skeleton にも足してある")
	void everySkillIsInTheSkeleton () throws IOException {

		Set<String> inSkeleton = new LinkedHashSet<>();

		for (Skeleton.Entry entry : Skeleton.ENTRIES) {
			if (entry.target().startsWith(".claude/skills/")) {
				inSkeleton.add(entry.target().split("/")[2]);
			}
		}

		List<String> missing = new ArrayList<>();

		for (String name : names()) {
			if (!inSkeleton.contains(name)) {
				missing.add(name);
			}
		}

		assertTrue(missing.isEmpty()
			, """
				%s に足した skill が、雛形に入っていません。
				jimble new で作ったプロジェクトには配られません。

				%s

				Skeleton.ENTRIES に足してください。
				""".formatted(SKILLS, String.join("\n", missing)));

		assertEquals(names(), inSkeleton, "雛形にだけある skill があります（消し忘れ）");

	}

	@Test
	@DisplayName("NF-D-09 雛形のリソースが jar に入っている")
	void everyEntryResolves () throws IOException {

		List<String> missing = new ArrayList<>();

		for (Skeleton.Entry entry : Skeleton.ENTRIES) {

			String path = Skeleton.resourcePath(entry);

			try (InputStream stream = JimbleNewSkillsTest.class.getResourceAsStream(path)) {
				if (stream == null) {
					missing.add(path);
				}
			}

		}

		assertTrue(missing.isEmpty()
			, "雛形のリソースがありません（jar の作り方か copySkills を見てください）:\n"
				+ String.join("\n", missing));

	}

	/**
	 * {@code .claude/skills/} にある skill の名前
	 *
	 * @return	名前
	 * @throws IOException	読めなかった場合
	 */
	private static Set<String> names () throws IOException {

		Path root = root().resolve(SKILLS);

		if (!Files.isDirectory(root)) {
			return Set.of();
		}

		Set<String> names = new LinkedHashSet<>();

		try (Stream<Path> paths = Files.list(root)) {
			paths.filter(path -> Files.exists(path.resolve("SKILL.md")))
				.map(path -> path.getFileName().toString())
				.sorted()
				.forEach(names::add);
		}

		return names;

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

}
