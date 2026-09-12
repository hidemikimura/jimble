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
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 公開 API と内部実装の線引きが古くなっていないか（要件 NF-C-03 / NF-C-04）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>線を引いただけの表は、必ず古くなる。</b>
 * パッケージを1つ増やしたときに表を直すのを忘れても、
 * <b>何も起きないので誰も気づかない</b>——気づくのは、
 * 内部のつもりだったものを利用者が使っていたと分かったときである。
 * </p>
 *
 * <p>
 * ここでは<b>ソースの木にあるパッケージと {@code docs/api-packages.txt} を突き合わせる。</b>
 * 片方にしか無いものがあれば落ちる。
 * <b>どちらなのかを決めるのは人の仕事</b>で、このテストは「決め忘れ」だけを塞ぐ。
 * </p>
 *
 * <p>
 * {@code FrameworkTables} の「定数を足して {@code ALL} に足し忘れる」を
 * リフレクションで塞いだのと同じ形である（D-68）。
 * </p>
 */
class ApiSurfaceTest {

	/** 線引きの表 */
	private static final String LIST = "docs/api-packages.txt";

	/**
	 * 外部の仕様に追随するので、1.0 の約束の対象外にするパッケージの頭（要件 D-158）
	 *
	 * <p>
	 * <b>MCP は2年で5版が出ており、毎回破壊的な変更が入っている。</b>
	 * jimble は1版だけ実装するので、仕様が変わるたびに
	 * 「公開 API を壊す」か「仕様に追随しない」かの二択になる——
	 * <b>「壊す前に非推奨期間を1マイナー置く」と同居できない。</b>
	 * </p>
	 */
	private static final List<String> PREVIEW_PREFIXES = List.of("io.jimble.mcp");

	/** 見に行くモジュール（公開しているものだけ。ツールと examples は見ない） */
	private static final List<String> MODULES = List.of(
		"jimble-core"
		, "jimble-util"
		, "jimble-db"
		, "jimble-web"
		, "jimble-batch"
		, "jimble-batch-manager"
		, "jimble-mq"
		, "jimble-mcp"
		, "jimble-otel"
		, "jimble-cli"
	);

	@Test
	@DisplayName("NF-C-04 すべてのパッケージが公開か内部か決まっている")
	void everyPackageIsClassified () throws IOException {

		Path root = projectRoot();

		/*
		 * <b>この表は .gitignore で消えうる。</b>docs/ はまるごと除外されていて、
		 * 出すものだけ ! で戻している。戻し忘れると<b>手元では通って CI で落ちる</b>ので、
		 * 「読めなかった」を IOException のまま出さずに、理由まで書く。
		 */
		assertTrue(Files.exists(root.resolve(LIST))
			, LIST + " がありません（.gitignore の /docs/* に飲まれていませんか）");

		Set<String> onDisk = packagesOnDisk(root);
		Set<String> listed = listedPackages(root.resolve(LIST));

		assertTrue(onDisk.size() > 100, "パッケージが少なすぎる（探し方が壊れている）: " + onDisk.size());

		Set<String> missing = new TreeSet<>(onDisk);
		missing.removeAll(listed);

		Set<String> stale = new TreeSet<>(listed);
		stale.removeAll(onDisk);

		if (!missing.isEmpty() || !stale.isEmpty()) {

			StringBuilder message = new StringBuilder("docs/api-packages.txt が実際のパッケージと合っていません。\n");

			if (!missing.isEmpty()) {
				message.append("\n【表に無い（公開か内部かを決めて足すこと）】\n");
				missing.forEach(p -> message.append("  ").append(p).append('\n'));
			}

			if (!stale.isEmpty()) {
				message.append("\n【もう存在しない（表から消すこと）】\n");
				stale.forEach(p -> message.append("  ").append(p).append('\n'));
			}

			fail(message.toString());

		}

	}

	@Test
	@DisplayName("D-158 追随する版を持つモジュールは、まるごと [preview] にある")
	void previewModulesAreNotPublic () throws IOException {

		Path root = projectRoot();

		Set<String> preview = sectionOf(root.resolve(LIST), "[preview]");

		List<String> misfiled = new ArrayList<>();

		for (String pkg : packagesOnDisk(root)) {

			boolean shouldBePreview = PREVIEW_PREFIXES.stream()
				.anyMatch(prefix -> pkg.equals(prefix) || pkg.startsWith(prefix + "."));

			if (shouldBePreview && !preview.contains(pkg)) {
				misfiled.add(pkg);
			}

		}

		/*
		 * <b>パッケージを1つ足したときが危ない。</b>
		 * {@code io.jimble.mcp.xxx} を作って [public] に書くと、
		 * <b>そこだけ 1.0 の約束の中に入る</b>——
		 * MCP の次の版が出たときに、壊せないものが1つ残る。
		 */
		assertTrue(misfiled.isEmpty()
			, "1.0 の約束の対象外のはずが [preview] にありません: " + misfiled);

		assertTrue(preview.size() >= 5, "[preview] が読めていない: " + preview.size());

	}

	@Test
	@DisplayName("NF-C-04 内部パッケージを examples が import していない")
	void examplesDoNotUseInternals () throws IOException {

		Path root = projectRoot();

		Set<String> internals = sectionOf(root.resolve(LIST), "[internal]");

		assertTrue(internals.size() > 10, "内部パッケージが読めていない: " + internals.size());

		List<String> offenders = new ArrayList<>();

		Path examples = root.resolve("examples");

		try (Stream<Path> files = Files.walk(examples)) {

			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {

				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {

					if (!line.startsWith("import io.jimble.")) {
						continue;
					}

					String type = line.substring("import ".length()).replace("static ", "").replace(";", "").trim();
					String pkg = packageOf(type);

					if (internals.contains(pkg)) {
						offenders.add(root.relativize(file) + " : " + type);
					}

				}

			}

		}

		/*
		 * <b>サンプルは「真似してよい書き方」の見本である。</b>
		 * そこが内部パッケージを触っていたら、
		 * <b>線の引き方が間違っているか、サンプルが間違っているか</b>のどちらかで、
		 * どちらにしても直さないといけない。
		 */
		assertTrue(offenders.isEmpty()
			, "サンプルが内部パッケージを import しています:\n  " + String.join("\n  ", offenders));

	}

	// region ここで固定していないこと

	/*
	 * - <b>クラス単位では見ていない。</b>公開パッケージの中に「本当は内部」の
	 *   クラスが混ざっていても落ちない。パッケージで線を引くと決めた以上、
	 *   その粒度でしか守れない（要件 NF-C-04）
	 * - <b>シグネチャの変更は見ていない。</b>公開 API のメソッドが消えても落ちない。
	 *   それを見るには前の版の jar と突き合わせる道具が要る。
	 *   1.0 に上げるときに入れるかどうかを決める
	 * - <b>docs/site が内部を名指ししていないかは見ていない。</b>
	 *   例に出てくる名前は文字列なので、import のようには拾えない
	 */

	// endregion

	// region 道具

	/**
	 * プロジェクトの根
	 *
	 * <p>テストの作業ディレクトリはモジュールなので、{@code settings.gradle.kts} まで遡る。</p>
	 *
	 * @return	根
	 */
	private static Path projectRoot () {

		Path at = Path.of("").toAbsolutePath();

		while (at != null) {

			if (Files.exists(at.resolve("settings.gradle.kts"))) {
				return at;
			}

			at = at.getParent();

		}

		throw new AssertionError("settings.gradle.kts が見つかりません");

	}

	/**
	 * ソースの木にあるパッケージ
	 *
	 * @param root	根
	 * @return	パッケージ
	 * @throws IOException	読めなかった場合
	 */
	private static Set<String> packagesOnDisk (Path root) throws IOException {

		Set<String> found = new TreeSet<>();

		for (String module : MODULES) {

			Path source = root.resolve(module).resolve("src/main/java");

			if (!Files.isDirectory(source)) {
				continue;
			}

			try (Stream<Path> dirs = Files.walk(source)) {

				dirs.filter(Files::isDirectory)
					.map(source::relativize)
					.map(Path::toString)
					.filter(p -> !p.isEmpty())
					.map(p -> p.replace('/', '.').replace('\\', '.'))
					.filter(p -> p.startsWith("io.jimble"))
					.forEach(found::add);

			}

		}

		return found;

	}

	/**
	 * 表に載っているパッケージ（節を問わない）
	 *
	 * @param list	表
	 * @return	パッケージ
	 * @throws IOException	読めなかった場合
	 */
	private static Set<String> listedPackages (Path list) throws IOException {

		Set<String> found = new LinkedHashSet<>();

		for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {

			String trimmed = line.trim();

			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
				continue;
			}

			found.add(trimmed);

		}

		return found;

	}

	/**
	 * 節のパッケージ
	 *
	 * @param list	表
	 * @param name	節（{@code [internal]} など）
	 * @return	パッケージ
	 * @throws IOException	読めなかった場合
	 */
	private static Set<String> sectionOf (Path list, String name) throws IOException {

		Set<String> found = new LinkedHashSet<>();
		boolean inside = false;

		for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {

			String trimmed = line.trim();

			if (trimmed.startsWith("[")) {
				inside = trimmed.startsWith(name);
				continue;
			}

			if (inside && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
				found.add(trimmed);
			}

		}

		return found;

	}

	/**
	 * import からパッケージを取る
	 *
	 * <p>
	 * {@code io.jimble.db.sql.SQL} → {@code io.jimble.db.sql}。
	 * {@code static} import（{@code ....Dsl.now}）は、
	 * 大文字で始まる部分より前までを取る。
	 * </p>
	 *
	 * @param type	型の名前
	 * @return	パッケージ
	 */
	private static String packageOf (String type) {

		StringBuilder pkg = new StringBuilder();

		for (String part : type.split("\\.")) {

			if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) {
				break;
			}

			if (!pkg.isEmpty()) {
				pkg.append('.');
			}

			pkg.append(part);

		}

		return pkg.toString();

	}

	// endregion

}
