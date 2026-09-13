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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
	@DisplayName("NF-C-04 内部パッケージは名前で分かる")
	void internalIsVisibleInTheName () throws IOException {

		Path root = projectRoot();
		Path list = root.resolve(LIST);

		Set<String> internals = sectionOf(list, "[internal]");
		Set<String> outside = new TreeSet<>(listedPackages(list));

		outside.removeAll(internals);

		List<String> problems = new ArrayList<>();

		for (String pkg : internals) {

			if (!isInternalName(pkg)) {
				problems.add("[internal] なのに名前で分からない: " + pkg);
			}

		}

		for (String pkg : outside) {

			if (isInternalName(pkg)) {
				problems.add("名前は internal なのに [internal] に無い: " + pkg);
			}

		}

		/*
		 * <b>表と名前が食い違うと、どちらを信じてよいか分からなくなる。</b>
		 * 名前は import に出るので<b>利用者が見るのはそちら</b>で、
		 * 表は<b>こちらが見る</b>——ずれたら、利用者だけが間違った側を見ることになる。
		 */
		assertTrue(problems.isEmpty(), String.join("\n  ", problems));

		assertTrue(internals.size() > 40, "内部パッケージが読めていない: " + internals.size());

	}

	/**
	 * 名前が「内部」と言っているか（要件 NF-C-04 / D-160）
	 *
	 * <p>
	 * {@code io.jimble.<モジュール>.internal} から下が内部である。
	 * </p>
	 *
	 * @param pkg	パッケージ
	 * @return	内部の名前なら true
	 */
	private static boolean isInternalName (String pkg) {

		return pkg.endsWith(".internal") || pkg.contains(".internal.");

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

	/**
	 * 公開パッケージのクラスに、public な可変フィールドが無いこと（D-173）
	 *
	 * <p>
	 * <b>フィールドはアクセサに置き換えられない。</b>{@code @Deprecated} を付けても
	 * 代替を同名で置けないので、<b>1.0 のあとは検証も、遅延計算も、不変化も、
	 * 防御的コピーも入れられなくなる</b>。
	 * </p>
	 *
	 * <p>
	 * ここは 9 つの公開型が該当していた（{@code DBConf} 20 本ほか）。
	 * <b>1つ直すだけでは、次に足された1本で元に戻る</b>ので、数えて見張る。
	 * </p>
	 *
	 * <p>ここで固定していないこと：内部パッケージと preview は見ていない。</p>
	 */
	@Test
	@DisplayName("D-173 公開パッケージに public な可変フィールドが無い")
	void noPublicMutableFields () throws IOException {

		List<String> found = publicSourceFiles().stream()
			.flatMap(file -> matchesIn(file, MUTABLE_FIELD).stream())
			.toList();

		assertTrue(found.isEmpty()
			, "public な可変フィールドは 1.0 のあと直せません:\n  " + String.join("\n  ", found));

	}

	/**
	 * 公開パッケージの {@code public static final} が、書き換えられる入れ物でないこと（D-173）
	 *
	 * <p>
	 * <b>{@code final} なのは参照だけである。</b>
	 * {@code public static final ArrayList} は<b>アプリから {@code clear()} できて、
	 * 消えるのはプロセス全体で1つの一覧</b>になる（{@code UserAgentInfo.DEVICE_LIST} がそうだった）。
	 * </p>
	 */
	@Test
	@DisplayName("D-173 公開パッケージの定数が、書き換えられる入れ物でない")
	void noPublicMutableConstants () throws IOException {

		List<String> found = publicSourceFiles().stream()
			.flatMap(file -> matchesIn(file, MUTABLE_CONSTANT).stream())
			.toList();

		assertTrue(found.isEmpty()
			, "外から書き換えられる定数です（List.of / Map.of / Set.of にしてください）:\n  "
				+ String.join("\n  ", found));

	}

	/**
	 * {@code public 型 名前;}（static でも final でもないもの）
	 *
	 * <p>メソッドと区別するため、名前のあとが {@code ;} か {@code =} のものだけを見る。</p>
	 */
	private static final Pattern MUTABLE_FIELD = Pattern.compile(
		"^\\s*public\\s+(?!static\\b)(?!final\\b)(?!abstract\\b)(?!class\\b)(?!interface\\b)"
			+ "(?!enum\\b)(?!record\\b)(?!sealed\\b)(?!non-sealed\\b)(?!default\\b)(?!synchronized\\b)"
			+ "[\\w.$<>,\\[\\]?\\s]+?\\s+(\\w+)\\s*(=[^;]*)?;\\s*$"
		, Pattern.MULTILINE);

	/** {@code public static final} で、中身を書き換えられる入れ物 */
	private static final Pattern MUTABLE_CONSTANT = Pattern.compile(
		"^\\s*public\\s+static\\s+final\\s+[\\w.<>,\\s]*\\s+(\\w+)\\s*="
			+ "\\s*new\\s+(?:[\\w]+\\.)*"
			+ "(ArrayList|LinkedList|HashMap|LinkedHashMap|TreeMap|HashSet|LinkedHashSet|TreeSet|ArrayDeque"
			+ "|StringBuilder|StringBuffer|AtomicReference|CopyOnWriteArrayList|ConcurrentHashMap)\\b"
		, Pattern.MULTILINE);

	/**
	 * 公開パッケージにあるソースファイル
	 *
	 * @return	ファイル
	 * @throws IOException	読めなかった場合
	 */
	private static List<Path> publicSourceFiles () throws IOException {

		Path root = projectRoot();
		Set<String> publics = sectionOf(root.resolve(LIST), "[public]");

		List<Path> files = new ArrayList<>();

		for (String module : MODULES) {

			Path source = root.resolve(module).resolve("src/main/java");

			if (!Files.isDirectory(source)) {
				continue;
			}

			try (Stream<Path> paths = Files.walk(source)) {

				paths.filter(Files::isRegularFile)
					.filter(f -> f.toString().endsWith(".java"))
					.filter(f -> publics.contains(
						source.relativize(f).getParent().toString().replace('/', '.').replace('\\', '.')))
					.forEach(files::add);

			}

		}

		assertFalse(files.isEmpty(), "公開パッケージのソースが1つも読めていない");

		return files;

	}

	/**
	 * 1つのファイルの中の当たり
	 *
	 * @param file		ファイル
	 * @param pattern	探すもの
	 * @return	「ファイル:行 中身」の一覧
	 */
	private static List<String> matchesIn (Path file, Pattern pattern) {

		List<String> found = new ArrayList<>();

		String text;

		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new AssertionError("読めませんでした: " + file, ex);
		}

		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {

			int line = (int) text.substring(0, matcher.start()).chars().filter(c -> c == '\n').count() + 1;
			found.add(file.getFileName() + ":" + line + "  " + matcher.group().trim());

		}

		return found;

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
