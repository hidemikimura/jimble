package io.jimble.gradle.ai;

import io.jimble.gradle.ai.SkillsInstaller.Result;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * skill を jimble の版に揃える（要件 D-187）
 *
 * <p>
 * <b>固めたいのは「手で直したものは上書きしない」こと。</b>揃えるたびにアプリの書き足しが消えると、
 * 誰もこのタスクを使わなくなる。逆に<b>jimble が置いたままのものは必ず入れ替わる</b>こと——
 * 入れ替わらなければ、古い skill が残る今の問題がそのまま残る。
 * </p>
 */
class SkillsInstallerTest {

	private static final byte[] AGENTS = "# agents\n".getBytes(StandardCharsets.UTF_8);
	private static final byte[] CLAUDE = "@AGENTS.md\n".getBytes(StandardCharsets.UTF_8);

	/** 1つの skill の大きさの上限（読むのに手間がかからず、要るところが埋もれない大きさ） */
	private static final int MAX_SKILL_BYTES = 8 * 1024;

	private static Map<String, byte[]> without (Map<String, byte[]> skills, String path) {

		Map<String, byte[]> fewer = new LinkedHashMap<>(skills);
		fewer.remove(path);
		return fewer;

	}

	private static Map<String, byte[]> skills (String body) {

		Map<String, byte[]> skills = new LinkedHashMap<>();
		skills.put("jimble/SKILL.md", ("jimble " + body).getBytes(StandardCharsets.UTF_8));
		skills.put("jimble-db/SKILL.md", ("db " + body).getBytes(StandardCharsets.UTF_8));
		return skills;

	}

	private static SkillsInstaller.Report install (Path root, Map<String, byte[]> skills, String version, boolean overwrite) {

		return SkillsInstaller.install(root, skills, version, AGENTS, CLAUDE, overwrite);

	}

	private static Result result (SkillsInstaller.Report report, String path) {

		return report.entries().stream().filter(entry -> entry.path().equals(path)).findFirst().orElseThrow().result();

	}

	private static String read (Path root, String path) throws IOException {

		return Files.readString(root.resolve(".claude/skills").resolve(path), StandardCharsets.UTF_8);

	}

	@Test
	@DisplayName("まっさらなら全部置き、AGENTS.md と CLAUDE.md も置き、控えを書く")
	void freshInstall (@TempDir Path root) throws IOException {

		SkillsInstaller.Report report = install(root, skills("v1"), "1.5.0", false);

		assertEquals(Result.ADDED, result(report, "jimble/SKILL.md"));
		assertEquals("jimble v1", read(root, "jimble/SKILL.md"));
		assertEquals(List.of("AGENTS.md", "CLAUDE.md"), report.created());
		assertEquals("1.5.0", SkillsInstaller.recordedVersion(root));
		assertEquals("", report.previousVersion());

		// 2度目は何もしない
		SkillsInstaller.Report again = install(root, skills("v1"), "1.5.0", false);
		assertEquals(Result.UNCHANGED, result(again, "jimble/SKILL.md"));
		assertTrue(again.created().isEmpty());

	}

	@Test
	@DisplayName("jimble が置いたままのものは、新しい版に入れ替わる")
	void untouchedIsUpdated (@TempDir Path root) throws IOException {

		install(root, skills("v1"), "1.5.0", false);

		SkillsInstaller.Report report = install(root, skills("v2"), "1.6.0", false);

		assertEquals(Result.UPDATED, result(report, "jimble/SKILL.md"));
		assertEquals("jimble v2", read(root, "jimble/SKILL.md"));
		assertEquals("1.5.0", report.previousVersion());
		assertEquals("1.6.0", SkillsInstaller.recordedVersion(root));
		assertFalse(report.hasKept());

	}

	@Test
	@DisplayName("手で直したものは上書きしない。次の版でも上書きしない")
	void editedIsKept (@TempDir Path root) throws IOException {

		install(root, skills("v1"), "1.5.0", false);
		Files.writeString(root.resolve(".claude/skills/jimble/SKILL.md"), "jimble v1 + アプリの書き足し");

		SkillsInstaller.Report report = install(root, skills("v2"), "1.6.0", false);

		assertEquals(Result.KEPT_EDITED, result(report, "jimble/SKILL.md"));
		assertEquals("jimble v1 + アプリの書き足し", read(root, "jimble/SKILL.md"));
		assertEquals(Result.UPDATED, result(report, "jimble-db/SKILL.md"), "直していないほうは入れ替わる");
		assertTrue(report.hasKept());

		/*
		 * <b>控えを新しい版のハッシュにしてしまうと、ここで上書きされる。</b>
		 * 直したものは、何度揃えても直したものとして扱う
		 */
		SkillsInstaller.Report next = install(root, skills("v3"), "1.7.0", false);
		assertEquals(Result.KEPT_EDITED, result(next, "jimble/SKILL.md"));
		assertEquals("jimble v1 + アプリの書き足し", read(root, "jimble/SKILL.md"));

	}

	@Test
	@DisplayName("控えの無いもの（控えを書く前の jimble new が置いたもの）は、頼まれるまで触らない")
	void unknownIsKeptUntilOverwrite (@TempDir Path root) throws IOException {

		Path skill = root.resolve(".claude/skills/jimble/SKILL.md");
		Files.createDirectories(skill.getParent());
		Files.writeString(skill, "jimble 古い版");

		SkillsInstaller.Report report = install(root, skills("v2"), "1.6.0", false);

		assertEquals(Result.KEPT_UNKNOWN, result(report, "jimble/SKILL.md"));
		assertEquals("jimble 古い版", read(root, "jimble/SKILL.md"));
		assertEquals(Result.ADDED, result(report, "jimble-db/SKILL.md"));

		// 何度揃えても「分からない」のまま（控えに書くと、次から「手で直した」と言い換えてしまう）
		assertEquals(Result.KEPT_UNKNOWN, result(install(root, skills("v2"), "1.6.0", false), "jimble/SKILL.md"));

		SkillsInstaller.Report forced = install(root, skills("v2"), "1.6.0", true);

		assertEquals(Result.OVERWRITTEN, result(forced, "jimble/SKILL.md"));
		assertEquals("jimble v2", read(root, "jimble/SKILL.md"));

		// 上書きしたあとは jimble が置いたものとして扱う（次の版で入れ替わる）
		assertEquals(Result.UPDATED, result(install(root, skills("v3"), "1.7.0", false), "jimble/SKILL.md"));

	}

	@Test
	@DisplayName("AGENTS.md / CLAUDE.md は、あれば触らない")
	void agentsIsNotTouched (@TempDir Path root) throws IOException {

		Files.writeString(root.resolve("AGENTS.md"), "アプリの決まり");

		SkillsInstaller.Report report = install(root, skills("v1"), "1.5.0", false);

		assertEquals(List.of("CLAUDE.md"), report.created());
		assertEquals("アプリの決まり", Files.readString(root.resolve("AGENTS.md")));

	}

	@Test
	@DisplayName("この版で無くなった skill は、jimble が置いたままなら消し、控えからも外す")
	void goneUntouchedIsRemoved (@TempDir Path root) {

		install(root, skills("v1"), "1.5.0", false);

		SkillsInstaller.Report report = install(root, without(skills("v2"), "jimble-db/SKILL.md"), "1.6.0", false);

		assertEquals(List.of("jimble-db/SKILL.md"), report.removed());
		assertTrue(report.gone().isEmpty());
		assertFalse(Files.exists(root.resolve(".claude/skills/jimble-db")), "空になったフォルダも消す");
		assertFalse(report.hasKept());

		// 控えから外したので、次は何も言わない
		SkillsInstaller.Report next = install(root, without(skills("v2"), "jimble-db/SKILL.md"), "1.6.0", false);
		assertTrue(next.removed().isEmpty());
		assertTrue(next.gone().isEmpty());

	}

	@Test
	@DisplayName("この版で無くなった skill でも、手で直してあれば消さずに知らせ続ける。--overwrite なら消す")
	void goneEditedIsKeptUntilOverwrite (@TempDir Path root) throws IOException {

		install(root, skills("v1"), "1.5.0", false);
		Files.writeString(root.resolve(".claude/skills/jimble-db/SKILL.md"), "db v1 + アプリの書き足し");

		SkillsInstaller.Report report = install(root, without(skills("v2"), "jimble-db/SKILL.md"), "1.6.0", false);

		assertEquals(List.of("jimble-db/SKILL.md"), report.gone());
		assertTrue(report.removed().isEmpty());
		assertEquals("db v1 + アプリの書き足し", read(root, "jimble-db/SKILL.md"));
		assertTrue(report.hasKept());

		// 控えに残すので、次に揃えたときも知らせる（黙って「アプリの skill」にならない）
		SkillsInstaller.Report next = install(root, without(skills("v3"), "jimble-db/SKILL.md"), "1.7.0", false);
		assertEquals(List.of("jimble-db/SKILL.md"), next.gone());

		SkillsInstaller.Report forced = install(root, without(skills("v3"), "jimble-db/SKILL.md"), "1.7.0", true);
		assertEquals(List.of("jimble-db/SKILL.md"), forced.removed());
		assertFalse(Files.exists(root.resolve(".claude/skills/jimble-db/SKILL.md")));

	}

	@Test
	@DisplayName("この版で無くなった skill を、もう手で消してあれば控えから外すだけ")
	void goneAlreadyDeletedIsForgotten (@TempDir Path root) throws IOException {

		install(root, skills("v1"), "1.5.0", false);
		Files.delete(root.resolve(".claude/skills/jimble-db/SKILL.md"));

		SkillsInstaller.Report report = install(root, without(skills("v2"), "jimble-db/SKILL.md"), "1.6.0", false);

		assertTrue(report.removed().isEmpty());
		assertTrue(report.gone().isEmpty());
		assertFalse(Files.readString(root.resolve(".claude/jimble-skills.properties")).contains("jimble-db"));

	}

	@Test
	@DisplayName("控えが .claude/skills/ の外を指していても、そこは消さない")
	void recordOutsideSkillsIsIgnored (@TempDir Path root) throws IOException {

		install(root, skills("v1"), "1.5.0", false);

		Path outside = root.resolve("build.gradle.kts");
		Files.writeString(outside, "plugins {}");
		Path record = root.resolve(".claude/jimble-skills.properties");
		Files.writeString(record, Files.readString(record) + "skills/../../build.gradle.kts=" + SkillsInstaller.sha256("plugins {}".getBytes(StandardCharsets.UTF_8)) + "\n");

		SkillsInstaller.Report report = install(root, skills("v1"), "1.5.0", true);

		assertTrue(report.removed().isEmpty());
		assertTrue(Files.exists(outside));

	}

	@Test
	@DisplayName("skill を分けた版に上げると、分けた skill が足され、元の skill は案内だけに入れ替わる")
	void splitSkillsAreAddedAndOriginalIsReplaced (@TempDir Path root) throws IOException {

		install(root, skills("v1"), "2.7.6", false);

		Map<String, byte[]> split = new LinkedHashMap<>(skills("案内だけ"));
		split.put("jimble-db-select/SKILL.md", "引く".getBytes(StandardCharsets.UTF_8));
		split.put("jimble-db-tx/SKILL.md", "トランザクション".getBytes(StandardCharsets.UTF_8));

		SkillsInstaller.Report report = install(root, split, "2.7.8", false);

		assertEquals(Result.UPDATED, result(report, "jimble-db/SKILL.md"));
		assertEquals("db 案内だけ", read(root, "jimble-db/SKILL.md"));
		assertEquals(Result.ADDED, result(report, "jimble-db-select/SKILL.md"));
		assertEquals(Result.ADDED, result(report, "jimble-db-tx/SKILL.md"));
		assertFalse(report.hasKept());

		// 足した skill も控えに載り、次の版で入れ替わる
		Map<String, byte[]> next = new LinkedHashMap<>(split);
		next.put("jimble-db-tx/SKILL.md", "トランザクション v2".getBytes(StandardCharsets.UTF_8));
		assertEquals(Result.UPDATED, result(install(root, next, "2.7.9", false), "jimble-db-tx/SKILL.md"));

	}

	@Test
	@DisplayName("アプリが自分で置いた skill（控えに無いもの）は、名前が jimble で始まっていても触らない")
	void appOwnSkillIsNotTouched (@TempDir Path root) throws IOException {

		Path own = root.resolve(".claude/skills/jimble-shop/SKILL.md");
		Files.createDirectories(own.getParent());
		Files.writeString(own, "アプリの skill");

		SkillsInstaller.Report report = install(root, skills("v1"), "2.7.8", false);

		assertTrue(report.removed().isEmpty());
		assertTrue(report.gone().isEmpty());
		assertEquals("アプリの skill", Files.readString(own));

	}

	@Test
	@DisplayName("プラグインに入っている skill は、リポジトリの .claude/skills と同じもの")
	void bundledMatchesRepository () throws IOException {

		Map<String, byte[]> bundled = AiResources.skills();

		Path repository = Path.of("..").resolve(".claude/skills");

		try (Stream<Path> dirs = Files.list(repository)) {
			assertEquals(dirs.filter(dir -> Files.exists(dir.resolve("SKILL.md"))).count(), bundled.size(), bundled.keySet().toString());
		}

		for (Map.Entry<String, byte[]> skill : bundled.entrySet()) {
			assertArrayEquals(Files.readAllBytes(repository.resolve(skill.getKey())), skill.getValue(), skill.getKey());
		}

		assertFalse(AiResources.version().isEmpty());
		assertTrue(new String(AiResources.agents(), StandardCharsets.UTF_8).contains("jimbleSkills"));
		assertEquals("@AGENTS.md", new String(AiResources.claude(), StandardCharsets.UTF_8).trim());

	}

	@Test
	@DisplayName("配る skill は1つ 8 KB まで。name はフォルダ名と同じで、description（いつ読むか）がある")
	void bundledSkillsAreSmall () {

		for (Map.Entry<String, byte[]> skill : AiResources.skills().entrySet()) {

			String name = skill.getKey().substring(0, skill.getKey().indexOf('/'));
			String text = new String(skill.getValue(), StandardCharsets.UTF_8);

			assertTrue(skill.getValue().length <= MAX_SKILL_BYTES
				, "%s が %,d バイトあります（%,d まで）。分野ごとの skill に分けてください".formatted(skill.getKey(), skill.getValue().length, MAX_SKILL_BYTES));
			assertTrue(text.startsWith("---\nname: " + name + "\n"), name + " の name がフォルダ名と違います");
			assertTrue(text.contains("\ndescription: ") && text.indexOf("\n---\n", 4) > 0, name + " に description がありません");

		}

	}

	@Test
	@DisplayName("どのプラグインを当てても jimbleSkills が付き、2つ当てても1つだけ")
	void registeredOnceFromAnyPlugin () {

		Project project = ProjectBuilder.builder().build();

		project.getPluginManager().apply("io.jimble.jte");
		project.getPluginManager().apply("io.jimble.run");

		assertNotNull(project.getTasks().findByName(JimbleAiSupport.SKILLS_TASK));

		Project dbOnly = ProjectBuilder.builder().build();
		dbOnly.getPluginManager().apply("io.jimble.db");

		assertNotNull(dbOnly.getTasks().findByName(JimbleAiSupport.SKILLS_TASK));

	}

}
